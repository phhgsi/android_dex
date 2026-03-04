# Copyright (C) 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Desktop Mode Device Configuration
# Includes all Desktop Mode components in the system build

# Desktop Mode Framework - Core API library
PRODUCT_PACKAGES += \
    desktop-mode-framework

# Desktop Mode Service - System service implementation
PRODUCT_PACKAGES += \
    desktop-mode-service

# Desktop Launcher - Desktop mode home screen
PRODUCT_PACKAGES += \
    DesktopLauncher

# Desktop Mode SystemUI Plugin - UI components for SystemUI
PRODUCT_PACKAGES += \
    desktop-systemui

# Enable Desktop Mode feature
PRODUCT_COPY_FILES += \
    device/common/desktop_mode/sepolicy/desktop_mode.te:$(TARGET_COPY_OUT_VENDOR)/etc/selinux/vendor_sepolicy.conf \
    device/common/desktop_mode/sepolicy/desktop_mode_service.te:$(TARGET_COPY_OUT_VENDOR)/etc/selinux/vendor_sepolicy_service.conf \
    device/common/desktop_mode/sepolicy/file_contexts:$(TARGET_COPY_OUT_VENDOR)/etc/selinux/vendor_file_contexts

# Desktop Mode properties
PRODUCT_PROPERTY_OVERRIDES += \
    ro.features.desktop_mode.enabled=true

# Include Desktop Mode sepolicy
BOARD_SEPOLICY_DIRS += device/common/desktop_mode/sepolicy
