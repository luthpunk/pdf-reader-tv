package com.luthpunk.tvreader.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.luthpunk.tvreader.R
import com.luthpunk.tvreader.data.pdf.PdfEngine
import kotlinx.coroutines.*

class PdfPageAdapter(
    private val pdfEngine: PdfEngine,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int = pdfEngine.pageCount

    inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPage: ImageView = itemView.findViewById(R.id.ivPdfPage)
        private val progress: ProgressBar = itemView.findViewById(R.id.pageLoading)
        private var renderJob: Job? = null

        fun bind(pageIndex: Int) {
            renderJob?.cancel()

            // 1. Check if bitmap is already in memory cache
            val cached = pdfEngine.getCachedBitmap(pageIndex)
            if (cached != null) {
                progress.visibility = View.GONE
                ivPage.setImageBitmap(cached)
                ivPage.alpha = 1f
            } else {
                // Do NOT clear immediately to avoid blinking; show subtle spinner
                progress.visibility = View.VISIBLE

                renderJob = scope.launch {
                    val displayMetrics = itemView.context.resources.displayMetrics
                    val targetW = displayMetrics.widthPixels.coerceAtLeast(1920)
                    val targetH = displayMetrics.heightPixels.coerceAtLeast(1080)

                    val bitmap = pdfEngine.renderPage(pageIndex, targetW, targetH)
                    if (isActive) {
                        progress.visibility = View.GONE
                        if (bitmap != null) {
                            ivPage.alpha = 0f
                            ivPage.setImageBitmap(bitmap)
                            ivPage.animate().alpha(1f).setDuration(120).start()
                        }
                    }
                }
            }

            // 2. Pre-render adjacent pages in background to guarantee smooth, instant slide transitions
            scope.launch(Dispatchers.IO) {
                val displayMetrics = itemView.context.resources.displayMetrics
                val targetW = displayMetrics.widthPixels.coerceAtLeast(1920)
                val targetH = displayMetrics.heightPixels.coerceAtLeast(1080)

                if (pageIndex + 1 < pdfEngine.pageCount && pdfEngine.getCachedBitmap(pageIndex + 1) == null) {
                    pdfEngine.renderPage(pageIndex + 1, targetW, targetH)
                }
                if (pageIndex - 1 >= 0 && pdfEngine.getCachedBitmap(pageIndex - 1) == null) {
                    pdfEngine.renderPage(pageIndex - 1, targetW, targetH)
                }
            }
        }
    }
}
