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
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * System tray for Desktop Mode.
 *
 * Displays:
 * - Clock (formatted HH:mm)
 * - Battery/charging icon
 * - Network status (WiFi/cellular)
 * - Notification indicator (dot)
 *
 * Horizontal layout, smaller icons than phone
 */
class DesktopSystemTray @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "DesktopSystemTray"
        private const val ICON_SIZE_DP = 20
        private const val SPACING_DP = 8
    }

    private lateinit var clockText: TextView
    private lateinit var batteryIcon: ImageView
    private lateinit var networkIcon: ImageView
    private lateinit var notificationIndicator: ImageView

    private var batteryLevel: Int = 100
    private var isCharging: Boolean = false
    private var networkType: NetworkType = NetworkType.WIFI
    private var notificationCount: Int = 0

    enum class NetworkType {
        WIFI,
        CELLULAR,
        ETHERNET,
        NONE
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END

        val density = context.resources.displayMetrics.density
        val spacingPx = (SPACING_DP * density).toInt()

        // Set padding
        setPadding(spacingPx, 0, spacingPx, 0)

        initViews(density)
    }

    private fun initViews(density: Float) {
        val iconSizePx = (ICON_SIZE_DP * density).toInt()

        // Create clock
        clockText = TextView(context).apply {
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = spacingPx
            }
        }
        addView(clockText)

        // Create notification indicator
        notificationIndicator = ImageView(context).apply {
            setImageResource(R.drawable.ic_notification_indicator)
            layoutParams = LayoutParams(iconSizePx, iconSizePx).apply {
                marginEnd = spacingPx
            }
        }
        addView(notificationIndicator)

        // Create network icon
        networkIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_network_wifi)
            layoutParams = LayoutParams(iconSizePx, iconSizePx).apply {
                marginEnd = spacingPx
            }
        }
        addView(networkIcon)

        // Create battery icon
        batteryIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_battery_full)
            layoutParams = LayoutParams(iconSizePx, iconSizePx)
        }
        addView(batteryIcon)

        // Set dark background with transparency
        setBackgroundColor(0x001A1A2E.toInt())

        // Initial update
        updateBattery(batteryLevel, isCharging)
        updateNetwork(networkType)
        updateNotificationIndicator(notificationCount)
    }

    /**
     * Update the time display
     */
    fun updateTime(time: String) {
        clockText.text = time
    }

    /**
     * Update battery status
     */
    fun updateBattery(level: Int, charging: Boolean) {
        batteryLevel = level
        isCharging = charging

        val iconRes = when {
            charging -> R.drawable.ic_battery_charging
            level >= 90 -> R.drawable.ic_battery_full
            level >= 60 -> R.drawable.ic_battery_80
            level >= 40 -> R.drawable.ic_battery_60
            level >= 20 -> R.drawable.ic_battery_40
            else -> R.drawable.ic_battery_low
        }

        batteryIcon.setImageResource(iconRes)

        // Could also update content description for accessibility
    }

    /**
     * Update network status
     */
    fun updateNetwork(type: NetworkType) {
        networkType = type

        val iconRes = when (type) {
            NetworkType.WIFI -> R.drawable.ic_network_wifi
            NetworkType.CELLULAR -> R.drawable.ic_network_cellular
            NetworkType.ETHERNET -> R.drawable.ic_network_ethernet
            NetworkType.NONE -> R.drawable.ic_network_none
        }

        networkIcon.setImageResource(iconRes)
    }

    /**
     * Update network with signal strength
     */
    fun updateNetwork(type: NetworkType, signalStrength: Int) {
        updateNetwork(type)
        // Could add signal strength indicator overlay
    }

    /**
     * Update notification indicator
     */
    fun updateNotificationIndicator(count: Int) {
        notificationCount = count

        if (count > 0) {
            notificationIndicator.visibility = VISIBLE
            // Could update the indicator based on count (e.g., different dot sizes)
        } else {
            notificationIndicator.visibility = GONE
        }
    }

    /**
     * Show or hide the entire system tray
     */
    fun setVisible(visible: Boolean) {
        visibility = if (visible) VISIBLE else GONE
    }

    /**
     * Enable/disable click interactions
     */
    fun setClickable(clickable: Boolean) {
        isClickable = clickable
        isFocusable = clickable
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // Ensure consistent height with taskbar
        val desiredHeight = (56 * context.resources.displayMetrics.density).toInt()
        if (measuredHeight != desiredHeight) {
            setMeasuredDimension(measuredWidth, desiredHeight)
        }
    }
}
