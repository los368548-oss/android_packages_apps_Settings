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

package com.android.settings.fuelgauge;

import android.content.Context;
import android.os.BatteryManager;

import com.android.settings.R;
import com.android.settings.widget.SettingsCategoryBannerController;

/**
 * Banner controller for Battery settings.
 */
public class BatteryBannerPreferenceController extends SettingsCategoryBannerController {

    private static final String KEY_BATTERY_BANNER = "battery_banner";
    private BatteryManager mBatteryManager;

    public BatteryBannerPreferenceController(Context context) {
        super(context, KEY_BATTERY_BANNER);
        mBatteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    }

    @Override
    protected int getBannerIconRes() {
        return R.drawable.ic_battery_banner;
    }

    @Override
    protected int getBannerTitleRes() {
        return R.string.power_usage_summary_title;
    }

    @Override
    protected int getBannerSubtitleRes() {
        int batteryLevel = mBatteryManager != null 
                ? mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                : 0;
        return batteryLevel > 50 ? R.string.battery_banner_subtitle_good 
                : R.string.battery_banner_subtitle_low;
    }

    @Override
    protected boolean isBannerClickable() {
        return false;
    }
}
