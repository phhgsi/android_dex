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

package android.app.desktop;

import android.view.Display;

/**
 * Constants and system properties for Desktop Mode.
 *
 * @hide
 */
public final class DesktopModeConstants {

    private DesktopModeConstants() {
        // Prevent instantiation
    }

    // ============================================================
    // System Properties
    // ============================================================

    /** System property to enable/disable desktop mode */
    public static final String SYSPROP_DESKTOP_MODE_ENABLED = "persist.sys.desktop_mode.enabled";

    /** System property for the active desktop display ID */
    public static final String SYSPROP_DESKTOP_MODE_DISPLAY = "persist.sys.desktop_mode.display";

    /** System property for desktop display DPI */
    public static final String SYSPROP_DESKTOP_MODE_DPI = "persist.sys.desktop_mode.dpi";

    /** System property to keep phone screen on when desktop mode is active */
    public static final String SYSPROP_DESKTOP_MODE_KEEP_PHONE_SCREEN =
            "persist.sys.desktop_mode.keep_phone_screen";

    /** Read-only system property for default desktop DPI */
    public static final String SYSPROP_DESKTOP_MODE_DEFAULT_DPI = "ro.sys.desktop_mode.default_dpi";

    /** Debug property for verbose logging */
    public static final String SYSPROP_DESKTOP_MODE_VERBOSE = "debug.sys.desktop_mode.verbose";

    /** Read-only property indicating if device supports desktop mode */
    public static final String SYSPROP_DESKTOP_MODE_SUPPORTED = "ro.sys.desktop_mode.supported";

    // ============================================================
    // Default Values
    // ============================================================

    /** Default desktop display DPI */
    public static final int DEFAULT_DESKTOP_DPI = 160;

    /** Fallback DPI when display-specific DPI cannot be determined */
    public static final int FALLBACK_DESKTOP_DPI = 240;

    /** Default refresh rate for desktop displays */
    public static final float DEFAULT_REFRESH_RATE = 60.0f;

    /** Invalid display ID indicator */
    public static final int INVALID_DISPLAY_ID = Display.INVALID_DISPLAY_ID;

    // ============================================================
    // Desktop Mode States
    // ============================================================

    /** Desktop mode is not active - phone UI is shown */
    public static final int STATE_PHONE_MODE = 0;

    /** Desktop mode is being activated */
    public static final int STATE_DESKTOP_ACTIVATING = 1;

    /** Desktop mode is fully active */
    public static final int STATE_DESKTOP_MODE = 2;

    /** Desktop mode is being deactivated */
    public static final int STATE_DESKTOP_DEACTIVATING = 3;

    /** Desktop mode is suspended (screen off) */
    public static final int STATE_SUSPENDED = 4;

    // ============================================================
    // Display Type Constants
    // ============================================================

    /** Display type for built-in display */
    public static final int DISPLAY_TYPE_BUILT_IN = Display.TYPE_BUILT_IN;

    /** Display type for HDMI external display */
    public static final int DISPLAY_TYPE_HDMI = Display.TYPE_HDMI;

    /** Display type for USB-C external display */
    public static final int DISPLAY_TYPE_USB_C = Display.TYPE_USB;

    /** Display type for wireless display */
    public static final int DISPLAY_TYPE_WIRELESS = Display.TYPE_WIRELESS;

    // ============================================================
    // Windowing Mode Constants
    // ============================================================

    /** Standard fullscreen windowing mode */
    public static final int WINDOWING_MODE_FULLSCREEN = 1;

    /** Freeform windowing mode (desktop) */
    public static final int WINDOWING_MODE_FREEFORM = 5;

    // ============================================================
    // Display Validation Constants
    // ============================================================

    /** Minimum supported width for desktop display */
    public static final int MIN_DESKTOP_WIDTH = 1024;

    /** Minimum supported height for desktop display */
    public static final int MIN_DESKTOP_HEIGHT = 768;

    /** Maximum supported width for desktop display */
    public static final int MAX_DESKTOP_WIDTH = 7680;

    /** Maximum supported height for desktop display */
    public static final int MAX_DESKTOP_HEIGHT = 4320;

    // ============================================================
    // Intent Actions
    // ============================================================

    /** Action broadcast when desktop mode state changes */
    public static final String ACTION_DESKTOP_MODE_STATE_CHANGED =
            "android.intent.action.DESKTOP_MODE_STATE_CHANGED";

    /** Action broadcast when a desktop display is connected */
    public static final String ACTION_DESKTOP_DISPLAY_CONNECTED =
            "android.intent.action.DESKTOP_DISPLAY_CONNECTED";

    /** Action broadcast when a desktop display is disconnected */
    public static final String ACTION_DESKTOP_DISPLAY_DISCONNECTED =
            "android.intent.action.DESKTOP_DISPLAY_DISCONNECTED";

    // ============================================================
    // Intent Extra Keys
    // ============================================================

    /** Extra key for desktop mode enabled state */
    public static final String EXTRA_DESKTOP_MODE_ENABLED = "desktop_mode_enabled";

    /** Extra key for desktop display ID */
    public static final String EXTRA_DESKTOP_DISPLAY_ID = "desktop_display_id";

    /** Extra key for previous desktop display ID */
    public static final String EXTRA_PREVIOUS_DISPLAY_ID = "previous_display_id";

    // ============================================================
    // Permission Constants
    // ============================================================

    /** Permission required to control desktop mode */
    public static final String PERMISSION_CONTROL_DESKTOP_MODE =
            "android.permission.CONTROL_DESKTOP_MODE";

    /** Permission required to query desktop mode state */
    public static final String PERMISSION_QUERY_DESKTOP_MODE =
            "android.permission.QUERY_DESKTOP_MODE";

    // ============================================================
    // Configuration Constants
    // ============================================================

    /** Timeout for desktop mode activation in milliseconds */
    public static final long ACTIVATION_TIMEOUT_MS = 5000;

    /** Timeout for desktop mode deactivation in milliseconds */
    public static final long DEACTIVATION_TIMEOUT_MS = 3000;

    /** Delay before re-enabling desktop mode after display reconnect */
    public static final long RECONNECT_DELAY_MS = 1000;

    // ============================================================
    // Feature Flags
    // ============================================================

    /** Enable multi-window support in desktop mode */
    public static final boolean FEATURE_MULTI_WINDOW = true;

    /** Enable window resize in desktop mode */
    public static final boolean FEATURE_WINDOW_RESIZE = true;

    /** Enable drag and drop in desktop mode */
    public static final boolean FEATURE_DRAG_AND_DROP = true;

    /** Enable clipboard sharing between phone and desktop */
    public static final boolean FEATURE_CLIPBOARD_SHARING = true;

    /** Enable mouse hover events in desktop mode */
    public static final boolean FEATURE_HOVER_EVENTS = true;

    /** Enable keyboard shortcuts in desktop mode */
    public static final boolean FEATURE_KEYBOARD_SHORTCUTS = true;
}
