package me.kcra.hydrazine.tasks

import com.fasterxml.jackson.module.kotlin.readValue
import com.squareup.javapoet.*
import com.uchuhimo.collections.BiMap
import com.uchuhimo.collections.MutableBiMap
import com.uchuhimo.collections.toMutableBiMap
import me.kcra.acetylene.core.TypedDescriptableMapping
import me.kcra.acetylene.core.TypedMappingFile
import me.kcra.acetylene.core.ancestry.ClassAncestorTree
import me.kcra.hydrazine.HydrazinePluginExtension
import me.kcra.hydrazine.utils.MAPPER
import me.kcra.hydrazine.utils.MappingType
import me.kcra.hydrazine.utils.ProtocolData
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.net.URL
import javax.inject.Inject
import javax.lang.model.element.Modifier

abstract class GeneratePacketsTask @Inject constructor(private val extension: HydrazinePluginExtension, private val sourceSet: SourceSet) : DefaultTask() {
    @get:Internal
    internal abstract var mappings: List<TypedMappingFile>

    companion object {
        private val primitiveTypes: List<String> = listOf("int", "void", "long", "float", "double", "boolean", "short", "byte", "char")
    }

    init {
        group = "hydrazine"
        description = "Generates the selected packet wrappers."
    }

    @TaskAction
    fun run() {
        val protocols: BiMap<String, Int> = protocolVersions()
        for (name: String in extension.packets) {
            val tree: ClassAncestorTree = ClassAncestorTree.of(name, mappings)
            if (tree.size() == 0) {
                throw RuntimeException("Could not map class $name")
            }
            val className: String = tree.classes[0].mapped(MappingType.MOJANG)?.replace('/', '.')
                ?: throw RuntimeException("Could not map class $name")
            val builder: TypeSpec.Builder = TypeSpec.classBuilder("W" + className.substring(className.lastIndexOf('.') + 1))
                .addModifiers(Modifier.PUBLIC)
            for (field: TypedDescriptableMapping in tree.classes[0].fields) {
                val type: String = convertType(field.descriptor)
                if (type.startsWith("java") || primitiveTypes.contains(type)) {
                    // basic java type
                    builder.createField(
                        FieldSpec.builder(bestGuess(type), field.mapped(MappingType.MOJANG))
                            .addModifiers(Modifier.PRIVATE)
                    )
                }
            }

            val constructorBuilder: MethodSpec.Builder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
            for (field: FieldSpec in builder.fieldSpecs) {
                constructorBuilder.addParameter(field.type, field.name)
                constructorBuilder.addStatement("this." + field.name + " = " + field.name)
            }
            builder.addMethod(constructorBuilder.build())
            JavaFile.builder(
                extension.packageName ?: className.substring(0, className.lastIndexOf('.')),
                builder.build()
            ).build().writeToFile(sourceSet.java.srcDirs.first())
        }
    }

    private fun protocolVersions(): BiMap<String, Int> {
        val versions: MutableBiMap<String, Int> = project.extensions.getByType(HydrazinePluginExtension::class.java).versions.toMutableBiMap()
        if (versions.containsValue(-1)) {
            val refreshedVersions: Map<String, Int> =
                MAPPER.readValue<List<ProtocolData>>(URL("https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/common/protocolVersions.json"))
                    .associateBy({ it.minecraftVersion }, { it.version })
            for (entry: MutableMap.MutableEntry<String, Int> in versions.entries) {
                if (entry.value == -1) {
                    entry.setValue(refreshedVersions[entry.key] ?: throw RuntimeException("Could not update version ${entry.key} definition"))
                }
            }
        }
        return versions
    }

    private fun convertType(type: String): String {
        return when (type) {
            "B" -> "byte"
            "C" -> "char"
            "D" -> "double"
            "F" -> "float"
            "I" -> "int"
            "J" -> "long"
            "S" -> "short"
            "Z" -> "boolean"
            "V" -> "void"
            else -> if (type.startsWith("[")) {
                convertType(type.substring(1)) + "[]"
            } else if (type.endsWith(";")) {
                type.substring(1, type.length - 1).replace("/", ".")
            } else {
                type.substring(1).replace("/", ".")
            }
        }
    }

    private fun bestGuess(name: String): ClassName {
        if (primitiveTypes.contains(name)) {
            return ClassName.get("", name)
        }
        return ClassName.bestGuess(name)
    }

    private fun TypeSpec.Builder.createField(spec: FieldSpec.Builder): TypeSpec.Builder {
        if (!extension.mutable) {
            spec.addModifiers(Modifier.FINAL)
        }
        val field: FieldSpec = spec.build()
        addField(field)
        createGetter(field)
        if (extension.mutable) {
            createSetter(field)
        }
        return this
    }

    private fun TypeSpec.Builder.createGetter(spec: FieldSpec): TypeSpec.Builder {
        addMethod(
            MethodSpec.methodBuilder(spec.name)
                .addModifiers(Modifier.PUBLIC)
                .returns(spec.type)
                .addStatement("return this." + spec.name)
                .build()
        )
        return this
    }

    private fun TypeSpec.Builder.createSetter(spec: FieldSpec): TypeSpec.Builder {
        addMethod(
            MethodSpec.methodBuilder(spec.name)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(spec.type, spec.name)
                .addStatement("this." + spec.name + " = " + spec.name)
                .build()
        )
        return this
    }
}