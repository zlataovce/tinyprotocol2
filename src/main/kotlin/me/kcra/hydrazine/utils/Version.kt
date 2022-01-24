package me.kcra.hydrazine.utils

class Version(version: String) {
    val majorVersion: Int
    val minorVersion: Int
    val patchVersion: Int
    val suffix: String?

    init {
        val split: List<String> =
            (if (version.contains('-')) version.substring(0, version.indexOf('-')) else version).split('.')
        majorVersion = split[0].toInt()
        minorVersion = split[1].toInt()
        patchVersion = split.getOrNull(2)?.toInt() ?: 0
        suffix = version.indexOf('-').let {
            if (it == -1) {
                return@let null
            }
            return@let version.substring(it + 1)
        }
    }

    fun matches(version: String): Boolean = toString() == version

    fun matches(version: Version): Boolean =
        matches(version.majorVersion, version.minorVersion, version.patchVersion, version.suffix)

    fun matches(major: Int, minor: Int, patch: Int, suffix: String?): Boolean =
        this.majorVersion == major && this.minorVersion == minor && this.patchVersion == patch && this.suffix == suffix

    fun isOlderThan(version: String): Boolean {
        val other = Version(version)
        return majorVersion < other.majorVersion ||
                (majorVersion == other.majorVersion && minorVersion < other.minorVersion) ||
                (majorVersion == other.majorVersion && minorVersion == other.minorVersion && patchVersion < other.patchVersion)
    }

    fun isOlderThan(major: Int, minor: Int, patch: Int, suffix: String?): Boolean =
        majorVersion < major || minorVersion < minor || patchVersion < patch || this.suffix != suffix

    fun isOlderThan(other: Version): Boolean {
        return majorVersion < other.majorVersion
                || majorVersion == other.majorVersion && minorVersion < other.minorVersion
                || majorVersion == other.majorVersion && minorVersion == other.minorVersion && patchVersion < other.patchVersion
    }

    fun isNewerThan(version: String): Boolean {
        val other = Version(version)
        return majorVersion > other.majorVersion ||
                (majorVersion == other.majorVersion && minorVersion > other.minorVersion) ||
                (majorVersion == other.majorVersion && minorVersion == other.minorVersion && patchVersion > other.patchVersion)
    }

    fun isNewerThan(major: Int, minor: Int, patch: Int, suffix: String?): Boolean =
        majorVersion > major || (minorVersion > minor && patchVersion > patch) || this.suffix != suffix

    fun isNewerThan(other: Version): Boolean {
        return majorVersion > other.majorVersion ||
                majorVersion == other.majorVersion && minorVersion > other.minorVersion ||
                majorVersion == other.majorVersion && minorVersion == other.minorVersion && patchVersion > other.patchVersion
    }

    fun isBetween(min: String, max: String): Boolean = isBetween(Version(min), Version(max))

    fun isBetween(min: Version, max: Version): Boolean =
        (isNewerThan(min) && isOlderThan(max)) || matches(min) || matches(max)

    override fun toString(): String =
        "$majorVersion.$minorVersion${if (patchVersion != 0) ".$patchVersion" else ""}${if (suffix != null) "-$suffix" else ""}"
}