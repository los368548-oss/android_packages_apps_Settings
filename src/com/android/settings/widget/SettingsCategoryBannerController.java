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

package com.android.settings.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.widget.LayoutPreference;

/**
 * Base controller for settings category banners.
 * Provides shimmer animation and common banner functionality.
 */
public abstract class SettingsCategoryBannerController extends BasePreferenceController {

    private LayoutPreference mPreference;
    private View mShimmerLayer;
    private Handler mShimmerHandler;
    private Runnable mShimmerRunnable;
    private boolean mShimmerRunning = false;

    public SettingsCategoryBannerController(Context context, String key) {
        super(context, key);
        mShimmerHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        if (mPreference != null) {
            setupBanner();
        }
    }

    protected void setupBanner() {
        // Set icon
        ImageView iconView = mPreference.findViewById(R.id.banner_icon);
        if (iconView != null) {
            iconView.setImageResource(getBannerIconRes());
        }

        // Set title
        TextView titleView = mPreference.findViewById(R.id.banner_title);
        if (titleView != null) {
            titleView.setText(getBannerTitleRes());
        }

        // Set subtitle
        TextView subtitleView = mPreference.findViewById(R.id.banner_subtitle);
        if (subtitleView != null) {
            subtitleView.setText(getBannerSubtitleRes());
        }

        // Setup click listener if clickable
        View bannerContainer = mPreference.findViewById(R.id.banner_container);
        if (bannerContainer != null && isBannerClickable()) {
            bannerContainer.setOnClickListener(v -> onBannerClick());
        }

        // Setup shimmer animation
        mShimmerLayer = mPreference.findViewById(R.id.shimmer_layer);
        if (mShimmerLayer != null && shouldShowShimmer()) {
            startShimmerAnimation();
        }
    }

    private void startShimmerAnimation() {
        if (mShimmerRunning || mShimmerLayer == null) {
            return;
        }

        mShimmerRunning = true;
        Animation shimmerAnim = AnimationUtils.loadAnimation(mContext, R.anim.shimmer_animation);
        
        mShimmerRunnable = new Runnable() {
            @Override
            public void run() {
                if (mShimmerLayer != null && mShimmerRunning) {
                    mShimmerLayer.startAnimation(shimmerAnim);
                    mShimmerHandler.postDelayed(this, 3000);
                }
            }
        };
        
        mShimmerHandler.post(mShimmerRunnable);
    }

    protected void stopShimmerAnimation() {
        mShimmerRunning = false;
        if (mShimmerHandler != null && mShimmerRunnable != null) {
            mShimmerHandler.removeCallbacks(mShimmerRunnable);
        }
        if (mShimmerLayer != null) {
            mShimmerLayer.clearAnimation();
        }
    }

    /**
     * @return The drawable resource ID for the banner icon
     */
    protected abstract int getBannerIconRes();

    /**
     * @return The string resource ID for the banner title
     */
    protected abstract int getBannerTitleRes();

    /**
     * @return The string resource ID for the banner subtitle
     */
    protected abstract int getBannerSubtitleRes();

    /**
     * @return true if the banner should be clickable
     */
    protected boolean isBannerClickable() {
        return false;
    }

    /**
     * @return true if shimmer animation should be shown
     */
    protected boolean shouldShowShimmer() {
        return true;
    }

    /**
     * Called when the banner is clicked
     */
    protected void onBannerClick() {
        // Override in subclass if clickable
    }

    @Override
    public boolean isSliceable() {
        return false;
    }
}
