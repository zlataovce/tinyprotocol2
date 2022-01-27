package me.kcra.tinyprotocol

import me.kcra.tinyprotocol.tasks.CleanSourcesTask
import me.kcra.tinyprotocol.tasks.PrepareMappingsTask
import me.kcra.tinyprotocol.tasks.GeneratePacketsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.util.GradleVersion

class TinyProtocolPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension: TinyProtocolPluginExtension = target.extensions.create("protocol", TinyProtocolPluginExtension::class.java)
        if (!target.plugins.hasPlugin(JavaPlugin::class.java)) {
            target.plugins.apply(JavaPlugin::class.java)
        }
        val sourceSets: SourceSetContainer = getSourceSets(target)
        val sourceSet: SourceSet = sourceSets.findByName(extension.sourceSet) ?: sourceSets.create(extension.sourceSet) {
            it.java.srcDir("src/${extension.sourceSet}/java")
            it.resources.srcDir("src/${extension.sourceSet}/resources")
        }

        target.tasks.create("prepareMappings", PrepareMappingsTask::class.java, extension)
        target.tasks.create("generatePackets", GeneratePacketsTask::class.java, extension, sourceSet)
            .dependsOn("prepareMappings")
        target.tasks.create("cleanSources", CleanSourcesTask::class.java, sourceSet)

        target.tasks.getByName("assemble")
            .dependsOn("generatePackets")
        target.tasks.getByName("clean")
            .dependsOn("cleanSources")
    }

    @Suppress("DEPRECATION")
    private fun getSourceSets(project: Project): SourceSetContainer =
        if (GradleVersion.version(project.gradle.gradleVersion) < GradleVersion.version("7.1")) project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets
        else project.extensions.getByType(JavaPluginExtension::class.java).sourceSets
}