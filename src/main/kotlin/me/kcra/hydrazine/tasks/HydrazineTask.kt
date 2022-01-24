package me.kcra.hydrazine.tasks

import me.kcra.hydrazine.HydrazinePluginExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import java.io.File
import java.nio.file.Path

abstract class HydrazineTask : DefaultTask() {
    @Internal
    protected val extension: HydrazinePluginExtension = project.extensions.getByType(HydrazinePluginExtension::class.java)
    @Internal
    protected val workFolder: File = Path.of(project.buildDir.absolutePath, "hydrazine").toFile().also { it.mkdirs() }
}