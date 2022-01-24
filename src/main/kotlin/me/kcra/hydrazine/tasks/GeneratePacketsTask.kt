package me.kcra.hydrazine.tasks

import com.fasterxml.jackson.module.kotlin.readValue
import com.uchuhimo.collections.BiMap
import com.uchuhimo.collections.toMutableBiMap
import me.kcra.acetylene.core.TypedMappingFile
import me.kcra.acetylene.core.ancestry.ClassAncestorTree
import me.kcra.hydrazine.utils.MAPPER
import me.kcra.hydrazine.utils.ProtocolData
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URL
import java.nio.file.Path
import javax.inject.Inject

abstract class GeneratePacketsTask @Inject constructor(private val sourceSet: SourceSet) : HydrazineTask() {
    init {
        group = "hydrazine"
        description = "Generates the selected packet wrappers."
    }

    @TaskAction
    fun run() {
        val cacheFile: File = Path.of(workFolder.absolutePath, "joined.json").toFile()
        val mappings: List<TypedMappingFile> = MAPPER.readValue(cacheFile)
        val protocols: BiMap<String, Int> = protocolVersions()
        for (name: String in extension.packets) {
            val tree: ClassAncestorTree = ClassAncestorTree.of(name, mappings)
        }
    }

    private fun protocolVersions(): BiMap<String, Int> =
        MAPPER.readValue<List<ProtocolData>>(URL("https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/common/protocolVersions.json"))
            .associateBy({ it.minecraftVersion }, { it.version })
            .toMutableBiMap()
            .also { it.putAll(extension.versions) }
}