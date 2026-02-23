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

package com.android.settings.display;

import android.content.Context;

import com.android.settings.R;
import com.android.settings.widget.SettingsCategoryBannerController;

/**
 * Banner controller for Display settings.
 */
public class DisplayBannerPreferenceController extends SettingsCategoryBannerController {

    private static final String KEY_DISPLAY_BANNER = "display_banner";

    public DisplayBannerPreferenceController(Context context) {
        super(context, KEY_DISPLAY_BANNER);
    }

    @Override
    protected int getBannerIconRes() {
        return R.drawable.ic_display_banner;
    }

    @Override
    protected int getBannerTitleRes() {
        return R.string.display_settings;
    }

    @Override
    protected int getBannerSubtitleRes() {
        return R.string.display_banner_subtitle;
    }

    @Override
    protected boolean isBannerClickable() {
        return false;
    }
}
