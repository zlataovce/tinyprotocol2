package me.kcra.tinyprotocol

import me.kcra.tinyprotocol.utils.ReflectType

abstract class TinyProtocolPluginExtension {
    internal val packets: MutableList<String> = mutableListOf()
    internal val versions: MutableMap<String, Int> = mutableMapOf()
    internal val reflectOptions: ReflectOptions = ReflectOptions()
    var sourceSet: String = "generated"
    var packageName: String? = null
    var utilsPackageName: String = "me.kcra.tinyprotocol.utils"
    var verifyChecksums: Boolean = true
    var generateMetadata: Boolean = false

    fun packet(vararg def: String) = packets.addAll(def)

    fun version(vararg ver: String) = ver.forEach { versions[it] = -1 }
    fun version(ver: String, protocol: Int) = if (protocol > -1) versions.put(ver, protocol)
        else throw IllegalArgumentException("Protocol version must be zero or higher")

    fun reflect(configurer: (ReflectOptions) -> Unit) = configurer(reflectOptions)

    data class ReflectOptions(
        var type: ReflectType = ReflectType.ZERODEP,
        var narcissusPackage: String = "io.github.toolfactory.narcissus",
        var objenesisPackage: String = "org.objenesis"
    )
}