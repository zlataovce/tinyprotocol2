package me.kcra.hydrazine

abstract class HydrazinePluginExtension {
    internal val packets: MutableList<String> = mutableListOf()
    internal val versions: MutableMap<String, Int> = mutableMapOf()
    var sourceSet: String = "generated"

    fun packet(def: String) = packets.add(def)

    fun version(ver: String) = versions.put(ver, -1)
    fun version(ver: String, protocol: Int) = if (protocol > -1) versions.put(ver, protocol)
        else throw IllegalArgumentException("Protocol version must be zero or higher")
}