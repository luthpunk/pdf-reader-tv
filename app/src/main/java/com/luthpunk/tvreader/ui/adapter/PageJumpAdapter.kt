package com.luthpunk.tvreader.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.luthpunk.tvreader.R

class PageJumpAdapter(
    private val totalPages: Int,
    private val onPageSelected: (Int) -> Unit
) : RecyclerView.Adapter<PageJumpAdapter.JumpViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JumpViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_page_jump, parent, false)
        return JumpViewHolder(view)
    }

    override fun onBindViewHolder(holder: JumpViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int = totalPages

    inner class JumpViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNum: TextView = itemView.findViewById(R.id.tvJumpPageNum)
        private val tvLabel: TextView = itemView.findViewById(R.id.tvJumpLabel)

        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onPageSelected(pos)
                }
            }
        }

        fun bind(pageIndex: Int) {
            tvNum.text = "Page ${pageIndex + 1}"
            tvLabel.text = if (pageIndex == 0) "Cover / Title Page" else "Section ${pageIndex + 1}"
        }
    }
}
