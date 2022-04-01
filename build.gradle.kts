plugins {
    kotlin("jvm") version "1.6.10"
    id("com.gradle.plugin-publish") version "0.20.0"
    id("org.cadixdev.licenser") version "0.6.1"
    id("java-gradle-plugin")
    id("maven-publish")
}

group = "me.kcra.tinyprotocol"
version = "0.0.4-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.kcra.me/releases")
    maven("https://repo.kcra.me/snapshots")
    maven("https://repo.screamingsandals.org/public")
}

dependencies {
    compileOnly(kotlin("stdlib"))
    implementation("me.kcra.acetylene:srgutils:0.0.2-SNAPSHOT")
    implementation("com.squareup:javapoet:1.13.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.2") {
        constraints {
            // bump jackson-databind version
            implementation("com.fasterxml.jackson.core:jackson-databind:2.13.2.2")
        }
    }
}

gradlePlugin {
    plugins {
        create("tinyprotocol") {
            id = "me.kcra.tinyprotocol"
            displayName = "Plugin for generating Minecraft packet wrappers"
            description = "A plugin for generating multi-version packet wrappers for Minecraft: JE"
            implementationClass = "me.kcra.tinyprotocol.TinyProtocolPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/zlataovce/tinyprotocol2"
    vcsUrl = "https://github.com/zlataovce/tinyprotocol2.git"
    tags = listOf("minecraft", "obfuscation", "packet")
}

configure<PublishingExtension> {
    repositories {
        maven {
            url = if ((project.version as String).endsWith("-SNAPSHOT")) uri("https://repo.kcra.me/snapshots")
                else uri("https://repo.kcra.me/releases")
            credentials {
                username = System.getenv("REPO_USERNAME")
                password = System.getenv("REPO_PASSWORD")
            }
        }
    }
}

java {
    withSourcesJar()
}

configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
}

license {
    header(file("license_header.txt"))
}
