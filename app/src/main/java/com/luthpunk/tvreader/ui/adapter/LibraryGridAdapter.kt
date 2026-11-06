package com.luthpunk.tvreader.ui.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.luthpunk.tvreader.R
import com.luthpunk.tvreader.data.model.BookRecord
import java.io.File

class LibraryGridAdapter(
    private var books: List<BookRecord>,
    private val onBookClicked: (BookRecord) -> Unit
) : RecyclerView.Adapter<LibraryGridAdapter.BookViewHolder>() {

    fun updateData(newBooks: List<BookRecord>) {
        books = newBooks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_book_card, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(books[position])
    }

    override fun getItemCount(): Int = books.size

    inner class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCover: ImageView = itemView.findViewById(R.id.cardCover)
        private val tvTitle: TextView = itemView.findViewById(R.id.cardTitle)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.cardProgressBar)
        private val tvProgress: TextView = itemView.findViewById(R.id.cardProgressText)

        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onBookClicked(books[pos])
                }
            }

            // TV Focus scale and elevation effect
            itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    itemView.elevation = 12f
                    itemView.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()
                } else {
                    itemView.elevation = 0f
                    itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }
        }

        fun bind(book: BookRecord) {
            tvTitle.text = book.title
            val progress = book.progressPercentage
            progressBar.progress = progress
            tvProgress.text = "Page ${book.lastPage + 1} of ${book.pageCount} ($progress%)"

            if (!book.coverThumbnailPath.isNullOrEmpty()) {
                val file = File(book.coverThumbnailPath!!)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ivCover.setImageBitmap(bitmap)
                } else {
                    ivCover.setImageResource(R.drawable.ic_launcher)
                }
            } else {
                ivCover.setImageResource(R.drawable.ic_launcher)
            }
        }
    }
}
