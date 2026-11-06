package com.luthpunk.tvreader.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luthpunk.tvreader.R
import com.luthpunk.tvreader.TvReaderApp
import com.luthpunk.tvreader.data.model.BookRecord
import com.luthpunk.tvreader.ui.adapter.LibraryGridAdapter
import com.luthpunk.tvreader.ui.filepicker.FilePickerDialog
import java.io.File

class MainActivity : AppCompatActivity() {

    private enum class SortMode { RECENT, TITLE, PROGRESS }
    private var currentSortMode = SortMode.RECENT

    private lateinit var app: TvReaderApp
    private lateinit var libraryAdapter: LibraryGridAdapter

    private lateinit var heroCard: FrameLayout
    private lateinit var heroContent: LinearLayout
    private lateinit var heroEmptyText: TextView
    private lateinit var heroCover: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroProgressBar: ProgressBar
    private lateinit var heroProgressText: TextView

    private lateinit var rvLibrary: RecyclerView
    private lateinit var btnBrowseStorage: Button
    private lateinit var btnBrowseUsb: Button
    private lateinit var btnSort: Button

    private var mostRecentBook: BookRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        app = application as TvReaderApp

        initViews()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshLibrary()
    }

    private fun initViews() {
        heroCard = findViewById(R.id.heroCard)
        heroContent = findViewById(R.id.heroContent)
        heroEmptyText = findViewById(R.id.heroEmptyText)
        heroCover = findViewById(R.id.heroCover)
        heroTitle = findViewById(R.id.heroTitle)
        heroProgressBar = findViewById(R.id.heroProgressBar)
        heroProgressText = findViewById(R.id.heroProgressText)

        rvLibrary = findViewById(R.id.rvLibrary)
        btnBrowseStorage = findViewById(R.id.btnBrowseStorage)
        btnBrowseUsb = findViewById(R.id.btnBrowseUsb)
        btnSort = findViewById(R.id.btnSort)

        libraryAdapter = LibraryGridAdapter(emptyList()) { book ->
            openBook(book)
        }

        // TV Grid with 4 columns
        rvLibrary.layoutManager = GridLayoutManager(this, 4)
        rvLibrary.adapter = libraryAdapter

        heroCard.setOnClickListener {
            mostRecentBook?.let { openBook(it) }
        }

        btnBrowseStorage.setOnClickListener {
            val root = Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
            showFilePicker(root)
        }

        btnBrowseUsb.setOnClickListener {
            // Android USB mounts usually live in /storage
            val storageDir = File("/storage")
            showFilePicker(storageDir)
        }

        btnSort.setOnClickListener {
            cycleSortMode()
        }
    }

    private fun cycleSortMode() {
        currentSortMode = when (currentSortMode) {
            SortMode.RECENT -> SortMode.TITLE
            SortMode.TITLE -> SortMode.PROGRESS
            SortMode.PROGRESS -> SortMode.RECENT
        }

        btnSort.text = when (currentSortMode) {
            SortMode.RECENT -> getString(R.string.sort_recent)
            SortMode.TITLE -> getString(R.string.sort_title)
            SortMode.PROGRESS -> getString(R.string.sort_progress)
        }
        refreshLibrary()
    }

    private fun refreshLibrary() {
        var books = app.repository.getRecentBooks()

        books = when (currentSortMode) {
            SortMode.RECENT -> books.sortedByDescending { it.lastOpenedTimestamp }
            SortMode.TITLE -> books.sortedBy { it.title.lowercase() }
            SortMode.PROGRESS -> books.sortedByDescending { it.progressPercentage }
        }

        libraryAdapter.updateData(books)

        mostRecentBook = app.repository.getMostRecentBook()
        val latest = mostRecentBook

        if (latest != null) {
            heroContent.visibility = View.VISIBLE
            heroEmptyText.visibility = View.GONE
            heroTitle.text = latest.title
            val progress = latest.progressPercentage
            heroProgressBar.progress = progress
            heroProgressText.text = "Page ${latest.lastPage + 1} of ${latest.pageCount} ($progress%)"

            if (!latest.coverThumbnailPath.isNullOrEmpty()) {
                val file = File(latest.coverThumbnailPath!!)
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    heroCover.setImageBitmap(bmp)
                } else {
                    heroCover.setImageResource(R.drawable.ic_launcher)
                }
            } else {
                heroCover.setImageResource(R.drawable.ic_launcher)
            }
            heroCard.requestFocus()
        } else {
            heroContent.visibility = View.GONE
            heroEmptyText.visibility = View.VISIBLE
            btnBrowseStorage.requestFocus()
        }
    }

    private fun showFilePicker(dir: File) {
        val picker = FilePickerDialog(this, dir) { selectedFile ->
            val book = app.repository.getOrCreateBook(
                filePath = selectedFile.absolutePath,
                title = selectedFile.nameWithoutExtension
            )
            openBook(book)
        }
        picker.show()
    }

    private fun openBook(book: BookRecord) {
        val intent = Intent(this, ReaderActivity::class.java).apply {
            putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id)
            putExtra(ReaderActivity.EXTRA_FILE_PATH, book.filePath)
            if (book.uriString != null) {
                data = Uri.parse(book.uriString)
            }
        }
        startActivity(intent)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (ignored: Exception) {}
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    101
                )
            }
        }
    }
}
