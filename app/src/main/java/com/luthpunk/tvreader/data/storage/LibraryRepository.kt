package com.luthpunk.tvreader.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.luthpunk.tvreader.data.model.BookRecord
import com.luthpunk.tvreader.data.model.BookmarkRecord
import java.io.File
import java.security.MessageDigest

class LibraryRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tv_reader_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val booksMap = mutableMapOf<String, BookRecord>()

    init {
        loadFromPrefs()
    }

    private fun generateId(pathOrUri: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(pathOrUri.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    private fun loadFromPrefs() {
        val json = prefs.getString("books_json", null) ?: return
        try {
            val type = object : TypeToken<Map<String, BookRecord>>() {}.type
            val loaded: Map<String, BookRecord> = gson.fromJson(json, type)
            booksMap.clear()
            booksMap.putAll(loaded)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun saveToPrefs() {
        val json = gson.toJson(booksMap)
        prefs.edit().putString("books_json", json).apply()
    }

    @Synchronized
    fun getOrCreateBook(filePath: String, uriString: String? = null, title: String): BookRecord {
        val id = generateId(filePath.ifEmpty { uriString ?: "unknown" })
        return booksMap.getOrPut(id) {
            BookRecord(
                id = id,
                filePath = filePath,
                uriString = uriString,
                title = title
            )
        }.also {
            it.lastOpenedTimestamp = System.currentTimeMillis()
            saveToPrefs()
        }
    }

    @Synchronized
    fun updateBook(book: BookRecord) {
        book.lastOpenedTimestamp = System.currentTimeMillis()
        booksMap[book.id] = book
        saveToPrefs()
    }

    @Synchronized
    fun getRecentBooks(): List<BookRecord> {
        return booksMap.values.sortedByDescending { it.lastOpenedTimestamp }
    }

    @Synchronized
    fun getMostRecentBook(): BookRecord? {
        return booksMap.values.maxByOrNull { it.lastOpenedTimestamp }
    }

    @Synchronized
    fun addBookmark(bookId: String, pageIndex: Int, note: String): Boolean {
        val book = booksMap[bookId] ?: return false
        if (book.bookmarks.none { it.pageIndex == pageIndex }) {
            book.bookmarks.add(BookmarkRecord(pageIndex, note))
            saveToPrefs()
            return true
        }
        return false
    }

    @Synchronized
    fun removeBookmark(bookId: String, pageIndex: Int): Boolean {
        val book = booksMap[bookId] ?: return false
        val removed = book.bookmarks.removeAll { it.pageIndex == pageIndex }
        if (removed) {
            saveToPrefs()
        }
        return removed
    }

    @Synchronized
    fun isBookmarked(bookId: String, pageIndex: Int): Boolean {
        val book = booksMap[bookId] ?: return false
        return book.bookmarks.any { it.pageIndex == pageIndex }
    }

    fun getCoverCacheDir(): File {
        val dir = File(context.cacheDir, "covers")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
