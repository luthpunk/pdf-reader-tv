package com.luthpunk.tvreader.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.luthpunk.tvreader.R
import com.luthpunk.tvreader.data.model.BookmarkRecord

class BookmarkAdapter(
    private var bookmarks: List<BookmarkRecord>,
    private val onBookmarkClicked: (BookmarkRecord) -> Unit,
    private val onBookmarkDeleteClicked: (BookmarkRecord) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder>() {

    fun updateData(newBookmarks: List<BookmarkRecord>) {
        bookmarks = newBookmarks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark, parent, false)
        return BookmarkViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        holder.bind(bookmarks[position])
    }

    override fun getItemCount(): Int = bookmarks.size

    inner class BookmarkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPage: TextView = itemView.findViewById(R.id.tvBookmarkPage)
        private val tvNote: TextView = itemView.findViewById(R.id.tvBookmarkNote)
        private val btnDelete: TextView = itemView.findViewById(R.id.btnDeleteBookmark)

        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onBookmarkClicked(bookmarks[pos])
                }
            }

            btnDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onBookmarkDeleteClicked(bookmarks[pos])
                }
            }
        }

        fun bind(bookmark: BookmarkRecord) {
            tvPage.text = "Page ${bookmark.pageIndex + 1}"
            tvNote.text = bookmark.note
        }
    }
}
