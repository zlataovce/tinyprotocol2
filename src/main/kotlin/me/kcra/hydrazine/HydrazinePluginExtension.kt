package me.kcra.hydrazine

abstract class HydrazinePluginExtension {
    internal val packets: MutableList<String> = mutableListOf()
    internal val versions: MutableMap<String, Int> = mutableMapOf()
    var sourceSet: String = "generated"
    var packageName: String? = null
    var mutable = false

    fun packet(vararg def: String) = packets.addAll(def)

    fun version(ver: String) = versions.put(ver, -1)
    fun version(vararg ver: String) = ver.forEach { versions[it] = -1 }
    fun version(ver: String, protocol: Int) = if (protocol > -1) versions.put(ver, protocol)
        else throw IllegalArgumentException("Protocol version must be zero or higher")
}