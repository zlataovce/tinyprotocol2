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
plugins {
    id("me.kcra.tinyprotocol") version "0.0.2-SNAPSHOT"
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
    packageName = null // the packet package name, defaults to mojang-mapped package name
    utilsPackageName = "me.kcra.tinyprotocol.utils" // the utils package name, defaults to "me.kcra.tinyprotocol.utils"
    // mapping checksum verification, defaults to true, setting this to false may cause unexpected errors
    // but marginally improves build performance (some mappings don't have checksums and are re-downloaded every time)
    verifyChecksums = true
    generateMetadata = false // generates a @Metadata annotation with additional information where available, defaults to false
}
```

## Features

- [x] Wrapper generation
- [x] @Metadata annotation
- [x] Mapping cache
- [x] FriendlyByteBuf read/write methods
- [x] [Narcissus](https://github.com/toolfactory/narcissus) and [Objenesis](https://github.com/easymock/objenesis) support (to provide an alternative to the Unsafe usages)
