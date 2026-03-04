package com.android.desktoplauncher

import android.graphics.drawable.Drawable

/**
 * Data class representing an application in the launcher.
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val launchIntent: android.content.Intent,
    val isSystemApp: Boolean = false,
    val isEnabled: Boolean = true,
    val versionName: String? = null,
    val versionCode: Long = 0
) {
    /**
     * Check if this app should be shown in the launcher
     */
    fun isLaunchable(): Boolean = launchIntent.component != null && isEnabled
    
    /**
     * Get unique identifier for this app
     */
    fun getKey(): String = packageName
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as AppInfo
        return packageName == other.packageName
    }
    
    override fun hashCode(): Int = packageName.hashCode()
}

/**
 * Data class representing an app folder in the launcher.
 */
data class FolderInfo(
    val id: String,
    val name: String,
    val apps: MutableList<AppInfo> = mutableListOf(),
    val icon1: Drawable? = null,
    val icon2: Drawable? = null
) {
    /**
     * Get the number of apps in this folder
     */
    fun getAppCount(): Int = apps.size
    
    /**
     * Check if folder is empty
     */
    fun isEmpty(): Boolean = apps.isEmpty()
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as FolderInfo
        return id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}

/**
 * Sealed class representing items in the launcher grid.
 */
sealed class LauncherItem {
    abstract val id: String
    
    data class AppItem(val appInfo: AppInfo) : LauncherItem() {
        override val id: String = "app_${appInfo.packageName}"
    }
    
    data class FolderItem(val folderInfo: FolderInfo) : LauncherItem() {
        override val id: String = "folder_${folderInfo.id}"
    }
}
