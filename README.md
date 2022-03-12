# tinyprotocol
![Maven releases](https://repo.kcra.me/api/badge/latest/releases/me/kcra/tinyprotocol/tinyprotocol)
![Maven snapshots](https://repo.kcra.me/api/badge/latest/snapshots/me/kcra/tinyprotocol/tinyprotocol)  

A Gradle plugin for generating multi-version packet class wrappers for Minecraft: Java Edition.

## Usage
```kotlin
// settings.gradle(.kts)
pluginManagement {
    repositories {
        mavenCentral()
        maven("https://repo.kcra.me/releases")
        maven("https://repo.kcra.me/snapshots")
        maven("https://repo.screamingsandals.org/public")
    }
}
```

```kotlin
// build.gradle(.kts)
import me.kcra.tinyprotocol.utils.ReflectType

plugins {
    id("me.kcra.tinyprotocol") version "0.0.3"
    java
}

// your build logic

protocol {
    version("1.18.1") // you can specify multiple target versions
    // you can manually specify a protocol version
    // if the PrismarineJS repo hasn't been updated for your version yet
    version("1.18", 757)
    
    packet("net/minecraft/network/protocol/game/ClientboundAddMobPacket") // you can specify multiple packets

    // optional settings
    sourceSet = "generated" // the source set name, defaults to "generated"
    className = "{className}" // the packet class name template, {className} is replaced with the mojang-mapped class name, defaults to "{className}"
    packageName = null // the packet package name, defaults to mojang-mapped package name
    utilsPackageName = "me.kcra.tinyprotocol.utils" // the utils package name, defaults to "me.kcra.tinyprotocol.utils"
    // mapping checksum verification, defaults to true, setting this to false may cause unexpected errors
    // but marginally improves build performance (some mappings don't have checksums and are re-downloaded every time)
    verifyChecksums = true
    generateMetadata = false // generates a @Metadata annotation with additional information where available, defaults to false
    
    // optional Reflect class settings
    reflect {
        // sets the Reflect class implementation type, defaults to ZERODEP
        // available: ZERODEP, NARCISSUS, OBJENESIS
        // NARCISSUS impl needs the Narcissus library on the classpath: https://github.com/toolfactory/narcissus
        // OBJENESIS impl needs the Objenesis library on the classpath: https://github.com/easymock/objenesis
        type = ReflectType.ZERODEP
        // the Narcissus library package name, defaults to "io.github.toolfactory.narcissus", not needed if not using NARCISSUS impl
        narcissusPackage = "io.github.toolfactory.narcissus"
        // the Objenesis library package name, defaults to "org.objenesis", not needed if not using OBJENESIS impl
        objenesisPackage = "org.objenesis"
    }
}
```

## Features

- [x] Wrapper generation
- [x] @Metadata annotation
- [x] Mapping cache
- [x] FriendlyByteBuf read/write methods
- [x] [Narcissus](https://github.com/toolfactory/narcissus) and [Objenesis](https://github.com/easymock/objenesis) support (to provide an alternative to the Unsafe usages)
