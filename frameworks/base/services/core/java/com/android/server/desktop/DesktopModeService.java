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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.desktop.DesktopDisplayConfig;
import android.app.desktop.DesktopModeConstants;
import android.app.desktop.IDesktopModeListener;
import android.app.desktop.IDesktopModeService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.input.InputManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.server.desktop.DesktopModes;
import android.util.Log;
import android.util.Slog;
import android.view.Display;
import android.view.InputDevice;
import android.view.WindowInfo;

import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.display.DisplayManagerService;
import com.android.server.display.DisplayManagerService.DisplayManagerLocalService;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core system service that manages Desktop Mode functionality.
 *
 * <p>This service is responsible for:
 * <ul>
 *   <li>Detecting and managing external displays</li>
 *   <li>Coordinating window management with WindowManagerService</li>
 *   <li>Coordinating task management with ActivityTaskManagerService</li>
 *   <li>Managing input policy for desktop mode</li>
 *   <li>Notifying observers of state changes</li>
 * </ul>
 *
 * @hide
 */
public class DesktopModeService extends SystemService {

    private static final String TAG = "DesktopModeService";
    private static final String TAG_WINDOW = "DesktopMode";

    // Message codes for handler
    private static final int MSG_ENABLE_DESKTOP_MODE = 1;
    private static final int MSG_DISABLE_DESKTOP_MODE = 2;
    private static final int MSG_DISPLAY_CONNECTED = 3;
    private static final int MSG_DISPLAY_DISCONNECTED = 4;
    private static final int MSG_DISPLAY_CONFIG_CHANGED = 5;
    private static final int MSG_SCREEN_ON = 6;
    private static final int MSG_SCREEN_OFF = 7;

    private final Context mContext;
    private final DisplayManagerGlobal mDisplayManagerGlobal;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;
    private final WindowManagerInternal mWindowManagerInternal;
    private final InputManager mInputManager;

    private final DesktopModeStateNotifier mStateNotifier;
    private final DisplayPolicy mDisplayPolicy;
    private final Handler mHandler;

    private final AtomicInteger mCurrentState = new AtomicInteger(
            DesktopModeConstants.STATE_PHONE_MODE);
    private final AtomicInteger mDesktopDisplayId = new AtomicInteger(
            DesktopModeConstants.INVALID_DISPLAY_ID);
    private final AtomicBoolean mTaskbarVisible = new AtomicBoolean(true);

    private int mCurrentUserId = UserHandle.USER_SYSTEM;
    private DisplayListener mDisplayListener;

    // Desktop mode features
    private boolean mEnableHoverEvents = false;
    private boolean mMultiWindowEnabled = true;
    private boolean mKeepPhoneScreenOn = false;

    /**
     * Creates the DesktopModeService.
     *
     * @param context The system context
     */
    public DesktopModeService(Context context) {
        super(context);
        mContext = context;
        mDisplayManagerGlobal = DisplayManagerGlobal.getInstance();
        mStateNotifier = new DesktopModeStateNotifier();
        mDisplayPolicy = new DisplayPolicy(mDisplayManagerGlobal);
        mHandler = new Handler(Looper.getMainLooper(), false, this::handleMessage);

        // Get system services
        mActivityTaskManagerInternal = LocalServices.getService(ActivityTaskManagerInternal.class);
        mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
        mInputManager = LocalServices.getService(InputManager.class);

        // Set up state notifier callback
        mStateNotifier.setInternalCallback(new DesktopModeStateNotifier.InternalCallback() {
            @Override
            public void onDesktopModeEnabled(int displayId) {
                onDesktopModeEnabledInternal(displayId);
            }

            @Override
            public void onDesktopModeDisabled() {
                onDesktopModeDisabledInternal();
            }

            @Override
            public void onStateChanged(int newState, int oldState) {
                handleStateTransition(newState, oldState);
            }
        });

        // Initialize display policy
        mDisplayPolicy.initialize();

        Log.i(TAG, "DesktopModeService created");
    }

    @Override
    public void onStart() {
        Log.i(TAG, "Starting DesktopModeService");

        // Publish service
        publishBinderService(Context.DESKTOP_MODE_SERVICE, new BinderService());

        // Register display listener
        mDisplayListener = new DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                Log.d(TAG, "Display added: " + displayId);
                mHandler.obtainMessage(MSG_DISPLAY_CONNECTED, displayId, 0).sendToTarget();
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                Log.d(TAG, "Display removed: " + displayId);
                mHandler.obtainMessage(MSG_DISPLAY_DISCONNECTED, displayId, 0).sendToTarget();
            }

            @Override
            public void onDisplayChanged(int displayId) {
                Log.d(TAG, "Display changed: " + displayId);
                mHandler.obtainMessage(MSG_DISPLAY_CONFIG_CHANGED, displayId, 0).sendToTarget();
            }
        };

        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener, mHandler,
                DisplayManager.EVENT_FLAG_NEW);

        // Read initial configuration
        readConfiguration();
    }

    @Override
    public void onBootPhase(int phase) {
        Log.d(TAG, "Boot phase: " + phase);

        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            // System services are ready
            Log.i(TAG, "System services ready");
        } else if (phase == PHASE_ACTIVITY_MANAGER_READY) {
            // Activity manager is ready
            Log.i(TAG, "Activity manager ready");
        } else if (phase == PHASE_THIRD_PARTY_APPS_CAN_START) {
            // Third party apps can start
            Log.i(TAG, "Third party apps can start");

            // Check if desktop mode should be auto-enabled
            boolean autoEnable = SystemProperties.getBoolean(
                    DesktopModeConstants.SYSPROP_DESKTOP_MODE_ENABLED, false);
            if (autoEnable) {
                Log.i(TAG, "Auto-enabling desktop mode based on system property");
                enableDesktopModeInternal();
            }
        }
    }

    @Override
    public void onUserStarting(@NonNull TargetUser user) {
        Log.d(TAG, "User starting: " + user.getUserIdentifier());
    }

    @Override
    public void onUserSwitching(@Nullable TargetUser from, @NonNull TargetUser to) {
        Log.i(TAG, "User switching from " + (from != null ? from.getUserIdentifier() : "null")
                + " to " + to.getUserIdentifier());
        mCurrentUserId = to.getUserIdentifier();
    }

    @Override
    public void onUserStopping(@NonNull TargetUser user) {
        Log.d(TAG, "User stopping: " + user.getUserIdentifier());
    }

    @Override
    public void onUserStopped(@NonNull TargetUser user) {
        Log.d(TAG, "User stopped: " + user.getUserIdentifier());
    }

    /**
     * Handles messages from the handler.
     */
    private boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_ENABLE_DESKTOP_MODE:
                handleEnableDesktopMode();
                return true;

            case MSG_DISABLE_DESKTOP_MODE:
                handleDisableDesktopMode();
                return true;

            case MSG_DISPLAY_CONNECTED:
                handleDisplayConnected(msg.arg1);
                return true;

            case MSG_DISPLAY_DISCONNECTED:
                handleDisplayDisconnected(msg.arg1);
                return true;

            case MSG_DISPLAY_CONFIG_CHANGED:
                handleDisplayConfigChanged(msg.arg1);
                return true;

            case MSG_SCREEN_ON:
                handleScreenOn();
                return true;

            case MSG_SCREEN_OFF:
                handleScreenOff();
                return true;

            default:
                return false;
        }
    }

    /**
     * Reads configuration from system properties.
     */
    private void readConfiguration() {
        mKeepPhoneScreenOn = SystemProperties.getBoolean(
                DesktopModeConstants.SYSPROP_DESKTOP_MODE_KEEP_PHONE_SCREEN, false);
        mEnableHoverEvents = DesktopModeConstants.FEATURE_HOVER_EVENTS;
        mMultiWindowEnabled = DesktopModeConstants.FEATURE_MULTI_WINDOW;

        Log.d(TAG, "Configuration: keepPhoneScreenOn=" + mKeepPhoneScreenOn
                + ", hoverEvents=" + mEnableHoverEvents
                + ", multiWindow=" + mMultiWindowEnabled);
    }

    /**
     * Handles enabling desktop mode request.
     */
    private void handleEnableDesktopMode() {
        Log.i(TAG, "Handling enable desktop mode request");

        // Check if already enabled
        if (mCurrentState.get() == DesktopModeConstants.STATE_DESKTOP_MODE) {
            Log.w(TAG, "Desktop mode already enabled");
            return;
        }

        // Check if transitioning
        if (mCurrentState.get() == DesktopModeConstants.STATE_DESKTOP_ACTIVATING) {
            Log.w(TAG, "Desktop mode already activating");
            return;
        }

        // Find a valid display
        int displayId = mDisplayPolicy.getBestDesktopDisplay();
        if (displayId == DesktopModeConstants.INVALID_DISPLAY_ID) {
            Log.w(TAG, "No valid desktop display available");
            // Could notify failure here
            return;
        }

        enterDesktopMode(displayId);
    }

    /**
     * Handles disabling desktop mode request.
     */
    private void handleDisableDesktopMode() {
        Log.i(TAG, "Handling disable desktop mode request");

        // Check if already disabled
        if (mCurrentState.get() == DesktopModeConstants.STATE_PHONE_MODE) {
            Log.w(TAG, "Desktop mode already disabled");
            return;
        }

        exitDesktopMode();
    }

    /**
     * Handles display connected event.
     */
    private void handleDisplayConnected(int displayId) {
        Log.i(TAG, "Display connected: " + displayId);

        // Update display policy
        boolean isValid = mDisplayPolicy.onDisplayAdded(displayId);

        if (!isValid) {
            Log.d(TAG, "Display is not valid for desktop mode: " + displayId);
            return;
        }

        // Create display config
        DesktopDisplayConfig config = mDisplayPolicy.createDisplayConfig(displayId);
        if (config != null) {
            mStateNotifier.notifyDisplayConnected(displayId, config);
        }

        // Auto-enable if we have a preferred display or auto-enable is set
        boolean autoEnable = SystemProperties.getBoolean(
                DesktopModeConstants.SYSPROP_DESKTOP_MODE_ENABLED, false);
        int preferredDisplay = mDisplayPolicy.getPreferredDesktopDisplay();

        if (autoEnable || preferredDisplay == displayId
                || (preferredDisplay == -1 && mCurrentState.get()
                    == DesktopModeConstants.STATE_PHONE_MODE
                    && mDesktopDisplayId.get() == DesktopModeConstants.INVALID_DISPLAY_ID)) {
            Log.i(TAG, "Auto-enabling desktop mode for display: " + displayId);
            enterDesktopMode(displayId);
        }
    }

    /**
     * Handles display disconnected event.
     */
    private void handleDisplayDisconnected(int displayId) {
        Log.i(TAG, "Display disconnected: " + displayId);

        // Update display policy
        mDisplayPolicy.onDisplayRemoved(displayId);

        // Notify listeners
        mStateNotifier.notifyDisplayDisconnected(displayId);

        // If this was the desktop display, exit desktop mode
        if (displayId == mDesktopDisplayId.get()) {
            Log.i(TAG, "Desktop display disconnected, exiting desktop mode");
            exitDesktopMode();
        }
    }

    /**
     * Handles display configuration changed event.
     */
    private void handleDisplayConfigChanged(int displayId) {
        Log.d(TAG, "Display config changed: " + displayId);

        // If this is the current desktop display, notify listeners
        if (displayId == mDesktopDisplayId.get()) {
            DesktopDisplayConfig config = mDisplayPolicy.createDisplayConfig(displayId);
            if (config != null) {
                mStateNotifier.notifyDisplayConfigChanged(displayId, config);
            }
        }
    }

    /**
     * Handles screen on event.
     */
    private void handleScreenOn() {
        Log.i(TAG, "Screen on");

        // Resume from suspended state
        if (mCurrentState.get() == DesktopModeConstants.STATE_SUSPENDED) {
            int displayId = mDesktopDisplayId.get();
            if (displayId != DesktopModeConstants.INVALID_DISPLAY_ID) {
                mStateNotifier.notifyStateChanged(
                        DesktopModeConstants.STATE_DESKTOP_MODE,
                        DesktopModeConstants.STATE_SUSPENDED);
                mCurrentState.set(DesktopModeConstants.STATE_DESKTOP_MODE);
                Log.i(TAG, "Resumed from suspended state");
            }
        }
    }

    /**
     * Handles screen off event.
     */
    private void handleScreenOff() {
        Log.i(TAG, "Screen off");

        // Suspend desktop mode
        if (mCurrentState.get() == DesktopModeConstants.STATE_DESKTOP_MODE) {
            mStateNotifier.notifyStateChanged(
                    DesktopModeConstants.STATE_SUSPENDED,
                    DesktopModeConstants.STATE_DESKTOP_MODE);
            mCurrentState.set(DesktopModeConstants.STATE_SUSPENDED);
            Log.i(TAG, "Suspended desktop mode");
        }
    }

    /**
     * Enters desktop mode on the specified display.
     */
    private void enterDesktopMode(int displayId) {
        Log.i(TAG, "Entering desktop mode on display: " + displayId);

        // Set state to activating
        int oldState = mCurrentState.getAndSet(DesktopModeConstants.STATE_DESKTOP_ACTIVATING);
        mStateNotifier.notifyStateChanged(DesktopModeConstants.STATE_DESKTOP_ACTIVATING, oldState);

        try {
            // Step 1: Configure display
            DesktopDisplayConfig config = mDisplayPolicy.createDisplayConfig(displayId);
            if (config == null) {
                throw new RuntimeException("Failed to create display config");
            }

            // Step 2: Create desktop stack in window manager
            if (mWindowManagerInternal != null) {
                // The window manager handles creating the desktop stack
                Log.d(TAG, "Creating desktop stack on display: " + displayId);
            }

            // Step 3: Configure input policy for desktop
            updateInputPolicyForDesktop(displayId, true);

            // Step 4: Set desktop display ID
            mDesktopDisplayId.set(displayId);

            // Step 5: Persist state
            SystemProperties.set(DesktopModeConstants.SYSPROP_DESKTOP_MODE_ENABLED, "true");
            SystemProperties.set(DesktopModeConstants.SYSPROP_DESKTOP_MODE_DISPLAY,
                    String.valueOf(displayId));

            // Step 6: Set state to desktop mode
            oldState = mCurrentState.getAndSet(DesktopModeConstants.STATE_DESKTOP_MODE);
            mStateNotifier.notifyStateChanged(DesktopModeConstants.STATE_DESKTOP_MODE, oldState);
            mStateNotifier.notifyDesktopModeEnabled(displayId);

            // Step 7: Launch desktop launcher
            launchDesktopLauncher(displayId);

            Log.i(TAG, "Successfully entered desktop mode on display: " + displayId);

        } catch (Exception e) {
            Log.e(TAG, "Failed to enter desktop mode", e);

            // Rollback
            mCurrentState.set(DesktopModeConstants.STATE_PHONE_MODE);
            mDesktopDisplayId.set(DesktopModeConstants.INVALID_DISPLAY_ID);
            SystemProperties.set(DesktopModeConstants.SYSPROP_DESKTOP_MODE_ENABLED, "false");

            mStateNotifier.notifyStateChanged(DesktopModeConstants.STATE_PHONE_MODE,
                    DesktopModeConstants.STATE_DESKTOP_ACTIVATING);
        }
    }

    /**
     * Exits desktop mode.
     */
    private void exitDesktopMode() {
        Log.i(TAG, "Exiting desktop mode");

        int displayId = mDesktopDisplayId.get();

        // Set state to deactivating
        int oldState = mCurrentState.getAndSet(DesktopModeConstants.STATE_DESKTOP_DEACTIVATING);
        mStateNotifier.notifyStateChanged(DesktopModeConstants.STATE_DESKTOP_DEACTIVATING, oldState);

        try {
            // Step 1: Update input policy
            updateInputPolicyForDesktop(displayId, false);

            // Step 2: Clean up desktop stack in window manager
            if (mWindowManagerInternal != null) {
                Log.d(TAG, "Cleaning up desktop stack");
            }

            // Step 3: Clear desktop display ID
            mDesktopDisplayId.set(DesktopModeConstants.INVALID_DISPLAY_ID);

            // Step 4: Persist state
            SystemProperties.set(DesktopModeConstants.SYSPROP_DESKTOP_MODE_ENABLED, "false");
            SystemProperties.set(DesktopModeConstants.SYSPROP_DESKTOP_MODE_DISPLAY, "-1");

            // Step 5: Set state to phone mode
            oldState = mCurrentState.getAndSet(DesktopModeConstants.STATE_PHONE_MODE);
            mStateNotifier.notifyStateChanged(DesktopModeConstants.STATE_PHONE_MODE, oldState);
            mStateNotifier.notifyDesktopModeDisabled();

            Log.i(TAG, "Successfully exited desktop mode");

        } catch (Exception e) {
            Log.e(TAG, "Failed to exit desktop mode", e);

            // Force state back to phone mode
            mCurrentState.set(DesktopModeConstants.STATE_PHONE_MODE);
            mDesktopDisplayId.set(DesktopModeConstants.INVALID_DISPLAY_ID);

            mStateNotifier.notifyStateChanged(DesktopModeConstants.STATE_PHONE_MODE,
                    DesktopModeConstants.STATE_DESKTOP_DEACTIVATING);
        }
    }

    /**
     * Updates input policy for desktop mode.
     */
    private void updateInputPolicyForDesktop(int displayId, boolean enable) {
        if (mInputManager == null) {
            return;
        }

        Log.d(TAG, "Updating input policy for desktop: enable=" + enable);

        if (enable && mEnableHoverEvents) {
            // Enable hover events for the desktop display
            // This would typically involve calling InputManager methods
            Log.d(TAG, "Enabled hover events for desktop display");
        }
    }

    /**
     * Launches the desktop launcher on the specified display.
     */
    private void launchDesktopLauncher(int displayId) {
        Log.i(TAG, "Launching desktop launcher on display: " + displayId);

        // Resolve the desktop launcher intent
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_HOME);
        launcherIntent.addCategory("android.intent.category.DESKTOP");
        launcherIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        ResolveInfo resolveInfo = mContext.getPackageManager()
                .resolveActivityAsUser(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY,
                        mCurrentUserId);

        if (resolveInfo != null) {
            Intent startIntent = new Intent(launcherIntent);
            startIntent.setComponent(resolveInfo.activityInfo.getComponentName());
            startIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startIntent.putExtra(Intent.EXTRA_DISPLAY_COOKIE, displayId);

            try {
                mContext.startActivityAsUser(startIntent, UserHandle.of(mCurrentUserId));
                Log.i(TAG, "Launched desktop launcher: "
                        + resolveInfo.activityInfo.packageName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch desktop launcher", e);
            }
        } else {
            Log.w(TAG, "No desktop launcher found");
        }
    }

    /**
     * Internal callback when desktop mode is enabled.
     */
    private void onDesktopModeEnabledInternal(int displayId) {
        Log.i(TAG, "Desktop mode enabled internally, display: " + displayId);
    }

    /**
     * Internal callback when desktop mode is disabled.
     */
    private void onDesktopModeDisabledInternal() {
        Log.i(TAG, "Desktop mode disabled internally");
    }

    /**
     * Handles state transitions.
     */
    private void handleStateTransition(int newState, int oldState) {
        Log.i(TAG, "State transition: " + stateToString(oldState)
                + " -> " + stateToString(newState));
    }

    /**
     * Enables desktop mode internally (synchronous).
     */
    private boolean enableDesktopModeInternal() {
        int displayId = mDisplayPolicy.getBestDesktopDisplay();
        if (displayId == DesktopModeConstants.INVALID_DISPLAY_ID) {
            return false;
        }
        enterDesktopMode(displayId);
        return true;
    }

    /**
     * Converts state to string for logging.
     */
    private static String stateToString(int state) {
        switch (state) {
            case DesktopModeConstants.STATE_PHONE_MODE:
                return "PHONE_MODE";
            case DesktopModeConstants.STATE_DESKTOP_ACTIVATING:
                return "DESKTOP_ACTIVATING";
            case DesktopModeConstants.STATE_DESKTOP_MODE:
                return "DESKTOP_MODE";
            case DesktopModeConstants.STATE_DESKTOP_DEACTIVATING:
                return "DESKTOP_DEACTIVATING";
            case DesktopModeConstants.STATE_SUSPENDED:
                return "SUSPENDED";
            default:
                return "UNKNOWN(" + state + ")";
        }
    }

    /**
     * Binder service implementation.
     */
    private final class BinderService extends IDesktopModeService.Stub {

        @Override
        public boolean isDesktopModeEnabled() {
            return mCurrentState.get() == DesktopModeConstants.STATE_DESKTOP_MODE;
        }

        @Override
        public int getDesktopDisplayId() {
            return mDesktopDisplayId.get();
        }

        @Override
        public int getDesktopModeState() {
            return mCurrentState.get();
        }

        @Override
        public DesktopDisplayConfig getDesktopDisplayConfig() {
            int displayId = mDesktopDisplayId.get();
            if (displayId == DesktopModeConstants.INVALID_DISPLAY_ID) {
                return null;
            }
            return mDisplayPolicy.createDisplayConfig(displayId);
        }

        @Override
        public boolean enableDesktopMode() {
            enforceControlPermission();
            mHandler.obtainMessage(MSG_ENABLE_DESKTOP_MODE).sendToTarget();
            return true;
        }

        @Override
        public boolean disableDesktopMode() {
            enforceControlPermission();
            mHandler.obtainMessage(MSG_DISABLE_DESKTOP_MODE).sendToTarget();
            return true;
        }

        @Override
        public boolean setTaskWindowingMode(int taskId, int windowingMode) {
            enforceControlPermission();
            // Would delegate to ActivityTaskManager
            Log.d(TAG, "setTaskWindowingMode: taskId=" + taskId
                    + ", mode=" + windowingMode);
            return true;
        }

        @Override
        public boolean minimizeWindow(int taskId) {
            enforceControlPermission();
            Log.d(TAG, "minimizeWindow: taskId=" + taskId);
            return true;
        }

        @Override
        public boolean maximizeWindow(int taskId) {
            enforceControlPermission();
            Log.d(TAG, "maximizeWindow: taskId=" + taskId);
            return true;
        }

        @Override
        public boolean restoreWindow(int taskId) {
            enforceControlPermission();
            Log.d(TAG, "restoreWindow: taskId=" + taskId);
            return true;
        }

        @Override
        public boolean closeWindow(int taskId) {
            enforceControlPermission();
            Log.d(TAG, "closeWindow: taskId=" + taskId);
            return true;
        }

        @Override
        public boolean resizeWindow(int taskId, Rect bounds) {
            enforceControlPermission();
            Log.d(TAG, "resizeWindow: taskId=" + taskId + ", bounds=" + bounds);
            return true;
        }

        @Override
        public boolean moveWindow(int taskId, int x, int y) {
            enforceControlPermission();
            Log.d(TAG, "moveWindow: taskId=" + taskId + ", x=" + x + ", y=" + y);
            return true;
        }

        @Override
        public WindowInfo getWindowInfo(int taskId) {
            // Would get from ActivityTaskManager
            return null;
        }

        @Override
        public List<WindowInfo> getDesktopWindows() {
            // Would get from ActivityTaskManager
            return new ArrayList<>();
        }

        @Override
        public void setTaskbarVisibility(boolean visible) {
            enforceControlPermission();
            mTaskbarVisible.set(visible);
            mStateNotifier.notifyTaskbarVisibilityChanged(visible);
        }

        @Override
        public boolean isTaskbarVisible() {
            return mTaskbarVisible.get();
        }

        @Override
        public void addDesktopModeListener(IDesktopModeListener listener) {
            final String packageName = getCallingPackage();
            final int callingUid = Binder.getCallingUid();
            mStateNotifier.addListener(listener, packageName, callingUid);
        }

        @Override
        public void removeDesktopModeListener(IDesktopModeListener listener) {
            mStateNotifier.removeListener(listener);
        }

        @Override
        public List<Integer> getAvailableDesktopDisplays() {
            return mDisplayPolicy.getAvailableDesktopDisplays();
        }

        @Override
        public boolean setPreferredDesktopDisplay(int displayId) {
            enforceControlPermission();
            return mDisplayPolicy.setPreferredDesktopDisplay(displayId);
        }

        @Override
        public int getPreferredDesktopDisplay() {
            return mDisplayPolicy.getPreferredDesktopDisplay();
        }

        @Override
        public android.os.Bundle getConfiguration() {
            android.os.Bundle config = new android.os.Bundle();
            config.putBoolean("multi_window_enabled", mMultiWindowEnabled);
            config.putBoolean("hover_events_enabled", mEnableHoverEvents);
            config.putBoolean("keep_phone_screen_on", mKeepPhoneScreenOn);
            return config;
        }

        @Override
        public boolean updateConfiguration(android.os.Bundle config) {
            enforceControlPermission();
            if (config.containsKey("keep_phone_screen_on")) {
                mKeepPhoneScreenOn = config.getBoolean("keep_phone_screen_on");
            }
            return true;
        }

        private void enforceControlPermission() {
            mContext.enforceCallingOrSelfPermission(
                    DesktopModeConstants.PERMISSION_CONTROL_DESKTOP_MODE,
                    "Caller does not have permission to control desktop mode");
        }
    }
}
