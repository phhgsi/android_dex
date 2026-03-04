package com.android.desktoplauncher

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

/**
 * Settings activity for Desktop Launcher.
 * Allows users to configure launcher preferences.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    
    // Views
    private lateinit var gridColumnsSpinner: Spinner
    private lateinit var showSystemAppsSwitch: Switch
    private lateinit var sortOrderSpinner: Spinner
    private lateinit var darkThemeSwitch: Switch
    private lateinit var autoLaunchSwitch: Switch
    private lateinit var windowAnimationSwitch: Switch
    private lateinit var versionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        settingsManager = SettingsManager.getInstance(this)
        
        initializeViews()
        loadSettings()
    }

    private fun initializeViews() {
        val toolbar = findViewById<Toolbar>(R.id.settingsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }
        
        gridColumnsSpinner = findViewById(R.id.gridColumnsSpinner)
        showSystemAppsSwitch = findViewById(R.id.showSystemAppsSwitch)
        sortOrderSpinner = findViewById(R.id.sortOrderSpinner)
        darkThemeSwitch = findViewById(R.id.darkThemeSwitch)
        autoLaunchSwitch = findViewById(R.id.autoLaunchSwitch)
        windowAnimationSwitch = findViewById(R.id.windowAnimationSwitch)
        versionText = findViewById(R.id.versionText)
        
        // Set up grid columns spinner
        val columns = arrayOf("3", "4", "5", "6", "7", "8")
        gridColumnsSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, columns)
        
        // Set up sort order spinner
        val sortOrders = arrayOf(
            getString(R.string.sort_by_name),
            getString(R.string.sort_by_install_date),
            getString(R.string.sort_by_last_update),
            getString(R.string.sort_by_size)
        )
        sortOrderSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sortOrders)
        
        // Set version
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            versionText.text = getString(R.string.version_format, packageInfo.versionName, packageInfo.versionCode)
        } catch (e: Exception) {
            versionText.text = getString(R.string.version_unknown)
        }
    }

    private fun loadSettings() {
        // Grid columns
        val currentColumns = settingsManager.getGridColumns()
        val columnsIndex = (3..8).indexOf(currentColumns)
        gridColumnsSpinner.setSelection(if (columnsIndex >= 0) columnsIndex else 3)
        
        // Show system apps
        showSystemAppsSwitch.isChecked = settingsManager.getShowSystemApps()
        
        // Sort order
        val sortOrder = settingsManager.getSortOrder()
        sortOrderSpinner.setSelection(sortOrder.ordinal)
        
        // Dark theme
        darkThemeSwitch.isChecked = settingsManager.getDarkTheme()
        
        // Auto launch
        autoLaunchSwitch.isChecked = settingsManager.getAutoLaunch()
        
        // Window animation
        windowAnimationSwitch.isChecked = settingsManager.getWindowAnimation()
    }

    fun onGridColumnsChanged(view: View) {
        val columns = gridColumnsSpinner.selectedItem?.toString()?.toIntOrNull() ?: 6
        settingsManager.setGridColumns(columns)
    }

    fun onShowSystemAppsChanged(view: View) {
        settingsManager.setShowSystemApps(showSystemAppsSwitch.isChecked)
    }

    fun onSortOrderChanged(view: View) {
        val sortOrder = SettingsManager.SortOrder.entries[sortOrderSpinner.selectedItemPosition]
        settingsManager.setSortOrder(sortOrder)
    }

    fun onDarkThemeChanged(view: View) {
        settingsManager.setDarkTheme(darkThemeSwitch.isChecked)
    }

    fun onAutoLaunchChanged(view: View) {
        settingsManager.setAutoLaunch(autoLaunchSwitch.isChecked)
    }

    fun onWindowAnimationChanged(view: View) {
        settingsManager.setWindowAnimation(windowAnimationSwitch.isChecked)
    }

    fun onResetToDefaults(view: View) {
        settingsManager.resetToDefaults()
        loadSettings()
    }

    override fun onPause() {
        super.onPause()
        // Save settings when leaving
        saveSettings()
    }

    private fun saveSettings() {
        // Settings are saved automatically via listeners
    }
}
