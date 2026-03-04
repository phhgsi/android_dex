/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.desktop

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Bottom taskbar view for Desktop Mode.
 *
 * This is the main taskbar displayed at the bottom of the screen when in Desktop Mode.
 * It shows:
 * - Home button (left side) - returns to launcher
 * - Running apps (center) - shows open windows as icons
 * - System tray (right side) - clock, battery, notifications
 *
 * Height: 56dp (similar to Android navigation bar)
 * Visible only on external display
 */
class DesktopTaskbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "DesktopTaskbar"
        const val TASKBAR_HEIGHT_DP = 56
    }

    private lateinit var homeButton: ImageButton
    private lateinit var appsScrollView: HorizontalScrollView
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var systemTray: DesktopSystemTray
    private lateinit var taskbarLayout: LinearLayout

    private var taskbarListener: TaskbarListener? = null
    private lateinit var appsAdapter: TaskbarAppsAdapter

    /**
     * Listener interface for taskbar events
     */
    interface TaskbarListener {
        fun onHomeButtonClicked()
        fun onAppClicked(appInfo: AppInfo)
        fun onAppLongClicked(appInfo: AppInfo)
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.desk_taskbar, this, true)

        initViews()
        setupAppsList()
    }

    private fun initViews() {
        homeButton = findViewById(R.id.desk_home_button)
        appsScrollView = findViewById(R.id.desk_apps_scroll)
        appsRecyclerView = findViewById(R.id.desk_apps_recycler)
        systemTray = findViewById(R.id.desk_system_tray)
        taskbarLayout = findViewById(R.id.desk_taskbar_layout)

        // Setup home button click listener
        homeButton.setOnClickListener {
            taskbarListener?.onHomeButtonClicked()
        }

        // Apply dark background
        taskbarLayout.setBackgroundColor(0xFF1A1A2E.toInt())
    }

    private fun setupAppsList() {
        appsAdapter = TaskbarAppsAdapter(
            onAppClick = { appInfo -> taskbarListener?.onAppClicked(appInfo) },
            onAppLongClick = { appInfo -> taskbarListener?.onAppLongClicked(appInfo) }
        )

        appsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = appsAdapter
            setHasFixedSize(true)
        }
    }

    /**
     * Set the listener for taskbar events
     */
    fun setTaskbarListener(listener: TaskbarListener) {
        this.taskbarListener = listener
    }

    /**
     * Update the list of running apps in the taskbar
     */
    fun updateRunningApps(apps: List<AppInfo>) {
        appsAdapter.submitList(apps)
    }

    /**
     * Set the currently focused/selected app
     */
    fun setSelectedApp(packageName: String?) {
        appsAdapter.setSelectedPackage(packageName)
    }

    /**
     * Show or hide the taskbar
     */
    fun setVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Update the system tray with current status
     */
    fun updateSystemTray(time: String, batteryLevel: Int, isCharging: Boolean, notificationCount: Int) {
        systemTray.updateTime(time)
        systemTray.updateBattery(batteryLevel, isCharging)
        systemTray.updateNotificationIndicator(notificationCount)
    }

    /**
     * Data class representing an app in the taskbar
     */
    data class AppInfo(
        val packageName: String,
        val title: String,
        val icon: Drawable?,
        val taskId: Int,
        val isMinimized: Boolean = false
    )

    /**
     * RecyclerView Adapter for taskbar app items
     */
    class TaskbarAppsAdapter(
        private val onAppClick: (AppInfo) -> Unit,
        private val onAppLongClick: (AppInfo) -> Unit
    ) : RecyclerView.Adapter<TaskbarAppsAdapter.AppViewHolder>() {

        private var apps: List<AppInfo> = emptyList()
        private var selectedPackage: String? = null

        fun submitList(newApps: List<AppInfo>) {
            apps = newApps
            notifyDataSetChanged()
        }

        fun setSelectedPackage(packageName: String?) {
            val oldSelected = selectedPackage
            selectedPackage = packageName

            // Notify changes for old and new selected items
            apps.forEachIndexed { index, app ->
                if (app.packageName == oldSelected || app.packageName == packageName) {
                    notifyItemChanged(index)
                }
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.desk_taskbar_app_item, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val app = apps[position]
            holder.bind(app, app.packageName == selectedPackage)
        }

        override fun getItemCount(): Int = apps.size

        inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconView: android.widget.ImageView = itemView.findViewById(R.id.app_icon)
            private val runningIndicator: View = itemView.findViewById(R.id.running_indicator)

            fun bind(app: AppInfo, isSelected: Boolean) {
                app.icon?.let { iconView.setImageDrawable(it) }
                runningIndicator.visibility = if (app.isMinimized) View.GONE else View.VISIBLE

                // Highlight selected app
                itemView.isSelected = isSelected
                itemView.alpha = if (app.isMinimized) 0.6f else 1.0f

                itemView.setOnClickListener { onAppClick(app) }
                itemView.setOnLongClickListener {
                    onAppLongClick(app)
                    true
                }
            }
        }
    }
}
