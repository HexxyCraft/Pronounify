plugins {
    java

    val kotlinVersion: String by System.getProperties()
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("fabric-loom") version "0.12.+"
    id("io.github.juuxel.loom-quiltflower") version "1.7.+"
}

group = "dev.isxander"
version = "2.1.2"

repositories {
    mavenCentral()
    maven("https://maven.isxander.dev/releases")
    maven("https://maven.shedaniel.me")
    maven("https://maven.terraformersmc.com/releases")
    maven("https://jitpack.io")
}

val minecraftVersion: String by project

dependencies {
    val fabricLoaderVersion: String by project
    val kotlinVersion: String by System.getProperties()

    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$minecraftVersion+build.+:v2")

    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation(fabricApi.module("fabric-resource-loader-v0", "0.57.3+1.19.1"))
    modImplementation("net.fabricmc:fabric-language-kotlin:1.8.1+kotlin.$kotlinVersion")

    include(implementation("dev.isxander.settxi:settxi-core:2.7.1")!!)
    include(implementation("dev.isxander.settxi:settxi-kotlinx-serialization:2.7.1")!!)
    include(modImplementation("dev.isxander.settxi:settxi-gui-cloth-config:2.7.1:fabric-1.19.2") {
        exclude(group = "me.shedaniel.cloth")
    })

    modImplementation("me.shedaniel.cloth:cloth-config-fabric:8.+")
    modImplementation("com.terraformersmc:modmenu:4.0.+")
}

loom {
    clientOnlyMinecraftJar()
}

java {
    withSourcesJar()
}

tasks {
    remapJar {
        archiveClassifier.set("fabric-$minecraftVersion")
    }

    remapSourcesJar {
        archiveClassifier.set("fabric-$minecraftVersion-sources")
    }

    processResources {
        val modId: String by project
        val modName: String by project
        val modDescription: String by project
        val githubProject: String by project

        inputs.property("id", modId)
        inputs.property("group", project.group)
        inputs.property("name", modName)
        inputs.property("description", modDescription)
        inputs.property("version", project.version)
        inputs.property("github", githubProject)

        filesMatching(listOf("fabric.mod.json", "quilt.mod.json")) {
            expand(
                "id" to modId,
                "group" to project.group,
                "name" to modName,
                "description" to modDescription,
                "version" to project.version,
                "github" to githubProject,
            )
        }
    }

    register("releaseMod") {
        group = "mod"

        dependsOn("modrinth")
        dependsOn("modrinthSyncBody")
        dependsOn("curseforge")
        dependsOn("publish")
        dependsOn("githubRelease")
    }
}
