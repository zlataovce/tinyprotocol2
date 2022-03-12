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

import com.fasterxml.jackson.module.kotlin.readValue
import com.squareup.javapoet.*
import me.kcra.acetylene.core.TypedDescriptableMapping
import me.kcra.acetylene.core.TypedMappingFile
import me.kcra.acetylene.core.ancestry.ClassAncestorTree
import me.kcra.acetylene.core.ancestry.DescriptableAncestorTree
import me.kcra.acetylene.core.utils.MappingUtils.*
import me.kcra.acetylene.core.utils.Pair
import me.kcra.tinyprotocol.TinyProtocolPluginExtension
import me.kcra.tinyprotocol.utils.MAPPER
import me.kcra.tinyprotocol.utils.MappingType
import me.kcra.tinyprotocol.utils.ProtocolData
import me.kcra.tinyprotocol.utils.ReflectType
import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Collectors
import javax.inject.Inject
import javax.lang.model.element.Modifier

abstract class GeneratePacketsTask @Inject constructor(private val extension: TinyProtocolPluginExtension, private val sourceSet: SourceSet) : DefaultTask() {
    @get:Internal
    internal abstract var mappings: List<TypedMappingFile>

    init {
        group = "protocol"
        description = "Generates selected packet wrappers."
    }

    @TaskAction
    fun run() {
        val protocols: Map<String, Int> = protocolVersions()
        val protocolList: List<Int> = protocols.values.toList()

        val reflectClass: ClassName = ClassName.get(extension.utilsPackageName, "Reflect")
        val mappingUtilsClass: ClassName = ClassName.get(extension.utilsPackageName, "MappingUtils")
        val packetTree: ClassAncestorTree = ClassAncestorTree.of(mappings, listOf(
            // mojang
            "net/minecraft/network/protocol/Packet",
            // searge 1.13.2 and lower
            "net/minecraft/network/Packet",
            // searge 1.14 and higher
            "net/minecraft/network/IPacket",
            // intermediary
            "net/minecraft/class_2596"
        ))
        var readMethodTree: DescriptableAncestorTree? = null
        try {
            readMethodTree = packetTree.methodAncestors(listOf(
                // mojang
                Pair.of("read", "(Lnet/minecraft/network/FriendlyByteBuf;)V"),
                // searge
                Pair.of("func_148837_a", "(Lnet/minecraft/network/PacketBuffer;)V"),
                // intermediary
                Pair.of("method_11053", "(Lnet/minecraft/class_2540;)V")
            ))
        } catch (ignored: IllegalArgumentException) {
            // ignored
        }
        val writeMethodTree: DescriptableAncestorTree = packetTree.methodAncestors(listOf(
            // mojang
            Pair.of("write", "(Lnet/minecraft/network/FriendlyByteBuf;)V"),
            // searge
            Pair.of("func_148840_b", "(Lnet/minecraft/network/PacketBuffer;)V"),
            // intermediary
            Pair.of("method_11052", "(Lnet/minecraft/class_2540;)V")
        ))
        val friendlyByteBufTree: ClassAncestorTree = ClassAncestorTree.of(mappings, listOf(
            // mojang
            "net/minecraft/network/FriendlyByteBuf",
            // searge
            "net/minecraft/network/PacketBuffer",
            // intermediary
            "net/minecraft/class_2540"
        ))

        for (name: String in extension.packets) {
            logger.log(LogLevel.INFO, "Creating packet wrapper of class $name...")
            val tree: ClassAncestorTree = ClassAncestorTree.of(name.replace('.', '/'), mappings)
            logger.log(LogLevel.INFO, "Mapped ${tree.size()} version(s) of mapping $name.")
            val className: String = tree.classes[0].mappings[0].value().replace('/', '.')
            val packageName: String = extension.packageName ?: className.substring(0, className.lastIndexOf('.'))
            val transformedClassName: String = extension.className.replace("{className}", className.substring(className.lastIndexOf('.') + 1))
            val builder: TypeSpec.Builder = TypeSpec.classBuilder(transformedClassName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ClassName.get(extension.utilsPackageName, "Packet"))
                .addAnnotation(
                    AnnotationSpec.builder(ClassName.get(extension.utilsPackageName, "Reobfuscate"))
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
            val currentClassName: ClassName = ClassName.get(packageName, transformedClassName)
            // fields
            for (field: TypedDescriptableMapping in tree.walkFields()) {
                if (field.has(MappingType.MOJANG) && field.isConstant(MappingType.MOJANG)) {
                    continue
                }
                val mappings1: List<String> = field.mappings.stream().map { it.value().key() }.toList()
                val fieldTree: DescriptableAncestorTree = tree.fieldAncestors(mappings1)
                logger.log(LogLevel.INFO, "Mapped ${fieldTree.size()} version(s) of friendly mapping ${mappings1.joinToString(",")}.")
                @Suppress("SENSELESS_COMPARISON")  // need this here, because searge inconsistencies
                if (field.descriptor == null) {
                    continue
                }
                val type: String = convertType(field.descriptor).replace("/", ".")
                logger.log(LogLevel.INFO, "Creating field ${field.mapped()}, is JDK type: ${(type.startsWith("java") || PRIMITIVE_TYPES.contains(type))}")
                builder.createField(
                    FieldSpec.builder(type.let {
                        if (type.startsWith("java") || PRIMITIVE_TYPES.contains(type)) {
                            if (((fieldTree.offset > 0) || ((fieldTree.size() + fieldTree.offset) < mappings.size)) && PRIMITIVE_TYPES.contains(type)) {
                                return@let bestGuess(PRIMITIVE_WRAPPER[type]!!)
                            }
                            return@let bestGuess(type)
                        }
                        return@let ClassName.OBJECT
                    }, mappings1[0])
                        .addModifiers(Modifier.PRIVATE)
                        .also { fieldBuilder ->
                            if ((fieldTree.offset > 0) || ((fieldTree.size() + fieldTree.offset) < mappings.size)) {
                                fieldBuilder.initializer("null")
                            }
                            val fieldType: TypeName = fieldBuilder.javaClass.getDeclaredField("type").also { it.trySetAccessible() }.get(fieldBuilder) as TypeName
                            if (fieldType == ClassName.OBJECT) {
                                val typeClass: String = convertType(field.mappings[0].value().value())
                                fieldBuilder.addJavadoc("A packet field with a non-JDK type: $typeClass")
                                if (extension.generateMetadata) {
                                    fieldBuilder.addAnnotation(
                                        AnnotationSpec.builder(ClassName.get(extension.utilsPackageName, "Metadata"))
                                            .addMember("externalType", "\$S", typeClass)
                                            .build()
                                    )
                                }
                            }
                        }
                        .addAnnotation(
                            AnnotationSpec.builder(ClassName.get(extension.utilsPackageName, "Reobfuscate"))
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
                        .build()
                )
            }
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
                                .addStatement("final \$T ${field.name}Field = \$T.getFieldSafe(nmsPacketClass, \$T.findMapping(name, \$T.getFieldSafe(getClass(), \$S), ver))", Field::class.java, reflectClass, mappingUtilsClass, reflectClass, field.name)
                                .addStatement("\$T.setField(${field.name}Field, nmsPacket, ${field.name})", reflectClass)
                                .endControlFlow()
                        } else if (min != -1) {
                            methodBuilder.beginControlFlow("if (ver >= \$L)", min)
                                .addStatement("final \$T ${field.name}Field = \$T.getFieldSafe(nmsPacketClass, \$T.findMapping(name, \$T.getFieldSafe(getClass(), \$S), ver))", Field::class.java, reflectClass, mappingUtilsClass, reflectClass, field.name)
                                .addStatement("\$T.setField(${field.name}Field, nmsPacket, ${field.name})", reflectClass)
                                .endControlFlow()
                        } else if (max != -1) {
                            methodBuilder.beginControlFlow("if (ver <= \$L)", max)
                                .addStatement("final \$T ${field.name}Field = \$T.getFieldSafe(nmsPacketClass, \$T.findMapping(name, \$T.getFieldSafe(getClass(), \$S), ver))", Field::class.java, reflectClass, mappingUtilsClass, reflectClass, field.name)
                                .addStatement("\$T.setField(${field.name}Field, nmsPacket, ${field.name})", reflectClass)
                                .endControlFlow()
                        } else {
                            methodBuilder.addStatement("final \$T ${field.name}Field = \$T.getFieldSafe(nmsPacketClass, \$T.findMapping(name, \$T.getFieldSafe(getClass(), \$S), ver))", Field::class.java, reflectClass, mappingUtilsClass, reflectClass, field.name)
                                .addStatement("\$T.setField(${field.name}Field, nmsPacket, ${field.name})", reflectClass)
                        }
                    } }
                    .addStatement("return nmsPacket")
                    .build()
            )
            // fromNMS method
            builder.addMethod(
                MethodSpec.methodBuilder("fromNMS")
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(ClassName.OBJECT, "raw")
                    .addParameter(ClassName.INT, "ver")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("java.lang", "Override")).build())
                    .addStatement("final String name = getClass().getSimpleName()")
                    .also { methodBuilder -> builder.fieldSpecs.forEach { field ->
                        val reobfAnnotation: AnnotationSpec = field.annotations.stream()
                            .filter { it.type is ClassName && (it.type as ClassName).simpleName().equals("Reobfuscate") }
                            .findFirst()
                            .orElseThrow { RuntimeException("Could not find @Reobfuscate annotation for field ${field.name}") }
                        val min: Int = reobfAnnotation.members["min"]?.get(0)?.toString()?.toInt() ?: -1
                        val max: Int = reobfAnnotation.members["max"]?.get(0)?.toString()?.toInt() ?: -1
                        if (min != -1 && max != -1) {
                            methodBuilder.beginControlFlow("if (ver < \$L && ver > \$L)", max, min)
                                .addStatement("this.${field.name} = (\$T) \$T.getField(raw, \$T.findMapping(name, \$T.getFieldSafe(getClass(), \$S), ver))", field.type, reflectClass, mappingUtilsClass, reflectClass, field.name)
                                .endControlFlow()
                        } else if (min != -1) {
                            methodBuilder.beginControlFlow("if (ver >= \$L)", min)
                                .addStatement("this.${field.name} = (\$T) \$T.getField(raw, \$T.findMapping(name, \$T.getFieldSafe(getClass(), \$S), ver))", field.type, reflectClass, mappingUtilsClass, reflectClass, field.name)
                                .endControlFlow()
                        } else if (max != -1) {
                            methodBuilder.beginControlFlow("if (ver <= \$L)", max)
                                .addStatement("this.${field.name} = (\$T) \$T.getField(raw, \$T.findMapping(name, \$T.getFieldSafe(getClass(), \$S), ver))", field.type, reflectClass, mappingUtilsClass, reflectClass, field.name)
                                .endControlFlow()
                        } else {
                            methodBuilder.addStatement("this.${field.name} = (\$T) \$T.getField(raw, \$T.findMapping(name, \$T.getFieldSafe(getClass(), \$S), ver))", field.type, reflectClass, mappingUtilsClass, reflectClass, field.name)
                        }
                    } }
                    .build()
            )
            // read method
            builder.addMethod(
                MethodSpec.methodBuilder("read")
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(ClassName.OBJECT, "buf")
                    .addParameter(ClassName.INT, "ver")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("java.lang", "Override")).build())
                    .addStatement("final Class<?> nmsPacketClass = \$T.getClassSafe(\$T.findMapping(getClass(), ver))", reflectClass, mappingUtilsClass)
                    .addStatement("final Class<?> friendlyByteBufClass = \$T.getClassSafe(\$T.findMapping(getClass(), \$S, ver))", reflectClass, mappingUtilsClass, joinMappings(friendlyByteBufTree, protocolList))
                    .also { methodBuilder ->
                        if (readMethodTree != null) {
                            methodBuilder.addStatement("final String readMethodMapping = \$T.findMapping(getClass(), \$S, ver)", mappingUtilsClass, joinMappings(readMethodTree, protocolList))
                                .beginControlFlow("if (readMethodMapping != null)")
                                .addStatement("final Object nmsPacket = toNMS(ver)")
                                .addStatement("final \$T readMethod = \$T.getMethodSafe(nmsPacket.getClass(), readMethodMapping, friendlyByteBufClass)", Method::class.java, reflectClass)
                                .addStatement("\$T.fastInvoke(readMethod, nmsPacket, buf)", reflectClass)
                                .addStatement("fromNMS(nmsPacket, ver)")
                                .nextControlFlow("else")
                                .addStatement("fromNMS(\$T.construct(nmsPacketClass, buf), ver)", reflectClass)
                                .endControlFlow()
                        } else {
                            methodBuilder.addStatement("fromNMS(\$T.construct(nmsPacketClass, buf), ver)", reflectClass)
                        }
                    }
                    .build()
            )
            // write method
            builder.addMethod(
                MethodSpec.methodBuilder("write")
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(ClassName.OBJECT, "buf")
                    .addParameter(ClassName.INT, "ver")
                    .addAnnotation(AnnotationSpec.builder(ClassName.get("java.lang", "Override")).build())
                    .addStatement("final Object nmsPacket = toNMS(ver)")
                    .addStatement("final Class<?> friendlyByteBufClass = \$T.getClassSafe(\$T.findMapping(getClass(), \$S, ver))", reflectClass, mappingUtilsClass, joinMappings(friendlyByteBufTree, protocolList))
                    .addStatement("final String writeMethodMapping = \$T.findMapping(getClass(), \$S, ver)", mappingUtilsClass, joinMappings(writeMethodTree, protocolList))
                    .addStatement("final \$T writeMethod = \$T.getMethodSafe(nmsPacket.getClass(), writeMethodMapping, friendlyByteBufClass)", Method::class.java, reflectClass)
                    .addStatement("\$T.fastInvoke(writeMethod, nmsPacket, buf)", reflectClass)
                    .build()
            )

            // constructors
            logger.log(LogLevel.INFO, "Creating constructors...")
            builder.addMethod(
                MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .also { builder.fieldSpecs.forEach { field -> it.addParameter(field.type, field.name).addStatement("this." + field.name + " = " + field.name) } }
                    .build()
            )
            builder.addMethod(
                MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(ClassName.OBJECT, "raw")
                    .addParameter(ClassName.INT, "ver")
                    .addStatement("final Class<?> nmsPacketClass = \$T.getClassSafe(\$T.findMapping(getClass(), ver))", reflectClass, mappingUtilsClass)
                    .addStatement("final Class<?> friendlyByteBufClass = \$T.getClassSafe(\$T.findMapping(getClass(), \$S, ver))", reflectClass, mappingUtilsClass, joinMappings(friendlyByteBufTree, protocolList))
                    .beginControlFlow("if (nmsPacketClass.isInstance(raw))")
                    .addStatement("fromNMS(raw, ver)")
                    .nextControlFlow("else if (friendlyByteBufClass.isInstance(raw))")
                    .addStatement("read(raw, ver)")
                    .nextControlFlow("else")
                    .addStatement("throw new IllegalArgumentException(\"Unsupported type provided for transformation\")")
                    .endControlFlow()
                    .build()
            )
            JavaFile.builder(packageName, builder.build())
                .indent("    ") // 4 space indent
                .addFileComment("This file was generated with tinyprotocol2. Do not edit, changes will be overwritten!")
                .skipJavaLangImports(true)
                .build()
                .writeToFile(sourceSet.java.srcDirs.first())
            logger.log(LogLevel.LIFECYCLE, "Wrote ${currentClassName.simpleName()}.")
        }
        if (extension.generateMetadata) {
            copyTemplateClass("Metadata")
        }
        when (extension.reflectOptions.type) {
            ReflectType.ZERODEP -> copyTemplateClass("Reflect")
            ReflectType.NARCISSUS -> copyTemplateClassAs("NarcissusReflect", "Reflect")
            ReflectType.OBJENESIS -> copyTemplateClassAs("ObjenesisReflect", "Reflect")
        }
        copyTemplateClasses("Reobfuscate", "MappingUtils", "Packet")
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

    private fun bestGuess(name: String): ClassName {
        if (PRIMITIVE_TYPES.contains(name)) {
            return ClassName.get("", name)
        }
        return ClassName.bestGuess(name)
    }

    private fun TypeSpec.Builder.createField(field: FieldSpec): TypeSpec.Builder {
        addField(field)
        createGetter(field)
        createSetter(field)
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

    private fun copyTemplateClasses(vararg names: String) = names.forEach { copyTemplateClass(it) }

    private fun copyTemplateClass(name: String) = copyTemplateClassAs(name, name)

    private fun copyTemplateClassAs(name: String, newName: String) {
        val file: File = Path.of(sourceSet.java.srcDirs.first().absolutePath, extension.utilsPackageName.replace('.', File.separatorChar), "$newName.java")
            .toFile()
            .also { it.parentFile.mkdirs() }
        Files.copy(javaClass.getResourceAsStream("/templates/$name.java")!!, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        file.writeText(
            file.readText()
                .replace("{utilsPackage}", extension.utilsPackageName)
                .replace("{narcissusPackage}", extension.reflectOptions.narcissusPackage)
                .replace("{objenesisPackage}", extension.reflectOptions.objenesisPackage)
        )
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
            .map { "${it.key}=${it.value.joinToString(",")}" }
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
            .map { "${it.key}=${it.value.joinToString(",")}" }
            .collect(Collectors.joining("+"))
    }
}