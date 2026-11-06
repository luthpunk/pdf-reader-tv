package com.luthpunk.tvreader.ui

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.luthpunk.tvreader.R
import com.luthpunk.tvreader.TvReaderApp
import com.luthpunk.tvreader.data.model.BookRecord
import com.luthpunk.tvreader.data.pdf.PdfEngine
import com.luthpunk.tvreader.ui.adapter.BookmarkAdapter
import com.luthpunk.tvreader.ui.adapter.PageJumpAdapter
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
    private var isContinuousMode = false
    private var isFocusMode = false

    // Views
    private lateinit var readerRoot: FrameLayout
    private lateinit var vpReaderPaged: ViewPager2
    private lateinit var rvReaderContinuous: RecyclerView
    private lateinit var loadingIndicator: ProgressBar

    private lateinit var topToolbar: LinearLayout
    private lateinit var bottomToolbar: LinearLayout
    private lateinit var btnBack: Button
    private lateinit var tvDocTitle: TextView
    private lateinit var btnToggleMode: Button
    private lateinit var btnToggleBookmark: Button
    private lateinit var btnOpenToc: Button

    private lateinit var tvPageIndicator: TextView
    private lateinit var readerProgressBar: ProgressBar

    // Side Drawer (TOC & Bookmarks)
    private lateinit var drawerLayout: LinearLayout
    private lateinit var btnCloseDrawer: Button
    private lateinit var rvBookmarks: RecyclerView
    private lateinit var tvNoBookmarks: TextView
    private lateinit var rvPageList: RecyclerView
    private lateinit var bookmarkAdapter: BookmarkAdapter

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
        rvReaderContinuous = findViewById(R.id.rvReaderContinuous)
        loadingIndicator = findViewById(R.id.docLoadingIndicator)

        topToolbar = findViewById(R.id.topToolbar)
        bottomToolbar = findViewById(R.id.bottomToolbar)
        btnBack = findViewById(R.id.btnBack)
        tvDocTitle = findViewById(R.id.tvDocTitle)
        btnToggleMode = findViewById(R.id.btnToggleMode)
        btnToggleBookmark = findViewById(R.id.btnToggleBookmark)
        btnOpenToc = findViewById(R.id.btnOpenToc)

        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        readerProgressBar = findViewById(R.id.readerProgressBar)

        drawerLayout = findViewById(R.id.drawerLayout)
        btnCloseDrawer = findViewById(R.id.btnCloseDrawer)
        rvBookmarks = findViewById(R.id.rvBookmarks)
        tvNoBookmarks = findViewById(R.id.tvNoBookmarks)
        rvPageList = findViewById(R.id.rvPageList)

        btnBack.setOnClickListener { finish() }

        btnToggleMode.setOnClickListener {
            toggleReadingMode()
        }

        btnToggleBookmark.setOnClickListener {
            toggleCurrentPageBookmark()
        }

        btnOpenToc.setOnClickListener {
            toggleDrawer(true)
        }

        btnCloseDrawer.setOnClickListener {
            toggleDrawer(false)
        }

        vpReaderPaged.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updatePageProgress(position)
            }
        })

        rvReaderContinuous.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                if (firstVisible != RecyclerView.NO_POSITION) {
                    updatePageProgress(firstVisible)
                }
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
                Toast.makeText(this@ReaderActivity, R.string.error_opening_pdf, Toast.LENGTH_LONG).show()
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
            setupDrawerContent()

            // Keep focus strictly on the reading canvas so OK toggles menus cleanly
            readerRoot.requestFocus()
        }
    }

    private fun setupAdapters() {
        val adapter = PdfPageAdapter(pdfEngine, lifecycleScope)
        vpReaderPaged.adapter = adapter
        // Keep 2 offscreen pages rendered so slide transitions are butter-smooth and instant
        vpReaderPaged.offscreenPageLimit = 2

        rvReaderContinuous.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rvReaderContinuous.adapter = adapter
    }

    private fun setupDrawerContent() {
        val book = currentBook ?: return

        bookmarkAdapter = BookmarkAdapter(
            bookmarks = book.bookmarks,
            onBookmarkClicked = { bookmark ->
                jumpToPage(bookmark.pageIndex)
                toggleDrawer(false)
            },
            onBookmarkDeleteClicked = { bookmark ->
                app.repository.removeBookmark(book.id, bookmark.pageIndex)
                updateBookmarkList()
                updateBookmarkButtonState()
            }
        )
        rvBookmarks.layoutManager = LinearLayoutManager(this)
        rvBookmarks.adapter = bookmarkAdapter
        updateBookmarkList()

        val jumpAdapter = PageJumpAdapter(pdfEngine.pageCount) { pageIndex ->
            jumpToPage(pageIndex)
            toggleDrawer(false)
        }
        rvPageList.layoutManager = LinearLayoutManager(this)
        rvPageList.adapter = jumpAdapter
    }

    private fun updateBookmarkList() {
        val book = currentBook ?: return
        bookmarkAdapter.updateData(book.bookmarks)
        tvNoBookmarks.visibility = if (book.bookmarks.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleReadingMode() {
        isContinuousMode = !isContinuousMode
        if (isContinuousMode) {
            vpReaderPaged.visibility = View.GONE
            rvReaderContinuous.visibility = View.VISIBLE
            btnToggleMode.text = getString(R.string.mode_continuous)
            rvReaderContinuous.scrollToPosition(currentPageIndex)
        } else {
            rvReaderContinuous.visibility = View.GONE
            vpReaderPaged.visibility = View.VISIBLE
            btnToggleMode.text = getString(R.string.mode_paged)
            vpReaderPaged.setCurrentItem(currentPageIndex, false)
        }
        // Return focus to toolbar button after click
        btnToggleMode.requestFocus()
    }

    private fun jumpToPage(pageIndex: Int) {
        val clamped = pageIndex.coerceIn(0, pdfEngine.pageCount - 1)
        currentPageIndex = clamped
        if (isContinuousMode) {
            rvReaderContinuous.scrollToPosition(clamped)
        } else {
            vpReaderPaged.setCurrentItem(clamped, true) // Smooth slide transition
        }
        updatePageProgress(clamped)
    }

    private fun updatePageProgress(pageIndex: Int) {
        currentPageIndex = pageIndex
        val total = pdfEngine.pageCount.coerceAtLeast(1)
        val currentDisplay = pageIndex + 1

        tvPageIndicator.text = getString(R.string.page_counter, currentDisplay, total)
        val progress = ((currentDisplay.toFloat() / total.toFloat()) * 100).toInt()
        readerProgressBar.progress = progress

        currentBook?.let {
            it.lastPage = pageIndex
            app.repository.updateBook(it)
        }

        updateBookmarkButtonState()
    }

    private fun updateBookmarkButtonState() {
        val book = currentBook ?: return
        val isMarked = app.repository.isBookmarked(book.id, currentPageIndex)
        btnToggleBookmark.text = if (isMarked) "★ Bookmarked" else "☆ Bookmark"
    }

    private fun toggleCurrentPageBookmark() {
        val book = currentBook ?: return
        val isMarked = app.repository.isBookmarked(book.id, currentPageIndex)
        if (isMarked) {
            app.repository.removeBookmark(book.id, currentPageIndex)
            Toast.makeText(this, "Bookmark removed for page ${currentPageIndex + 1}", Toast.LENGTH_SHORT).show()
        } else {
            app.repository.addBookmark(book.id, currentPageIndex, "Page ${currentPageIndex + 1}")
            Toast.makeText(this, "Page ${currentPageIndex + 1} bookmarked", Toast.LENGTH_SHORT).show()
        }
        updateBookmarkButtonState()
        updateBookmarkList()
        btnToggleBookmark.requestFocus()
    }

    private fun toggleFocusMode() {
        isFocusMode = !isFocusMode
        val visibility = if (isFocusMode) View.GONE else View.VISIBLE
        topToolbar.visibility = visibility
        bottomToolbar.visibility = visibility

        // Keep focus on readerRoot so subsequent OK presses toggle menus without activating buttons
        readerRoot.requestFocus()
    }

    private fun toggleDrawer(open: Boolean) {
        if (open) {
            drawerLayout.visibility = View.VISIBLE
            updateBookmarkList()
            btnCloseDrawer.requestFocus()
        } else {
            drawerLayout.visibility = View.GONE
            readerRoot.requestFocus()
        }
    }

    // Android TV Remote Control Key Interceptor
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // 1. If Drawer is open, Back button closes it
        if (drawerLayout.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                toggleDrawer(false)
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            // 2. Remote OK / Center button:
            // If focus is on the reader canvas: ALWAYS toggles toolbar visibility (show / hide)
            // If focus was explicitly moved UP to toolbar: let the focused button handle the click!
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!topToolbar.hasFocus() && !bottomToolbar.hasFocus()) {
                    toggleFocusMode()
                    return true
                }
                return super.onKeyDown(keyCode, event)
            }

            // 3. Remote UP:
            // If menu is visible and focus is on reading canvas: move highlight UP to toolbar
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (!isFocusMode && !topToolbar.hasFocus() && !bottomToolbar.hasFocus()) {
                    btnToggleMode.requestFocus()
                    return true
                }
            }

            // 4. Remote DOWN:
            // If focus is currently in top toolbar: move highlight DOWN back to reading canvas
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (topToolbar.hasFocus()) {
                    readerRoot.requestFocus()
                    return true
                }
            }

            // 5. Remote Left / Page Up: flips to previous page in Paged Mode
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP -> {
                if (!isContinuousMode && !topToolbar.hasFocus() && !bottomToolbar.hasFocus()) {
                    if (currentPageIndex > 0) {
                        jumpToPage(currentPageIndex - 1)
                        return true
                    }
                }
            }

            // 6. Remote Right / Page Down: flips to next page in Paged Mode
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (!isContinuousMode && !topToolbar.hasFocus() && !bottomToolbar.hasFocus()) {
                    if (currentPageIndex < pdfEngine.pageCount - 1) {
                        jumpToPage(currentPageIndex + 1)
                        return true
                    }
                }
            }

            // 7. Menu / Info button opens TOC Drawer
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                toggleDrawer(true)
                return true
            }

            // 8. Back button: if menu is visible, hide it first; else exit
            KeyEvent.KEYCODE_BACK -> {
                if (!isFocusMode) {
                    toggleFocusMode()
                    return true
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfEngine.close()
    }
}
