package com.luthpunk.tvreader.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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

    // Top Navigation Pills (Clean Pill Outlined)
    private lateinit var btnSearch: Button
    private lateinit var btnBrowseFiles: Button

    // Search Box
    private lateinit var layoutSearchBar: LinearLayout
    private lateinit var etSearchQuery: EditText
    private lateinit var btnClearSearch: Button

    // Swimlanes
    private lateinit var layoutContinueReading: LinearLayout
    private lateinit var rvContinueReading: RecyclerView
    private lateinit var continueAdapter: LibraryGridAdapter

    private lateinit var layoutAllDocuments: LinearLayout
    private lateinit var tvTotalDocumentsCount: TextView
    private lateinit var rvAllDocuments: RecyclerView
    private lateinit var allDocsAdapter: LibraryGridAdapter
    private lateinit var layoutEmptyState: LinearLayout

    private var allDiscoveredBooks: List<BookRecord> = emptyList()

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

        // Background auto-scan on startup to discover all PDFs
        autoScanDevicePdfs()
    }

    override fun onResume() {
        super.onResume()
        refreshLists()
    }

    private fun initViews() {
        btnSearch = findViewById(R.id.btnSearch)
        btnBrowseFiles = findViewById(R.id.btnBrowseFiles)

        layoutSearchBar = findViewById(R.id.layoutSearchBar)
        etSearchQuery = findViewById(R.id.etSearchQuery)
        btnClearSearch = findViewById(R.id.btnClearSearch)

        layoutContinueReading = findViewById(R.id.layoutContinueReading)
        rvContinueReading = findViewById(R.id.rvContinueReading)

        layoutAllDocuments = findViewById(R.id.layoutAllDocuments)
        tvTotalDocumentsCount = findViewById(R.id.tvTotalDocumentsCount)
        rvAllDocuments = findViewById(R.id.rvAllDocuments)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)

        // Setup Horizontal Adapters (Fixed size, no scaling)
        continueAdapter = LibraryGridAdapter(emptyList(), isAllDocumentsRow = false) { book -> openBook(book) }
        rvContinueReading.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvContinueReading.adapter = continueAdapter

        // User request: All Documents row displays uniform square PDF document art so all cards match cohesively
        allDocsAdapter = LibraryGridAdapter(emptyList(), isAllDocumentsRow = true) { book -> openBook(book) }
        rvAllDocuments.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAllDocuments.adapter = allDocsAdapter

        // Top Pill Actions
        btnSearch.setOnClickListener {
            if (layoutSearchBar.visibility == View.VISIBLE) {
                layoutSearchBar.visibility = View.GONE
                etSearchQuery.setText("")
            } else {
                layoutSearchBar.visibility = View.VISIBLE
                etSearchQuery.requestFocus()
            }
        }

        btnClearSearch.setOnClickListener {
            etSearchQuery.setText("")
            layoutSearchBar.visibility = View.GONE
            btnSearch.requestFocus()
        }

        etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnBrowseFiles.setOnClickListener {
            openCustomFilePicker()
        }
    }

    private fun refreshLists() {
        val storedBooks = app.repository.getRecentBooks()

        // 1. Continue Reading Row: only books with active reading progress
        val continueBooks = storedBooks.filter { it.lastPage > 0 }
        if (continueBooks.isNotEmpty()) {
            layoutContinueReading.visibility = View.VISIBLE
            continueAdapter.updateData(continueBooks)
        } else {
            layoutContinueReading.visibility = View.GONE
        }

        // 2. All Documents: Merge stored books with any scanned books
        allDiscoveredBooks = storedBooks
        applyFilter()
    }

    private fun applyFilter() {
        val query = etSearchQuery.text.toString().trim().lowercase()

        val filtered = if (query.isEmpty()) {
            allDiscoveredBooks.sortedByDescending { it.lastOpenedTimestamp }
        } else {
            allDiscoveredBooks.filter { it.title.lowercase().contains(query) }
                .sortedByDescending { it.lastOpenedTimestamp }
        }

        allDocsAdapter.updateData(filtered)
        tvTotalDocumentsCount.text = "(${filtered.size} files)"

        layoutEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun autoScanDevicePdfs() {
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

            refreshLists()
        }
    }

    private fun scanFolder(dir: File, list: MutableList<File>, depth: Int) {
        if (depth <= 0 || list.size >= 120) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.isDirectory && !f.name.startsWith(".") && f.name != "Android") {
                scanFolder(f, list, depth - 1)
            } else if (f.isFile && f.name.endsWith(".pdf", ignoreCase = true)) {
                list.add(f)
            }
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
