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

package me.kcra.tinyprotocol.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.stream.Collectors
import java.util.zip.ZipFile
import kotlin.experimental.and

val MAPPER: ObjectMapper = jacksonObjectMapper()
val OVERRIDE_ANNOTATION: AnnotationSpec = AnnotationSpec.builder(ClassName.get("java.lang", "Override")).build()
private val SHA_1 = MessageDigest.getInstance("SHA-1")

fun newFile(fileName: String, workFolder: File): File {
    workFolder.mkdirs()
    return Path.of(workFolder.absolutePath, fileName).toFile()
}

fun getFromURL(urlS: String, fileName: String, workFolder: File, sha1: String?, verifyChecksums: Boolean): File? {
    val downloadedFile: File = newFile(fileName, workFolder)
    val url = URL(urlS)
    if (downloadedFile.isFile) {
        if (!verifyChecksums) {
            return downloadedFile
        } else if (sha1 != null) {
            if (getFileChecksum(SHA_1, downloadedFile) == sha1) {
                return downloadedFile
            }
        } else {
            if (getContentLength(url) == downloadedFile.length()) {
                return downloadedFile
            }
        }
    }
    try {
        url.openStream().use { inputStream ->
            Files.copy(inputStream, downloadedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } catch (e: Exception) {
        return null
    }
    return downloadedFile
}

fun getStringFromURL(url: String): String? {
    try {
        URL(url).openStream().use { inputStream ->
            return BufferedReader(InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n"))
        }
    } catch (ignored: Exception) {
        // ignored
    }
    return null
}

fun minecraftResource(ver: String, res: String, workFolder: File, verifyChecksums: Boolean): File? {
    val manifest: JsonNode = MAPPER.readTree(URL("https://launchermeta.mojang.com/mc/game/version_manifest.json"))
    for (jsonNode in manifest.path("versions")) {
        if (jsonNode.get("id").asText().equals(ver)) {
            val versionManifest: JsonNode = MAPPER.readTree(URL(jsonNode.get("url").asText()))
            if (versionManifest.path("downloads").has(res)) {
                return getFromURL(
                    versionManifest.path("downloads").path(res).get("url").asText(),
                    res + "_" + ver + ".res", workFolder,
                    versionManifest.path("downloads").path(res).get("sha1").asText(),
                    verifyChecksums
                )
            }
        }
    }
    return null
}

fun seargeMapping(ver: String, workFolder: File, verifyChecksums: Boolean): InputStream? {
    return seargeMapping0("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/$ver/mcp_config-$ver.zip", ver, workFolder, verifyChecksums)
        ?: seargeMapping0("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/$ver/mcp-$ver-srg.zip", ver, workFolder, verifyChecksums)
}

private fun seargeMapping0(url: String, ver: String, workFolder: File, verifyChecksums: Boolean): InputStream? {
    val file: File = getFromURL(url, "mcp_$ver.zip", workFolder, getStringFromURL("$url.sha1"), verifyChecksums) ?: return null
    val zipFile = ZipFile(file)
    return zipFile.getInputStream(
        zipFile.stream()
            .filter { e -> e.name.equals("config/joined.tsrg") || e.name.equals("joined.srg") }
            .findFirst()
            .orElseThrow { RuntimeException("Searge mapping file not found") }
    )
}

fun intermediaryMapping(ver: String, workFolder: File, verifyChecksums: Boolean): File? =
    getFromURL("https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/$ver.tiny", "$ver.tiny", workFolder, null, verifyChecksums)

fun spigotMapping(ver: String, workFolder: File, verifyChecksums: Boolean): File? {
    val versionManifest: JsonNode = try {
        MAPPER.readTree(URL("https://hub.spigotmc.org/versions/$ver.json"))
    } catch (ignored: FileNotFoundException) {
        return null
    }
    val buildDataRev: String = versionManifest.path("refs").path("BuildData").asText()
    val buildDataManifest: JsonNode =
        MAPPER.readTree(URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/info.json?at=$buildDataRev"))
    if (!buildDataManifest.has("classMappings")) {
        return null
    }
    val classMapping: File = getFromURL(
        "https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/" + buildDataManifest.path("classMappings")
            .asText() + "?at=$buildDataRev",
        "spigot_${ver}_cl.csrg",
        workFolder,
        null,
        verifyChecksums
    ) ?: return null
    if (buildDataManifest.has("memberMappings")) {
        return concatFiles(
            newFile("spigot_${ver}_joined.csrg", workFolder),
            classMapping,
            getFromURL(
                "https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/" + buildDataManifest.path("memberMappings")
                    .asText() + "?at=$buildDataRev",
                "spigot_${ver}_mem.csrg",
                workFolder,
                null,
                verifyChecksums
            ) ?: return classMapping
        )
    }
    return classMapping
}

fun getFileChecksum(digest: MessageDigest, file: File): String {
    FileInputStream(file).use { fis ->
        val byteArray = ByteArray(1024)
        var bytesCount: Int
        while (fis.read(byteArray).also { bytesCount = it } != -1) {
            digest.update(byteArray, 0, bytesCount)
        }
    }
    val bytes = digest.digest()
    val sb = StringBuilder()
    for (aByte in bytes) {
        sb.append(((aByte and 0xff.toByte()) + 0x100).toString(16).substring(1))
    }
    return sb.toString()
}

fun getContentLength(url: URL): Long {
    var conn: URLConnection? = null
    try {
        conn = url.openConnection()
        if (conn is HttpURLConnection) {
            (conn as HttpURLConnection?)?.requestMethod = "HEAD"
        }
        return conn.contentLengthLong
    } catch (ignored: IOException) {
        // ignored
    } finally {
        if (conn is HttpURLConnection) {
            (conn as HttpURLConnection?)?.disconnect()
        }
    }
    return -1
}

fun concatFiles(outFile: File, vararg files: File): File {
    FileOutputStream(outFile).use { outputStream ->
        val buf = ByteArray(1024)
        for (file in files) {
            FileInputStream(file).use { inputStream ->
                var b: Int
                while (inputStream.read(buf).also { b = it } != -1) {
                    outputStream.write(buf, 0, b)
                }
            }
        }
    }
    return outFile
}