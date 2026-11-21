package com.wbzmm.startv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/** 播放界面左侧频道列表浮层的适配器 */
class OverlayAdapter(
    private val channels: List<Channel>,
    private val onClick: (position: Int) -> Unit
) : RecyclerView.Adapter<OverlayAdapter.VH>() {

    private var currentIndex = 0

    fun setCurrentIndex(index: Int) {
        if (currentIndex != index) {
            val old = currentIndex
            currentIndex = index
            notifyItemChanged(old)
            notifyItemChanged(index)
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_overlay_name)
        val count: TextView = view.findViewById(R.id.tv_overlay_url_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_overlay_channel, parent, false))

    override fun getItemCount(): Int = channels.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = channels[position]
        holder.name.text = if (c.group.isNotEmpty()) "${c.group} · ${c.name}" else c.name
        holder.count.text = when {
            c.urls.isEmpty() -> "无源"
            else -> "${c.urls.size} 线路"
        }
        holder.name.setTextColor(
            if (position == currentIndex)
                ContextCompat.getColor(holder.itemView.context, R.color.accent)
            else
                ContextCompat.getColor(holder.itemView.context, R.color.text_primary)
        )
        holder.itemView.setOnClickListener { onClick(position) }
    }
}
