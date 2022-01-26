package me.kcra.tinyprotocol

abstract class TinyProtocolPluginExtension {
    internal val packets: MutableList<String> = mutableListOf()
    internal val versions: MutableMap<String, Int> = mutableMapOf()
    var sourceSet: String = "generated"
    var packageName: String? = null
    var utilsPackageName: String? = null
    var mutable = false

    fun packet(vararg def: String) = packets.addAll(def)

    fun version(vararg ver: String) = ver.forEach { versions[it] = -1 }
    fun version(ver: String, protocol: Int) = if (protocol > -1) versions.put(ver, protocol)
        else throw IllegalArgumentException("Protocol version must be zero or higher")
}