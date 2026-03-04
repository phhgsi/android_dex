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

import android.os.Parcel;
import android.os.Parcelable;
import android.util.DisplayMetrics;

import java.util.Objects;

/**
 * Configuration data class for desktop display properties.
 *
 * This class encapsulates all configuration parameters needed to set up
 * and manage a desktop mode display, including resolution, density,
 * refresh rate, and physical size information.
 *
 * @hide
 */
public final class DesktopDisplayConfig implements Parcelable {

    private final int mDisplayId;
    private final int mWidth;
    private final int mHeight;
    private final int mDensityDpi;
    private final float mRefreshRate;
    private final float mPhysicalWidth;
    private final float mPhysicalHeight;
    private final int mDisplayType;
    private final boolean mIsPrimary;

    private DesktopDisplayConfig(Builder builder) {
        mDisplayId = builder.mDisplayId;
        mWidth = builder.mWidth;
        mHeight = builder.mHeight;
        mDensityDpi = builder.mDensityDpi;
        mRefreshRate = builder.mRefreshRate;
        mPhysicalWidth = builder.mPhysicalWidth;
        mPhysicalHeight = builder.mPhysicalHeight;
        mDisplayType = builder.mDisplayType;
        mIsPrimary = builder.mIsPrimary;
    }

    /**
     * Creates a default desktop display configuration.
     *
     * @param displayId The display ID for this configuration
     * @return A new DesktopDisplayConfig with default values
     */
    public static DesktopDisplayConfig createDefault(int displayId) {
        return new Builder(displayId)
                .setWidth(1920)
                .setHeight(1080)
                .setDensityDpi(DesktopModeConstants.DEFAULT_DESKTOP_DPI)
                .setRefreshRate(DesktopModeConstants.DEFAULT_REFRESH_RATE)
                .setDisplayType(DesktopModeConstants.DISPLAY_TYPE_HDMI)
                .setIsPrimary(false)
                .build();
    }

    /**
     * @return The display ID
     */
    public int getDisplayId() {
        return mDisplayId;
    }

    /**
     * @return The display width in pixels
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * @return The display height in pixels
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * @return The display density in DPI (dots per inch)
     */
    public int getDensityDpi() {
        return mDensityDpi;
    }

    /**
     * @return The display refresh rate in Hz
     */
    public float getRefreshRate() {
        return mRefreshRate;
    }

    /**
     * @return The physical display width in millimeters
     */
    public float getPhysicalWidth() {
        return mPhysicalWidth;
    }

    /**
     * @return The physical display height in millimeters
     */
    public float getPhysicalHeight() {
        return mPhysicalHeight;
    }

    /**
     * @return The display type (HDMI, USB-C, etc.)
     */
    public int getDisplayType() {
        return mDisplayType;
    }

    /**
     * @return Whether this is the primary display
     */
    public boolean isPrimary() {
        return mIsPrimary;
    }

    /**
     * Calculates the optimal DPI based on physical display size.
     *
     * This method uses the physical dimensions of the display to calculate
     * an appropriate density that provides a comfortable desktop experience.
     * For typical monitor sizes, this results in a lower DPI to ensure
     * UI elements are properly sized for desktop use.
     *
     * @return The optimal DPI value for this display configuration
     */
    public int calculateOptimalDpi() {
        // Calculate diagonal size in inches
        double diagonalInches = Math.sqrt(
                (mPhysicalWidth * mPhysicalWidth) + (mPhysicalHeight * mPhysicalHeight)
        ) / 25.4; // Convert mm to inches

        // Calculate diagonal resolution
        double diagonalPixels = Math.sqrt(
                (mWidth * mWidth) + (mHeight * mHeight)
        );

        // Calculate raw DPI
        int rawDpi = (int) (diagonalPixels / diagonalInches);

        // Adjust for typical desktop monitor sizes
        // Desktop displays typically work best with 96-160 DPI
        if (diagonalInches >= 24) {
            // Large monitor (24"+)
            return Math.max(96, Math.min(rawDpi, 160));
        } else if (diagonalInches >= 15) {
            // Medium monitor (15-24")
            return Math.max(120, Math.min(rawDpi, 200));
        } else {
            // Small display (tablet, <15")
            return Math.max(160, Math.min(rawDpi, 320));
        }
    }

    /**
     * Gets the optimal DPI, or returns a fallback if physical size is not available.
     *
     * @return Optimal DPI if physical size is available, otherwise default/fallback DPI
     */
    public int getOptimalDpiOrDefault() {
        if (mPhysicalWidth > 0 && mPhysicalHeight > 0) {
            return calculateOptimalDpi();
        }
        return mDensityDpi > 0 ? mDensityDpi : DesktopModeConstants.FALLBACK_DESKTOP_DPI;
    }

    /**
     * Calculates the screen density ratio compared to the base density.
     *
     * @return The density ratio (e.g., 1.0 for 160dpi, 1.5 for 240dpi)
     */
    public float getDensityRatio() {
        return mDensityDpi / (float) DisplayMetrics.DENSITY_DEFAULT;
    }

    /**
     * Calculates the usable area ratio after accounting for system UI.
     *
     * @param systemUiHeight Height reserved for system UI in pixels
     * @return The ratio of usable area to total area
     */
    public float getUsableAreaRatio(int systemUiHeight) {
        int usableHeight = mHeight - systemUiHeight;
        return (float) usableHeight / mHeight;
    }

    /**
     * Validates that this display configuration meets minimum requirements.
     *
     * @return true if the configuration is valid for desktop mode
     */
    public boolean isValid() {
        return mDisplayId != DesktopModeConstants.INVALID_DISPLAY_ID
                && mWidth >= DesktopModeConstants.MIN_DESKTOP_WIDTH
                && mHeight >= DesktopModeConstants.MIN_DESKTOP_HEIGHT
                && mWidth <= DesktopModeConstants.MAX_DESKTOP_WIDTH
                && mHeight <= DesktopModeConstants.MAX_DESKTOP_HEIGHT
                && mDensityDpi > 0
                && mRefreshRate > 0;
    }

    /**
     * Returns a new Builder populated with this configuration's values.
     *
     * @return A new Builder initialized with this configuration
     */
    public Builder toBuilder() {
        return new Builder(mDisplayId)
                .setWidth(mWidth)
                .setHeight(mHeight)
                .setDensityDpi(mDensityDpi)
                .setRefreshRate(mRefreshRate)
                .setPhysicalWidth(mPhysicalWidth)
                .setPhysicalHeight(mPhysicalHeight)
                .setDisplayType(mDisplayType)
                .setIsPrimary(mIsPrimary);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDisplayId);
        dest.writeInt(mWidth);
        dest.writeInt(mHeight);
        dest.writeInt(mDensityDpi);
        dest.writeFloat(mRefreshRate);
        dest.writeFloat(mPhysicalWidth);
        dest.writeFloat(mPhysicalHeight);
        dest.writeInt(mDisplayType);
        dest.writeInt(mIsPrimary ? 1 : 0);
    }

    public static final Creator<DesktopDisplayConfig> CREATOR =
            new Creator<DesktopDisplayConfig>() {
                @Override
                public DesktopDisplayConfig createFromParcel(Parcel source) {
                    return new Builder(source.readInt())
                            .setWidth(source.readInt())
                            .setHeight(source.readInt())
                            .setDensityDpi(source.readInt())
                            .setRefreshRate(source.readFloat())
                            .setPhysicalWidth(source.readFloat())
                            .setPhysicalHeight(source.readFloat())
                            .setDisplayType(source.readInt())
                            .setIsPrimary(source.readInt() == 1)
                            .build();
                }

                @Override
                public DesktopDisplayConfig[] newArray(int size) {
                    return new DesktopDisplayConfig[size];
                }
            };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DesktopDisplayConfig that = (DesktopDisplayConfig) o;
        return mDisplayId == that.mDisplayId
                && mWidth == that.mWidth
                && mHeight == that.mHeight
                && mDensityDpi == that.mDensityDpi
                && Float.compare(that.mRefreshRate, mRefreshRate) == 0
                && Float.compare(that.mPhysicalWidth, mPhysicalWidth) == 0
                && Float.compare(that.mPhysicalHeight, mPhysicalHeight) == 0
                && mDisplayType == that.mDisplayType
                && mIsPrimary == that.mIsPrimary;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mDisplayId,
                mWidth,
                mHeight,
                mDensityDpi,
                mRefreshRate,
                mPhysicalWidth,
                mPhysicalHeight,
                mDisplayType,
                mIsPrimary
        );
    }

    @Override
    public String toString() {
        return "DesktopDisplayConfig{"
                + "displayId="
                + mDisplayId
                + ", width="
                + mWidth
                + ", height="
                + mHeight
                + ", densityDpi="
                + mDensityDpi
                + ", refreshRate="
                + mRefreshRate
                + ", physicalWidth="
                + mPhysicalWidth
                + ", physicalHeight="
                + mPhysicalHeight
                + ", displayType="
                + mDisplayType
                + ", isPrimary="
                + mIsPrimary
                + '}';
    }

    /**
     * Builder class for creating DesktopDisplayConfig instances.
     */
    public static final class Builder {
        private final int mDisplayId;
        private int mWidth = 1920;
        private int mHeight = 1080;
        private int mDensityDpi = DesktopModeConstants.DEFAULT_DESKTOP_DPI;
        private float mRefreshRate = DesktopModeConstants.DEFAULT_REFRESH_RATE;
        private float mPhysicalWidth = 0;
        private float mPhysicalHeight = 0;
        private int mDisplayType = DesktopModeConstants.DISPLAY_TYPE_HDMI;
        private boolean mIsPrimary = false;

        public Builder(int displayId) {
            mDisplayId = displayId;
        }

        public Builder setWidth(int width) {
            mWidth = width;
            return this;
        }

        public Builder setHeight(int height) {
            mHeight = height;
            return this;
        }

        public Builder setDensityDpi(int densityDpi) {
            mDensityDpi = densityDpi;
            return this;
        }

        public Builder setRefreshRate(float refreshRate) {
            mRefreshRate = refreshRate;
            return this;
        }

        public Builder setPhysicalWidth(float physicalWidth) {
            mPhysicalWidth = physicalWidth;
            return this;
        }

        public Builder setPhysicalHeight(float physicalHeight) {
            mPhysicalHeight = physicalHeight;
            return this;
        }

        public Builder setDisplayType(int displayType) {
            mDisplayType = displayType;
            return this;
        }

        public Builder setIsPrimary(boolean isPrimary) {
            mIsPrimary = isPrimary;
            return this;
        }

        public DesktopDisplayConfig build() {
            return new DesktopDisplayConfig(this);
        }
    }
}
