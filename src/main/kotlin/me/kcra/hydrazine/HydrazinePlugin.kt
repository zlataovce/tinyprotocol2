package me.kcra.hydrazine

import me.kcra.hydrazine.tasks.GenerateMappingsTask
import me.kcra.hydrazine.tasks.GeneratePacketsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

class HydrazinePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension: HydrazinePluginExtension = target.extensions.create("hydrazine", HydrazinePluginExtension::class.java)
        val sourceSets: SourceSetContainer = target.extensions.getByType(JavaPluginExtension::class.java).sourceSets
        val sourceSet: SourceSet = sourceSets.findByName(extension.sourceSet) ?: sourceSets.create(extension.sourceSet) {
            it.java.srcDir("src/${extension.sourceSet}/java")
            it.resources.srcDir("src/${extension.sourceSet}/resources")
        }

        target.tasks.create("generateMappings", GenerateMappingsTask::class.java)
        target.tasks.create("generatePackets", GeneratePacketsTask::class.java, sourceSet)
            .dependsOn("generateMappings")
        target.tasks.getByName("assemble")
            .dependsOn("generatePackets")
    }
}