package eu.kanade.tachiyomi

import eu.kanade.tachiyomi.data.database.models.isManhuaTagText
import eu.kanade.tachiyomi.data.database.models.isManhwaTagText
import eu.kanade.tachiyomi.extension.util.selectExtensionPackages
import eu.kanade.tachiyomi.ui.reader.viewer.hasAvifBrand
import eu.kanade.tachiyomi.ui.reader.viewer.isAvifContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import eu.kanade.tachiyomi.extension.api.readLimitedBytes
import eu.kanade.tachiyomi.data.download.writeStoredZipEntry
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class RegressionTests {
    private data class Extension(val id: String, val version: Int, val shared: Boolean)

    @Test fun selectsNewerPrivateExtensionAndKeepsSharedOnTies() {
        val shared = Extension("a", 1, true)
        val private = Extension("a", 2, false)
        fun select(sharedValue: Extension, privateValue: Extension) =
            selectExtensionPackages(listOf(sharedValue), listOf(privateValue), { it.id }) { left, right ->
                if (right != null && (left == null || right.version > left.version)) right else left
            }.single()
        assertEquals(private, select(shared, private))
        assertEquals(shared.copy(version = 2), select(shared.copy(version = 2), private))
    }

    @Test fun handlesLargeExtensionCatalogWithoutDuplicateParsing() {
        val installed = (1..250).map { Extension("pkg.$it", 1, true) }
        val private = (1..202).map { Extension("pkg.$it", 2, false) }
        var selections = 0
        val result = selectExtensionPackages(installed, private, { it.id }) { a, b ->
            selections++
            if (b != null && (a == null || b.version > a.version)) b else a
        }
        assertEquals(250, selections)
        assertEquals(202, result.count { !it.shared })
    }

    @Test fun acceptsManhwaAliasesAndChineseScriptAliases() {
        assertTrue(isManhwaTagText(" Manwha "))
        assertTrue(isManhwaTagText("manhwa"))
        assertFalse(isManhwaTagText("manhua"))
        assertTrue(isManhuaTagText("zh-Hans"))
        assertTrue(isManhuaTagText("ZH-hant"))
        assertFalse(isManhuaTagText("manwha"))
    }

    @Test fun avifSniffFindsCompatibleBrandWithoutConsumingStream() {
        val buffer = byteArrayOf(0, 0, 0, 24) + "ftypmif10000avif".toByteArray()
        val stream = ByteArrayInputStream(buffer)
        assertTrue(hasAvifBrand(buffer))
        assertTrue(isAvifContainer(stream))
        assertEquals(0, stream.read())
    }

    @Test fun streamingCbzCanReopenMultipleEntries() {
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            writeStoredZipEntry(zip, "001.jpg") { ByteArrayInputStream(byteArrayOf(1, 2)) }
            writeStoredZipEntry(zip, "ComicInfo.xml") { ByteArrayInputStream("<ComicInfo/>".toByteArray()) }
        }
        ZipInputStream(ByteArrayInputStream(archive.toByteArray())).use { reader ->
            assertEquals("001.jpg", reader.nextEntry.name)
            assertTrue(reader.readBytes().contentEquals(byteArrayOf(1, 2)))
            assertEquals("ComicInfo.xml", reader.nextEntry.name)
        }
    }

    @Test fun refusesOversizedRepoIndexes() {
        assertEquals(16, ByteArrayInputStream(ByteArray(16)).readLimitedBytes(16).size)
        var rejected = false
        try { ByteArrayInputStream(ByteArray(17)).readLimitedBytes(16) }
        catch (_: IOException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun rejectsOtherImageContainers() {
        val heic = byteArrayOf(0, 0, 0, 24) + "ftypheic0000mif1".toByteArray()
        assertFalse(hasAvifBrand(heic))
        assertFalse(hasAvifBrand(byteArrayOf(0xff.toByte(), 0xd8.toByte())))
    }
}
