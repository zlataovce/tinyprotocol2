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