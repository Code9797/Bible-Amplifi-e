package com.webmotion.bibleamplifiee

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webmotion.bibleamplifiee.data.*
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleApp(vm: BibleViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val books by vm.books.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val selectedBook by vm.selectedBook.collectAsStateWithLifecycle()
    val chapter by vm.chapter.collectAsStateWithLifecycle()
    val translations by vm.translations.collectAsStateWithLifecycle()
    val translationStatus by vm.translationStatus.collectAsStateWithLifecycle()
    val displayMode by vm.displayMode.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val highlights by vm.highlights.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val activeWorkspaceId by vm.activeWorkspaceId.collectAsStateWithLifecycle()
    val splitView by vm.splitView.collectAsStateWithLifecycle()
    val secondaryBook by vm.secondaryBook.collectAsStateWithLifecycle()
    val secondaryChapter by vm.secondaryChapter.collectAsStateWithLifecycle()
    val secondaryTranslations by vm.secondaryTranslations.collectAsStateWithLifecycle()
    val fontScale by vm.fontScale.collectAsStateWithLifecycle()
    val planProgress by vm.planProgress.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedVerse by remember { mutableStateOf<VerseRef?>(null) }
    var noteEditorRef by remember { mutableStateOf<VerseRef?>(null) }
    var labelEditorRef by remember { mutableStateOf<VerseRef?>(null) }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader()
                DrawerItem("Lecture", Icons.Default.MenuBook, screen == Screen.READER) { vm.navigate(Screen.READER); scope.launch { drawerState.close() } }
                DrawerItem("Bibliothèque", Icons.Default.LibraryBooks, screen == Screen.LIBRARY) { vm.navigate(Screen.LIBRARY); scope.launch { drawerState.close() } }
                DrawerItem("Recherche", Icons.Default.Search, screen == Screen.SEARCH) { vm.navigate(Screen.SEARCH); scope.launch { drawerState.close() } }
                DrawerItem("Signets & surlignages", Icons.Default.Bookmark, screen == Screen.BOOKMARKS) { vm.navigate(Screen.BOOKMARKS); scope.launch { drawerState.close() } }
                DrawerItem("Notes", Icons.Default.EditNote, screen == Screen.NOTES) { vm.navigate(Screen.NOTES); scope.launch { drawerState.close() } }
                DrawerItem("Historique", Icons.Default.History, screen == Screen.HISTORY) { vm.navigate(Screen.HISTORY); scope.launch { drawerState.close() } }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                DrawerItem("Espaces de travail", Icons.Default.Dashboard, screen == Screen.WORKSPACES) { vm.navigate(Screen.WORKSPACES); scope.launch { drawerState.close() } }
                DrawerItem("Plans de lecture", Icons.Default.AutoStories, screen == Screen.PLANS) { vm.navigate(Screen.PLANS); scope.launch { drawerState.close() } }
                DrawerItem("Réglages", Icons.Default.Settings, screen == Screen.SETTINGS) { vm.navigate(Screen.SETTINGS); scope.launch { drawerState.close() } }
                Spacer(Modifier.weight(1f))
                Text(
                    "Quanda Bible Amplifiée\nInterface d'étude inspirée des principes d'AndBible",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (screen == Screen.READER && selectedBook != null) "${selectedBook!!.nameFr} $chapter" else screenTitle(screen),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )
                            if (screen == Screen.READER) {
                                val active = workspaces.firstOrNull { it.id == activeWorkspaceId }
                                Text(active?.name ?: "Espace de lecture", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    actions = {
                        if (screen == Screen.READER) {
                            IconButton(onClick = { vm.navigate(Screen.SEARCH) }) { Icon(Icons.Default.Search, "Recherche") }
                            IconButton(onClick = vm::toggleSplitView) {
                                Icon(if (splitView) Icons.Default.ViewAgenda else Icons.Default.ViewColumn, "Fenêtres")
                            }
                            IconButton(onClick = vm::speakCurrentChapter) { Icon(Icons.Default.VolumeUp, "Écouter") }
                            IconButton(onClick = vm::stopSpeech) { Icon(Icons.Default.Stop, "Arrêter") }
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                if (loading) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Chargement de la Bible…")
                    }
                } else {
                    when (screen) {
                        Screen.READER -> ReaderScreen(
                            vm = vm,
                            book = selectedBook,
                            chapterNumber = chapter,
                            translations = translations,
                            status = translationStatus,
                            displayMode = displayMode,
                            favorites = favorites,
                            notes = notes,
                            highlights = highlights,
                            labels = labels,
                            fontScale = fontScale,
                            splitView = splitView,
                            secondaryBook = secondaryBook,
                            secondaryChapter = secondaryChapter,
                            secondaryTranslations = secondaryTranslations,
                            onVerse = { selectedVerse = it }
                        )
                        Screen.LIBRARY -> LibraryScreen(books, vm::openBook)
                        Screen.SEARCH -> SearchScreen(vm)
                        Screen.BOOKMARKS -> BookmarkScreen(vm, favorites, highlights, labels)
                        Screen.NOTES -> NotesScreen(vm, notes)
                        Screen.HISTORY -> HistoryScreen(vm, history)
                        Screen.WORKSPACES -> WorkspaceScreen(vm, workspaces, activeWorkspaceId)
                        Screen.PLANS -> ReadingPlansScreen(vm, vm.readingPlans, planProgress)
                        Screen.SETTINGS -> SettingsScreen(vm)
                    }
                }
            }
        }
    }

    selectedVerse?.let { ref ->
        VerseActionSheet(
            vm = vm,
            ref = ref,
            isFavorite = ref.key in favorites,
            currentHighlight = highlights[ref.key] ?: HighlightColor.NONE,
            currentLabels = labels[ref.key].orEmpty(),
            onDismiss = { selectedVerse = null },
            onEditNote = { noteEditorRef = ref; selectedVerse = null },
            onEditLabels = { labelEditorRef = ref; selectedVerse = null }
        )
    }

    noteEditorRef?.let { ref ->
        TextEditorDialog(
            title = "Note — ${vm.referenceLabel(ref)}",
            initial = notes[ref.key].orEmpty(),
            placeholder = "Écrivez votre note d'étude…",
            onDismiss = { noteEditorRef = null },
            onSave = { vm.setNote(ref, it); noteEditorRef = null }
        )
    }

    labelEditorRef?.let { ref ->
        TextEditorDialog(
            title = "Étiquettes — ${vm.referenceLabel(ref)}",
            initial = labels[ref.key].orEmpty().joinToString(", "),
            placeholder = "Promesse, Prière, Foi…",
            onDismiss = { labelEditorRef = null },
            onSave = { raw ->
                vm.setLabels(ref, raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet())
                labelEditorRef = null
            }
        )
    }
}

@Composable
private fun DrawerHeader() {
    Row(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.logo_quanda_bible),
            contentDescription = "Quanda Bible Amplifiée",
            modifier = Modifier.size(58.dp),
            contentScale = ContentScale.Fit
        )
        Column {
            Text("Quanda Bible", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("BIBLE AMPLIFIÉE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

private fun screenTitle(screen: Screen): String = when (screen) {
    Screen.READER -> "Lecture"
    Screen.LIBRARY -> "Bibliothèque"
    Screen.SEARCH -> "Recherche"
    Screen.BOOKMARKS -> "Signets & surlignages"
    Screen.NOTES -> "Notes"
    Screen.HISTORY -> "Historique"
    Screen.WORKSPACES -> "Espaces de travail"
    Screen.PLANS -> "Plans de lecture"
    Screen.SETTINGS -> "Réglages"
}

@Composable
private fun ReaderScreen(
    vm: BibleViewModel,
    book: BibleBook?,
    chapterNumber: Int,
    translations: Map<Int, String>,
    status: String,
    displayMode: DisplayMode,
    favorites: Set<String>,
    notes: Map<String, String>,
    highlights: Map<String, HighlightColor>,
    labels: Map<String, Set<String>>,
    fontScale: Float,
    splitView: Boolean,
    secondaryBook: BibleBook?,
    secondaryChapter: Int,
    secondaryTranslations: Map<Int, String>,
    onVerse: (VerseRef) -> Unit
) {
    if (book == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { vm.navigate(Screen.LIBRARY) }) { Text("Choisir un livre") }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        ReaderToolbar(vm, book, chapterNumber, status, displayMode)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (splitView && secondaryBook != null) {
                if (maxWidth >= 700.dp) {
                    Row(Modifier.fillMaxSize()) {
                        ReaderPane(book, chapterNumber, translations, displayMode, favorites, notes, highlights, labels, fontScale, onVerse, Modifier.weight(1f))
                        VerticalDivider()
                        ReaderPane(secondaryBook, secondaryChapter, secondaryTranslations, displayMode, favorites, notes, highlights, labels, fontScale, onVerse, Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        ReaderPane(book, chapterNumber, translations, displayMode, favorites, notes, highlights, labels, fontScale, onVerse, Modifier.weight(1f))
                        HorizontalDivider()
                        ReaderPane(secondaryBook, secondaryChapter, secondaryTranslations, displayMode, favorites, notes, highlights, labels, fontScale, onVerse, Modifier.weight(1f))
                    }
                }
            } else {
                ReaderPane(book, chapterNumber, translations, displayMode, favorites, notes, highlights, labels, fontScale, onVerse, Modifier.fillMaxSize())
            }
        }
        Surface(tonalElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = vm::previousChapter) { Icon(Icons.Default.ChevronLeft, "Chapitre précédent") }
                TextButton(onClick = { vm.navigate(Screen.LIBRARY) }) {
                    Icon(Icons.Default.MenuBook, null)
                    Spacer(Modifier.width(6.dp))
                    Text("${book.nameFr} $chapterNumber", fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = vm::nextChapter) { Icon(Icons.Default.ChevronRight, "Chapitre suivant") }
            }
        }
    }
}

@Composable
private fun ReaderToolbar(vm: BibleViewModel, book: BibleBook, chapter: Int, status: String, displayMode: DisplayMode) {
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(book.chapters, key = { it.number }) { ch ->
                FilterChip(
                    selected = ch.number == chapter,
                    onClick = { vm.setChapter(ch.number) },
                    label = { Text(ch.number.toString()) }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(status, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            AssistChip(onClick = {
                vm.setDisplayMode(
                    when (displayMode) {
                        DisplayMode.FR -> DisplayMode.BILINGUAL
                        DisplayMode.BILINGUAL -> DisplayMode.EN
                        DisplayMode.EN -> DisplayMode.FR
                    }
                )
            }, label = {
                Text(when (displayMode) { DisplayMode.FR -> "FR"; DisplayMode.BILINGUAL -> "FR + EN"; DisplayMode.EN -> "EN" })
            })
        }
    }
}

@Composable
private fun ReaderPane(
    book: BibleBook,
    chapterNumber: Int,
    translations: Map<Int, String>,
    displayMode: DisplayMode,
    favorites: Set<String>,
    notes: Map<String, String>,
    highlights: Map<String, HighlightColor>,
    labels: Map<String, Set<String>>,
    fontScale: Float,
    onVerse: (VerseRef) -> Unit,
    modifier: Modifier = Modifier
) {
    val chapter = book.chapters.firstOrNull { it.number == chapterNumber } ?: return
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        item {
            Text("${book.nameFr} $chapterNumber", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
        }
        items(chapter.verses, key = { it.number }) { verse ->
            val ref = VerseRef(book.number, chapterNumber, verse.number)
            val highlight = highlights[ref.key] ?: HighlightColor.NONE
            VerseLine(
                verse = verse,
                french = translations[verse.number],
                ref = ref,
                displayMode = displayMode,
                fontScale = fontScale,
                highlight = highlight,
                bookmarked = ref.key in favorites,
                hasNote = !notes[ref.key].isNullOrBlank(),
                labels = labels[ref.key].orEmpty(),
                onClick = { onVerse(ref) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun VerseLine(
    verse: BibleVerse,
    french: String?,
    ref: VerseRef,
    displayMode: DisplayMode,
    fontScale: Float,
    highlight: HighlightColor,
    bookmarked: Boolean,
    hasNote: Boolean,
    labels: Set<String>,
    onClick: () -> Unit
) {
    val bg = highlightBackground(highlight)
    val textColor = if (highlight == HighlightColor.NONE) MaterialTheme.colorScheme.onSurface else Color(0xFF181818)
    Surface(
        color = if (highlight == HighlightColor.NONE) Color.Transparent else bg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    verse.number.toString(),
                    color = if (highlight == HighlightColor.NONE) MaterialTheme.colorScheme.primary else Color(0xFF6D5200),
                    fontWeight = FontWeight.Black,
                    fontSize = (11f * fontScale).sp,
                    modifier = Modifier.width(26.dp)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (displayMode != DisplayMode.EN) {
                        Text(
                            french ?: verse.textEn,
                            fontSize = (18f * fontScale).sp,
                            lineHeight = (29f * fontScale).sp,
                            color = textColor
                        )
                    }
                    if (displayMode != DisplayMode.FR) {
                        Text(
                            verse.textEn,
                            fontSize = (14.5f * fontScale).sp,
                            lineHeight = (23f * fontScale).sp,
                            color = if (highlight == HighlightColor.NONE) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF383838)
                        )
                    }
                }
                if (bookmarked || hasNote) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bookmarked) Icon(Icons.Default.Bookmark, null, tint = if (highlight == HighlightColor.NONE) MaterialTheme.colorScheme.primary else Color(0xFF5B4700), modifier = Modifier.size(16.dp))
                        if (hasNote) Icon(Icons.Default.EditNote, null, tint = if (highlight == HighlightColor.NONE) MaterialTheme.colorScheme.secondary else Color(0xFF4A4A4A), modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (labels.isNotEmpty()) {
                Text(labels.joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = if (highlight == HighlightColor.NONE) MaterialTheme.colorScheme.tertiary else Color(0xFF4A4A4A), modifier = Modifier.padding(start = 26.dp, top = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerseActionSheet(
    vm: BibleViewModel,
    ref: VerseRef,
    isFavorite: Boolean,
    currentHighlight: HighlightColor,
    currentLabels: Set<String>,
    onDismiss: () -> Unit,
    onEditNote: () -> Unit,
    onEditLabels: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(vm.referenceLabel(ref), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(vm.verseText(ref), maxLines = 6, overflow = TextOverflow.Ellipsis)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ActionChip(if (isFavorite) "Retirer signet" else "Signet", if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder) {
                        vm.toggleFavorite(ref)
                    }
                }
                item { ActionChip("Note", Icons.Default.EditNote, onEditNote) }
                item { ActionChip("Étiquettes", Icons.Default.Label, onEditLabels) }
                item { ActionChip("Écouter", Icons.Default.VolumeUp) { vm.speakRef(ref) } }
                item { ActionChip("Copier", Icons.Default.ContentCopy) { copyVerse(context, vm.referenceLabel(ref), vm.verseText(ref)) } }
                item { ActionChip("Partager", Icons.Default.Share) { shareVerse(context, vm.referenceLabel(ref), vm.verseText(ref)) } }
            }
            Text("Surlignage", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HighlightColor.values().toList()) { color ->
                    FilterChip(
                        selected = currentHighlight == color,
                        onClick = { vm.setHighlight(ref, color) },
                        label = { Text(highlightLabel(color)) },
                        leadingIcon = {
                            Box(Modifier.size(16.dp).background(highlightBackground(color), RoundedCornerShape(50)))
                        }
                    )
                }
            }
            if (currentLabels.isNotEmpty()) {
                Text("Étiquettes : ${currentLabels.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) })
}

@Composable
private fun TextEditorDialog(title: String, initial: String, placeholder: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(placeholder) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun LibraryScreen(books: List<BibleBook>, onBook: (BibleBook, Int) -> Unit) {
    var testament by remember { mutableStateOf<Testament?>(null) }
    var chosenBook by remember { mutableStateOf<BibleBook?>(null) }
    var query by remember { mutableStateOf("") }

    if (chosenBook != null) {
        val book = chosenBook!!
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { chosenBook = null }) { Icon(Icons.Default.ArrowBack, "Retour") }
                Column {
                    Text(book.nameFr, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("${book.chapters.size} chapitre${if (book.chapters.size > 1) "s" else ""}")
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(columns = GridCells.Adaptive(58.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                gridItems(book.chapters, key = { it.number }) { chapter ->
                    FilledTonalButton(onClick = { onBook(book, chapter.number) }, contentPadding = PaddingValues(0.dp)) { Text(chapter.number.toString()) }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Rechercher un livre") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            singleLine = true
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(testament == null, { testament = null }, { Text("Tous") }) }
            item { FilterChip(testament == Testament.OLD, { testament = Testament.OLD }, { Text("Ancien Testament") }) }
            item { FilterChip(testament == Testament.NEW, { testament = Testament.NEW }, { Text("Nouveau Testament") }) }
        }
        val filtered = books.filter { book ->
            (testament == null || book.testament == testament) &&
                (query.isBlank() || book.nameFr.contains(query, true) || book.nameEn.contains(query, true))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.number }) { book ->
                ListItem(
                    headlineContent = { Text(book.nameFr, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("${book.nameEn} • ${book.chapters.size} chapitres") },
                    leadingContent = { Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text(book.number.toString(), Modifier.padding(10.dp), fontWeight = FontWeight.Black) } },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.clickable { chosenBook = book }
                )
            }
        }
    }
}

@Composable
private fun SearchScreen(vm: BibleViewModel) {
    val results by vm.searchResults.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Mot, expression ou verset") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { IconButton(onClick = { vm.search(query) }) { Icon(Icons.Default.ArrowForward, "Rechercher") } }
        )
        Text("Recherche dans le texte anglais et dans les traductions françaises déjà enregistrées.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.ref.key }) { hit -> SearchHitRow(hit, onClick = { vm.openRef(hit.ref) }) }
        }
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, onClick: () -> Unit, supporting: String? = null) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${hit.bookName} ${hit.ref.chapter}:${hit.ref.verse}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            hit.french?.let { Text(it, maxLines = 4, overflow = TextOverflow.Ellipsis) }
            Text(hit.english, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            supporting?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
        }
    }
}

@Composable
private fun BookmarkScreen(vm: BibleViewModel, favorites: Set<String>, highlights: Map<String, HighlightColor>, labels: Map<String, Set<String>>) {
    val hits = remember(favorites, highlights, labels) { vm.bookmarkHits() }
    val allLabels = labels.values.flatten().toSet().sorted()
    var filter by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (allLabels.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item { FilterChip(filter == null, { filter = null }, { Text("Tous") }) }
                items(allLabels) { label -> FilterChip(filter == label, { filter = label }, { Text(label) }) }
            }
        }
        val visible = hits.filter { filter == null || filter in labels[it.ref.key].orEmpty() }
        if (visible.isEmpty()) {
            EmptyState(Icons.Default.BookmarkBorder, "Aucun signet", "Touchez un verset dans la lecture pour ajouter un signet, une couleur ou une étiquette.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.ref.key }) { hit ->
                    SearchHitRow(hit, { vm.openRef(hit.ref) }, labels[hit.ref.key]?.joinToString(" • "))
                }
            }
        }
    }
}

@Composable
private fun NotesScreen(vm: BibleViewModel, notes: Map<String, String>) {
    val hits = remember(notes) { vm.noteHits() }
    if (hits.isEmpty()) {
        EmptyState(Icons.Default.EditNote, "Aucune note", "Ajoutez vos observations, prières et commentaires depuis le menu d'un verset.")
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hits, key = { it.first.ref.key }) { (hit, note) -> SearchHitRow(hit, { vm.openRef(hit.ref) }, "Note : $note") }
        }
    }
}

@Composable
private fun HistoryScreen(vm: BibleViewModel, history: List<HistoryEntry>) {
    val hits = remember(history) { vm.historyHits() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = vm::clearHistory, enabled = history.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("Effacer") }
        }
        if (hits.isEmpty()) {
            EmptyState(Icons.Default.History, "Historique vide", "Les passages ouverts récemment apparaîtront ici.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(hits, key = { it.ref.key }) { hit -> SearchHitRow(hit, { vm.openRef(hit.ref) }) }
            }
        }
    }
}

@Composable
private fun WorkspaceScreen(vm: BibleViewModel, workspaces: List<WorkspaceState>, activeId: Int) {
    var rename by remember { mutableStateOf<WorkspaceState?>(null) }
    Column(Modifier.fillMaxSize()) {
        ElevatedCard(Modifier.fillMaxWidth().padding(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Espaces de travail", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Chaque espace conserve son passage et son mode à deux fenêtres. Utilisez-les pour séparer une étude, un sermon ou une lecture personnelle.")
                Button(onClick = vm::createWorkspace) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Nouvel espace") }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(workspaces, key = { it.id }) { ws ->
                Card(onClick = { vm.activateWorkspace(ws.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ws.id == activeId) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ws.name, fontWeight = FontWeight.Bold)
                            Text(vm.referenceLabel(ws.primaryRef) + if (ws.split) " • 2 fenêtres" else "", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { rename = ws }) { Icon(Icons.Default.Edit, "Renommer") }
                        IconButton(onClick = { vm.copyWorkspace(ws.id) }) { Icon(Icons.Default.ContentCopy, "Copier") }
                        IconButton(onClick = { vm.deleteWorkspace(ws.id) }, enabled = workspaces.size > 1) { Icon(Icons.Default.Delete, "Supprimer") }
                    }
                }
            }
        }
    }
    rename?.let { ws ->
        TextEditorDialog("Renommer l'espace", ws.name, "Nom de l'espace", { rename = null }) { vm.renameWorkspace(ws.id, it); rename = null }
    }
}

@Composable
private fun ReadingPlansScreen(vm: BibleViewModel, plans: List<ReadingPlan>, progress: Map<String, Set<String>>) {
    var opened by remember { mutableStateOf<ReadingPlan?>(null) }
    val plan = opened
    if (plan != null) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { opened = null }) { Icon(Icons.Default.ArrowBack, "Retour") }
                Column {
                    Text(plan.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(plan.description, style = MaterialTheme.typography.bodySmall)
                }
            }
            val done = progress[plan.id].orEmpty()
            LinearProgressIndicator(progress = { done.size.toFloat() / plan.days.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(plan.days, key = { it.number }) { day ->
                    ListItem(
                        headlineContent = { Text("Jour ${day.number} — ${day.title}", fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(vm.referenceLabel(day.ref)) },
                        leadingContent = {
                            Checkbox(checked = day.number.toString() in done, onCheckedChange = { vm.togglePlanDay(plan.id, day.number) })
                        },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                        modifier = Modifier.clickable { vm.openPlanDay(day) }
                    )
                }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Plans de lecture", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Progressez régulièrement et cochez les lectures terminées.")
                Spacer(Modifier.height(10.dp))
            }
            items(plans, key = { it.id }) { p ->
                val done = progress[p.id].orEmpty().size
                ElevatedCard(onClick = { opened = p }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(p.description)
                        LinearProgressIndicator(progress = { done.toFloat() / p.days.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                        Text("$done / ${p.days.size} jours", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: BibleViewModel) {
    val dark by vm.darkMode.collectAsStateWithLifecycle()
    val scale by vm.fontScale.collectAsStateWithLifecycle()
    val mode by vm.displayMode.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("Lecture", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            ListItem(headlineContent = { Text("Mode sombre") }, supportingContent = { Text("Fond sombre pour la lecture nocturne") }, trailingContent = { Switch(dark, vm::setDarkMode) })
        }
        item {
            Text("Taille du texte : ${(scale * 100).toInt()} %", fontWeight = FontWeight.Bold)
            Slider(value = scale, onValueChange = vm::setFontScale, valueRange = 0.80f..1.70f)
        }
        item {
            Text("Langue d'affichage", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(mode == DisplayMode.FR, { vm.setDisplayMode(DisplayMode.FR) }, { Text("Français") }) }
                item { FilterChip(mode == DisplayMode.BILINGUAL, { vm.setDisplayMode(DisplayMode.BILINGUAL) }, { Text("FR + EN") }) }
                item { FilterChip(mode == DisplayMode.EN, { vm.setDisplayMode(DisplayMode.EN) }, { Text("English") }) }
            }
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Fonctions d'étude", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Navigation par bibliothèque, recherche, signets, surlignages, étiquettes, notes, historique, espaces de travail, double fenêtre, plans de lecture et lecture audio TTS.")
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Texte biblique", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Le texte anglais provient du fichier XML fourni. Le français est généré localement avec ML Kit puis mis en cache hors ligne. Vérifiez les droits du texte source avant toute redistribution publique ou commerciale.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("À propos du design", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Cette interface reprend des principes généraux d'applications d'étude comme AndBible (navigation, espaces, fenêtres et outils de verset) avec une implémentation Quanda originale et sans reprendre la marque ni les ressources graphiques d'AndBible.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun highlightBackground(color: HighlightColor): Color = when (color) {
    HighlightColor.NONE -> Color.Transparent
    HighlightColor.YELLOW -> Color(0xFFFFF59D)
    HighlightColor.GREEN -> Color(0xFFC8E6C9)
    HighlightColor.BLUE -> Color(0xFFBBDEFB)
    HighlightColor.PINK -> Color(0xFFF8BBD0)
}

private fun highlightLabel(color: HighlightColor): String = when (color) {
    HighlightColor.NONE -> "Aucun"
    HighlightColor.YELLOW -> "Jaune"
    HighlightColor.GREEN -> "Vert"
    HighlightColor.BLUE -> "Bleu"
    HighlightColor.PINK -> "Rose"
}

private fun copyVerse(context: Context, reference: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(reference, "$reference\n$text"))
}

private fun shareVerse(context: Context, reference: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$reference\n$text\n\n— Quanda Bible Amplifiée")
    }
    context.startActivity(Intent.createChooser(intent, "Partager le verset"))
}
