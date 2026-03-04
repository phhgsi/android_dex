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
import android.app.desktop.DesktopDisplayConfig;
import android.app.desktop.DesktopModeConstants;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;
import android.view.WindowInfo;

import com.android.server.desktop.IDesktopModeListener;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Observer pattern implementation for Desktop Mode state changes.
 *
 * <p>This class manages listeners and notifies them when desktop mode
 * state changes, display changes, or window changes occur.
 *
 * @hide
 */
public class DesktopModeStateNotifier {

    private static final String TAG = "DesktopModeStateNotifier";

    /** Current desktop mode state */
    private final AtomicInteger mCurrentState = new AtomicInteger(
            DesktopModeConstants.STATE_PHONE_MODE);

    /** Current desktop display ID (-1 if not in desktop mode) */
    private final AtomicInteger mDesktopDisplayId = new AtomicInteger(
            DesktopModeConstants.INVALID_DISPLAY_ID);

    /** Current desktop display configuration */
    private final AtomicReference<DesktopDisplayConfig> mDisplayConfig =
            new AtomicReference<>(null);

    /** Listeners registered for state changes */
    private final CopyOnWriteArrayList<ListenerRecord> mListeners =
            new CopyOnWriteArrayList<>();

    /** Callback for internal state changes */
    @Nullable
    private InternalCallback mInternalCallback;

    /**
     * Listener record holding the listener and its associated executor.
     */
    private static class ListenerRecord {
        final IDesktopModeListener listener;
        final String packageName;
        final int callingUid;

        ListenerRecord(IDesktopModeListener listener, String packageName, int callingUid) {
            this.listener = listener;
            this.packageName = packageName;
            this.callingUid = callingUid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListenerRecord that = (ListenerRecord) o;
            return listener.asBinder().equals(that.listener.asBinder());
        }

        @Override
        public int hashCode() {
            return listener.asBinder().hashCode();
        }
    }

    /**
     * Internal callback interface for state changes.
     */
    public interface InternalCallback {
        /**
         * Called when desktop mode is enabled.
         *
         * @param displayId The desktop display ID
         */
        default void onDesktopModeEnabled(int displayId) {}

        /**
         * Called when desktop mode is disabled.
         */
        default void onDesktopModeDisabled() {}

        /**
         * Called when desktop mode state changes.
         *
         * @param newState The new state
         * @param oldState The previous state
         */
        default void onStateChanged(int newState, int oldState) {}
    }

    /**
     * Sets the internal callback for state changes.
     *
     * @param callback The callback to set
     */
    public void setInternalCallback(@Nullable InternalCallback callback) {
        mInternalCallback = callback;
    }

    /**
     * Gets the current desktop mode state.
     *
     * @return The current state
     */
    public int getCurrentState() {
        return mCurrentState.get();
    }

    /**
     * Gets the current desktop display ID.
     *
     * @return The desktop display ID, or -1 if not in desktop mode
     */
    public int getDesktopDisplayId() {
        return mDesktopDisplayId.get();
    }

    /**
     * Gets the current desktop display configuration.
     *
     * @return The display config, or null if not in desktop mode
     */
    @Nullable
    public DesktopDisplayConfig getDisplayConfig() {
        return mDisplayConfig.get();
    }

    /**
     * Checks if desktop mode is currently enabled.
     *
     * @return true if desktop mode is enabled
     */
    public boolean isDesktopModeEnabled() {
        return mCurrentState.get() == DesktopModeConstants.STATE_DESKTOP_MODE;
    }

    /**
     * Registers a listener for desktop mode state changes.
     *
     * @param listener The listener to add
     * @param packageName The package name of the caller
     * @param callingUid The UID of the caller
     * @return true if the listener was added successfully
     */
    public boolean addListener(@NonNull IDesktopModeListener listener,
                               @NonNull String packageName, int callingUid) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }

        // Check if listener is already registered
        for (ListenerRecord record : mListeners) {
            if (record.listener.asBinder().equals(listener.asBinder())) {
                Log.w(TAG, "Listener already registered: " + packageName);
                return false;
            }
        }

        ListenerRecord record = new ListenerRecord(listener, packageName, callingUid);
        mListeners.add(record);

        Log.d(TAG, "Added listener for package: " + packageName
                + ", total listeners: " + mListeners.size());

        return true;
    }

    /**
     * Unregisters a listener.
     *
     * @param listener The listener to remove
     * @return true if the listener was found and removed
     */
    public boolean removeListener(@NonNull IDesktopModeListener listener) {
        if (listener == null) {
            return false;
        }

        boolean removed = false;
        for (ListenerRecord record : mListeners) {
            if (record.listener.asBinder().equals(listener.asBinder())) {
                mListeners.remove(record);
                removed = true;
                Log.d(TAG, "Removed listener for package: " + record.packageName
                        + ", remaining listeners: " + mListeners.size());
                break;
            }
        }

        return removed;
    }

    /**
     * Gets the number of registered listeners.
     *
     * @return The listener count
     */
    public int getListenerCount() {
        return mListeners.size();
    }

    /**
     * Notifies all listeners that desktop mode has been enabled.
     *
     * @param displayId The desktop display ID
     */
    public void notifyDesktopModeEnabled(int displayId) {
        mDesktopDisplayId.set(displayId);
        mCurrentState.set(DesktopModeConstants.STATE_DESKTOP_MODE);
        mDisplayConfig.set(null); // Will be set by notifyDisplayChanged

        Log.i(TAG, "Notifying desktop mode enabled, displayId: " + displayId);

        // Notify internal callback first
        if (mInternalCallback != null) {
            mInternalCallback.onDesktopModeEnabled(displayId);
        }

        // Notify all listeners
        for (ListenerRecord record : mListeners) {
            notifyListener(record, listener -> {
                try {
                    listener.onDesktopModeEnabled(displayId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to notify listener: " + record.packageName, e);
                }
            });
        }
    }

    /**
     * Notifies all listeners that desktop mode has been disabled.
     */
    public void notifyDesktopModeDisabled() {
        int oldDisplayId = mDesktopDisplayId.getAndSet(
                DesktopModeConstants.INVALID_DISPLAY_ID);
        int oldState = mCurrentState.getAndSet(DesktopModeConstants.STATE_PHONE_MODE);
        mDisplayConfig.set(null);

        Log.i(TAG, "Notifying desktop mode disabled, was on display: " + oldDisplayId);

        // Notify internal callback first
        if (mInternalCallback != null) {
            mInternalCallback.onDesktopModeDisabled();
        }

        // Notify all listeners
        for (ListenerRecord record : mListeners) {
            notifyListener(record, listener -> {
                try {
                    listener.onDesktopModeDisabled();
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to notify listener: " + record.packageName, e);
                }
            });
        }
    }

    /**
     * Notifies all listeners that the desktop mode state has changed.
     *
     * @param newState The new state
     * @param oldState The previous state
     */
    public void notifyStateChanged(int newState, int oldState) {
        mCurrentState.set(newState);

        Log.i(TAG, "Notifying state changed: " + stateToString(oldState)
                + " -> " + stateToString(newState));

        // Notify internal callback
        if (mInternalCallback != null) {
            mInternalCallback.onStateChanged(newState, oldState);
        }

        // Notify all listeners
        for (ListenerRecord record : mListeners) {
            notifyListener(record, listener -> {
                try {
                    listener.onDesktopModeStateChanged(newState);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to notify listener: " + record.packageName, e);
                }
            });
        }
    }

    /**
     * Notifies all listeners that a desktop display has been connected.
     *
     * @param displayId The display ID
     * @param config The display configuration
     */
    public void notifyDisplayConnected(int displayId,
                                        @NonNull DesktopDisplayConfig config) {
        Log.i(TAG, "Notifying display connected: " + displayId);

        for (ListenerRecord record : mListeners) {
            notifyListener(record, listener -> {
                try {
                    listener.onDesktopDisplayConnected(displayId, config);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to notify listener: " + record.packageName, e);
                }
            });
        }
    }

    /**
     * Notifies all listeners that a desktop display has been disconnected.
     *
     * @param displayId The display ID
     */
    public void notifyDisplayDisconnected(int displayId) {
        Log.i(TAG, "Notifying display disconnected: " + displayId);

        // Clear display config if this was the current desktop display
        if (displayId == mDesktopDisplayId.get()) {
            mDisplayConfig.set(null);
        }

        for (ListenerRecord record : mListeners) {
            notifyListener(record, listener -> {
                try {
                    listener.onDesktopDisplayDisconnected(displayId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to notify listener: " + record.packageName, e);
                }
            });
        }
    }

    /**
     * Notifies all listeners that display configuration has changed.
     *
     * @param displayId The display ID
     * @param config The new configuration
     */
    public void notifyDisplayConfigChanged(int displayId,
                                            @NonNull DesktopDisplayConfig config) {
        // Update stored config if this is the current desktop display
        if (displayId == mDesktopDisplayId.get()) {
            mDisplayConfig.set(config);
        }

        Log.i(TAG, "Notifying display config changed: " + displayId);

        for (ListenerRecord record : mListeners) {
            notifyListener(record, listener -> {
                try {
                    listener.onDesktopDisplayConfigChanged(displayId, config);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to notify listener: " + record.packageName, e);
                }
            });
        }
    }

    /**
     * Notifies all listeners that a new window has been added.
     *
     * @param windowInfo Information about the new window
     */
    public void notifyWindowAdded(@NonNull WindowInfo windowInfo) {
        Log.d(TAG, "Notifying window added: taskId=" + windowInfo.taskId);

        for (ListenerRecord record : mListeners) {
            notifyListener(record, listener -> {
                try {
                    listener.onWindowAdded(windowInfo);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to notify listener: " + record.packageName, e);
                }
            });
        }
    }

    /**
     * Notifies all listeners that a window has been removed.
     *
     * @param taskId The ID of the removed task
     */
    public void notifyWindowRemoved(int taskId) {
        Log.d(TAG, "Notifying window removed: taskId=" + taskId);

        for (ListenerRecord record : mListeners) {
            notifyListener(record, listener -> {
                try {
                    listener.onWindowRemoved(taskId);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to notify listener: " + record.packageName, e);
                }
            });
        }
    }

    /**
     * Notifies all listeners that a window's focus has changed.
     *
     * @param taskId The task ID
     * @param hasFocus Whether the window has focus
     */
    public void notifyWindowFocusChanged(int taskId, boolean hasFocus) {
        Log.d(TAG, "Notifying window focus changed: taskId=" + taskId
                + ", hasFocus=" + hasFocus);

        for (ListenerRecord record : mListeners) {
            notifyListener(record, listener -> {
                try {
                    listener.onWindowFocusChanged(taskId, hasFocus);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to notify listener: " + record.packageName, e);
                }
            });
        }
    }

    /**
     * Notifies all listeners that window bounds have changed.
     *
     * @param taskId The task ID
     * @param bounds The new bounds
     */
    public void notifyWindowBoundsChanged(int taskId, @NonNull android.graphics.Rect bounds) {
        Log.d(TAG, "Notifying window bounds changed: taskId=" + taskId);

        for (ListenerRecord record : mListeners) {
            notifyListener(record, listener -> {
                try {
                    listener.onWindowBoundsChanged(taskId, bounds);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to notify listener: " + record.packageName, e);
                }
            });
        }
    }

    /**
     * Notifies all listeners that taskbar visibility has changed.
     *
     * @param visible Whether the taskbar is visible
     */
    public void notifyTaskbarVisibilityChanged(boolean visible) {
        Log.d(TAG, "Notifying taskbar visibility changed: visible=" + visible);

        for (ListenerRecord record : mListeners) {
            notifyListener(record, listener -> {
                try {
                    listener.onTaskbarVisibilityChanged(visible);
                } catch (RemoteException e) {
                    Log.w(TAG, "Failed to notify listener: " + record.packageName, e);
                }
            });
        }
    }

    /**
     * Helper interface for notification.
     */
    private interface NotificationRunnable {
        void run(IDesktopModeListener listener) throws RemoteException;
    }

    /**
     * Helper method to notify a listener safely.
     */
    private void notifyListener(ListenerRecord record, NotificationRunnable runnable) {
        try {
            runnable.run(record.listener);
        } catch (RemoteException e) {
            // Check if the binder has died
            Log.w(TAG, "Listener died: " + record.packageName, e);
            // Optionally remove dead listeners
            // mListeners.remove(record);
        }
    }

    /**
     * Converts state constant to string for logging.
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
     * Clears all listeners. Useful for testing.
     */
    public void clearListeners() {
        mListeners.clear();
        Log.d(TAG, "Cleared all listeners");
    }
}
