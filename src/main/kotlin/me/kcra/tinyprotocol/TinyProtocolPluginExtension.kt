/*
 * This file is part of tinyprotocol2, licensed under the MIT License.
 *
 * Copyright (c) 2022 Matouš Kučera
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.kcra.tinyprotocol

import me.kcra.tinyprotocol.utils.ReflectType

abstract class TinyProtocolPluginExtension {
    internal val packets: MutableList<String> = mutableListOf()
    internal val versions: MutableMap<String, Int> = mutableMapOf()
    internal val reflectOptions: ReflectOptions = ReflectOptions()
    var sourceSet: String = "generated"
    var className: String = "{className}"
    var packageName: String? = null
    var utilsPackageName: String = "me.kcra.tinyprotocol.utils"
    var verifyChecksums: Boolean = true
    var generateMetadata: Boolean = false

    fun packet(vararg def: String) = packets.addAll(def)

    fun version(vararg ver: String) = ver.forEach { versions[it] = -1 }
    fun version(ver: String, protocol: Int) = if (protocol > -1) versions.put(ver, protocol)
        else throw IllegalArgumentException("Protocol version must be zero or higher")

    fun reflect(configurer: ReflectOptions.() -> Unit) = configurer(reflectOptions)

    data class ReflectOptions(
        var type: ReflectType = ReflectType.ZERODEP,
        var narcissusPackage: String = "io.github.toolfactory.narcissus",
        var objenesisPackage: String = "org.objenesis"
    )
}