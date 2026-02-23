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
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.widget.LayoutPreference;

import java.io.File;

/**
 * Controller for displaying storage information.
 * Shows total and available storage with a progress bar visualization.
 */
public class StorageInfoPreferenceController extends BasePreferenceController {

    private static final String KEY_STORAGE_INFO = "storage_info";

    private LayoutPreference mPreference;

    public StorageInfoPreferenceController(Context context) {
        super(context, KEY_STORAGE_INFO);
    }

    public StorageInfoPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(KEY_STORAGE_INFO);
        if (mPreference != null) {
            setupCard();
        }
    }

    private void setupCard() {
        // Set icon
        ImageView iconView = mPreference.findViewById(R.id.device_info_icon);
        if (iconView != null) {
            iconView.setImageResource(R.drawable.ic_storage_info);
        }

        // Set title
        TextView titleView = mPreference.findViewById(R.id.device_info_title);
        if (titleView != null) {
            titleView.setText(R.string.storage_info_title);
        }

        // Set summary
        TextView summaryView = mPreference.findViewById(R.id.device_info_summary);
        if (summaryView != null) {
            summaryView.setText(getStorageSummary());
        }

        // Show progress bar
        View progressContainer = mPreference.findViewById(R.id.progress_container);
        if (progressContainer != null) {
            progressContainer.setVisibility(View.VISIBLE);
            
            ProgressBar progressBar = mPreference.findViewById(R.id.storage_progress);
            if (progressBar != null) {
                int progress = getStorageUsagePercent();
                progressBar.setProgress(progress);
            }
            
            TextView usedLabel = mPreference.findViewById(R.id.used_label);
            TextView freeLabel = mPreference.findViewById(R.id.free_label);
            
            if (usedLabel != null && freeLabel != null) {
                File path = Environment.getDataDirectory();
                StatFs stat = new StatFs(path.getPath());
                
                long blockSize = stat.getBlockSizeLong();
                long totalBlocks = stat.getBlockCountLong();
                long availableBlocks = stat.getAvailableBlocksLong();
                
                long totalStorage = totalBlocks * blockSize;
                long availableStorage = availableBlocks * blockSize;
                long usedStorage = totalStorage - availableStorage;
                
                usedLabel.setText(mContext.getString(R.string.used_format, 
                        Formatter.formatFileSize(mContext, usedStorage)));
                freeLabel.setText(mContext.getString(R.string.free_format, 
                        Formatter.formatFileSize(mContext, availableStorage)));
            }
        }
    }

    private String getStorageSummary() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        
        long totalStorage = totalBlocks * blockSize;
        long availableStorage = availableBlocks * blockSize;
        long usedStorage = totalStorage - availableStorage;
        
        String totalStr = Formatter.formatFileSize(mContext, totalStorage);
        String usedStr = Formatter.formatFileSize(mContext, usedStorage);
        String availableStr = Formatter.formatFileSize(mContext, availableStorage);
        
        return mContext.getString(R.string.storage_summary_format, 
                usedStr, totalStr, availableStr);
    }

    private int getStorageUsagePercent() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        long availableBlocks = stat.getAvailableBlocksLong();
        
        long totalStorage = totalBlocks * blockSize;
        long availableStorage = availableBlocks * blockSize;
        long usedStorage = totalStorage - availableStorage;
        
        if (totalStorage == 0) return 0;
        return (int) ((usedStorage * 100) / totalStorage);
    }

    /**
     * Get total internal storage size
     */
    public long getTotalStorage() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        return stat.getBlockCountLong() * stat.getBlockSizeLong();
    }

    /**
     * Get available internal storage size
     */
    public long getAvailableStorage() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
    }

    @Override
    public boolean isSliceable() {
        return false;
    }
}
