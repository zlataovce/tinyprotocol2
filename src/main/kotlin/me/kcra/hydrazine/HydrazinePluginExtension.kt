package me.kcra.hydrazine

abstract class HydrazinePluginExtension {
    internal val packets: MutableList<String> = mutableListOf()
    internal val versions: MutableMap<String, Int> = mutableMapOf()
    var sourceSet: String = "generated"

    fun packet(def: String) = packets.add(def)
    fun version(protocol: Int, ver: String) = versions.put(ver, protocol)
}