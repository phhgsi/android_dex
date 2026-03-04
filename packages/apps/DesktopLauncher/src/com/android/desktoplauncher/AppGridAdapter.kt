package com.android.desktoplauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RecyclerView adapter for displaying apps in a grid layout.
 * Supports both app items and folder items.
 */
class AppGridAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo, View) -> Unit,
    private val onFolderClick: (FolderInfo) -> Unit,
    private val onFolderLongClick: (FolderInfo, View) -> Unit
) : ListAdapter<LauncherItem, RecyclerView.ViewHolder>(LauncherItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_APP = 0
        private const val VIEW_TYPE_FOLDER = 1
        
        // Grid configuration
        const val GRID_COLUMNS = 6
    }

    private var filteredApps: List<LauncherItem> = emptyList()
    private val adapterScope = CoroutineScope(Dispatchers.Main)

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is LauncherItem.AppItem -> VIEW_TYPE_APP
            is LauncherItem.FolderItem -> VIEW_TYPE_FOLDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        
        return when (viewType) {
            VIEW_TYPE_APP -> {
                val view = inflater.inflate(R.layout.item_app, parent, false)
                AppViewHolder(view)
            }
            VIEW_TYPE_FOLDER -> {
                val view = inflater.inflate(R.layout.item_folder, parent, false)
                FolderViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is LauncherItem.AppItem -> (holder as AppViewHolder).bind(item.appInfo)
            is LauncherItem.FolderItem -> (holder as FolderViewHolder).bind(item.folderInfo)
        }
    }

    /**
     * Submit list of apps to display
     */
    fun submitAppList(apps: List<AppInfo>) {
        adapterScope.launch {
            val items = apps.map { LauncherItem.AppItem(it) }
            withContext(Dispatchers.Main) {
                submitList(items)
            }
        }
    }

    /**
     * Submit combined list of apps and folders
     */
    fun submitItems(items: List<LauncherItem>) {
        adapterScope.launch {
            withContext(Dispatchers.Main) {
                submitList(items)
            }
        }
    }

    /**
     * Filter apps by search query
     */
    fun filterApps(query: String, apps: List<AppInfo>, folders: List<FolderInfo>) {
        adapterScope.launch {
            val lowerQuery = query.lowercase()
            
            val filteredItems = mutableListOf<LauncherItem>()
            
            // Add matching folders
            folders.filter { it.name.lowercase().contains(lowerQuery) }
                .forEach { filteredItems.add(LauncherItem.FolderItem(it)) }
            
            // Add matching apps
            apps.filter { it.appName.lowercase().contains(lowerQuery) }
                .forEach { filteredItems.add(LauncherItem.AppItem(it)) }
            
            withContext(Dispatchers.Main) {
                submitList(filteredItems)
            }
        }
    }

    /**
     * ViewHolder for app items
     */
    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appName: TextView = itemView.findViewById(R.id.appName)

        fun bind(appInfo: AppInfo) {
            appName.text = appInfo.appName
            appIcon.setImageDrawable(appInfo.icon)

            itemView.setOnClickListener {
                onAppClick(appInfo)
            }

            itemView.setOnLongClickListener { view ->
                onAppLongClick(appInfo, view)
                true
            }

            itemView.contentDescription = itemView.context.getString(
                R.string.content_description_app_item,
                appInfo.appName
            )
        }
    }

    /**
     * ViewHolder for folder items
     */
    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val folderIcon1: ImageView = itemView.findViewById(R.id.folderIcon1)
        private val folderIcon2: ImageView = itemView.findViewById(R.id.folderIcon2)
        private val folderName: TextView = itemView.findViewById(R.id.folderName)
        private val folderCount: TextView = itemView.findViewById(R.id.folderCount)

        fun bind(folderInfo: FolderInfo) {
            folderName.text = folderInfo.name
            folderCount.text = itemView.context.getString(
                R.string.content_description_folder_item,
                folderInfo.name,
                folderInfo.getAppCount()
            ).let { "${folderInfo.getAppCount()} apps" }

            // Set folder icons
            if (folderInfo.icon1 != null) {
                folderIcon1.setImageDrawable(folderInfo.icon1)
            }
            if (folderInfo.icon2 != null) {
                folderIcon2.setImageDrawable(folderInfo.icon2)
            }

            itemView.setOnClickListener {
                onFolderClick(folderInfo)
            }

            itemView.setOnLongClickListener { view ->
                onFolderLongClick(folderInfo, view)
                true
            }

            itemView.contentDescription = itemView.context.getString(
                R.string.content_description_folder_item,
                folderInfo.name,
                folderInfo.getAppCount()
            )
        }
    }

    /**
     * DiffUtil callback for launcher items
     */
    class LauncherItemDiffCallback : DiffUtil.ItemCallback<LauncherItem>() {
        override fun areItemsTheSame(oldItem: LauncherItem, newItem: LauncherItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LauncherItem, newItem: LauncherItem): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        /**
         * Create a GridLayoutManager with the correct configuration
         */
        fun createGridLayoutManager(context: android.content.Context): GridLayoutManager {
            val layoutManager = GridLayoutManager(context, GRID_COLUMNS)
            return layoutManager
        }
    }
}
