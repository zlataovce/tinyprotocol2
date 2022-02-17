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
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

abstract class PrepareMappingsTask @Inject constructor(private val extension: TinyProtocolPluginExtension) : DefaultTask() {
    init {
        group = "protocol"
        description = "Prepares mappings for all selected versions."
    }

    @TaskAction
    fun run() {
        val workFolder: File = Path.of(project.buildDir.absolutePath, "tinyprotocol").toFile().also { it.mkdirs() }
        val futures: MutableList<CompletableFuture<TypedMappingFile>> = mutableListOf()

        for (ver: String in extension.versions.keys) {
            futures.add(
                CompletableFuture.supplyAsync {
                    return@supplyAsync SrgUtilsMappingLoader.of(
                        // order matters
                        Pair.of(MappingType.MOJANG, minecraftResource(ver, "server_mappings", workFolder, extension.verifyChecksums)?.let { IMappingFile.load(it).reverse() }),
                        Pair.of(MappingType.INTERMEDIARY, intermediaryMapping(ver, workFolder, extension.verifyChecksums)),
                        Pair.of(MappingType.SEARGE, seargeMapping(ver, workFolder, extension.verifyChecksums)?.let { IMappingFile.load(it) }),
                        Pair.of(MappingType.SPIGOT, spigotMapping(ver, workFolder, extension.verifyChecksums))
                    ).loadTyped().also { logger.log(LogLevel.LIFECYCLE, "Loaded mappings for $ver.") }
                }
            )
        }

        project.tasks.withType(GeneratePacketsTask::class.java) {
            it.mappings = futures.stream().map { f -> f.join() }.toList()
        }
    }
}