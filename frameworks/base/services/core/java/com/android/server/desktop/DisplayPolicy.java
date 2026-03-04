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

package com.android.server.desktop;

import android.app.desktop.DesktopDisplayConfig;
import android.app.desktop.DesktopModeConstants;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.DisplayViewport;
import android.os.SystemProperties;
import android.util.Log;
import android.view.Display;
import android.view.DisplayInfo;

import com.android.server.display.DisplayManagerService;
import com.android.server.display.DisplayManagerService.DisplayManagerLocalService;

import java.util.ArrayList;
import java.util.List;

/**
 * Display detection and validation logic for Desktop Mode.
 *
 * <p>This class handles:
 * <ul>
 *   <li>Detecting valid desktop displays</li>
 *   <li>Validating display properties</li>
 *   <li>Determining display type (HDMI, USB-C, etc.)</li>
 *   <li>Managing preferred display selection</li>
 * </ul>
 *
 * @hide
 */
public class DisplayPolicy {

    private static final String TAG = "DisplayPolicy";

    private final DisplayManagerGlobal mDisplayManagerGlobal;
    private final Object mLock = new Object();

    /** Preferred desktop display ID (-1 for auto-select) */
    private int mPreferredDisplayId = -1;

    /** List of valid desktop display IDs */
    private final List<Integer> mValidDesktopDisplays = new ArrayList<>();

    /** Whether the policy has been initialized */
    private boolean mInitialized = false;

    public DisplayPolicy(DisplayManagerGlobal displayManagerGlobal) {
        mDisplayManagerGlobal = displayManagerGlobal;
    }

    /**
     * Initializes the display policy by scanning for available displays.
     */
    public void initialize() {
        synchronized (mLock) {
            scanAvailableDisplays();
            mInitialized = true;
            Log.i(TAG, "DisplayPolicy initialized with "
                    + mValidDesktopDisplays.size() + " valid desktop displays");
        }
    }

    /**
     * Scans all available displays and updates the list of valid desktop displays.
     */
    private void scanAvailableDisplays() {
        mValidDesktopDisplays.clear();

        Display[] displays = mDisplayManagerGlobal.getDisplays();
        for (Display display : displays) {
            if (isValidDesktopDisplay(display)) {
                mValidDesktopDisplays.add(display.getDisplayId());
                Log.d(TAG, "Found valid desktop display: " + display.getDisplayId()
                        + ", type: " + getDesktopDisplayType(display));
            }
        }
    }

    /**
     * Checks if a display is a valid desktop display.
     *
     * @param display The display to check
     * @return true if the display can be used for desktop mode
     */
    public boolean isValidDesktopDisplay(Display display) {
        if (display == null) {
            return false;
        }

        int displayId = display.getDisplayId();

        // Built-in displays are not valid for desktop mode
        if (display.getType() == Display.TYPE_BUILT_IN) {
            return false;
        }

        // Check if device supports desktop mode
        boolean supported = SystemProperties.getBoolean(
                DesktopModeConstants.SYSPROP_DESKTOP_MODE_SUPPORTED, false);
        if (!supported) {
            // For development/testing, allow all external displays
            Log.d(TAG, "Desktop mode not officially supported, allowing for testing");
        }

        // Check if display properties are valid
        DisplayInfo info = new DisplayInfo();
        if (!mDisplayManagerGlobal.getDisplayInfo(displayId, info)) {
            Log.w(TAG, "Could not get DisplayInfo for display: " + displayId);
            return false;
        }

        return validateDisplayProperties(info.logicalWidth, info.logicalHeight);
    }

    /**
     * Checks if a display ID corresponds to a valid desktop display.
     *
     * @param displayId The display ID to check
     * @return true if the display can be used for desktop mode
     */
    public boolean isValidDesktopDisplay(int displayId) {
        synchronized (mLock) {
            return mValidDesktopDisplays.contains(displayId);
        }
    }

    /**
     * Gets the desktop display type for a display.
     *
     * @param display The display
     * @return One of: DISPLAY_TYPE_BUILT_IN, DISPLAY_TYPE_HDMI,
     *         DISPLAY_TYPE_USB_C, DISPLAY_TYPE_WIRELESS
     */
    public int getDesktopDisplayType(Display display) {
        if (display == null) {
            return -1;
        }

        int type = display.getType();
        switch (type) {
            case Display.TYPE_BUILT_IN:
                return DesktopModeConstants.DISPLAY_TYPE_BUILT_IN;
            case Display.TYPE_HDMI:
                return DesktopModeConstants.DISPLAY_TYPE_HDMI;
            case Display.TYPE_USB:
                return DesktopModeConstants.DISPLAY_TYPE_USB_C;
            case Display.TYPE_WIRELESS:
                return DesktopModeConstants.DISPLAY_TYPE_WIRELESS;
            default:
                return type;
        }
    }

    /**
     * Gets the desktop display type for a display ID.
     *
     * @param displayId The display ID
     * @return The display type, or -1 if not found
     */
    public int getDesktopDisplayType(int displayId) {
        Display display = mDisplayManagerGlobal.getDisplay(displayId);
        return getDesktopDisplayType(display);
    }

    /**
     * Validates display properties for desktop mode.
     *
     * @param width The display width in pixels
     * @param height The display height in pixels
     * @return true if the properties meet minimum requirements
     */
    public boolean validateDisplayProperties(int width, int height) {
        // Check minimum resolution
        if (width < DesktopModeConstants.MIN_DESKTOP_WIDTH
                || height < DesktopModeConstants.MIN_DESKTOP_HEIGHT) {
            Log.w(TAG, "Display resolution too low: " + width + "x" + height
                    + " (minimum: " + DesktopModeConstants.MIN_DESKTOP_WIDTH + "x"
                    + DesktopModeConstants.MIN_DESKTOP_HEIGHT + ")");
            return false;
        }

        // Check maximum resolution
        if (width > DesktopModeConstants.MAX_DESKTOP_WIDTH
                || height > DesktopModeConstants.MAX_DESKTOP_HEIGHT) {
            Log.w(TAG, "Display resolution too high: " + width + "x" + height
                    + " (maximum: " + DesktopModeConstants.MAX_DESKTOP_WIDTH + "x"
                    + DesktopModeConstants.MAX_DESKTOP_HEIGHT + ")");
            return false;
        }

        return true;
    }

    /**
     * Gets the list of available desktop displays.
     *
     * @return List of display IDs that can be used for desktop mode
     */
    public List<Integer> getAvailableDesktopDisplays() {
        synchronized (mLock) {
            return new ArrayList<>(mValidDesktopDisplays);
        }
    }

    /**
     * Sets the preferred display for desktop mode.
     *
     * @param displayId The display ID, or -1 for auto-select
     * @return true if the preferred display was set successfully
     */
    public boolean setPreferredDesktopDisplay(int displayId) {
        synchronized (mLock) {
            if (displayId == -1) {
                // Auto-select - clear preference
                mPreferredDisplayId = -1;
                Log.i(TAG, "Cleared preferred desktop display");
                return true;
            }

            // Validate the display
            if (!mValidDesktopDisplays.contains(displayId)) {
                Log.w(TAG, "Cannot set preferred display: not a valid desktop display: "
                        + displayId);
                return false;
            }

            mPreferredDisplayId = displayId;
            Log.i(TAG, "Set preferred desktop display: " + displayId);
            return true;
        }
    }

    /**
     * Gets the preferred display for desktop mode.
     *
     * @return The preferred display ID, or -1 for auto-select
     */
    public int getPreferredDesktopDisplay() {
        synchronized (mLock) {
            return mPreferredDisplayId;
        }
    }

    /**
     * Gets the best display to use for desktop mode.
     *
     * <p>If a preferred display is set and valid, it will be returned.
     * Otherwise, the first available valid display will be returned.
     *
     * @return The display ID to use, or -1 if no valid display available
     */
    public int getBestDesktopDisplay() {
        synchronized (mLock) {
            // Return preferred if valid
            if (mPreferredDisplayId != -1
                    && mValidDesktopDisplays.contains(mPreferredDisplayId)) {
                return mPreferredDisplayId;
            }

            // Return first available
            if (!mValidDesktopDisplays.isEmpty()) {
                return mValidDesktopDisplays.get(0);
            }

            return DesktopModeConstants.INVALID_DISPLAY_ID;
        }
    }

    /**
     * Handles display added event.
     *
     * @param displayId The newly added display ID
     * @return true if the display is a valid desktop display
     */
    public boolean onDisplayAdded(int displayId) {
        synchronized (mLock) {
            Display display = mDisplayManagerGlobal.getDisplay(displayId);
            if (display != null && isValidDesktopDisplay(display)) {
                if (!mValidDesktopDisplays.contains(displayId)) {
                    mValidDesktopDisplays.add(displayId);
                    Log.i(TAG, "Added valid desktop display: " + displayId);
                }
                return true;
            }
            return false;
        }
    }

    /**
     * Handles display removed event.
     *
     * @param displayId The removed display ID
     */
    public void onDisplayRemoved(int displayId) {
        synchronized (mLock) {
            boolean removed = mValidDesktopDisplays.remove(Integer.valueOf(displayId));
            if (removed) {
                Log.i(TAG, "Removed desktop display: " + displayId);

                // Clear preferred if it was removed
                if (mPreferredDisplayId == displayId) {
                    mPreferredDisplayId = -1;
                    Log.i(TAG, "Cleared preferred display (was removed)");
                }
            }
        }
    }

    /**
     * Gets the DisplayInfo for a display.
     *
     * @param displayId The display ID
     * @return The DisplayInfo, or null if not found
     */
    public DisplayInfo getDisplayInfo(int displayId) {
        DisplayInfo info = new DisplayInfo();
        if (mDisplayManagerGlobal.getDisplayInfo(displayId, info)) {
            return info;
        }
        return null;
    }

    /**
     * Creates a DesktopDisplayConfig from a display.
     *
     * @param displayId The display ID
     * @return The DesktopDisplayConfig, or null if display not found
     */
    public DesktopDisplayConfig createDisplayConfig(int displayId) {
        Display display = mDisplayManagerGlobal.getDisplay(displayId);
        if (display == null) {
            return null;
        }

        DisplayInfo info = getDisplayInfo(displayId);
        if (info == null) {
            return null;
        }

        DesktopDisplayConfig.Builder builder = new DesktopDisplayConfig.Builder(displayId)
                .setWidth(info.logicalWidth)
                .setHeight(info.logicalHeight)
                .setDensityDpi(info.logicalDensityDpi)
                .setRefreshRate(info.refreshRate)
                .setDisplayType(getDesktopDisplayType(display))
                .setIsPrimary(display.isPrimary());

        // Set physical size if available
        if (info.physicalWidth > 0 && info.physicalHeight > 0) {
            builder.setPhysicalWidth(info.physicalWidth);
            builder.setPhysicalHeight(info.physicalHeight);
        }

        return builder.build();
    }

    /**
     * Checks if the policy has been initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return mInitialized;
    }

    /**
     * Forces a rescan of available displays.
     */
    public void rescanDisplays() {
        synchronized (mLock) {
            scanAvailableDisplays();
            Log.i(TAG, "Rescanned displays, found " + mValidDesktopDisplays.size()
                    + " valid desktop displays");
        }
    }
}
