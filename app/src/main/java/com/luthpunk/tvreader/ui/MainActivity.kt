package com.luthpunk.tvreader.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luthpunk.tvreader.R
import com.luthpunk.tvreader.TvReaderApp
import com.luthpunk.tvreader.data.model.BookRecord
import com.luthpunk.tvreader.ui.adapter.LibraryGridAdapter
import com.luthpunk.tvreader.ui.filepicker.FilePickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var app: TvReaderApp
    private lateinit var libraryAdapter: LibraryGridAdapter

    // Top 2 Buttons
    private lateinit var btnBrowseFiles: Button
    private lateinit var btnLibrary: Button

    // Hero Card (Continue Reading)
    private lateinit var heroCard: FrameLayout
    private lateinit var heroContent: LinearLayout
    private lateinit var heroEmptyLayout: LinearLayout
    private lateinit var heroCover: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroProgressBar: ProgressBar
    private lateinit var heroProgressText: TextView

    // Recent Books Section
    private lateinit var rvRecentBooks: RecyclerView
    private lateinit var tvBookCount: TextView
    private lateinit var recentEmptyLayout: LinearLayout
    private lateinit var btnScanStorage: Button

    private var mostRecentBook: BookRecord? = null

    // System File Picker Launcher
    private val systemPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (ignored: Exception) {}

                val title = resolveUriDisplayName(uri) ?: "PDF Document"
                val book = app.repository.getOrCreateBook(
                    filePath = "",
                    uriString = uri.toString(),
                    title = title
                )
                openBook(book)
            }
        }
    }

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
        btnBrowseFiles = findViewById(R.id.btnBrowseFiles)
        btnLibrary = findViewById(R.id.btnLibrary)

        heroCard = findViewById(R.id.heroCard)
        heroContent = findViewById(R.id.heroContent)
        heroEmptyLayout = findViewById(R.id.heroEmptyLayout)
        heroCover = findViewById(R.id.heroCover)
        heroTitle = findViewById(R.id.heroTitle)
        heroProgressBar = findViewById(R.id.heroProgressBar)
        heroProgressText = findViewById(R.id.heroProgressText)

        rvRecentBooks = findViewById(R.id.rvRecentBooks)
        tvBookCount = findViewById(R.id.tvBookCount)
        recentEmptyLayout = findViewById(R.id.recentEmptyLayout)
        btnScanStorage = findViewById(R.id.btnScanStorage)

        libraryAdapter = LibraryGridAdapter(emptyList()) { book ->
            openBook(book)
        }

        // TV Grid with 3 columns for sharp spacing
        rvRecentBooks.layoutManager = GridLayoutManager(this, 3)
        rvRecentBooks.adapter = libraryAdapter

        // Top Button 1: Browse Files
        btnBrowseFiles.setOnClickListener {
            openCustomFilePicker()
        }
        btnBrowseFiles.setOnFocusChangeListener { _, hasFocus ->
            btnBrowseFiles.animate().scaleX(if (hasFocus) 1.08f else 1.0f).scaleY(if (hasFocus) 1.08f else 1.0f).setDuration(120).start()
        }

        // Top Button 2: Library (Auto-scans device & populates library)
        btnLibrary.setOnClickListener {
            scanAndPopulateLibrary()
        }
        btnLibrary.setOnFocusChangeListener { _, hasFocus ->
            btnLibrary.animate().scaleX(if (hasFocus) 1.08f else 1.0f).scaleY(if (hasFocus) 1.08f else 1.0f).setDuration(120).start()
        }

        heroCard.setOnClickListener {
            mostRecentBook?.let { openBook(it) }
        }

        heroCard.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                heroCard.elevation = 12f
                heroCard.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
            } else {
                heroCard.elevation = 0f
                heroCard.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
        }

        btnScanStorage.setOnClickListener {
            scanAndPopulateLibrary()
        }
    }

    private fun refreshLibrary() {
        val books = app.repository.getRecentBooks()
        libraryAdapter.updateData(books)
        tvBookCount.text = "${books.size} documents"

        mostRecentBook = app.repository.getMostRecentBook()
        val latest = mostRecentBook

        if (latest != null) {
            heroContent.visibility = View.VISIBLE
            heroEmptyLayout.visibility = View.GONE
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
        } else {
            heroContent.visibility = View.GONE
            heroEmptyLayout.visibility = View.VISIBLE
        }

        recentEmptyLayout.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE

        // Set initial focus
        if (latest != null) {
            heroCard.requestFocus()
        } else {
            btnBrowseFiles.requestFocus()
        }
    }

    private fun openCustomFilePicker() {
        val root = Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
        val picker = FilePickerDialog(
            context = this,
            startDirectory = root,
            onPdfFileSelected = { selectedFile ->
                val book = app.repository.getOrCreateBook(
                    filePath = selectedFile.absolutePath,
                    title = selectedFile.nameWithoutExtension
                )
                openBook(book)
            },
            onSystemPickerRequested = {
                launchSystemFilePicker()
            }
        )
        picker.show()
    }

    private fun launchSystemFilePicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "*/*"))
            }
            systemPickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "System file picker not available on this TV.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanAndPopulateLibrary() {
        Toast.makeText(this, "Scanning device for PDF files...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val foundPdfs = withContext(Dispatchers.IO) {
                val results = mutableListOf<File>()
                val roots = listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    Environment.getExternalStorageDirectory(),
                    File("/storage")
                )
                for (r in roots) {
                    if (r != null && r.exists() && r.canRead()) {
                        scanFolder(r, results, depth = 4)
                    }
                }
                results.distinctBy { it.absolutePath }
            }

            for (pdf in foundPdfs) {
                app.repository.getOrCreateBook(
                    filePath = pdf.absolutePath,
                    title = pdf.nameWithoutExtension
                )
            }

            refreshLibrary()
            Toast.makeText(this@MainActivity, "Found ${foundPdfs.size} PDFs on device!", Toast.LENGTH_LONG).show()
        }
    }

    private fun scanFolder(dir: File, list: MutableList<File>, depth: Int) {
        if (depth <= 0 || list.size >= 100) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.isDirectory && !f.name.startsWith(".") && f.name != "Android") {
                scanFolder(f, list, depth - 1)
            } else if (f.isFile && f.name.endsWith(".pdf", ignoreCase = true)) {
                list.add(f)
            }
        }
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

    private fun resolveUriDisplayName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {}
        }
        return name?.substringBeforeLast(".") ?: uri.lastPathSegment?.substringBeforeLast(".")
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
        }

        val readPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        if (readPerm != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                101
            )
        }
    }
}
