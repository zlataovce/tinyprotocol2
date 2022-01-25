plugins {
    kotlin("jvm") version "1.6.10"
    id("com.gradle.plugin-publish") version "0.20.0"
    id("java-gradle-plugin")
    id("maven-publish")
}

group = "me.kcra.hydrazine"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.screamingsandals.org/public")
    mavenLocal()
}

dependencies {
    compileOnly(kotlin("stdlib"))
    implementation("me.kcra.acetylene:srgutils:0.0.1-SNAPSHOT")
    implementation("net.minecraftforge:srgutils:0.4.11-SNAPSHOT")
    implementation("com.squareup:javapoet:1.13.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
}

gradlePlugin {
    plugins {
        create("hydrazine") {
            id = "me.kcra.hydrazine"
            displayName = "Plugin for generating Minecraft packet wrappers"
            description = "A plugin for generating multi-version packet wrappers for Minecraft: JE"
            implementationClass = "me.kcra.hydrazine.HydrazinePlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/zlataovce/hydrazine"
    vcsUrl = "https://github.com/zlataovce/hydrazine.git"
    tags = listOf("minecraft", "obfuscation", "packet")
}