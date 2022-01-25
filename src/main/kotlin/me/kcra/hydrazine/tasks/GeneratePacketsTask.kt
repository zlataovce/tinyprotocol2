package me.kcra.hydrazine.tasks

import com.fasterxml.jackson.module.kotlin.readValue
import com.squareup.javapoet.*
import com.uchuhimo.collections.BiMap
import com.uchuhimo.collections.MutableBiMap
import com.uchuhimo.collections.toMutableBiMap
import me.kcra.acetylene.core.TypedDescriptableMapping
import me.kcra.acetylene.core.TypedMappingFile
import me.kcra.acetylene.core.ancestry.ClassAncestorTree
import me.kcra.acetylene.core.ancestry.DescriptableAncestorTree
import me.kcra.hydrazine.HydrazinePluginExtension
import me.kcra.hydrazine.utils.MAPPER
import me.kcra.hydrazine.utils.MappingType
import me.kcra.hydrazine.utils.ProtocolData
import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Collectors
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
        val protocolList: List<Int> = protocols.values.toList()
        for (name: String in extension.packets) {
            logger.log(LogLevel.INFO, "Creating packet wrapper of class $name...")
            val tree: ClassAncestorTree = ClassAncestorTree.of(name, mappings)
            if (tree.size() == 0) {
                throw RuntimeException("Could not map class $name")
            }
            val className: String = tree.classes[0].mapped(MappingType.MOJANG)?.replace('/', '.')
                ?: throw RuntimeException("Could not map class $name")
            val builder: TypeSpec.Builder = TypeSpec.classBuilder("W" + className.substring(className.lastIndexOf('.') + 1))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ClassName.get("me.kcra.hydrazine.utils", "Packet"))
                .addAnnotation(
                    AnnotationSpec.builder(ClassName.get("me.kcra.hydrazine.utils", "Reobfuscate"))
                        .addMember("value", "\$S", joinMappings(tree, protocolList))
                        .build()
                )
            // fields
            for (field: TypedDescriptableMapping in tree.classes[0].fields) {
                val fieldTree: DescriptableAncestorTree = tree.fieldAncestors(field.mapped(MappingType.MOJANG))
                val type: String = convertType(field.descriptor)
                logger.log(LogLevel.INFO, "Creating field " + field.mapped() + ", is JDK type: " + (type.startsWith("java") || primitiveTypes.contains(type)))
                if (type.startsWith("java") || primitiveTypes.contains(type)) {
                    // basic java type
                    builder.createField(
                        FieldSpec.builder(bestGuess(type), field.mapped(MappingType.MOJANG))
                            .addModifiers(Modifier.PRIVATE)
                            .addAnnotation(
                                AnnotationSpec.builder(ClassName.get("me.kcra.hydrazine.utils", "Reobfuscate"))
                                    .addMember("value", "\$S", joinMappings(fieldTree, protocolList))
                                    .build()
                            )
                    )
                }
            }
            // toNMS method
            builder.addMethod(
                MethodSpec.methodBuilder("toNMS")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ClassName.OBJECT)
                    .addParameter(ClassName.INT, "ver")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("java.lang", "Override")).build())
                    .addStatement("final String name = getClass().getSimpleName()")
                    .addStatement("final Class<?> nmsPacketClass = getClassSafe(findMapping(getClass(), ver))")
                    .addStatement("final Object nmsPacket = construct(nmsPacketClass)")
                    .also { methodBuilder -> builder.fieldSpecs.forEach { field ->
                        methodBuilder.addStatement("setField(nmsPacketClass, findMapping(name, getField(getClass(), \$S), ver), nmsPacket, ${field.name})", field.name)
                    } }
                    .addStatement("return nmsPacket")
                    .build()
            )

            // required args constructor
            logger.log(LogLevel.INFO, "Creating constructor...")
            builder.addMethod(
                MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .also { builder.fieldSpecs.forEach { field -> it.addParameter(field.type, field.name).addStatement("this." + field.name + " = " + field.name) } }
                    .build()
            )
            JavaFile.builder(extension.packageName ?: className.substring(0, className.lastIndexOf('.')), builder.build())
                .addStaticImport(ClassName.get("me.kcra.hydrazine.utils", "Reflect"), "*")
                .addStaticImport(ClassName.get("me.kcra.hydrazine.utils", "MappingUtils"), "*")
                .build()
                .writeToFile(sourceSet.java.srcDirs.first())
        }
        copyTemplateClasses("Reflect", "Reobfuscate", "MappingUtils", "Packet")
    }

    private fun protocolVersions(): BiMap<String, Int> {
        val versions: MutableBiMap<String, Int> = extension.versions.toMutableBiMap()
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

    private fun copyTemplateClasses(vararg names: String) {
        names.forEach { copyTemplateClass(it) }
    }

    private fun copyTemplateClass(name: String) {
        val path: Path = Path.of(sourceSet.java.srcDirs.first().absolutePath,"me", "kcra", "hydrazine", "utils", "$name.java")
            .also { it.parent.toFile().mkdirs() }
        Files.copy(javaClass.getResourceAsStream("/templates/$name.java")!!, path, StandardCopyOption.REPLACE_EXISTING)
        Files.writeString(path, "package me.kcra.hydrazine.utils;\n\n" + Files.readString(path, StandardCharsets.UTF_8))
    }

    private fun joinMappings(tree: ClassAncestorTree, protocolVersions: List<Int>): String {
        // obfuscated -> versions
        val joined: MutableMap<String, MutableList<Int>> = mutableMapOf()
        tree.classes.forEachIndexed { index, element ->
            joined.getOrPut(element.mapped(MappingType.SPIGOT) ?: element.original) { mutableListOf() }.add(protocolVersions[index + tree.offset])
        }
        return joined.entries.stream()
            .map { "[${it.value.joinToString(",")}]=${it.key}" }
            .collect(Collectors.joining("+"))
    }

    private fun joinMappings(tree: DescriptableAncestorTree, protocolVersions: List<Int>): String {
        // obfuscated -> versions
        val joined: MutableMap<String, MutableList<Int>> = mutableMapOf()
        tree.descriptables.forEachIndexed { index, element ->
            joined.getOrPut(element.mapped(MappingType.SPIGOT) ?: element.original) { mutableListOf() }.add(protocolVersions[index + tree.offset])
        }
        return joined.entries.stream()
            .map { "${it.value.joinToString(",")}=${it.key}" }
            .collect(Collectors.joining("+"))
    }
}