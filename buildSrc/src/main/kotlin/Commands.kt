import org.gradle.api.Project
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.util.Date

// Git metadata is available in a normal checkout, but source ZIPs may not
// contain the original worktree metadata. Treat missing Git data as an
// archive build instead of failing Gradle configuration.
fun Project.getCommitCount(): String {
    return runCommand("git rev-list --count HEAD").ifBlank { "0" }
}

fun Project.getBetaCount(): String {
    val betaTags = runCommand("git tag -l --sort=refname v${AndroidVersions.versionName}-b*")
    return String.format("%02d", if (betaTags.isNotEmpty()) {
        val betaTag = betaTags.split("\n").last().substringAfter("-b").toIntOrNull()
        ((betaTag ?: 0) + 1)
    } else {
        1
    })
}

fun Project.getGitSha(): String {
    return runCommand("git rev-parse --short HEAD").ifBlank { "archive" }
}

fun Project.getBuildTime(): String {
    val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'")
    df.timeZone = TimeZone.getTimeZone("UTC")
    return df.format(Date())
}

fun Project.runCommand(command: String): String {
    return try {
        val output = providers.exec {
            commandLine = command.split(" ")
            isIgnoreExitValue = true
        }
        if (output.result.get().exitValue == 0) {
            output.standardOutput.asText.get().trim()
        } else {
            ""
        }
    } catch (_: Exception) {
        ""
    }
}
