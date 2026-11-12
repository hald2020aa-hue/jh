package eu.kanade.tachiyomi

import eu.kanade.tachiyomi.data.library.LibrarySelectionStore
import eu.kanade.tachiyomi.ui.library.search.DuplicateCandidate
import eu.kanade.tachiyomi.ui.library.search.FieldQueryNode
import eu.kanade.tachiyomi.ui.library.search.MangaField
import eu.kanade.tachiyomi.ui.library.search.QueryNode
import eu.kanade.tachiyomi.ui.library.search.crossSourceDuplicateIds
import eu.kanade.tachiyomi.ui.library.search.libraryTitleContains
import eu.kanade.tachiyomi.util.system.readAtMost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class ReleasePhaseTests {
    @Test fun dynamicLibrarySelectionsSurviveProcessRestart() {
        val dir = Files.createTempDirectory("j2k-test-selection").toFile()
        try {
            val fileName = LibrarySelectionStore(dir).save((1L..5000L).toList())
            assertEquals(5000, LibrarySelectionStore(dir).loadAndDelete(fileName).size)
            assertFalse(java.io.File(dir, fileName).exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun oversizedBackupIsRejectedBeforeFullAllocation() {
        val input = ByteArrayInputStream(ByteArray(33))
        assertTrue(runCatching { input.readAtMost(32) }.isFailure)
    }

    @Test fun duplicateSearchMatchesOnlyCrossSourceTitles() {
        val records = listOf(
            DuplicateCandidate(1, "أبطال الساحة", 1),
            DuplicateCandidate(2, "ابطال الساحة", 2),
            DuplicateCandidate(3, "أبطال الساحة", 1),
        )
        assertEquals(setOf(1L, 2L, 3L), crossSourceDuplicateIds(records))
        assertTrue(libraryTitleContains("أبطال الساحة", "ابطال"))
        val field = QueryNode.from("dup:yes") as FieldQueryNode
        assertEquals(MangaField.DUPLICATE, field.field)
    }
}
