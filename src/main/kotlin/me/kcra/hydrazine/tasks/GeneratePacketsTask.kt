package me.kcra.hydrazine.tasks

import com.fasterxml.jackson.module.kotlin.readValue
import com.squareup.javapoet.*
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
import java.io.File
import java.lang.reflect.Field
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
        val protocols: Map<String, Int> = protocolVersions()
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
                .addSuperinterface(ClassName.get(extension.utilsPackageName ?: "me.kcra.hydrazine.utils", "Packet"))
                .addAnnotation(
                    AnnotationSpec.builder(ClassName.get(extension.utilsPackageName ?: "me.kcra.hydrazine.utils", "Reobfuscate"))
                        .addMember("value", "\$S", joinMappings(tree, protocolList))
                        .build()
                )
            // LimitedSupport annotation
            if (tree.size() < mappings.size) {
                builder.addAnnotation(
                    AnnotationSpec.builder(ClassName.get(extension.utilsPackageName ?: "me.kcra.hydrazine.utils", "LimitedSupport"))
                        .also { annotationBuilder ->
                            if (tree.offset > 0) {
                                annotationBuilder.addMember("min", "\$L", protocolList[tree.offset + 1])
                            }
                            if ((tree.size() + tree.offset) < mappings.size) {
                                annotationBuilder.addMember("max", "\$L", protocolList[tree.size() + tree.offset])
                            }
                        }
                        .build()
                )
            }
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
                                AnnotationSpec.builder(ClassName.get(extension.utilsPackageName ?: "me.kcra.hydrazine.utils", "Reobfuscate"))
                                    .addMember("value", "\$S", joinMappings(fieldTree, protocolList))
                                    .build()
                            )
                            .also { fieldBuilder ->
                                // LimitedSupport annotation
                                if (fieldTree.size() < mappings.size) {
                                    fieldBuilder.addAnnotation(
                                        AnnotationSpec.builder(ClassName.get(extension.utilsPackageName ?: "me.kcra.hydrazine.utils", "LimitedSupport"))
                                            .also { annotationBuilder ->
                                                if (fieldTree.offset > 0) {
                                                    annotationBuilder.addMember("min", "\$L", protocolList[fieldTree.offset + 1])
                                                }
                                                if ((fieldTree.size() + fieldTree.offset) < mappings.size) {
                                                    annotationBuilder.addMember("max", "\$L", protocolList[fieldTree.size() + fieldTree.offset])
                                                }
                                            }
                                            .build()
                                    )
                                }
                            }
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
                        val limitedSupport: AnnotationSpec? = field.annotations.stream()
                            .filter { it.type is ClassName && (it.type as ClassName).simpleName().equals("LimitedSupport") }
                            .findFirst()
                            .orElse(null)
                        if (limitedSupport != null) {
                            val min: Int? = limitedSupport.members["min"]?.get(0)?.toString()?.toInt()
                            val max: Int? = limitedSupport.members["max"]?.get(0)?.toString()?.toInt()
                            if (min != null && max != null) {
                                methodBuilder.beginControlFlow("if (ver < \$L && ver > \$L)", max, min)
                                    .addStatement("final \$T ${field.name}Field = getField(nmsPacketClass, findMapping(name, getField(getClass(), \$S), ver))", Field::class.java, field.name)
                                    .addStatement("setField(${field.name}Field, nmsPacket, ${field.name})")
                                    .endControlFlow()
                            } else if (min != null) {
                                methodBuilder.beginControlFlow("if (ver > \$L)", min)
                                    .addStatement("final \$T ${field.name}Field = getField(nmsPacketClass, findMapping(name, getField(getClass(), \$S), ver))", Field::class.java, field.name)
                                    .addStatement("setField(${field.name}Field, nmsPacket, ${field.name})")
                                    .endControlFlow()
                            } else if (max != null) {
                                methodBuilder.beginControlFlow("if (ver < \$L)", max)
                                    .addStatement("final \$T ${field.name}Field = getField(nmsPacketClass, findMapping(name, getField(getClass(), \$S), ver))", Field::class.java, field.name)
                                    .addStatement("setField(${field.name}Field, nmsPacket, ${field.name})")
                                    .endControlFlow()
                            }
                        } else {
                            methodBuilder.addStatement("final \$T ${field.name}Field = getField(nmsPacketClass, findMapping(name, getField(getClass(), \$S), ver))", Field::class.java, field.name)
                                .addStatement("setField(${field.name}Field, nmsPacket, ${field.name})")
                        }
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
                .addStaticImport(ClassName.get(extension.utilsPackageName ?: "me.kcra.hydrazine.utils", "Reflect"), "*")
                .addStaticImport(ClassName.get(extension.utilsPackageName ?: "me.kcra.hydrazine.utils", "MappingUtils"), "*")
                .build()
                .writeToFile(sourceSet.java.srcDirs.first())
        }
        copyTemplateClasses("Reflect", "Reobfuscate", "MappingUtils", "Packet", "LimitedSupport")
    }

    private fun protocolVersions(): Map<String, Int> {
        val versions: MutableMap<String, Int> = extension.versions.toMutableMap()
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
        val utilPackage = extension.utilsPackageName ?: "me.kcra.hydrazine.utils"
        val path: Path = Path.of(sourceSet.java.srcDirs.first().absolutePath, utilPackage.replace('.', File.separatorChar), "$name.java")
            .also { it.parent.toFile().mkdirs() }
        Files.copy(javaClass.getResourceAsStream("/templates/$name.java")!!, path, StandardCopyOption.REPLACE_EXISTING)
        Files.writeString(path, "package $utilPackage;\n\n" + Files.readString(path, StandardCharsets.UTF_8).replace("{utilPackage}", utilPackage))
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