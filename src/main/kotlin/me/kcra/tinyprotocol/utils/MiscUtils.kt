package me.kcra.tinyprotocol.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
private val SHA_1 = MessageDigest.getInstance("SHA-1")

fun newFile(fileName: String, workFolder: File): File {
    workFolder.mkdirs()
    return Path.of(workFolder.absolutePath, fileName).toFile()
}

fun getFromURL(urlS: String, fileName: String, workFolder: File, sha1: String?): File? {
    val downloadedFile: File = newFile(fileName, workFolder)
    val url = URL(urlS)
    if (downloadedFile.isFile) {
        if (sha1 != null) {
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

fun minecraftResource(ver: String, res: String, workFolder: File): File? {
    val manifest: JsonNode = MAPPER.readTree(URL("https://launchermeta.mojang.com/mc/game/version_manifest.json"))
    for (jsonNode in manifest.path("versions")) {
        if (jsonNode.get("id").asText().equals(ver)) {
            val versionManifest: JsonNode = MAPPER.readTree(URL(jsonNode.get("url").asText()))
            if (versionManifest.path("downloads").has(res)) {
                return getFromURL(
                    versionManifest.path("downloads").path(res).get("url").asText(),
                    res + "_" + ver + ".res", workFolder,
                    versionManifest.path("downloads").path(res).get("sha1").asText()
                )
            }
        }
    }
    return null
}

fun seargeMapping(ver: String, workFolder: File): InputStream? {
    return seargeMapping0("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/$ver/mcp_config-$ver.zip", ver, workFolder)
        ?: seargeMapping0("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/$ver/mcp-$ver-srg.zip", ver, workFolder)
}

private fun seargeMapping0(url: String, ver: String, workFolder: File): InputStream? {
    val file: File = getFromURL(url, "mcp_$ver.zip", workFolder, getStringFromURL("$url.sha1")) ?: return null
    val zipFile = ZipFile(file)
    return zipFile.getInputStream(
        zipFile.stream()
            .filter { e -> e.name.equals("config/joined.tsrg") || e.name.equals("joined.srg") }
            .findFirst()
            .orElseThrow { RuntimeException("Searge mapping file not found") }
    )
}

fun intermediaryMapping(ver: String, workFolder: File): File? =
    getFromURL("https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/$ver.tiny", "$ver.tiny", workFolder, null)

fun spigotMapping(ver: String, workFolder: File): File? {
    val versionManifest: JsonNode = try {
        MAPPER.readTree(URL("https://hub.spigotmc.org/versions/$ver.json"))
    } catch (ignored: FileNotFoundException) {
        return null
    }
    val buildDataRev: String = versionManifest.path("refs").path("BuildData").asText()
    val buildDataManifest: JsonNode =
        MAPPER.readTree(URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/info.json?at=$buildDataRev"))
    return if (buildDataManifest.has("classMappings")) {
        getFromURL(
            "https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/" + buildDataManifest.path("classMappings")
                .asText().toString() + "?at=$buildDataRev",
            "spigot_$ver.csrg",
            workFolder,
            null
        )
    } else null
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