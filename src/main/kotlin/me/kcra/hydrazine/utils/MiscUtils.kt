package me.kcra.hydrazine.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.minecraftforge.srgutils.IMappingFile
import java.io.*
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.*
import java.util.stream.Collectors
import java.util.zip.ZipFile
import kotlin.experimental.and

val MAPPER: ObjectMapper = jacksonObjectMapper()
private val SHA_1 = MessageDigest.getInstance("SHA-1")

fun newFile(fileName: String, workFolder: File): File {
    workFolder.mkdirs()
    return Path.of(workFolder.absolutePath, fileName).toFile()
}

fun getFromURL(url: String, fileName: String, workFolder: File, sha1: String?): File? {
    val downloadedFile: File = newFile(fileName, workFolder)
    if (downloadedFile.isFile && sha1 != null) {
        if (getFileChecksum(SHA_1, downloadedFile) == sha1) {
            return downloadedFile
        }
    }
    try {
        URL(url).openStream().use { inputStream ->
            Files.copy(
                inputStream,
                downloadedFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    } catch (e: Exception) {
        return null
    }
    return downloadedFile
}

fun getFromURL(url: String): String? {
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
                    res + "_" + ver + ".res",
                    workFolder,
                    versionManifest.path("downloads").path(res).get("sha1").asText()
                )
            }
        }
    }
    return null
}

fun seargeMapping(ver: String, workFolder: File): File? {
    return Objects.requireNonNullElseGet(
        seargeMapping0("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/$ver/mcp_config-$ver.zip", ver, workFolder)
    ) {
        seargeMapping0(
            "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/$ver/mcp-$ver-srg.zip",
            ver,
            workFolder
        )
    }
}

fun seargeMappingRaw(ver: String, workFolder: File): InputStream? {
    return Objects.requireNonNullElseGet(
        seargeMapping1("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/$ver/mcp_config-$ver.zip", ver, workFolder)
    ) {
        seargeMapping1(
            "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/$ver/mcp-$ver-srg.zip",
            ver,
            workFolder
        )
    }
}

private fun seargeMapping0(url: String, ver: String, workFolder: File): File? {
    val extractedFile: File = newFile("mcp_$ver.extracted", workFolder)
    val output = seargeMapping1(url, ver, workFolder) ?: return null
    Files.copy(output, extractedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    return extractedFile
}

private fun seargeMapping1(url: String, ver: String, workFolder: File): InputStream? {
    val file: File = getFromURL(url, "mcp_$ver.zip", workFolder, getFromURL("$url.sha1")) ?: return null
    val zipFile = ZipFile(file)
    return zipFile.getInputStream(
        zipFile.stream()
            .filter { e -> e.name.equals("config/joined.tsrg") || e.name.equals("joined.srg") }
            .findFirst()
            .orElseThrow { RuntimeException("Searge mapping file not found") }
    )
}

fun intermediaryMapping(ver: String, workFolder: File): File? =
    getFromURL(
        "https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/$ver.tiny",
        "$ver.tiny", workFolder, null
    )

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

fun reverseMappingFile(file: File, format: IMappingFile.Format) {
    val iMappingFile: IMappingFile = IMappingFile.load(file)
    if (iMappingFile.classes.stream().anyMatch { e -> e.original.contains("/") }) {
        // needs to be reversed
        iMappingFile.reverse().write(file.toPath(), format, false)
    }
}