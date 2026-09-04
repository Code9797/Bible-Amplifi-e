package com.webmotion.bibleamplifiee

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.webmotion.bibleamplifiee.data.*
import com.webmotion.bibleamplifiee.translation.BibleTextToSpeech
import com.webmotion.bibleamplifiee.translation.FrenchTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen {
    READER, LIBRARY, SEARCH, BOOKMARKS, NOTES, HISTORY, WORKSPACES, PLANS, SETTINGS
}

enum class DisplayMode { FR, BILINGUAL, EN }

class BibleViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BibleRepository(app)
    private val cache = TranslationCache(app)
    private val prefs = PreferenceStore(app)
    private val translator = FrenchTranslator()
    private val secondaryTranslator = FrenchTranslator()
    private val tts = BibleTextToSpeech(app)

    private var translationJob: Job? = null
    private var secondaryTranslationJob: Job? = null

    private val _books = MutableStateFlow<List<BibleBook>>(emptyList())
    val books: StateFlow<List<BibleBook>> = _books.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _screen = MutableStateFlow(Screen.READER)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _selectedBook = MutableStateFlow<BibleBook?>(null)
    val selectedBook: StateFlow<BibleBook?> = _selectedBook.asStateFlow()

    private val _chapter = MutableStateFlow(1)
    val chapter: StateFlow<Int> = _chapter.asStateFlow()

    private val _translations = MutableStateFlow<Map<Int, String>>(emptyMap())
    val translations: StateFlow<Map<Int, String>> = _translations.asStateFlow()

    private val _translationStatus = MutableStateFlow("Prêt")
    val translationStatus: StateFlow<String> = _translationStatus.asStateFlow()

    private val _secondaryBook = MutableStateFlow<BibleBook?>(null)
    val secondaryBook: StateFlow<BibleBook?> = _secondaryBook.asStateFlow()

    private val _secondaryChapter = MutableStateFlow(1)
    val secondaryChapter: StateFlow<Int> = _secondaryChapter.asStateFlow()

    private val _secondaryTranslations = MutableStateFlow<Map<Int, String>>(emptyMap())
    val secondaryTranslations: StateFlow<Map<Int, String>> = _secondaryTranslations.asStateFlow()

    private val _splitView = MutableStateFlow(false)
    val splitView: StateFlow<Boolean> = _splitView.asStateFlow()

    private val _displayMode = MutableStateFlow(
        runCatching { DisplayMode.valueOf(prefs.displayMode) }.getOrDefault(DisplayMode.FR)
    )
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    private val _darkMode = MutableStateFlow(prefs.darkMode)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _fontScale = MutableStateFlow(prefs.fontScale.coerceIn(0.80f, 1.70f))
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    private val _favorites = MutableStateFlow(prefs.favorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _notes = MutableStateFlow(prefs.notes())
    val notes: StateFlow<Map<String, String>> = _notes.asStateFlow()

    private val _highlights = MutableStateFlow(prefs.highlights())
    val highlights: StateFlow<Map<String, HighlightColor>> = _highlights.asStateFlow()

    private val _labels = MutableStateFlow(prefs.labels())
    val labels: StateFlow<Map<String, Set<String>>> = _labels.asStateFlow()

    private val _history = MutableStateFlow(prefs.history())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _workspaces = MutableStateFlow(prefs.workspaces())
    val workspaces: StateFlow<List<WorkspaceState>> = _workspaces.asStateFlow()

    private val _activeWorkspaceId = MutableStateFlow(prefs.activeWorkspaceId)
    val activeWorkspaceId: StateFlow<Int> = _activeWorkspaceId.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchHit>>(emptyList())
    val searchResults: StateFlow<List<SearchHit>> = _searchResults.asStateFlow()

    val readingPlans: List<ReadingPlan> = listOf(
        ReadingPlan(
            id = "john21",
            title = "Évangile de Jean — 21 jours",
            description = "Un chapitre de Jean par jour.",
            days = (1..21).map { ReadingPlanDay(it, "Jean $it", VerseRef(43, it, 1)) }
        ),
        ReadingPlan(
            id = "psalms30",
            title = "Psaumes — 30 jours",
            description = "Commencer les Psaumes, un chapitre par jour.",
            days = (1..30).map { ReadingPlanDay(it, "Psaume $it", VerseRef(19, it, 1)) }
        ),
        ReadingPlan(
            id = "proverbs31",
            title = "Proverbes — 31 jours",
            description = "Un chapitre de Proverbes chaque jour.",
            days = (1..31).map { ReadingPlanDay(it, "Proverbes $it", VerseRef(20, it, 1)) }
        )
    )

    private val _planProgress = MutableStateFlow(
        readingPlans.associate { it.id to prefs.planProgress(it.id) }
    )
    val planProgress: StateFlow<Map<String, Set<String>>> = _planProgress.asStateFlow()

    init {
        viewModelScope.launch {
            _books.value = repo.loadBooks()
            restoreActiveWorkspace()
            _loading.value = false
        }
    }

    fun navigate(screen: Screen) {
        _screen.value = screen
        if (screen != Screen.READER) {
            translationJob?.cancel()
            secondaryTranslationJob?.cancel()
        }
    }

    fun openBook(book: BibleBook, chapter: Int = 1, verse: Int = 1) {
        openRef(VerseRef(book.number, chapter, verse))
    }

    fun openRef(ref: VerseRef, addToHistory: Boolean = true) {
        val book = _books.value.firstOrNull { it.number == ref.book } ?: return
        _selectedBook.value = book
        _chapter.value = ref.chapter.coerceIn(1, book.chapters.size)
        _screen.value = Screen.READER
        if (addToHistory) {
            prefs.addHistory(ref)
            _history.value = prefs.history()
        }
        updateActiveWorkspacePrimary(ref.copy(chapter = _chapter.value, verse = ref.verse.coerceAtLeast(1)))
        loadAndTranslateChapter()
    }

    fun openSecondaryRef(ref: VerseRef) {
        val book = _books.value.firstOrNull { it.number == ref.book } ?: return
        _secondaryBook.value = book
        _secondaryChapter.value = ref.chapter.coerceIn(1, book.chapters.size)
        _splitView.value = true
        updateActiveWorkspaceSecondary(ref.copy(chapter = _secondaryChapter.value, verse = ref.verse.coerceAtLeast(1)), true)
        loadAndTranslateSecondaryChapter()
    }

    fun setChapter(number: Int) {
        val book = _selectedBook.value ?: return
        _chapter.value = number.coerceIn(1, book.chapters.size)
        val ref = VerseRef(book.number, _chapter.value, 1)
        prefs.addHistory(ref)
        _history.value = prefs.history()
        updateActiveWorkspacePrimary(ref)
        loadAndTranslateChapter()
    }

    fun setSecondaryChapter(number: Int) {
        val book = _secondaryBook.value ?: return
        _secondaryChapter.value = number.coerceIn(1, book.chapters.size)
        updateActiveWorkspaceSecondary(VerseRef(book.number, _secondaryChapter.value, 1), true)
        loadAndTranslateSecondaryChapter()
    }

    fun nextChapter() = stepChapter(1)
    fun previousChapter() = stepChapter(-1)

    private fun stepChapter(delta: Int) {
        val books = _books.value
        val book = _selectedBook.value ?: return
        val nextChapter = _chapter.value + delta
        when {
            nextChapter in 1..book.chapters.size -> setChapter(nextChapter)
            delta > 0 -> {
                val nextBook = books.firstOrNull { it.number == book.number + 1 } ?: return
                openBook(nextBook, 1)
            }
            else -> {
                val previousBook = books.firstOrNull { it.number == book.number - 1 } ?: return
                openBook(previousBook, previousBook.chapters.size)
            }
        }
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
        prefs.displayMode = mode.name
        if (mode != DisplayMode.EN) {
            loadAndTranslateChapter()
            if (_splitView.value) loadAndTranslateSecondaryChapter()
        }
    }

    fun setDarkMode(value: Boolean) {
        _darkMode.value = value
        prefs.darkMode = value
    }

    fun setFontScale(value: Float) {
        _fontScale.value = value.coerceIn(0.80f, 1.70f)
        prefs.fontScale = _fontScale.value
    }

    fun toggleFavorite(ref: VerseRef) {
        prefs.toggleFavorite(ref)
        _favorites.value = prefs.favorites()
    }

    fun setNote(ref: VerseRef, text: String) {
        prefs.setNote(ref, text)
        _notes.value = prefs.notes()
    }

    fun setHighlight(ref: VerseRef, color: HighlightColor) {
        prefs.setHighlight(ref, color)
        _highlights.value = prefs.highlights()
    }

    fun setLabels(ref: VerseRef, labels: Set<String>) {
        prefs.setLabels(ref, labels)
        _labels.value = prefs.labels()
    }

    fun toggleSplitView() {
        val enabled = !_splitView.value
        _splitView.value = enabled
        if (enabled && _secondaryBook.value == null) {
            val primary = currentPrimaryRef() ?: VerseRef(1, 1, 1)
            val book = _selectedBook.value
            val secondary = if (book != null && _chapter.value < book.chapters.size) {
                VerseRef(book.number, _chapter.value + 1, 1)
            } else primary
            openSecondaryRef(secondary)
        } else {
            updateActiveWorkspaceSecondary(currentSecondaryRef(), enabled)
        }
    }

    fun createWorkspace() {
        val list = _workspaces.value.toMutableList()
        val newId = (list.maxOfOrNull { it.id } ?: 0) + 1
        val base = currentPrimaryRef() ?: VerseRef(1, 1, 1)
        val workspace = WorkspaceState(newId, "Espace $newId", base)
        list += workspace
        saveWorkspaces(list)
        activateWorkspace(newId)
    }

    fun copyWorkspace(id: Int) {
        val source = _workspaces.value.firstOrNull { it.id == id } ?: return
        val list = _workspaces.value.toMutableList()
        val newId = (list.maxOfOrNull { it.id } ?: 0) + 1
        list += source.copy(id = newId, name = "${source.name} — copie")
        saveWorkspaces(list)
    }

    fun deleteWorkspace(id: Int) {
        val current = _workspaces.value
        if (current.size <= 1) return
        val list = current.filterNot { it.id == id }
        saveWorkspaces(list)
        if (_activeWorkspaceId.value == id) activateWorkspace(list.first().id)
    }

    fun renameWorkspace(id: Int, name: String) {
        val clean = name.trim().ifBlank { "Espace $id" }
        saveWorkspaces(_workspaces.value.map { if (it.id == id) it.copy(name = clean) else it })
    }

    fun activateWorkspace(id: Int) {
        val ws = _workspaces.value.firstOrNull { it.id == id } ?: return
        _activeWorkspaceId.value = id
        prefs.activeWorkspaceId = id
        _splitView.value = ws.split
        restorePrimary(ws.primaryRef)
        if (ws.split && ws.secondaryRef != null) restoreSecondary(ws.secondaryRef) else {
            _secondaryBook.value = null
            _secondaryTranslations.value = emptyMap()
        }
        _screen.value = Screen.READER
    }

    fun clearHistory() {
        prefs.clearHistory()
        _history.value = emptyList()
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val byRef = linkedMapOf<String, SearchHit>()
            _books.value.forEach { book ->
                book.chapters.forEach { ch ->
                    ch.verses.forEach { verse ->
                        if (verse.textEn.contains(q, ignoreCase = true) && byRef.size < 150) {
                            val ref = VerseRef(book.number, ch.number, verse.number)
                            byRef[ref.key] = SearchHit(ref, book.nameFr, verse.textEn, cache.get(ref))
                        }
                    }
                }
            }
            cache.searchFrench(q, 150).forEach { (ref, fr) ->
                if (byRef.size < 150 || byRef.containsKey(ref.key)) {
                    val found = repo.findVerse(ref)
                    if (found != null) {
                        byRef[ref.key] = SearchHit(ref, found.first.nameFr, found.second.textEn, fr)
                    }
                }
            }
            withContext(Dispatchers.Main) { _searchResults.value = byRef.values.take(150) }
        }
    }

    fun bookmarkHits(): List<SearchHit> = _favorites.value.mapNotNull(::hitForKey)
        .sortedWith(compareBy({ it.ref.book }, { it.ref.chapter }, { it.ref.verse }))

    fun noteHits(): List<Pair<SearchHit, String>> = _notes.value.mapNotNull { (key, note) ->
        hitForKey(key)?.let { it to note }
    }.sortedWith(compareBy({ it.first.ref.book }, { it.first.ref.chapter }, { it.first.ref.verse }))

    fun historyHits(): List<SearchHit> = _history.value.mapNotNull { entry -> hitForRef(entry.ref) }

    fun referenceLabel(ref: VerseRef): String {
        val book = _books.value.firstOrNull { it.number == ref.book }
        return "${book?.nameFr ?: "Livre ${ref.book}"} ${ref.chapter}:${ref.verse}"
    }

    fun verseText(ref: VerseRef): String {
        val found = repo.findVerse(ref) ?: return ""
        val french = cache.get(ref)
        return when (_displayMode.value) {
            DisplayMode.FR -> french ?: found.second.textEn
            DisplayMode.BILINGUAL -> listOfNotNull(french, found.second.textEn).joinToString("\n\n")
            DisplayMode.EN -> found.second.textEn
        }
    }

    fun speakRef(ref: VerseRef) {
        val found = repo.findVerse(ref) ?: return
        val text = cache.get(ref) ?: found.second.textEn
        tts.speak(text, ref.key)
    }

    fun speakVerse(verseNumber: Int) {
        val book = _selectedBook.value ?: return
        speakRef(VerseRef(book.number, _chapter.value, verseNumber))
    }

    fun speakCurrentChapter() {
        val book = _selectedBook.value ?: return
        val ch = book.chapters.firstOrNull { it.number == _chapter.value } ?: return
        val lines = ch.verses.map { verse ->
            cache.get(VerseRef(book.number, ch.number, verse.number)) ?: verse.textEn
        }
        tts.speakChapter(lines)
    }

    fun stopSpeech() = tts.stop()

    fun togglePlanDay(planId: String, day: Int) {
        val updated = prefs.togglePlanDay(planId, day)
        _planProgress.value = _planProgress.value.toMutableMap().apply { put(planId, updated) }
    }

    fun openPlanDay(day: ReadingPlanDay) = openRef(day.ref)

    private fun hitForKey(key: String): SearchHit? = VerseRef.fromKey(key)?.let(::hitForRef)

    private fun hitForRef(ref: VerseRef): SearchHit? {
        val found = repo.findVerse(ref) ?: return null
        return SearchHit(ref, found.first.nameFr, found.second.textEn, cache.get(ref))
    }

    private fun restoreActiveWorkspace() {
        var list = _workspaces.value
        if (list.isEmpty()) {
            list = listOf(WorkspaceState(1, "Espace 1", VerseRef(1, 1, 1)))
            saveWorkspaces(list)
        }
        val active = list.firstOrNull { it.id == _activeWorkspaceId.value } ?: list.first()
        _activeWorkspaceId.value = active.id
        prefs.activeWorkspaceId = active.id
        _splitView.value = active.split
        restorePrimary(active.primaryRef)
        if (active.split && active.secondaryRef != null) restoreSecondary(active.secondaryRef)
    }

    private fun restorePrimary(ref: VerseRef) {
        val book = _books.value.firstOrNull { it.number == ref.book } ?: _books.value.firstOrNull() ?: return
        _selectedBook.value = book
        _chapter.value = ref.chapter.coerceIn(1, book.chapters.size)
        loadAndTranslateChapter()
    }

    private fun restoreSecondary(ref: VerseRef) {
        val book = _books.value.firstOrNull { it.number == ref.book } ?: return
        _secondaryBook.value = book
        _secondaryChapter.value = ref.chapter.coerceIn(1, book.chapters.size)
        loadAndTranslateSecondaryChapter()
    }

    private fun currentPrimaryRef(): VerseRef? = _selectedBook.value?.let { VerseRef(it.number, _chapter.value, 1) }

    private fun currentSecondaryRef(): VerseRef? = _secondaryBook.value?.let { VerseRef(it.number, _secondaryChapter.value, 1) }

    private fun updateActiveWorkspacePrimary(ref: VerseRef) {
        val id = _activeWorkspaceId.value
        saveWorkspaces(_workspaces.value.map { if (it.id == id) it.copy(primaryRef = ref) else it })
    }

    private fun updateActiveWorkspaceSecondary(ref: VerseRef?, enabled: Boolean) {
        val id = _activeWorkspaceId.value
        saveWorkspaces(_workspaces.value.map {
            if (it.id == id) it.copy(secondaryRef = ref ?: it.secondaryRef, split = enabled) else it
        })
    }

    private fun saveWorkspaces(list: List<WorkspaceState>) {
        _workspaces.value = list
        prefs.saveWorkspaces(list)
    }

    private fun loadAndTranslateChapter() {
        translationJob?.cancel()
        val book = _selectedBook.value ?: return
        val chapterNumber = _chapter.value
        val ch = book.chapters.firstOrNull { it.number == chapterNumber } ?: return

        translationJob = viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { cache.getChapter(book.number, chapterNumber) }
            _translations.value = cached
            if (_displayMode.value == DisplayMode.EN) {
                _translationStatus.value = "Affichage anglais"
                return@launch
            }
            val missing = ch.verses.filterNot { cached.containsKey(it.number) }
            if (missing.isEmpty()) {
                _translationStatus.value = "Français disponible hors ligne"
                return@launch
            }
            try {
                _translationStatus.value = "Préparation de la traduction française…"
                translator.prepareModel()
                val working = cached.toMutableMap()
                missing.forEachIndexed { index, verse ->
                    val fr = translator.translate(verse.textEn)
                    val ref = VerseRef(book.number, chapterNumber, verse.number)
                    withContext(Dispatchers.IO) { cache.put(ref, fr) }
                    working[verse.number] = fr
                    _translations.value = working.toMap()
                    _translationStatus.value = "Traduction ${cached.size + index + 1}/${ch.verses.size}"
                }
                _translationStatus.value = "Français enregistré hors ligne"
            } catch (e: Exception) {
                _translationStatus.value = "Traduction indisponible : ${e.localizedMessage ?: "modèle requis"}"
            }
        }
    }

    private fun loadAndTranslateSecondaryChapter() {
        secondaryTranslationJob?.cancel()
        val book = _secondaryBook.value ?: return
        val chapterNumber = _secondaryChapter.value
        val ch = book.chapters.firstOrNull { it.number == chapterNumber } ?: return

        secondaryTranslationJob = viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { cache.getChapter(book.number, chapterNumber) }
            _secondaryTranslations.value = cached
            if (_displayMode.value == DisplayMode.EN) return@launch
            val missing = ch.verses.filterNot { cached.containsKey(it.number) }
            if (missing.isEmpty()) return@launch
            try {
                secondaryTranslator.prepareModel()
                val working = cached.toMutableMap()
                missing.forEach { verse ->
                    val fr = secondaryTranslator.translate(verse.textEn)
                    val ref = VerseRef(book.number, chapterNumber, verse.number)
                    withContext(Dispatchers.IO) { cache.put(ref, fr) }
                    working[verse.number] = fr
                    _secondaryTranslations.value = working.toMap()
                }
            } catch (_: Exception) {
                // La fenêtre secondaire garde le texte anglais si le modèle n'est pas prêt.
            }
        }
    }

    override fun onCleared() {
        translationJob?.cancel()
        secondaryTranslationJob?.cancel()
        translator.close()
        secondaryTranslator.close()
        tts.close()
        cache.close()
        super.onCleared()
    }
}
