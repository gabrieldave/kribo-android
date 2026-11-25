package com.blackeye.kribo.ui.home

import com.blackeye.kribo.data.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #41: ordering, pinned-on-top and scope filtering over a simulated 500-note library. */
class LibraryScaleTest {

    private fun note(
        id: Long,
        folderId: Long? = null,
        title: String = "Nota $id",
        updatedAt: Long = id,
        pinned: Boolean = false,
    ): NoteListItem = NoteListItem(
        note = NoteEntity(
            id = id,
            folderId = folderId,
            title = title,
            blocksJson = "[]",
            plainTextIndex = title,
            ocrTextIndex = "",
            isPinned = pinned,
            createdAt = updatedAt,
            updatedAt = updatedAt,
        ),
        snippet = title,
        matchedOcr = false,
        hasOcr = false,
        tasksDone = 0,
        tasksTotal = 0,
        attachmentPath = null,
    )

    private fun library500(): List<NoteListItem> =
        (1L..500L).map { id ->
            note(
                id = id,
                folderId = (id % 100L) + 1,
                updatedAt = id * 10,
                pinned = id == 250L,
            )
        }

    @Test
    fun pinnedStaysOnTopAndRecentSortsByUpdatedDesc() {
        val sorted = sortNoteItems(library500(), NoteSort.RECENT)

        assertEquals(500, sorted.size)
        assertEquals(250L, sorted.first().note.id)
        assertEquals(500L, sorted[1].note.id)
        assertEquals(1L, sorted.last().note.id)
    }

    @Test
    fun oldestSortsByUpdatedAscWithPinnedFirst() {
        val sorted = sortNoteItems(library500(), NoteSort.OLDEST)

        assertEquals(250L, sorted.first().note.id)
        assertEquals(1L, sorted[1].note.id)
        assertEquals(500L, sorted.last().note.id)
    }

    @Test
    fun titleSortIsAlphabeticalAndStable() {
        val items = listOf(
            note(1, title = "banana"),
            note(2, title = "Ábaco"),
            note(3, title = "cereza"),
            note(4, title = "albacete", pinned = true),
        )

        val sorted = sortNoteItems(items, NoteSort.TITLE_ASC)

        assertEquals(4L, sorted.first().note.id)
        assertEquals(
            listOf(2L, 1L, 3L),
            sorted.drop(1).map { it.note.id },
        )
    }

    @Test
    fun folderScopeKeepsOnlyThatFolder() {
        val library = library500()
        val inFolder7 = library.filter { it.note.folderId == 7L }

        assertEquals(5, inFolder7.size)
        assertTrue(inFolder7.all { it.note.folderId == 7L })
    }

    @Test
    fun sortingIsDeterministicUnderReordering() {
        val library = library500().reversed()
        val first = sortNoteItems(library, NoteSort.RECENT).map { it.note.id }
        val second = sortNoteItems(library500(), NoteSort.RECENT).map { it.note.id }

        assertEquals(first, second)
    }
}
