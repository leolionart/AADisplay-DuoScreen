package com.cva.duoscreen.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cva.duoscreen.R
import com.cva.duoscreen.model.AppInfo

class AppListAdapter(
    private val apps: List<AppInfo>,
    private val onAppSelected: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
        val tvPackage: TextView = view.findViewById(R.id.tvAppPackage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.tvName.text = app.name
        holder.tvPackage.text = app.packageName
        if (app.icon != null) {
            holder.ivIcon.setImageDrawable(app.icon)
        } else {
            holder.ivIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        holder.itemView.setOnClickListener {
            onAppSelected(app)
        }
    }

    override fun getItemCount(): Int = apps.size
}
