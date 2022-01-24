package me.kcra.hydrazine.tasks

import me.kcra.acetylene.core.TypedMappingFile
import me.kcra.acetylene.core.utils.Pair
import me.kcra.acetylene.srgutils.SrgUtilsMappingLoader
import me.kcra.hydrazine.HydrazinePluginExtension
import me.kcra.hydrazine.utils.*
import net.minecraftforge.srgutils.IMappingFile
import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Path
import javax.inject.Inject

abstract class PrepareMappingsTask @Inject constructor(private val extension: HydrazinePluginExtension) : DefaultTask() {
    init {
        group = "hydrazine"
        description = "Prepares mappings for all selected versions."
    }

    @TaskAction
    fun run() {
        val workFolder: File = Path.of(project.buildDir.absolutePath, "hydrazine").toFile().also { it.mkdirs() }
        val files: MutableList<TypedMappingFile> = mutableListOf()
        for (ver: String in extension.versions.keys) {
            files.add(
                SrgUtilsMappingLoader.of(
                    Pair.of(MappingType.MOJANG, minecraftResource(ver, "server_mappings", workFolder)?.let { IMappingFile.load(it).reverse() }),
                    Pair.of(MappingType.INTERMEDIARY, intermediaryMapping(ver, workFolder)),
                    Pair.of(MappingType.SEARGE, seargeMapping(ver, workFolder)?.let { IMappingFile.load(it) }),
                    Pair.of(MappingType.SPIGOT, spigotMapping(ver, workFolder))
                ).loadTyped()
            )
            logger.log(LogLevel.LIFECYCLE, "Loaded mappings for $ver.")
        }
        project.tasks.withType(GeneratePacketsTask::class.java) {
            it.mappings = files
        }
    }
}