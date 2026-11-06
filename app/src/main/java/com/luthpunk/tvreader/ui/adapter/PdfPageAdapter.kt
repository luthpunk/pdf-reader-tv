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
            ivPage.setImageBitmap(null)
            progress.visibility = View.VISIBLE

            renderJob = scope.launch {
                val displayMetrics = itemView.context.resources.displayMetrics
                val targetW = displayMetrics.widthPixels.coerceAtLeast(1920)
                val targetH = displayMetrics.heightPixels.coerceAtLeast(1080)

                val bitmap = pdfEngine.renderPage(pageIndex, targetW, targetH)
                if (isActive) {
                    progress.visibility = View.GONE
                    if (bitmap != null) {
                        ivPage.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }
}
