package me.kcra.hydrazine.tasks

import com.fasterxml.jackson.module.kotlin.readValue
import com.uchuhimo.collections.BiMap
import com.uchuhimo.collections.MutableBiMap
import com.uchuhimo.collections.toMutableBiMap
import me.kcra.acetylene.core.TypedMappingFile
import me.kcra.acetylene.core.ancestry.ClassAncestorTree
import me.kcra.hydrazine.HydrazinePluginExtension
import me.kcra.hydrazine.utils.MAPPER
import me.kcra.hydrazine.utils.ProtocolData
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.net.URL
import javax.inject.Inject

abstract class GeneratePacketsTask @Inject constructor(private val extension: HydrazinePluginExtension, private val sourceSet: SourceSet) : DefaultTask() {
    internal abstract var mappings: List<TypedMappingFile>

    init {
        group = "hydrazine"
        description = "Generates the selected packet wrappers."
    }

    @TaskAction
    fun run() {
        val protocols: BiMap<String, Int> = protocolVersions()
        for (name: String in extension.packets) {
            val tree: ClassAncestorTree = ClassAncestorTree.of(name, mappings)
        }
    }

    private fun protocolVersions(): BiMap<String, Int> {
        val versions: MutableBiMap<String, Int> = project.extensions.getByType(HydrazinePluginExtension::class.java).versions.toMutableBiMap()
        if (versions.containsValue(-1)) {
            val refreshedVersions: Map<String, Int> =
                MAPPER.readValue<List<ProtocolData>>(URL("https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/common/protocolVersions.json"))
                    .associateBy({ it.minecraftVersion }, { it.version })
            for (entry: Map.Entry<String, Int> in versions.entries) {
                if (entry.value == -1) {
                    versions[entry.key] = refreshedVersions[entry.key] ?: throw RuntimeException("Could not update version ${entry.key} definition")
                }
            }
        }
        return versions
    }
}