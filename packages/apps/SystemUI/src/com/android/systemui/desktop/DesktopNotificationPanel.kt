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

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Notification shade adapted for Desktop Mode with mouse interaction.
 *
 * Features:
 * - Click to expand notifications
 * - Swipe to dismiss (mouse-compatible - click X to dismiss)
 * - Quick settings tiles grid (2-3 columns)
 * - Click to open settings
 * - Different layout from phone notification panel
 */
class DesktopNotificationPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "DesktopNotificationPanel"
        private const val QUICK_SETTINGS_COLUMNS = 3
    }

    interface NotificationPanelListener {
        fun onNotificationClicked(notificationKey: String)
        fun onNotificationDismissed(notificationKey: String)
        fun onQuickSettingsTileClicked(tileId: String)
        fun onSettingsClicked()
        fun onPanelCollapsed()
        fun onPanelExpanded()
    }

    private lateinit var headerLayout: LinearLayout
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var clearAllButton: ImageButton
    private lateinit var settingsButton: ImageButton

    private lateinit var notificationsScrollView: ScrollView
    private lateinit var notificationsContainer: LinearLayout
    private lateinit var notificationsRecyclerView: RecyclerView

    private lateinit var quickSettingsContainer: GridLayout
    private lateinit var quickSettingsLabel: TextView

    private var listener: NotificationPanelListener? = null
    private var notificationsAdapter: NotificationAdapter? = null

    private var isExpanded = false

    init {
        LayoutInflater.from(context).inflate(R.layout.desk_notification_panel, this, true)
        initViews()
        setupNotificationsList()
        setupQuickSettings()
    }

    private fun initViews() {
        headerLayout = findViewById(R.id.panel_header)
        clockText = findViewById(R.id.panel_clock)
        dateText = findViewById(R.id.panel_date)
        clearAllButton = findViewById(R.id.btn_clear_all)
        settingsButton = findViewById(R.id.btn_settings)

        notificationsScrollView = findViewById(R.id.notifications_scroll)
        notificationsContainer = findViewById(R.id.notifications_container)
        notificationsRecyclerView = findViewById(R.id.notifications_recycler)

        quickSettingsContainer = findViewById(R.id.quick_settings_grid)
        quickSettingsLabel = findViewById(R.id.quick_settings_label)

        // Setup button listeners
        clearAllButton.setOnClickListener { clearAllNotifications() }
        settingsButton.setOnClickListener { listener?.onSettingsClicked() }

        // Set dark background
        setBackgroundColor(0xFF1A1A2E.toInt())
    }

    private fun setupNotificationsList() {
        notificationsAdapter = NotificationAdapter(
            onNotificationClick = { notification ->
                listener?.onNotificationClicked(notification.key)
            },
            onNotificationDismiss = { notification ->
                listener?.onNotificationDismissed(notification.key)
            }
        )

        notificationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = notificationsAdapter
        }
    }

    private fun setupQuickSettings() {
        // Setup quick settings grid columns
        quickSettingsContainer.columnCount = QUICK_SETTINGS_COLUMNS

        // Add default quick settings tiles
        // In real implementation, these would come from SystemUI's QS tiles
    }

    /**
     * Set the listener for panel events
     */
    fun setNotificationPanelListener(listener: NotificationPanelListener) {
        this.listener = listener
    }

    /**
     * Update the clock display
     */
    fun updateTime(time: String, date: String) {
        clockText.text = time
        dateText.text = date
    }

    /**
     * Update the list of notifications
     */
    fun updateNotifications(notifications: List<DesktopNotification>) {
        notificationsAdapter?.submitList(notifications)

        // Show/hide empty state
        if (notifications.isEmpty()) {
            findViewById<View>(R.id.empty_notifications_text)?.visibility = View.VISIBLE
        } else {
            findViewById<View>(R.id.empty_notifications_text)?.visibility = View.GONE
        }
    }

    /**
     * Add a quick settings tile
     */
    fun addQuickSettingTile(tile: QuickSettingsTile) {
        val tileView = createQuickSettingsTileView(tile)
        quickSettingsContainer.addView(tileView)
    }

    /**
     * Clear all quick settings tiles
     */
    fun clearQuickSettings() {
        quickSettingsContainer.removeAllViews()
    }

    private fun createQuickSettingsTileView(tile: QuickSettingsTile): View {
        val tileLayout = LayoutInflater.from(context).inflate(
            R.layout.quick_settings_tile,
            quickSettingsContainer,
            false
        )

        val iconView = tileLayout.findViewById<android.widget.ImageView>(R.id.tile_icon)
        val labelView = tileLayout.findViewById<TextView>(R.id.tile_label)

        tile.icon?.let { iconView.setImageDrawable(it) }
        labelView.text = tile.label

        tileLayout.setOnClickListener {
            listener?.onQuickSettingsTileClicked(tile.id)
        }

        // Set enabled state visual
        tileLayout.alpha = if (tile.isEnabled) 1.0f else 0.5f

        return tileLayout
    }

    /**
     * Clear all notifications
     */
    private fun clearAllNotifications() {
        // In real implementation, this would clear all notifications via NotificationManager
        notificationsAdapter?.clearAll()
        listener?.onPanelCollapsed()
    }

    /**
     * Expand the panel
     */
    fun expand() {
        isExpanded = true
        listener?.onPanelExpanded()
    }

    /**
     * Collapse the panel
     */
    fun collapse() {
        isExpanded = false
        listener?.onPanelCollapsed()
    }

    /**
     * Toggle panel expansion
     */
    fun toggle() {
        if (isExpanded) collapse() else expand()
    }

    /**
     * Check if panel is expanded
     */
    fun isExpanded(): Boolean = isExpanded

    /**
     * Data class for desktop notifications
     */
    data class DesktopNotification(
        val key: String,
        val appName: String,
        val appIcon: Drawable?,
        val title: String,
        val text: String,
        val subText: String? = null,
        val time: String? = null,
        val isOngoing: Boolean = false,
        val priority: Int = Notification.PRIORITY_DEFAULT
    )

    /**
     * Data class for quick settings tiles
     */
    data class QuickSettingsTile(
        val id: String,
        val label: String,
        val icon: Drawable?,
        val isEnabled: Boolean = false
    )

    /**
     * RecyclerView Adapter for notifications
     */
    class NotificationAdapter(
        private val onNotificationClick: (DesktopNotification) -> Unit,
        private val onNotificationDismiss: (DesktopNotification) -> Unit
    ) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

        private var notifications: List<DesktopNotification> = emptyList()

        fun submitList(newList: List<DesktopNotification>) {
            notifications = newList
            notifyDataSetChanged()
        }

        fun clearAll() {
            notifications = emptyList()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.notification_item, parent, false)
            return NotificationViewHolder(view)
        }

        override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
            holder.bind(notifications[position])
        }

        override fun getItemCount(): Int = notifications.size

        inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val appIcon: android.widget.ImageView = itemView.findViewById(R.id.notification_app_icon)
            private val appName: TextView = itemView.findViewById(R.id.notification_app_name)
            private val title: TextView = itemView.findViewById(R.id.notification_title)
            private val text: TextView = itemView.findViewById(R.id.notification_text)
            private val time: TextView = itemView.findViewById(R.id.notification_time)
            private val dismissButton: ImageButton = itemView.findViewById(R.id.btn_dismiss)

            fun bind(notification: DesktopNotification) {
                notification.appIcon?.let { appIcon.setImageDrawable(it) }
                appName.text = notification.appName
                title.text = notification.title
                text.text = notification.text
                time.text = notification.time ?: ""

                // Show/hide dismiss button based on ongoing status
                dismissButton.visibility = if (notification.isOngoing) View.GONE else View.VISIBLE

                itemView.setOnClickListener { onNotificationClick(notification) }
                dismissButton.setOnClickListener { onNotificationDismiss(notification) }
            }
        }
    }
}
