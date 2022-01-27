# tinyprotocol
A Gradle plugin for generating multi-version packet class wrappers for Minecraft: Java Edition.

## Usage
```kotlin
plugins {
    id("me.kcra.tinyprotocol") version "0.0.1-SNAPSHOT"
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
}
```