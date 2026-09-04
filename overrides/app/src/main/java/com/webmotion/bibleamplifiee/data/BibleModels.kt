package com.webmotion.bibleamplifiee.data

data class BibleVerse(
    val number: Int,
    val textEn: String
)

data class BibleChapter(
    val number: Int,
    val verses: List<BibleVerse>
)

data class BibleBook(
    val number: Int,
    val nameFr: String,
    val nameEn: String,
    val testament: Testament,
    val chapters: List<BibleChapter>
)

enum class Testament { OLD, NEW }

data class VerseRef(val book: Int, val chapter: Int, val verse: Int) {
    val key: String get() = "$book:$chapter:$verse"

    companion object {
        fun fromKey(key: String): VerseRef? {
            val p = key.split(":")
            if (p.size != 3) return null
            return VerseRef(
                p[0].toIntOrNull() ?: return null,
                p[1].toIntOrNull() ?: return null,
                p[2].toIntOrNull() ?: return null
            )
        }
    }
}

data class SearchHit(
    val ref: VerseRef,
    val bookName: String,
    val english: String,
    val french: String?
)

enum class HighlightColor {
    NONE, YELLOW, GREEN, BLUE, PINK
}

data class HistoryEntry(
    val ref: VerseRef,
    val timestamp: Long
)

data class WorkspaceState(
    val id: Int,
    val name: String,
    val primaryRef: VerseRef,
    val secondaryRef: VerseRef? = null,
    val split: Boolean = false
)

data class ReadingPlanDay(
    val number: Int,
    val title: String,
    val ref: VerseRef
)

data class ReadingPlan(
    val id: String,
    val title: String,
    val description: String,
    val days: List<ReadingPlanDay>
)
