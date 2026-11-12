package eu.kanade.tachiyomi.ui.source.health

import java.net.URI

/** Deliberately conservative HTTP diagnostics; does not evaluate manga/chapter endpoints. */
internal enum class ProbeStatus {
    NOT_CHECKED,
    CHECKING,
    AVAILABLE,
    LIMITED,
    UNAVAILABLE,
    INVALID_ENDPOINT,
}

internal object SourceHealthRules {
    fun classify(statusCode: Int): ProbeStatus =
        when (statusCode) {
            in 200..399 -> ProbeStatus.AVAILABLE
            401, 403, 407, 429 -> ProbeStatus.LIMITED
            else -> ProbeStatus.UNAVAILABLE
        }

    /** Avoid creating an implicit scanner for local/private services from third-party extensions. */
    fun isSafeHttpEndpoint(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (host.contains(':')) return false // Only public DNS names / IPv4; no IPv6 loopback or link-local probes
        if (uri.userInfo != null || host == "localhost" || host.endsWith(".localhost")) return false
        if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".test")) return false
        if (host == "::1" || host == "[::1]" || host == "0.0.0.0") return false
        val ip = host.split('.')
        if (ip.size == 4 && ip.all { it.toIntOrNull() in 0..255 }) {
            val a = ip[0].toInt()
            val b = ip[1].toInt()
            if (a == 0 || a == 10 || a == 127 || a >= 224 ||
                (a == 169 && b == 254) || (a == 192 && b == 168) ||
                (a == 172 && b in 16..31) || (a == 100 && b in 64..127)
            ) return false
        }
        return true
    }
}
