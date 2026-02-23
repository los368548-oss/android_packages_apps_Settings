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
import android.graphics.Point;
import android.os.SystemProperties;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.widget.LayoutPreference;

/**
 * Controller for displaying Display information.
 * Shows screen resolution, refresh rate, and density.
 */
public class DisplayInfoPreferenceController extends BasePreferenceController {

    private static final String KEY_DISPLAY_INFO = "display_info";

    private LayoutPreference mPreference;
    private WindowManager mWindowManager;

    public DisplayInfoPreferenceController(Context context) {
        super(context, KEY_DISPLAY_INFO);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public DisplayInfoPreferenceController(Context context, String key) {
        super(context, key);
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(KEY_DISPLAY_INFO);
        if (mPreference != null) {
            setupCard();
        }
    }

    private void setupCard() {
        // Set icon
        ImageView iconView = mPreference.findViewById(R.id.device_info_icon);
        if (iconView != null) {
            iconView.setImageResource(R.drawable.ic_display_info);
        }

        // Set title
        TextView titleView = mPreference.findViewById(R.id.device_info_title);
        if (titleView != null) {
            titleView.setText(R.string.display_info_title);
        }

        // Set summary with display info
        TextView summaryView = mPreference.findViewById(R.id.device_info_summary);
        if (summaryView != null) {
            summaryView.setText(getDisplaySummary());
        }

        // Hide progress bar for display info
        android.view.View progressContainer = mPreference.findViewById(R.id.progress_container);
        if (progressContainer != null) {
            progressContainer.setVisibility(android.view.View.GONE);
        }
    }

    private String getDisplaySummary() {
        Display display = mWindowManager.getDefaultDisplay();
        Point realSize = new Point();
        display.getRealSize(realSize);
        
        int width = realSize.x;
        int height = realSize.y;
        
        // Get refresh rate
        float refreshRate = display.getRefreshRate();
        
        // Get density
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        int densityDpi = metrics.densityDpi;
        
        // Try to get custom display info from system property
        String displayInfo = SystemProperties.get("ro.havoc.display", "");
        
        if (!displayInfo.isEmpty()) {
            return displayInfo;
        }
        
        // Format: "1080 x 2400 • 120Hz • 420dpi"
        StringBuilder sb = new StringBuilder();
        sb.append(width).append(" x ").append(height);
        sb.append(" • ").append(Math.round(refreshRate)).append("Hz");
        sb.append(" • ").append(densityDpi).append("dpi");
        
        return sb.toString();
    }

    @Override
    public boolean isSliceable() {
        return false;
    }
}
