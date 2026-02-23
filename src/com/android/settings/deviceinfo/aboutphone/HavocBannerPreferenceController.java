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
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.widget.LayoutPreference;

/**
 * Controller for the HavocOOS banner in About Phone.
 * Shows the HavocOOS version and links to the updater.
 * Features enhanced glassmorphic design with shimmer animation.
 */
public class HavocBannerPreferenceController extends BasePreferenceController {

    private static final String KEY_HAVOC_BANNER = "havoc_banner";
    private static final String PROP_HAVOC_VERSION = "ro.havoc.build.version";
    private static final String PROP_HAVOC_RELEASE = "ro.havoc.build.version.release";
    private static final String PROP_HAVOC_CODENAME = "ro.havoc.build.codename";
    
    // Updater package and activity
    private static final String UPDATER_PACKAGE = "org.havocos.updater";
    private static final String UPDATER_ACTIVITY = UPDATER_PACKAGE + ".UpdatesActivity";

    private LayoutPreference mPreference;
    private View mShimmerLayer;
    private Handler mShimmerHandler;
    private Runnable mShimmerRunnable;
    private boolean mShimmerRunning = false;

    public HavocBannerPreferenceController(Context context) {
        super(context, KEY_HAVOC_BANNER);
        mShimmerHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(KEY_HAVOC_BANNER);
        if (mPreference != null) {
            setupBanner();
        }
    }

    private void setupBanner() {
        // Set version text
        TextView versionView = mPreference.findViewById(R.id.havoc_version);
        if (versionView != null) {
            String version = getHavocVersion();
            versionView.setText(version);
        }

        // Set up click listener to open updater
        View bannerContainer = mPreference.findViewById(R.id.banner_container);
        if (bannerContainer != null) {
            bannerContainer.setOnClickListener(v -> launchUpdater());
        }

        // Setup shimmer animation
        mShimmerLayer = mPreference.findViewById(R.id.shimmer_layer);
        if (mShimmerLayer != null) {
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

    private void stopShimmerAnimation() {
        mShimmerRunning = false;
        if (mShimmerHandler != null && mShimmerRunnable != null) {
            mShimmerHandler.removeCallbacks(mShimmerRunnable);
        }
        if (mShimmerLayer != null) {
            mShimmerLayer.clearAnimation();
        }
    }

    private String getHavocVersion() {
        String version = SystemProperties.get(PROP_HAVOC_VERSION, "");
        String release = SystemProperties.get(PROP_HAVOC_RELEASE, "");
        String codename = SystemProperties.get(PROP_HAVOC_CODENAME, "");
        
        StringBuilder sb = new StringBuilder();
        if (!version.isEmpty()) {
            sb.append("HavocOOS ").append(version);
        } else if (!release.isEmpty()) {
            sb.append("HavocOOS ").append(release);
        } else {
            sb.append("HavocOOS");
        }
        
        if (!codename.isEmpty()) {
            sb.append(" | ").append(codename);
        }
        
        return sb.toString();
    }

    private void launchUpdater() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(UPDATER_PACKAGE, UPDATER_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        } catch (Exception e) {
            // Updater not available, try alternative
            try {
                Intent intent = mContext.getPackageManager()
                        .getLaunchIntentForPackage(UPDATER_PACKAGE);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
                }
            } catch (Exception ex) {
                // Ignore if updater is not installed
            }
        }
    }

    @Override
    public boolean isSliceable() {
        return false;
    }
}
