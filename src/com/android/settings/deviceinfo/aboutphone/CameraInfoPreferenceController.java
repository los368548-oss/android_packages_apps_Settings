/*
 * Copyright (C) 2025 The HavocOOS Project
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

package com.android.settings.deviceinfo.aboutphone;

import android.content.Context;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.widget.LayoutPreference;

/**
 * Controller for displaying camera information from system property.
 * Reads from ro.havoc.camera property.
 */
public class CameraInfoPreferenceController extends BasePreferenceController {

    private static final String KEY_CAMERA_INFO = "camera_info";
    private static final String PROP_CAMERA_INFO = "ro.havoc.camera";

    private LayoutPreference mPreference;

    public CameraInfoPreferenceController(Context context) {
        super(context, KEY_CAMERA_INFO);
    }

    public CameraInfoPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(KEY_CAMERA_INFO);
        if (mPreference != null) {
            setupCard();
        }
    }

    private void setupCard() {
        // Set icon
        ImageView iconView = mPreference.findViewById(R.id.device_info_icon);
        if (iconView != null) {
            iconView.setImageResource(R.drawable.ic_camera_info);
        }

        // Set title
        TextView titleView = mPreference.findViewById(R.id.device_info_title);
        if (titleView != null) {
            titleView.setText(R.string.camera_info_title);
        }

        // Set summary
        TextView summaryView = mPreference.findViewById(R.id.device_info_summary);
        if (summaryView != null) {
            String cameraInfo = SystemProperties.get(PROP_CAMERA_INFO, "");
            if (TextUtils.isEmpty(cameraInfo)) {
                cameraInfo = mContext.getString(R.string.camera_info_default);
            }
            summaryView.setText(cameraInfo);
        }
    }

    @Override
    public boolean isSliceable() {
        return false;
    }
}
