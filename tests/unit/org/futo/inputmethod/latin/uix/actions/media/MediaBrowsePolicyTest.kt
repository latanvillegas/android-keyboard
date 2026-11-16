package org.futo.inputmethod.latin.uix.actions.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBrowsePolicyTest {
    private fun item(
        id: String,
        kind: MediaKind,
        type: MediaContentType,
        tags: List<String> = emptyList(),
        favorite: Boolean = false,
        recent: Long = 0L
    ) = MediaItem(
        id = id, kind = kind, displayName = "Greeting $id",
        length = 100, createdAt = 1L, favorite = favorite,
        contentType = type, tags = tags, collection = "Familia",
        lastUsedAt = recent
    )

    @Test fun gifAndImageClassificationDoNotConflateStickers() {
        val gif = item("gif", MediaKind.GIF, MediaContentType.GIF)
        val photo = item("photo", MediaKind.JPEG, MediaContentType.IMAGE)
        val sticker = item("sticker", MediaKind.PNG, MediaContentType.STICKER)
        assertTrue(MediaBrowsePolicy.matches(gif, MediaShelf.GIF, ""))
        assertFalse(MediaBrowsePolicy.matches(gif, MediaShelf.STICKERS, ""))
        assertFalse(MediaBrowsePolicy.matches(photo, MediaShelf.STICKERS, ""))
        assertTrue(MediaBrowsePolicy.matches(photo, MediaShelf.IMAGES, ""))
        assertTrue(MediaBrowsePolicy.matches(sticker, MediaShelf.STICKERS, ""))
    }

    @Test fun searchUsesTagsNamesAndCollections() {
        val saved = item("one", MediaKind.PNG, MediaContentType.STICKER, listOf("Saludos", "trabajo"))
        assertTrue(MediaBrowsePolicy.matches(saved, MediaShelf.ALL, "TRABAJO"))
        assertTrue(MediaBrowsePolicy.matches(saved, MediaShelf.ALL, "Familia"))
        assertTrue(MediaBrowsePolicy.matches(saved, MediaShelf.ALL, "greeting"))
        assertFalse(MediaBrowsePolicy.matches(saved, MediaShelf.ALL, "inexistente"))
        assertTrue(MediaBrowsePolicy.matches(saved, MediaShelf.ALL, "", "Familia"))
        assertFalse(MediaBrowsePolicy.matches(saved, MediaShelf.ALL, "", "Otra"))
    }

    @Test fun recentsAndFavoritesReferToSharedMetadata() {
        val first = item("first", MediaKind.GIF, MediaContentType.GIF, favorite = true, recent = 40)
        val second = item("second", MediaKind.PNG, MediaContentType.STICKER, recent = 80)
        val never = item("never", MediaKind.PNG, MediaContentType.STICKER)
        assertTrue(MediaBrowsePolicy.matches(first, MediaShelf.FAVORITES, ""))
        assertFalse(MediaBrowsePolicy.matches(second, MediaShelf.FAVORITES, ""))
        assertFalse(MediaBrowsePolicy.matches(never, MediaShelf.RECENT, ""))
        assertEquals(listOf("second", "first"),
            MediaBrowsePolicy.sorted(listOf(first, second), MediaShelf.RECENT).map { it.id })
    }
}
