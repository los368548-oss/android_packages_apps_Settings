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
import android.os.BatteryManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.widget.LayoutPreference;

/**
 * Controller for displaying Battery information.
 * Shows battery capacity and current level with visualization.
 */
public class BatteryInfoPreferenceController extends BasePreferenceController {

    private static final String KEY_BATTERY_INFO = "battery_info";

    private LayoutPreference mPreference;
    private BatteryManager mBatteryManager;

    public BatteryInfoPreferenceController(Context context) {
        super(context, KEY_BATTERY_INFO);
        mBatteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    }

    public BatteryInfoPreferenceController(Context context, String key) {
        super(context, key);
        mBatteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(KEY_BATTERY_INFO);
        if (mPreference != null) {
            setupCard();
        }
    }

    private void setupCard() {
        // Set icon
        ImageView iconView = mPreference.findViewById(R.id.device_info_icon);
        if (iconView != null) {
            iconView.setImageResource(R.drawable.ic_battery_info);
        }

        // Set title
        TextView titleView = mPreference.findViewById(R.id.device_info_title);
        if (titleView != null) {
            titleView.setText(R.string.battery_info_title);
        }

        // Set summary with battery info
        TextView summaryView = mPreference.findViewById(R.id.device_info_summary);
        if (summaryView != null) {
            summaryView.setText(getBatterySummary());
        }

        // Show progress bar
        View progressContainer = mPreference.findViewById(R.id.progress_container);
        if (progressContainer != null) {
            progressContainer.setVisibility(View.VISIBLE);
            
            ProgressBar progressBar = mPreference.findViewById(R.id.storage_progress);
            if (progressBar != null) {
                int batteryLevel = mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                progressBar.setProgress(batteryLevel);
            }
            
            TextView usedLabel = mPreference.findViewById(R.id.used_label);
            TextView freeLabel = mPreference.findViewById(R.id.free_label);
            
            if (usedLabel != null && freeLabel != null) {
                int batteryLevel = mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                
                usedLabel.setText(mContext.getString(R.string.battery_level_format, batteryLevel));
                freeLabel.setText(mContext.getString(R.string.battery_health_status, getBatteryHealth()));
            }
        }
    }

    private String getBatterySummary() {
        int batteryLevel = mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        int capacityMah = getBatteryCapacity();
        
        if (capacityMah > 0) {
            return mContext.getString(R.string.battery_summary_capacity_format, 
                    batteryLevel, capacityMah);
        }
        return mContext.getString(R.string.battery_summary_format, batteryLevel);
    }

    private int getBatteryCapacity() {
        // Try to get battery capacity from system property
        String capacityProp = SystemProperties.get("ro.havoc.battery.capacity", "0");
        try {
            return Integer.parseInt(capacityProp);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String getBatteryHealth() {
        // This would typically come from BatteryManager, simplified here
        return mContext.getString(R.string.battery_health_good);
    }

    @Override
    public boolean isSliceable() {
        return false;
    }
}