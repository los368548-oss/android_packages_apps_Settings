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
import android.os.Build;
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
 * Controller for displaying processor information from system property.
 * Reads from ro.havoc.processor property, falls back to hardware info.
 */
public class ProcessorInfoPreferenceController extends BasePreferenceController {

    private static final String KEY_PROCESSOR_INFO = "processor_info";
    private static final String PROP_PROCESSOR_INFO = "ro.havoc.processor";
    private static final String PROP_SOC_MANUFACTURER = "ro.soc.manufacturer";
    private static final String PROP_SOC_MODEL = "ro.soc.model";
    private static final String PROP_CPU_ABI = "ro.product.cpu.abi";

    private LayoutPreference mPreference;

    public ProcessorInfoPreferenceController(Context context) {
        super(context, KEY_PROCESSOR_INFO);
    }

    public ProcessorInfoPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(KEY_PROCESSOR_INFO);
        if (mPreference != null) {
            setupCard();
        }
    }

    private void setupCard() {
        // Set icon
        ImageView iconView = mPreference.findViewById(R.id.device_info_icon);
        if (iconView != null) {
            iconView.setImageResource(R.drawable.ic_processor_info);
        }

        // Set title
        TextView titleView = mPreference.findViewById(R.id.device_info_title);
        if (titleView != null) {
            titleView.setText(R.string.processor_info_title);
        }

        // Set summary
        TextView summaryView = mPreference.findViewById(R.id.device_info_summary);
        if (summaryView != null) {
            summaryView.setText(getProcessorInfo());
        }
    }

    private String getProcessorInfo() {
        String processor = SystemProperties.get(PROP_PROCESSOR_INFO, "");
        
        if (TextUtils.isEmpty(processor)) {
            // Fallback to SoC info
            String socManufacturer = SystemProperties.get(PROP_SOC_MANUFACTURER, "");
            String socModel = SystemProperties.get(PROP_SOC_MODEL, "");
            
            if (!TextUtils.isEmpty(socModel)) {
                processor = socModel;
                if (!TextUtils.isEmpty(socManufacturer)) {
                    processor = socManufacturer + " " + socModel;
                }
            } else {
                // Final fallback to hardware string
                processor = Build.HARDWARE.toUpperCase();
            }
        }
        
        return processor;
    }

    @Override
    public boolean isSliceable() {
        return false;
    }
}
