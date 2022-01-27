package me.kcra.tinyprotocol.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

abstract class CleanSourcesTask @Inject constructor(private val sourceSet: SourceSet) : DefaultTask() {
    init {
        group = "protocol"
        description = "Removes generated source files."
    }

    @TaskAction
    fun run() {
        val sourceDir: File = sourceSet.java.srcDirs.first()
        if (sourceDir.isDirectory) {
            for (file in sourceDir.listFiles()!!) {
                file.deleteRecursively()
            }
        }
    }
}