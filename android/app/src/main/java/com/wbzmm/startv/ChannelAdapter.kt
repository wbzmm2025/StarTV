package com.wbzmm.startv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * 主界面频道网格适配器:
 * 支持两种条目 —— 分组头(整行) 与 频道卡片, 央视/卫视 分组展示。
 */
class ChannelAdapter(
    private val rows: List<Row>,
    private val onClick: (Channel) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class Header(val title: String, val count: Int) : Row()
        data class Chan(val channel: Channel) : Row()
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_CHANNEL = 1
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Header) TYPE_HEADER else TYPE_CHANNEL

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val v = inflater.inflate(R.layout.item_group_header, parent, false)
            HeaderHolder(v)
        } else {
            val v = inflater.inflate(R.layout.item_channel, parent, false)
            ChannelHolder(v)
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).bind(row)
            is Row.Chan -> (holder as ChannelHolder).bind(row.channel, onClick)
        }
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tv_group)
        fun bind(header: Row.Header) {
            tv.text = "${header.title} (${header.count})"
        }
    }

    class ChannelHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val logo: ImageView = view.findViewById(R.id.iv_logo)
        private val name: TextView = view.findViewById(R.id.tv_name)

        fun bind(channel: Channel, onClick: (Channel) -> Unit) {
            name.text = channel.name
            if (channel.logo.isNotEmpty()) {
                Glide.with(logo).load(channel.logo)
                    .placeholder(android.R.color.transparent)
                    .error(android.R.color.transparent)
                    .into(logo)
                logo.visibility = View.VISIBLE
            } else {
                Glide.with(logo).clear(logo)
                logo.visibility = View.GONE
            }
            // TV 焦点效果: 聚焦放大
            itemView.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.05f else 1f)
                    .scaleY(if (hasFocus) 1.05f else 1f)
                    .setDuration(120L).start()
            }
            itemView.setOnClickListener { onClick(channel) }
        }
    }
}
