package com.luthpunk.tvreader.ui

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.luthpunk.tvreader.R
import com.luthpunk.tvreader.TvReaderApp
import com.luthpunk.tvreader.data.model.BookRecord
import com.luthpunk.tvreader.data.pdf.PdfEngine
import com.luthpunk.tvreader.ui.adapter.PdfPageAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    private lateinit var app: TvReaderApp
    private lateinit var pdfEngine: PdfEngine

    private var currentBook: BookRecord? = null
    private var currentPageIndex: Int = 0
    private var isInfoVisible = true

    // Views
    private lateinit var readerRoot: FrameLayout
    private lateinit var vpReaderPaged: ViewPager2
    private lateinit var loadingIndicator: ProgressBar

    // Floating Overlays (Title only, no buttons)
    private lateinit var topToolbar: LinearLayout
    private lateinit var bottomToolbar: LinearLayout
    private lateinit var tvDocTitle: TextView
    private lateinit var tvPageIndicator: TextView
    private lateinit var readerProgressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        app = application as TvReaderApp
        pdfEngine = PdfEngine(this)

        initViews()
        resolveIntentAndOpen()
    }

    private fun initViews() {
        readerRoot = findViewById(R.id.readerRoot)
        vpReaderPaged = findViewById(R.id.vpReaderPaged)
        loadingIndicator = findViewById(R.id.docLoadingIndicator)

        topToolbar = findViewById(R.id.topToolbar)
        bottomToolbar = findViewById(R.id.bottomToolbar)
        tvDocTitle = findViewById(R.id.tvDocTitle)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        readerProgressBar = findViewById(R.id.readerProgressBar)

        vpReaderPaged.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updatePageProgress(position)
            }
        })

        readerRoot.isFocusable = true
        readerRoot.isFocusableInTouchMode = true
        readerRoot.requestFocus()
    }

    private fun resolveIntentAndOpen() {
        val intentData: Uri? = intent.data
        val filePathExtra = intent.getStringExtra(EXTRA_FILE_PATH)
        val bookIdExtra = intent.getStringExtra(EXTRA_BOOK_ID)

        val filePath = filePathExtra ?: (if (intentData?.scheme == "file") intentData.path else "") ?: ""
        val uri = intentData

        var title = "PDF Document"
        if (filePath.isNotEmpty()) {
            title = File(filePath).nameWithoutExtension
        } else if (uri != null) {
            title = resolveUriDisplayName(uri) ?: "PDF Document"
        }

        tvDocTitle.text = title

        currentBook = if (bookIdExtra != null) {
            app.repository.getOrCreateBook(filePath, uri?.toString(), title)
        } else {
            app.repository.getOrCreateBook(filePath, uri?.toString(), title)
        }

        loadPdf(filePath, uri)
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return name?.substringBeforeLast(".") ?: uri.lastPathSegment?.substringBeforeLast(".")
    }

    private fun loadPdf(filePath: String, uri: Uri?) {
        loadingIndicator.visibility = View.VISIBLE

        lifecycleScope.launch {
            val opened = pdfEngine.open(filePath, uri)
            loadingIndicator.visibility = View.GONE

            if (!opened || pdfEngine.pageCount == 0) {
                Toast.makeText(this@ReaderActivity, "Unable to open PDF document", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }

            val total = pdfEngine.pageCount
            currentBook?.let { book ->
                book.pageCount = total
                currentPageIndex = book.lastPage.coerceIn(0, total - 1)
                app.repository.updateBook(book)
            }

            // Generate cover thumbnail in background if missing
            currentBook?.let { book ->
                if (book.coverThumbnailPath.isNullOrEmpty() || !File(book.coverThumbnailPath!!).exists()) {
                    val coverFile = File(app.repository.getCoverCacheDir(), "${book.id}_cover.png")
                    launch(Dispatchers.IO) {
                        if (pdfEngine.generateCoverThumbnail(coverFile)) {
                            book.coverThumbnailPath = coverFile.absolutePath
                            app.repository.updateBook(book)
                        }
                    }
                }
            }

            setupAdapters()
            jumpToPage(currentPageIndex)
            updatePageProgress(currentPageIndex)

            readerRoot.requestFocus()
        }
    }

    private fun setupAdapters() {
        val adapter = PdfPageAdapter(pdfEngine, lifecycleScope)
        vpReaderPaged.adapter = adapter
        // Keep 2 offscreen pages rendered for instant, butter-smooth page flips
        vpReaderPaged.offscreenPageLimit = 2
    }

    private fun jumpToPage(pageIndex: Int) {
        val clamped = pageIndex.coerceIn(0, pdfEngine.pageCount - 1)
        currentPageIndex = clamped
        vpReaderPaged.setCurrentItem(clamped, true)
        updatePageProgress(clamped)
    }

    private fun updatePageProgress(pageIndex: Int) {
        currentPageIndex = pageIndex
        val total = pdfEngine.pageCount.coerceAtLeast(1)
        val currentDisplay = pageIndex + 1

        tvPageIndicator.text = "Page $currentDisplay of $total"
        val progress = ((currentDisplay.toFloat() / total.toFloat()) * 100).toInt()
        readerProgressBar.progress = progress

        currentBook?.let {
            it.lastPage = pageIndex
            app.repository.updateBook(it)
        }
    }

    private fun toggleInfoOverlays() {
        isInfoVisible = !isInfoVisible
        val visibility = if (isInfoVisible) View.VISIBLE else View.GONE
        topToolbar.visibility = visibility
        bottomToolbar.visibility = visibility
    }

    // Android TV Remote Control Key Interceptor
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            // Remote OK / Center button: toggles title and bottom indicator visibility
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                toggleInfoOverlays()
                return true
            }

            // Remote Left / Page Up: flips to previous page
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP -> {
                if (currentPageIndex > 0) {
                    jumpToPage(currentPageIndex - 1)
                    return true
                }
            }

            // Remote Right / Page Down: flips to next page
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (currentPageIndex < pdfEngine.pageCount - 1) {
                    jumpToPage(currentPageIndex + 1)
                    return true
                }
            }

            // Back button exits reader immediately
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfEngine.close()
    }
}
