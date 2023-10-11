package dev.isxander.pronounify.utils

import com.google.common.cache.CacheBuilder
import com.google.common.collect.Sets
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*

object PronounManager {
    // this blocks chat messages from being displayed, so it should be relatively short
    private val FETCH_PRONOUN_TIMEOUT = Duration.ofMillis(2000)

    private val pronounsCache = CacheBuilder.newBuilder().apply {
        expireAfterAccess(Duration.ofMinutes(10))
        maximumSize(500)
    }.build<UUID, Pronouns>()

    private val inProgressFetching = Sets.newConcurrentHashSet<UUID>()

    private val pronounEvents = mutableMapOf<UUID, MutableList<(Pronouns) -> Unit>>()

    fun isCurrentlyFetching(uuid: UUID) = uuid in inProgressFetching

    fun isPronounCached(uuid: UUID) = pronounsCache.getIfPresent(uuid) != null

    fun getPronoun(uuid: UUID) = pronounsCache.getIfPresent(uuid)!!

    @JvmOverloads
    fun cachePronoun(uuid: UUID, completion: ((Pronouns) -> Unit)? = null) {
        if (isPronounCached(uuid) || isCurrentlyFetching(uuid))
            return

        completion?.let { listenToPronounGet(uuid, it) }

        inProgressFetching += uuid

        runAsync {
            val pronouns = try {
                fetchPronoun(uuid)
            } catch (e: Exception) {
                LOGGER.error("Failed to get pronouns for %s".format(uuid), e)
                Pronouns.UNSPECIFIED
            }

            // always cache and run callbacks, even in case of error, because otherwise chat messages will disappear
            pronounsCache.put(uuid, pronouns)
            inProgressFetching -= uuid
            pronounEvents[uuid]?.forEach { it(pronouns) }
            pronounEvents.remove(uuid)
        }
    }

    fun bulkCachePronouns(uuids: MutableCollection<UUID>) {
        val filtered = uuids.filterNot { isCurrentlyFetching(it) || isPronounCached(it) }
        val chunked = filtered.chunked(50)

        inProgressFetching.addAll(filtered)

        runAsync { runBlocking {
            val httpClient = HttpClient.newHttpClient()
            coroutineScope {
                chunked.map {
                    async {
                        val pronouns = try {
                            val url = URI.create("https://pronoundb.org/api/v1/lookup-bulk?platform=minecraft&ids=${it.joinToString(",")}")
                            val request = HttpRequest.newBuilder(url).timeout(FETCH_PRONOUN_TIMEOUT).build()
                            val response = withContext(Dispatchers.IO) {
                                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                            }

                            Json.decodeFromString<Map<String, String>>(response.body())
                                .mapKeys { (k, _) -> UUID.fromString(k) }
                                .mapValues { (_, v) -> Pronouns.fromId(v) }
                        } catch (e: Exception) {
                            LOGGER.error("Failed to get batch of pronouns", e)
                            it.associateWith { Pronouns.UNSPECIFIED }
                        }

                        pronounsCache.putAll(pronouns)
                        inProgressFetching.removeAll(it.toSet())
                    }
                }.awaitAll()
            }
        }}
    }

    private fun listenToPronounGet(uuid: UUID, listener: (Pronouns) -> Unit) {
        pronounEvents.getOrPut(uuid) { mutableListOf() } += listener
    }

    private fun fetchPronoun(uuid: UUID): Pronouns {
        val httpClient = HttpClient.newHttpClient()
        val url = URI.create("https://pronoundb.org/api/v1/lookup?platform=minecraft&id=$uuid")
        val request = HttpRequest.newBuilder(url).timeout(FETCH_PRONOUN_TIMEOUT).build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return Json.decodeFromString<SingleLookupResponse>(response.body()).toEnum()!!
    }

    @Serializable
    private data class SingleLookupResponse(val pronouns: String) {
        fun toEnum() = Pronouns.fromId(pronouns)
    }
}
