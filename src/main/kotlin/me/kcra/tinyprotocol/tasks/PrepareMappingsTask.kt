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

package me.kcra.tinyprotocol.tasks

import me.kcra.acetylene.core.TypedMappingFile
import me.kcra.acetylene.core.utils.Pair
import me.kcra.acetylene.srgutils.SrgUtilsMappingLoader
import me.kcra.tinyprotocol.TinyProtocolPluginExtension
import me.kcra.tinyprotocol.utils.*
import net.minecraftforge.srgutils.IMappingFile
import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Path
import javax.inject.Inject

abstract class PrepareMappingsTask @Inject constructor(private val extension: TinyProtocolPluginExtension) : DefaultTask() {
    init {
        group = "protocol"
        description = "Prepares mappings for all selected versions."
    }

    @TaskAction
    fun run() {
        val workFolder: File = Path.of(project.buildDir.absolutePath, "tinyprotocol").toFile().also { it.mkdirs() }
        val files: MutableList<TypedMappingFile> = mutableListOf()
        for (ver: String in extension.versions.keys) {
            files.add(
                SrgUtilsMappingLoader.of(
                    // order matters
                    Pair.of(MappingType.MOJANG, minecraftResource(ver, "server_mappings", workFolder, extension.verifyChecksums)?.let { IMappingFile.load(it).reverse() }),
                    Pair.of(MappingType.INTERMEDIARY, intermediaryMapping(ver, workFolder, extension.verifyChecksums)),
                    Pair.of(MappingType.SEARGE, seargeMapping(ver, workFolder, extension.verifyChecksums)?.let { IMappingFile.load(it) }),
                    Pair.of(MappingType.SPIGOT, spigotMapping(ver, workFolder, extension.verifyChecksums))
                ).loadTyped()
            )
            logger.log(LogLevel.LIFECYCLE, "Loaded mappings for $ver.")
        }
        project.tasks.withType(GeneratePacketsTask::class.java) {
            it.mappings = files
        }
    }
}