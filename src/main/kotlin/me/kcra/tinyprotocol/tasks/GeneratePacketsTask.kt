package me.kcra.tinyprotocol.tasks

import com.fasterxml.jackson.module.kotlin.readValue
import com.squareup.javapoet.*
import me.kcra.acetylene.core.TypedClassMapping
import me.kcra.acetylene.core.TypedDescriptableMapping
import me.kcra.acetylene.core.TypedMappingFile
import me.kcra.acetylene.core.ancestry.ClassAncestorTree
import me.kcra.acetylene.core.ancestry.DescriptableAncestorTree
import me.kcra.tinyprotocol.TinyProtocolPluginExtension
import me.kcra.tinyprotocol.utils.MAPPER
import me.kcra.tinyprotocol.utils.MappingType
import me.kcra.tinyprotocol.utils.ProtocolData
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
import java.util.*
import java.util.stream.Collectors
import javax.inject.Inject
import javax.lang.model.element.Modifier

abstract class GeneratePacketsTask @Inject constructor(private val extension: TinyProtocolPluginExtension, private val sourceSet: SourceSet) : DefaultTask() {
    @get:Internal
    internal abstract var mappings: List<TypedMappingFile>

    companion object {
        private val primitiveTypes: List<String> = listOf("int", "void", "long", "float", "double", "boolean", "short", "byte", "char")
    }

    init {
        group = "protocol"
        description = "Generates selected packet wrappers."
        outputs.upToDateWhen {
            extension.packets.stream().allMatch {
                val name: String = it.replace('.', '/')
                val packageName: String = (extension.packageName ?: name.substring(0, name.lastIndexOf('/'))).replace('/', File.separatorChar)
                Path.of(sourceSet.java.srcDirs.first().absolutePath, packageName, "W" + name.substring(name.lastIndexOf('/') + 1)).toFile().isFile
            }
        }
    }

    @TaskAction
    fun run() {
        val protocols: Map<String, Int> = protocolVersions()
        val protocolList: List<Int> = protocols.values.toList()
        for (name: String in extension.packets) {
            logger.log(LogLevel.INFO, "Creating packet wrapper of class $name...")
            val tree: ClassAncestorTree = ClassAncestorTree.of(name.replace('.', '/'), mappings)
            logger.log(LogLevel.INFO, "Mapped ${tree.size()} version(s) of mapping $name.")
            if (tree.size() == 0) {
                throw RuntimeException("Could not map class $name")
            }
            val className: String = tree.classes[0].mappings[0].value().replace('/', '.')
            val builder: TypeSpec.Builder = TypeSpec.classBuilder("W" + className.substring(className.lastIndexOf('.') + 1))
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ClassName.get(extension.utilsPackageName ?: "me.kcra.tinyprotocol.utils", "Packet"))
                .addAnnotation(
                    AnnotationSpec.builder(ClassName.get(extension.utilsPackageName ?: "me.kcra.tinyprotocol.utils", "Reobfuscate"))
                        .addMember("value", "\$S", joinMappings(tree, protocolList))
                        .also { annotationBuilder ->
                            // limited support
                            if (tree.size() < mappings.size) {
                                if (tree.offset > 0) {
                                    annotationBuilder.addMember("min", "\$L", protocolList[tree.offset])
                                }
                                if ((tree.size() + tree.offset) < mappings.size) {
                                    annotationBuilder.addMember("max", "\$L", protocolList[(tree.size() - 1) + tree.offset])
                                }
                            }
                        }
                        .build()
                )
            val currentClassName: ClassName = ClassName.get(extension.packageName ?: className.substring(0, className.lastIndexOf('.')), "W" + className.substring(className.lastIndexOf('.') + 1))
            // fields
            for (field: TypedDescriptableMapping in walkFields(tree)) {
                val mappings1: List<String> = field.mappings.stream().map { it.value().key() }.toList()
                val fieldTree: DescriptableAncestorTree = tree.fieldAncestors(mappings1)
                logger.log(LogLevel.INFO, "Mapped ${fieldTree.size()} version(s) of friendly mapping ${mappings1.joinToString(",")}.")
                @Suppress("SENSELESS_COMPARISON")  // need this here, because searge inconsistencies
                if (field.descriptor == null) {
                    continue
                }
                val type: String = convertType(field.descriptor)
                logger.log(LogLevel.INFO, "Creating field ${field.mapped()}, is JDK type: ${(type.startsWith("java") || primitiveTypes.contains(type))}")
                builder.createField(
                    FieldSpec.builder(if (type.startsWith("java") || primitiveTypes.contains(type)) bestGuess(type) else ClassName.OBJECT, mappings1[0])
                        .addModifiers(Modifier.PRIVATE)
                        .addAnnotation(
                            AnnotationSpec.builder(ClassName.get(extension.utilsPackageName ?: "me.kcra.tinyprotocol.utils", "Reobfuscate"))
                                .addMember("value", "\$S", joinMappings(fieldTree, protocolList))
                                .also { annotationBuilder ->
                                    // limited support
                                    if (fieldTree.offset > 0) {
                                        annotationBuilder.addMember("min", "\$L", protocolList[fieldTree.offset])
                                    }
                                    if ((fieldTree.size() + fieldTree.offset) < mappings.size) {
                                        annotationBuilder.addMember("max", "\$L", protocolList[(fieldTree.size() - 1) + fieldTree.offset])
                                    }
                                }
                                .build()
                        )
                )
            }
            val reflectClass: ClassName = ClassName.get(extension.utilsPackageName ?: "me.kcra.tinyprotocol.utils", "Reflect")
            val mappingUtilsClass: ClassName = ClassName.get(extension.utilsPackageName ?: "me.kcra.tinyprotocol.utils", "MappingUtils")
            // toNMS method
            builder.addMethod(
                MethodSpec.methodBuilder("toNMS")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ClassName.OBJECT)
                    .addParameter(ClassName.INT, "ver")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("java.lang", "Override")).build())
                    .addStatement("final String name = getClass().getSimpleName()")
                    .addStatement("final Class<?> nmsPacketClass = \$T.getClassSafe(\$T.findMapping(getClass(), ver))", reflectClass, mappingUtilsClass)
                    .addStatement("final Object nmsPacket = \$T.construct(nmsPacketClass)", reflectClass)
                    .also { methodBuilder -> builder.fieldSpecs.forEach { field ->
                        val reobfAnnotation: AnnotationSpec = field.annotations.stream()
                            .filter { it.type is ClassName && (it.type as ClassName).simpleName().equals("Reobfuscate") }
                            .findFirst()
                            .orElseThrow { RuntimeException("Could not find @Reobfuscate annotation for field ${field.name}") }
                        val min: Int = reobfAnnotation.members["min"]?.get(0)?.toString()?.toInt() ?: -1
                        val max: Int = reobfAnnotation.members["max"]?.get(0)?.toString()?.toInt() ?: -1
                        if (min != -1 && max != -1) {
                            methodBuilder.beginControlFlow("if (ver < \$L && ver > \$L)", max, min)
                                .addStatement("final \$T ${field.name}Field = \$T.getField(nmsPacketClass, \$T.findMapping(name, \$T.getField(getClass(), \$S), ver))", Field::class.java, reflectClass, mappingUtilsClass, reflectClass, field.name)
                                .addStatement("\$T.setField(${field.name}Field, nmsPacket, ${field.name})", reflectClass)
                                .endControlFlow()
                        } else if (min != -1) {
                            methodBuilder.beginControlFlow("if (ver >= \$L)", min)
                                .addStatement("final \$T ${field.name}Field = \$T.getField(nmsPacketClass, \$T.findMapping(name, \$T.getField(getClass(), \$S), ver))", Field::class.java, reflectClass, mappingUtilsClass, reflectClass, field.name)
                                .addStatement("\$T.setField(${field.name}Field, nmsPacket, ${field.name})", reflectClass)
                                .endControlFlow()
                        } else if (max != -1) {
                            methodBuilder.beginControlFlow("if (ver <= \$L)", max)
                                .addStatement("final \$T ${field.name}Field = \$T.getField(nmsPacketClass, \$T.findMapping(name, \$T.getField(getClass(), \$S), ver))", Field::class.java, reflectClass, mappingUtilsClass, reflectClass, field.name)
                                .addStatement("\$T.setField(${field.name}Field, nmsPacket, ${field.name})", reflectClass)
                                .endControlFlow()
                        } else {
                            methodBuilder.addStatement("final \$T ${field.name}Field = \$T.getField(nmsPacketClass, \$T.findMapping(name, \$T.getField(getClass(), \$S), ver))", Field::class.java, reflectClass, mappingUtilsClass, reflectClass, field.name)
                                .addStatement("\$T.setField(${field.name}Field, nmsPacket, ${field.name})", reflectClass)
                        }
                    } }
                    .addStatement("return nmsPacket")
                    .build()
            )
            // fromNMS method
            builder.addMethod(
                MethodSpec.methodBuilder("fromNMS")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(currentClassName)
                    .addParameter(ClassName.OBJECT, "raw")
                    .addParameter(ClassName.INT, "ver")
                    .addStatement("final String name = \$T.class.getSimpleName()", currentClassName)
                    .also { methodBuilder -> builder.fieldSpecs.forEach { field ->
                        val reobfAnnotation: AnnotationSpec = field.annotations.stream()
                            .filter { it.type is ClassName && (it.type as ClassName).simpleName().equals("Reobfuscate") }
                            .findFirst()
                            .orElseThrow { RuntimeException("Could not find @Reobfuscate annotation for field ${field.name}") }
                        val min: Int = reobfAnnotation.members["min"]?.get(0)?.toString()?.toInt() ?: -1
                        val max: Int = reobfAnnotation.members["max"]?.get(0)?.toString()?.toInt() ?: -1
                        if (min != -1 && max != -1) {
                            methodBuilder.addStatement("\$T ${field.name} = null", field.type)
                                .beginControlFlow("if (ver < \$L && ver > \$L)", max, min)
                                .addStatement("${field.name} = (\$T) \$T.getFieldSafe(raw, \$T.findMapping(name, \$T.getField(\$T.class, \$S), ver))", field.type, reflectClass, mappingUtilsClass, reflectClass, currentClassName, field.name)
                                .endControlFlow()
                        } else if (min != -1) {
                            methodBuilder.addStatement("\$T ${field.name} = null", field.type)
                                .beginControlFlow("if (ver >= \$L)", min)
                                .addStatement("${field.name} = (\$T) \$T.getFieldSafe(raw, \$T.findMapping(name, \$T.getField(\$T.class, \$S), ver))", field.type, reflectClass, mappingUtilsClass, reflectClass, currentClassName, field.name)
                                .endControlFlow()
                        } else if (max != -1) {
                            methodBuilder.addStatement("\$T ${field.name} = null", field.type)
                                .beginControlFlow("if (ver <= \$L)", max)
                                .addStatement("${field.name} = (\$T) \$T.getFieldSafe(raw, \$T.findMapping(name, \$T.getField(\$T.class, \$S), ver))", field.type, reflectClass, mappingUtilsClass, reflectClass, currentClassName, field.name)
                                .endControlFlow()
                        } else {
                            methodBuilder.addStatement("final \$T ${field.name} = (\$T) \$T.getFieldSafe(raw, \$T.findMapping(name, \$T.getField(\$T.class, \$S), ver))", field.type, field.type, reflectClass, mappingUtilsClass, reflectClass, currentClassName, field.name)
                        }
                    } }
                    .addStatement("return new \$T(${builder.fieldSpecs.stream().map { it.name }.collect(Collectors.joining(", "))})", currentClassName)
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
                .build()
                .writeToFile(sourceSet.java.srcDirs.first())
            logger.log(LogLevel.LIFECYCLE, "Wrote ${currentClassName.simpleName()}.")
        }
        copyTemplateClasses("Reflect", "Reobfuscate", "MappingUtils", "Packet")
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
        val utilPackage = extension.utilsPackageName ?: "me.kcra.tinyprotocol.utils"
        val path: Path = Path.of(sourceSet.java.srcDirs.first().absolutePath, utilPackage.replace('.', File.separatorChar), "$name.java")
            .also { it.parent.toFile().mkdirs() }
        Files.copy(javaClass.getResourceAsStream("/templates/$name.java")!!, path, StandardCopyOption.REPLACE_EXISTING)
        Files.writeString(path, "package $utilPackage;\n\n" + Files.readString(path, StandardCharsets.UTF_8).replace("{utilPackage}", utilPackage))
    }

    private fun joinMappings(tree: ClassAncestorTree, protocolVersions: List<Int>): String {
        // obfuscated -> versions
        val joined: MutableMap<String, MutableList<Int>> = mutableMapOf()
        tree.classes.forEachIndexed { index, element ->
            joined.getOrPut(element.mapped(MappingType.SPIGOT) ?: element.original) { mutableListOf() }.also { versions ->
                val ver: Int = protocolVersions[index + tree.offset]
                if (!versions.contains(ver)) {
                    versions.add(ver)
                }
            }
        }
        return joined.entries.stream()
            .map { "${it.value.joinToString(",")}=${it.key}" }
            .collect(Collectors.joining("+"))
    }

    private fun joinMappings(tree: DescriptableAncestorTree, protocolVersions: List<Int>): String {
        // obfuscated -> versions
        val joined: MutableMap<String, MutableList<Int>> = mutableMapOf()
        tree.descriptables.forEachIndexed { index, element ->
            joined.getOrPut(element.mapped(MappingType.SPIGOT) ?: element.original) { mutableListOf() }.also { versions ->
                val ver: Int = protocolVersions[index + tree.offset]
                if (!versions.contains(ver)) {
                    versions.add(ver)
                }
            }
        }
        return joined.entries.stream()
            .map { "${it.value.joinToString(",")}=${it.key}" }
            .collect(Collectors.joining("+"))
    }

    private fun walkFields(tree: ClassAncestorTree): List<TypedDescriptableMapping> {
        val mappingSet: MutableSet<String> = mutableSetOf()
        val fields: MutableList<TypedDescriptableMapping> = mutableListOf()
        for (clazz: TypedClassMapping in tree.classes) {
            fields.addAll(
                clazz.fields.stream()
                    .filter { f ->
                        val mapStr: List<String> = f.mappings.stream().map { it.value().key() }.toList()
                        return@filter Collections.disjoint(mapStr, mappingSet).also { disjoint ->
                            if (disjoint) {
                                mappingSet.addAll(mapStr)
                            }
                        }
                    }
                    .toList()
            )
        }
        return fields
    }
}