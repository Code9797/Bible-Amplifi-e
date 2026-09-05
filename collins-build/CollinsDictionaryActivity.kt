/*
 * Collins English Dictionary launcher for an AndBible-derived build.
 * AndBible-derived code remains GPL-3.0-or-later.
 * Dictionary data is imported by the user and is licensed separately.
 */
package net.bible.android.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class CollinsDictionaryActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private val worker = Executors.newSingleThreadExecutor()
    private var db: SQLiteDatabase? = null
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var articleTitle: TextView
    private lateinit var articleBody: TextView
    private lateinit var status: TextView
    private lateinit var importButton: Button
    private lateinit var suggestions: ArrayAdapter<String>
    private val suggestionWords = ArrayList<String>()
    private var currentWord: String? = null
    private var tts: TextToSpeech? = null

    private val openDatabaseFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importDatabase(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Collins English Dictionary"
        tts = TextToSpeech(this, this)
        buildUi()
        worker.execute { openInstalledDatabaseIfPresent() }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
        }
        val logo = ImageView(this).apply {
            setImageResource(net.bible.android.R.drawable.collins_logo)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Collins English Dictionary"
            setPadding(0, 0, 0, dp(10))
        }
        val heading = TextView(this).apply {
            text = "Collins English"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "English Dictionary • AndBible-based Android edition"
            textSize = 13f
            alpha = .72f
            setPadding(0, 0, 0, dp(8))
        }
        search = EditText(this).apply {
            hint = "Search a word…"
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            isEnabled = false
        }
        importButton = Button(this).apply {
            text = "Import Collins dictionary data"
            setOnClickListener { openDatabaseFile.launch(arrayOf("application/octet-stream", "application/vnd.sqlite3", "*/*")) }
        }
        status = TextView(this).apply {
            text = "Checking dictionary data…"
            textSize = 12f
            alpha = .72f
            setPadding(0, dp(5), 0, dp(7))
        }
        list = ListView(this)
        suggestions = ArrayAdapter(this, android.R.layout.simple_list_item_1, suggestionWords)
        list.adapter = suggestions

        val article = ScrollView(this)
        val articleWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(12), dp(4), dp(18))
        }
        articleTitle = TextView(this).apply {
            textSize = 25f
            setTypeface(typeface, Typeface.BOLD)
            visibility = View.GONE
        }
        articleBody = TextView(this).apply {
            textSize = 18f
            setLineSpacing(0f, 1.25f)
            textIsSelectable = true
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun action(label: String, fn: () -> Unit): Button = Button(this).apply {
            text = label
            setOnClickListener { fn() }
        }
        actions.addView(action("Speak") { speakCurrent() }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(action("Copy") { copyCurrent() }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(action("Share") { shareCurrent() }, LinearLayout.LayoutParams(0, -2, 1f))
        articleWrap.addView(articleTitle)
        articleWrap.addView(actions)
        articleWrap.addView(articleBody)
        article.addView(articleWrap)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list, LinearLayout.LayoutParams(-1, 0, 0.42f))
        content.addView(article, LinearLayout.LayoutParams(-1, 0, 0.58f))

        root.addView(logo, LinearLayout.LayoutParams(-1, dp(190)))
        root.addView(heading)
        root.addView(subtitle)
        root.addView(search, LinearLayout.LayoutParams(-1, -2))
        root.addView(importButton, LinearLayout.LayoutParams(-1, -2))
        root.addView(status)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        search.addTextChangedListener(SimpleTextWatcher { value -> loadSuggestions(value) })
        search.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                openBestMatch(search.text.toString())
                true
            } else false
        }
        list.setOnItemClickListener { _, _, position, _ -> openEntry(suggestionWords[position]) }
    }

    private fun databaseFile(): File = getDatabasePath("collins_english.db")

    private fun openInstalledDatabaseIfPresent() {
        val target = databaseFile()
        if (!target.exists()) {
            runOnUiThread {
                status.text = "Dictionary data not installed. Import Collins-English-Dictionary-Data.db."
                importButton.visibility = View.VISIBLE
            }
            return
        }
        try {
            val opened = SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val count = opened.rawQuery("SELECT COUNT(*) FROM entries", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            db = opened
            runOnUiThread {
                status.text = "$count entries • offline"
                search.isEnabled = true
                importButton.text = "Replace dictionary data"
                loadSuggestions("")
            }
        } catch (e: Exception) {
            runOnUiThread { status.text = "Dictionary database error: ${e.message}" }
        }
    }

    private fun importDatabase(uri: Uri) {
        search.isEnabled = false
        status.text = "Importing dictionary…"
        worker.execute {
            try {
                val target = databaseFile()
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, "collins_import.tmp")
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to read selected file" }
                    temp.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
                }
                val test = SQLiteDatabase.openDatabase(temp.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                val count = test.rawQuery("SELECT COUNT(*) FROM entries", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
                require(count > 0) { "No dictionary entries found" }
                test.close()
                db?.close(); db = null
                if (target.exists()) target.delete()
                require(temp.renameTo(target)) { "Unable to install database" }
                runOnUiThread {
                    status.text = "Dictionary imported successfully"
                    suggestionWords.clear(); suggestions.notifyDataSetChanged()
                }
                openInstalledDatabaseIfPresent()
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Import failed: ${e.message}"
                    search.isEnabled = db != null
                }
            }
        }
    }

    private fun loadSuggestions(query: String) {
        val database = db ?: return
        worker.execute {
            val q = query.trim()
            val words = ArrayList<String>()
            val sql: String
            val args: Array<String>
            if (q.isEmpty()) {
                sql = "SELECT headword FROM entries ORDER BY headword COLLATE NOCASE LIMIT 80"
                args = emptyArray()
            } else {
                sql = "SELECT headword FROM entries WHERE headword >= ? COLLATE NOCASE AND headword < ? COLLATE NOCASE ORDER BY headword COLLATE NOCASE LIMIT 80"
                args = arrayOf(q, q + '\uffff')
            }
            database.rawQuery(sql, args).use { c -> while (c.moveToNext()) words.add(c.getString(0)) }
            runOnUiThread {
                suggestionWords.clear(); suggestionWords.addAll(words); suggestions.notifyDataSetChanged()
                status.text = if (q.isEmpty()) "Offline dictionary ready" else "${words.size} matches"
            }
        }
    }

    private fun openBestMatch(query: String) {
        val database = db ?: return
        worker.execute {
            var word: String? = null
            database.rawQuery("SELECT headword FROM entries WHERE headword = ? COLLATE NOCASE LIMIT 1", arrayOf(query.trim())).use { c ->
                if (c.moveToFirst()) word = c.getString(0)
            }
            if (word == null) {
                database.rawQuery("SELECT headword FROM entries WHERE headword >= ? COLLATE NOCASE ORDER BY headword COLLATE NOCASE LIMIT 1", arrayOf(query.trim())).use { c ->
                    if (c.moveToFirst()) word = c.getString(0)
                }
            }
            word?.let { runOnUiThread { openEntry(it) } }
        }
    }

    private fun openEntry(word: String) {
        val database = db ?: return
        worker.execute {
            var body: String? = null
            database.rawQuery("SELECT plain FROM entries WHERE headword = ? COLLATE NOCASE LIMIT 1", arrayOf(word)).use { c ->
                if (c.moveToFirst()) body = c.getString(0)
            }
            runOnUiThread {
                currentWord = word
                articleTitle.text = word
                articleTitle.visibility = View.VISIBLE
                articleBody.text = body ?: "Entry not found."
            }
        }
    }

    private fun speakCurrent() {
        val word = currentWord ?: return
        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "collins-word")
    }

    private fun copyCurrent() {
        val word = currentWord ?: return
        val text = "$word\n\n${articleBody.text}"
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(word, text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareCurrent() {
        val word = currentWord ?: return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$word\n\n${articleBody.text}")
        }, "Share entry"))
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) tts?.language = Locale.UK
    }

    override fun onDestroy() {
        worker.shutdownNow()
        db?.close(); db = null
        tts?.stop(); tts?.shutdown()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private class SimpleTextWatcher(private val changed: (String) -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed(s?.toString().orEmpty())
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }
}
