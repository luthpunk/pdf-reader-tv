package com.luthpunk.tvreader.ui.filepicker

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luthpunk.tvreader.R
import com.luthpunk.tvreader.ui.adapter.FilePickerAdapter
import kotlinx.coroutines.*
import java.io.File

class FilePickerDialog(
    context: Context,
    private val startDirectory: File,
    private val onPdfFileSelected: (File) -> Unit,
    private val onSystemPickerRequested: (() -> Unit)? = null
) : Dialog(context, R.style.Theme_TvReader) {

    private var currentDir: File = startDirectory
    private lateinit var adapter: FilePickerAdapter
    private lateinit var tvCurrentPath: TextView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var rvFiles: RecyclerView
    private lateinit var pbScanning: ProgressBar

    private val dialogScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_file_picker)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        tvCurrentPath = findViewById(R.id.tvCurrentPath)
        layoutEmpty = findViewById(R.id.layoutEmptyFolder)
        rvFiles = findViewById(R.id.rvFileList)
        pbScanning = findViewById(R.id.pbScanning)

        val btnClose: Button = findViewById(R.id.btnPickerClose)
        val btnShortcutDownload: Button = findViewById(R.id.btnShortcutDownload)
        val btnShortcutDocuments: Button = findViewById(R.id.btnShortcutDocuments)
        val btnShortcutUsb: Button = findViewById(R.id.btnShortcutUsb)
        val btnShortcutScanAll: Button = findViewById(R.id.btnShortcutScanAll)
        val btnShortcutSystemPicker: Button = findViewById(R.id.btnShortcutSystemPicker)

        btnClose.setOnClickListener { dismiss() }

        // Setup shortcuts
        btnShortcutDownload.setOnClickListener {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            navigateTo(downloadDir)
        }

        btnShortcutDocuments.setOnClickListener {
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            navigateTo(docsDir)
        }

        btnShortcutUsb.setOnClickListener {
            navigateToUsbOrStorage()
        }

        btnShortcutScanAll.setOnClickListener {
            scanEntireDeviceForPdfs()
        }

        btnShortcutSystemPicker.setOnClickListener {
            dismiss()
            onSystemPickerRequested?.invoke()
        }

        adapter = FilePickerAdapter(emptyList()) { selectedFile ->
            if (selectedFile.isDirectory) {
                navigateTo(selectedFile)
            } else if (selectedFile.name.endsWith(".pdf", ignoreCase = true)) {
                onPdfFileSelected(selectedFile)
                dismiss()
            }
        }

        rvFiles.layoutManager = LinearLayoutManager(context)
        rvFiles.adapter = adapter

        // If startDirectory has no pdf files directly in it, check Download directory first
        val hasDirectPdf = startDirectory.listFiles()?.any { it.name.endsWith(".pdf", ignoreCase = true) } == true
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!hasDirectPdf && downloadDir.exists() && downloadDir.canRead()) {
            navigateTo(downloadDir)
        } else {
            navigateTo(startDirectory)
        }
    }

    private fun navigateToUsbOrStorage() {
        val storageRoot = File("/storage")
        val usbDirs = storageRoot.listFiles { file ->
            file.isDirectory && file.name != "emulated" && file.name != "self"
        }

        if (!usbDirs.isNullOrEmpty()) {
            navigateTo(usbDirs.first())
        } else {
            navigateTo(storageRoot)
        }
    }

    private fun navigateTo(dir: File) {
        if (!dir.exists() || !dir.canRead()) {
            // If dir is not readable/accessible directly, fallback to storage root or scan
            tvCurrentPath.text = "${dir.absolutePath} (Inaccessible)"
            layoutEmpty.visibility = View.VISIBLE
            adapter.updateData(emptyList())
            return
        }

        currentDir = dir
        tvCurrentPath.text = dir.absolutePath

        val files = dir.listFiles { file ->
            (file.isDirectory && !file.name.startsWith(".")) ||
                    file.name.endsWith(".pdf", ignoreCase = true)
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

        adapter.updateData(files)
        layoutEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        if (files.isNotEmpty()) {
            rvFiles.requestFocus()
        }
    }

    private fun scanEntireDeviceForPdfs() {
        pbScanning.visibility = View.VISIBLE
        layoutEmpty.visibility = View.GONE
        tvCurrentPath.text = "Scanning entire device for PDF files..."

        dialogScope.launch {
            val foundPdfs = withContext(Dispatchers.IO) {
                val results = mutableListOf<File>()
                val searchRoots = listOf(
                    Environment.getExternalStorageDirectory(),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    File("/storage")
                )

                for (root in searchRoots) {
                    if (root != null && root.exists() && root.canRead()) {
                        scanFolderRecursive(root, results, maxDepth = 4)
                    }
                }
                results.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }
            }

            pbScanning.visibility = View.GONE
            tvCurrentPath.text = "Found ${foundPdfs.size} PDF files on device"
            adapter.updateData(foundPdfs)
            layoutEmpty.visibility = if (foundPdfs.isEmpty()) View.VISIBLE else View.GONE
            if (foundPdfs.isNotEmpty()) {
                rvFiles.requestFocus()
            }
        }
    }

    private fun scanFolderRecursive(folder: File, accumulator: MutableList<File>, maxDepth: Int) {
        if (maxDepth <= 0 || accumulator.size >= 150) return
        val children = folder.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory && !child.name.startsWith(".") && child.name != "Android") {
                scanFolderRecursive(child, accumulator, maxDepth - 1)
            } else if (child.isFile && child.name.endsWith(".pdf", ignoreCase = true)) {
                accumulator.add(child)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Back button goes up one directory
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val parent = currentDir.parentFile
            if (parent != null && parent.canRead() &&
                currentDir.absolutePath != startDirectory.absolutePath &&
                currentDir.absolutePath != "/storage" &&
                currentDir.absolutePath != "/storage/emulated") {
                navigateTo(parent)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dialogScope.cancel()
    }
}
