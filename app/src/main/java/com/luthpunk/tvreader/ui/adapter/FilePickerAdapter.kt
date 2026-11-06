package com.luthpunk.tvreader.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.luthpunk.tvreader.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilePickerAdapter(
    private var items: List<File>,
    private val onItemClicked: (File) -> Unit
) : RecyclerView.Adapter<FilePickerAdapter.FileViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun updateData(newItems: List<File>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file_entry, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIcon: TextView = itemView.findViewById(R.id.tvFileIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvFileDetails)

        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClicked(items[pos])
                }
            }
        }

        fun bind(file: File) {
            tvName.text = file.name
            if (file.isDirectory) {
                tvIcon.text = "📁"
                val count = file.listFiles()?.size ?: 0
                tvDetails.text = "Folder • $count items"
            } else {
                tvIcon.text = "📄"
                val sizeKb = file.length() / 1024
                val dateStr = dateFormat.format(Date(file.lastModified()))
                tvDetails.text = "${sizeKb} KB • $dateStr"
            }
        }
    }
}
