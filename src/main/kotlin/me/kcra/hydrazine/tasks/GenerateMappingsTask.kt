package me.kcra.hydrazine.tasks

import me.kcra.acetylene.core.TypedMappingFile
import me.kcra.acetylene.srgutils.SrgUtilsMappingLoader
import me.kcra.hydrazine.utils.*
import net.minecraftforge.srgutils.IMappingFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Path

abstract class GenerateMappingsTask : HydrazineTask() {
    @Internal
    private val cacheFile: File = Path.of(workFolder.absolutePath, "joined.json").toFile()

    init {
        group = "hydrazine"
        description = "Generates a file with mappings for all selected versions."
        outputs.upToDateWhen { cacheFile.isFile }
    }

    @TaskAction
    fun run() {
        val files: MutableList<TypedMappingFile> = mutableListOf()
        for (ver: String in extension.versions.keys) {
            val mojangMapping: File? = minecraftResource(ver, "server_mappings", workFolder)
            var mojangFile: IMappingFile? = null
            if (mojangMapping != null) {
                mojangFile = IMappingFile.load(mojangMapping).reverse()
            }
            files.add(
                SrgUtilsMappingLoader.of(
                    me.kcra.acetylene.core.utils.Pair.of(MappingType.MOJANG, mojangFile),
                    me.kcra.acetylene.core.utils.Pair.of(MappingType.INTERMEDIARY, intermediaryMapping(ver, workFolder)),
                    me.kcra.acetylene.core.utils.Pair.of(MappingType.SEARGE, seargeMapping(ver, workFolder)),
                    me.kcra.acetylene.core.utils.Pair.of(MappingType.SPIGOT, spigotMapping(ver, workFolder))
                ).loadTyped()
            )
            println("Loaded mappings for $ver.")
        }
        MAPPER.writeValue(cacheFile, files)
    }
}