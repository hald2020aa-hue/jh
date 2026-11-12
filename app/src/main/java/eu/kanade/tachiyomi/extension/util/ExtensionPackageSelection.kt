package eu.kanade.tachiyomi.extension.util

/**
 * Selects one package per extension name while preserving existing J2K precedence.
 *
 * The private extensions are indexed once. Reusing a lazy Sequence for every shared
 * extension used to reparse APK archives repeatedly (quadratic in the number of APKs).
 * A duplicate private package name deliberately has no unique private match: this
 * matches the historical `singleOrNull` behavior for that edge case.
 */
internal fun <T> selectExtensionPackages(
    sharedExtensions: List<T>,
    privateExtensions: List<T>,
    packageName: (T) -> String,
    selectByVersion: (T?, T?) -> T?,
): List<T> {
    val privateByPackage = privateExtensions.groupBy(packageName)

    return (sharedExtensions + privateExtensions)
        .distinctBy(packageName)
        .mapNotNull { candidate ->
            val privateMatch = privateByPackage[packageName(candidate)]?.singleOrNull()
            selectByVersion(candidate, privateMatch)
        }
}
