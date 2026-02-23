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

import android.app.ActivityManager;
import android.content.Context;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.widget.LayoutPreference;

/**
 * Controller for displaying RAM information.
 * Shows total and available RAM with usage visualization.
 */
public class RamInfoPreferenceController extends BasePreferenceController {

    private static final String KEY_RAM_INFO = "ram_info";

    private LayoutPreference mPreference;
    private ActivityManager mActivityManager;

    public RamInfoPreferenceController(Context context) {
        super(context, KEY_RAM_INFO);
        mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    }

    public RamInfoPreferenceController(Context context, String key) {
        super(context, key);
        mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(KEY_RAM_INFO);
        if (mPreference != null) {
            setupCard();
        }
    }

    private void setupCard() {
        // Set icon
        ImageView iconView = mPreference.findViewById(R.id.device_info_icon);
        if (iconView != null) {
            iconView.setImageResource(R.drawable.ic_ram_info);
        }

        // Set title
        TextView titleView = mPreference.findViewById(R.id.device_info_title);
        if (titleView != null) {
            titleView.setText(R.string.ram_info_title);
        }

        // Set summary with RAM info
        TextView summaryView = mPreference.findViewById(R.id.device_info_summary);
        if (summaryView != null) {
            summaryView.setText(getRamSummary());
        }

        // Show progress bar
        View progressContainer = mPreference.findViewById(R.id.progress_container);
        if (progressContainer != null) {
            progressContainer.setVisibility(View.VISIBLE);
            
            ProgressBar progressBar = mPreference.findViewById(R.id.storage_progress);
            if (progressBar != null) {
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                mActivityManager.getMemoryInfo(memInfo);
                
                long totalMem = memInfo.totalMem;
                long usedMem = totalMem - memInfo.availMem;
                int progress = (int) ((usedMem * 100) / totalMem);
                
                progressBar.setProgress(progress);
            }
            
            TextView usedLabel = mPreference.findViewById(R.id.used_label);
            TextView freeLabel = mPreference.findViewById(R.id.free_label);
            
            if (usedLabel != null && freeLabel != null) {
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                mActivityManager.getMemoryInfo(memInfo);
                
                long totalMem = memInfo.totalMem;
                long usedMem = totalMem - memInfo.availMem;
                
                usedLabel.setText(mContext.getString(R.string.used_format, 
                        Formatter.formatFileSize(mContext, usedMem)));
                freeLabel.setText(mContext.getString(R.string.free_format, 
                        Formatter.formatFileSize(mContext, memInfo.availMem)));
            }
        }
    }

    private String getRamSummary() {
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        mActivityManager.getMemoryInfo(memInfo);
        
        String totalStr = Formatter.formatFileSize(mContext, memInfo.totalMem);
        return mContext.getString(R.string.ram_summary_format, totalStr);
    }

    @Override
    public boolean isSliceable() {
        return false;
    }
}
