package com.luthpunk.tvreader.ui.filepicker

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luthpunk.tvreader.R
import com.luthpunk.tvreader.ui.adapter.FilePickerAdapter
import java.io.File

class FilePickerDialog(
    context: Context,
    private val startDirectory: File,
    private val onPdfFileSelected: (File) -> Unit
) : Dialog(context, R.style.Theme_TvReader) {

    private var currentDir: File = startDirectory
    private lateinit var adapter: FilePickerAdapter
    private lateinit var tvCurrentPath: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var rvFiles: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_file_picker)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        tvCurrentPath = findViewById(R.id.tvCurrentPath)
        tvEmpty = findViewById(R.id.tvEmptyFolder)
        rvFiles = findViewById(R.id.rvFileList)
        val btnClose: Button = findViewById(R.id.btnPickerClose)

        btnClose.setOnClickListener { dismiss() }

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

        navigateTo(currentDir)
    }

    private fun navigateTo(dir: File) {
        if (!dir.exists() || !dir.canRead()) return
        currentDir = dir
        tvCurrentPath.text = dir.absolutePath

        val files = dir.listFiles { file ->
            (file.isDirectory && !file.name.startsWith(".")) ||
                    file.name.endsWith(".pdf", ignoreCase = true)
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

        adapter.updateData(files)
        tvEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        if (files.isNotEmpty()) {
            rvFiles.requestFocus()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Handle Back button to go up directory hierarchy
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val parent = currentDir.parentFile
            if (parent != null && parent.canRead() && currentDir.absolutePath != startDirectory.absolutePath && currentDir.absolutePath != "/storage") {
                navigateTo(parent)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
