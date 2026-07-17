package com.example.appblacklist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val onCheckChanged: (AppEntity, Boolean) -> Unit
) : ListAdapter<AppEntity, AppListAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivIcon)
        val name: TextView = view.findViewById(R.id.tvName)
        val pkg: TextView = view.findViewById(R.id.tvPackage)
        val status: TextView = view.findViewById(R.id.tvStatus)
        val checkBox: CheckBox = view.findViewById(R.id.cbBlacklist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.name.text = item.appName
        holder.pkg.text = item.packageName
        holder.status.text = if (item.isInstalled) "已安装" else "已卸载（记录保留）"

        val bitmap = AppScanner.base64ToBitmap(item.iconBase64)
        if (bitmap != null) {
            holder.icon.setImageBitmap(bitmap)
        } else {
            holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        // 先清掉旧的监听，避免 RecyclerView 复用导致回调错乱
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = item.isBlacklisted
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            onCheckChanged(item, isChecked)
        }

        holder.itemView.alpha = if (item.isInstalled) 1.0f else 0.5f
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppEntity>() {
            override fun areItemsTheSame(oldItem: AppEntity, newItem: AppEntity) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: AppEntity, newItem: AppEntity) =
                oldItem == newItem
        }
    }
}
