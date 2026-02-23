/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.flags.Flags;
import com.android.settingslib.widget.SettingsThemeHelper;

/** Helper for homepage preference to manage layout with visual effects. */
public class HomepagePreferenceLayoutHelper {

    private View mIcon;
    private View mText;
    private View mAlertFrame;
    private View mAlertUnnumbered;
    private View mAlertNumberedFrame;
    private TextView mAlertNumberText;
    private View mShimmerLayer;
    private View mIconGlow;
    private boolean mIconVisible = true;
    private int mIconPaddingStart = -1;
    private int mTextPaddingStart = -1;
    private int mAlertValue = -1;
    
    // Shimmer animation
    private Handler mShimmerHandler;
    private Runnable mShimmerRunnable;
    private boolean mShimmerRunning = false;

    /** The interface for managing preference layouts on homepage */
    public interface HomepagePreferenceLayout {
        /** Returns a {@link HomepagePreferenceLayoutHelper}  */
        HomepagePreferenceLayoutHelper getHelper();
    }

    public HomepagePreferenceLayoutHelper(Preference preference) {
        preference.setLayoutResource(
                SettingsThemeHelper.isExpressiveTheme(preference.getContext())
                        ? R.layout.homepage_preference_expressive
                        : R.layout.homepage_preference);
        mShimmerHandler = new Handler(Looper.getMainLooper());
    }

    /** Sets whether the icon should be visible */
    public void setIconVisible(boolean visible) {
        mIconVisible = visible;
        if (mIcon != null) {
            mIcon.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** Sets the icon padding start */
    public void setIconPaddingStart(int paddingStart) {
        mIconPaddingStart = paddingStart;
        if (mIcon != null && paddingStart >= 0) {
            mIcon.setPaddingRelative(paddingStart, mIcon.getPaddingTop(), mIcon.getPaddingEnd(),
                    mIcon.getPaddingBottom());
        }
    }

    /** Sets the text padding start */
    public void setTextPaddingStart(int paddingStart) {
        mTextPaddingStart = paddingStart;
        if (mText != null && paddingStart >= 0) {
            mText.setPaddingRelative(paddingStart, mText.getPaddingTop(), mText.getPaddingEnd(),
                    mText.getPaddingBottom());
        }
    }

    /** Sets the alert value and view */
    public void setAlert(int value) {
        if (Flags.homepageTileAlert()) {
            mAlertValue = value;
            if (mAlertFrame != null && mAlertUnnumbered != null
                    && mAlertNumberedFrame != null && mAlertNumberText != null) {
                mAlertFrame.setVisibility((value > 0) ? View.VISIBLE : View.GONE);
                // only display number if it's single digit, more than 1
                if (value == 1 || value > 9) {
                    mAlertNumberedFrame.setVisibility(View.GONE);
                    mAlertUnnumbered.setVisibility(View.VISIBLE);
                    mAlertFrame.setContentDescription(mAlertFrame.getResources()
                            .getString(R.string.homepage_unnumbered_alert_description));
                } else if (value > 1) {
                    mAlertUnnumbered.setVisibility(View.GONE);
                    mAlertNumberedFrame.setVisibility(View.VISIBLE);
                    mAlertNumberText.setVisibility(View.VISIBLE);
                    mAlertNumberText.setText(String.valueOf(value));
                    mAlertFrame.setContentDescription(mAlertFrame.getResources()
                            .getString(R.string.homepage_numbered_alert_description, value));
                }
            }
        }
    }

    void onBindViewHolder(PreferenceViewHolder holder) {
        mIcon = holder.findViewById(R.id.icon_frame);
        mText = holder.findViewById(R.id.text_frame);
        mAlertFrame = holder.findViewById(R.id.alert_frame);
        mAlertUnnumbered = holder.findViewById(R.id.alert_unnumbered);
        mAlertNumberedFrame = holder.findViewById(R.id.alert_numbered_frame);
        mAlertNumberText = (TextView) holder.findViewById(R.id.alert_number_fg);
        mShimmerLayer = holder.findViewById(R.id.shimmer_layer);
        mIconGlow = holder.findViewById(R.id.icon_glow);
        
        setIconVisible(mIconVisible);
        setIconPaddingStart(mIconPaddingStart);
        setTextPaddingStart(mTextPaddingStart);
        setAlert(mAlertValue);
        
        // Start shimmer animation
        startShimmerAnimation();
    }

    private void startShimmerAnimation() {
        if (mShimmerRunning || mShimmerLayer == null) {
            return;
        }

        mShimmerRunning = true;
        Animation shimmerAnim = AnimationUtils.loadAnimation(
                mShimmerLayer.getContext(), R.anim.shimmer_animation);
        
        mShimmerRunnable = new Runnable() {
            @Override
            public void run() {
                if (mShimmerLayer != null && mShimmerRunning) {
                    mShimmerLayer.startAnimation(shimmerAnim);
                    mShimmerHandler.postDelayed(this, 3500);
                }
            }
        };
        
        // Stagger the start of shimmer for each item
        mShimmerHandler.postDelayed(mShimmerRunnable, (long) (Math.random() * 2000));
    }

    /**
     * Stop shimmer animation when the view is recycled
     */
    public void stopShimmerAnimation() {
        mShimmerRunning = false;
        if (mShimmerHandler != null && mShimmerRunnable != null) {
            mShimmerHandler.removeCallbacks(mShimmerRunnable);
        }
        if (mShimmerLayer != null) {
            mShimmerLayer.clearAnimation();
        }
    }
}
