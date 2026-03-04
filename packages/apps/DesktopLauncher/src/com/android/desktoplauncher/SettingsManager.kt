package com.android.desktoplauncher

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manager class for handling launcher settings and preferences.
 */
class SettingsManager(context: Context) {

    companion object {
        private const val TAG = "SettingsManager"
        
        private const val PREFS_NAME = "desktop_launcher_prefs"
        
        // Preference keys
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_AUTO_LAUNCH = "auto_launch"
        const val KEY_WINDOW_ANIMATION = "window_animation"
        const val KEY_SHOW_WIDGETS = "show_widgets"
        const val KEY_LAUNCHER_SCALE = "launcher_scale"
        
        // Default values
        const val DEFAULT_GRID_COLUMNS = 6
        const val DEFAULT_SHOW_SYSTEM_APPS = true
        const val DEFAULT_SORT_ORDER = SortOrder.NAME
        const val DEFAULT_DARK_THEME = true
        const val DEFAULT_AUTO_LAUNCH = true
        const val DEFAULT_WINDOW_ANIMATION = true
        const val DEFAULT_SHOW_WIDGETS = false
        const val DEFAULT_LAUNCHER_SCALE = 1.0f
        
        @Volatile
        private var instance: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val listenerMap = mutableMapOf<String, SharedPreferences.OnSharedPreferenceChangeListener>()

    /**
     * Sort order options for apps
     */
    enum class SortOrder {
        NAME,
        INSTALL_DATE,
        LAST_UPDATE,
        SIZE
    }

    // Grid Columns
    
    fun getGridColumns(): Int {
        return prefs.getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS)
    }
    
    fun setGridColumns(columns: Int) {
        prefs.edit().putInt(KEY_GRID_COLUMNS, columns).apply()
    }

    // Show System Apps
    
    fun getShowSystemApps(): Boolean {
        return prefs.getBoolean(KEY_SHOW_SYSTEM_APPS, DEFAULT_SHOW_SYSTEM_APPS)
    }
    
    fun setShowSystemApps(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SYSTEM_APPS, show).apply()
    }

    // Sort Order
    
    fun getSortOrder(): SortOrder {
        val value = prefs.getString(KEY_SORT_ORDER, DEFAULT_SORT_ORDER.name)
        return try {
            SortOrder.valueOf(value ?: DEFAULT_SORT_ORDER.name)
        } catch (e: Exception) {
            DEFAULT_SORT_ORDER
        }
    }
    
    fun setSortOrder(sortOrder: SortOrder) {
        prefs.edit().putString(KEY_SORT_ORDER, sortOrder.name).apply()
    }

    // Dark Theme
    
    fun getDarkTheme(): Boolean {
        return prefs.getBoolean(KEY_DARK_THEME, DEFAULT_DARK_THEME)
    }
    
    fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    // Auto Launch
    
    fun getAutoLaunch(): Boolean {
        return prefs.getBoolean(KEY_AUTO_LAUNCH, DEFAULT_AUTO_LAUNCH)
    }
    
    fun setAutoLaunch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LAUNCH, enabled).apply()
    }

    // Window Animation
    
    fun getWindowAnimation(): Boolean {
        return prefs.getBoolean(KEY_WINDOW_ANIMATION, DEFAULT_WINDOW_ANIMATION)
    }
    
    fun setWindowAnimation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WINDOW_ANIMATION, enabled).apply()
    }

    // Show Widgets
    
    fun getShowWidgets(): Boolean {
        return prefs.getBoolean(KEY_SHOW_WIDGETS, DEFAULT_SHOW_WIDGETS)
    }
    
    fun setShowWidgets(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WIDGETS, show).apply()
    }

    // Launcher Scale
    
    fun getLauncherScale(): Float {
        return prefs.getFloat(KEY_LAUNCHER_SCALE, DEFAULT_LAUNCHER_SCALE)
    }
    
    fun setLauncherScale(scale: Float) {
        prefs.edit().putFloat(KEY_LAUNCHER_SCALE, scale).apply()
    }

    /**
     * Register a listener for preference changes
     */
    fun registerOnPreferenceChangeListener(key: String, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        listenerMap[key] = listener
    }

    /**
     * Unregister a listener for preference changes
     */
    fun unregisterOnPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        listenerMap.entries.removeIf { it.value == listener }
    }

    /**
     * Clear all preferences
     */
    fun clearAll() {
        prefs.edit().clear().apply()
        Log.i(TAG, "All preferences cleared")
    }

    /**
     * Reset to defaults
     */
    fun resetToDefaults() {
        prefs.edit().apply {
            putInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS)
            putBoolean(KEY_SHOW_SYSTEM_APPS, DEFAULT_SHOW_SYSTEM_APPS)
            putString(KEY_SORT_ORDER, DEFAULT_SORT_ORDER.name)
            putBoolean(KEY_DARK_THEME, DEFAULT_DARK_THEME)
            putBoolean(KEY_AUTO_LAUNCH, DEFAULT_AUTO_LAUNCH)
            putBoolean(KEY_WINDOW_ANIMATION, DEFAULT_WINDOW_ANIMATION)
            putBoolean(KEY_SHOW_WIDGETS, DEFAULT_SHOW_WIDGETS)
            putFloat(KEY_LAUNCHER_SCALE, DEFAULT_LAUNCHER_SCALE)
        }.apply()
        
        Log.i(TAG, "Preferences reset to defaults")
    }
}
