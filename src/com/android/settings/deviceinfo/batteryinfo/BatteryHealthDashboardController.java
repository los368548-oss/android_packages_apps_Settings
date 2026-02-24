/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.settings.deviceinfo.batteryinfo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Controller for the Battery Health Dashboard preference.
 * Displays comprehensive battery health information including:
 * - Battery cycle count
 * - Current capacity vs design capacity
 * - Battery health percentage
 * - Temperature monitoring
 * - Charging speed analysis
 * - Battery age estimation
 */
public class BatteryHealthDashboardController extends BasePreferenceController
        implements LifecycleObserver, OnStart, OnStop {

    private static final String TAG = "BatteryHealthDashboard";
    private static final String KEY_BATTERY_HEALTH_DASHBOARD = "battery_health_dashboard";

    // Battery health thresholds
    private static final int HEALTH_EXCELLENT = 90;
    private static final int HEALTH_GOOD = 80;
    private static final int HEALTH_FAIR = 70;
    private static final int HEALTH_POOR = 60;

    // Update interval for real-time data (30 seconds)
    private static final long UPDATE_INTERVAL_MS = 30_000L;

    private BatteryManager mBatteryManager;
    private Preference mPreference;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsRegistered = false;

    // Cached battery data
    private int mCycleCount = -1;
    private long mDesignCapacity = -1;
    private long mCurrentCapacity = -1;
    private int mHealthPercentage = -1;
    private float mTemperature = -1;
    private int mChargeCounter = -1;
    private String mBatteryTechnology = "";
    private String mManufactureDate = "";

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                updateBatteryData(intent);
                refreshUi();
            }
        }
    };

    private final Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateBatteryStats();
            refreshUi();
            mHandler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    public BatteryHealthDashboardController(@NonNull Context context) {
        super(context, KEY_BATTERY_HEALTH_DASHBOARD);
        mBatteryManager = context.getSystemService(BatteryManager.class);
        loadBatteryInfo();
    }

    @Override
    public int getAvailabilityStatus() {
        return hasBattery() ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(@NonNull PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onStart() {
        registerReceiver();
        startPeriodicUpdate();
    }

    @Override
    public void onStop() {
        unregisterReceiver();
        stopPeriodicUpdate();
    }

    @Override
    public CharSequence getSummary() {
        if (mHealthPercentage < 0) {
            return mContext.getString(R.string.battery_health_calculating);
        }

        String healthStatus = getHealthStatusString();
        return mContext.getString(R.string.battery_health_summary,
                mHealthPercentage, healthStatus);
    }

    /**
     * Get detailed battery health information.
     *
     * @return Bundle containing all battery health data
     */
    @NonNull
    public Bundle getBatteryHealthDetails() {
        Bundle details = new Bundle();

        // Basic health info
        details.putInt("cycle_count", mCycleCount);
        details.putInt("health_percentage", mHealthPercentage);
        details.putLong("design_capacity_mah", mDesignCapacity);
        details.putLong("current_capacity_mah", mCurrentCapacity);
        details.putFloat("temperature_celsius", mTemperature);
        details.putString("technology", mBatteryTechnology);
        details.putString("manufacture_date", mManufactureDate);

        // Calculated metrics
        details.putFloat("capacity_degradation", calculateCapacityDegradation());
        details.putString("estimated_remaining_life", estimateRemainingLife());
        details.putString("health_status", getHealthStatusString());
        details.putInt("health_status_resid", getHealthStatusResId());

        // Charging info
        details.putBoolean("is_charging", isCharging());
        details.putInt("charge_speed", getChargeSpeed());
        details.putString("charge_speed_description", getChargeSpeedDescription());

        return details;
    }

    /**
     * Get the battery cycle count.
     *
     * @return Cycle count or -1 if unavailable
     */
    public int getCycleCount() {
        return mCycleCount;
    }

    /**
     * Get the battery health percentage.
     *
     * @return Health percentage (0-100) or -1 if unavailable
     */
    public int getHealthPercentage() {
        return mHealthPercentage;
    }

    /**
     * Get the current battery temperature.
     *
     * @return Temperature in Celsius or -1 if unavailable
     */
    public float getTemperature() {
        return mTemperature;
    }

    /**
     * Get the design capacity of the battery.
     *
     * @return Design capacity in mAh or -1 if unavailable
     */
    public long getDesignCapacity() {
        return mDesignCapacity;
    }

    /**
     * Get the current estimated capacity of the battery.
     *
     * @return Current capacity in mAh or -1 if unavailable
     */
    public long getCurrentCapacity() {
        return mCurrentCapacity;
    }

    /**
     * Check if the battery is currently charging.
     *
     * @return true if charging
     */
    public boolean isCharging() {
        if (mBatteryManager == null) return false;
        return mBatteryManager.isCharging();
    }

    /**
     * Get the current charge speed level.
     *
     * @return Charge speed (0=none, 1=slow, 2=normal, 3=fast, 4=super fast)
     */
    public int getChargeSpeed() {
        if (!isCharging()) return 0;

        if (mBatteryManager != null) {
            int chargeCounter = mBatteryManager.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            int currentNow = mBatteryManager.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);

            // Estimate charge speed based on current
            if (currentNow > 5000000) return 4; // Super fast (>5A)
            if (currentNow > 3000000) return 3; // Fast (>3A)
            if (currentNow > 1000000) return 2; // Normal (>1A)
            return 1; // Slow
        }
        return 1;
    }

    /**
     * Get a description of the current charge speed.
     *
     * @return Charge speed description string
     */
    @NonNull
    public String getChargeSpeedDescription() {
        int speed = getChargeSpeed();
        switch (speed) {
            case 4:
                return mContext.getString(R.string.battery_charge_speed_super_fast);
            case 3:
                return mContext.getString(R.string.battery_charge_speed_fast);
            case 2:
                return mContext.getString(R.string.battery_charge_speed_normal);
            case 1:
                return mContext.getString(R.string.battery_charge_speed_slow);
            default:
                return mContext.getString(R.string.battery_charge_speed_none);
        }
    }

    /**
     * Get battery health recommendations.
     *
     * @return Array of recommendation strings
     */
    @NonNull
    public String[] getHealthRecommendations() {
        java.util.List<String> recommendations = new java.util.ArrayList<>();

        if (mHealthPercentage < HEALTH_GOOD) {
            recommendations.add(mContext.getString(R.string.battery_recommend_replace));
        }

        if (mCycleCount > 500) {
            recommendations.add(mContext.getString(R.string.battery_recommend_cycle_limit));
        }

        if (mTemperature > 40) {
            recommendations.add(mContext.getString(R.string.battery_recommend_temperature));
        }

        if (isCharging() && getChargeSpeed() >= 3) {
            recommendations.add(mContext.getString(R.string.battery_recommend_fast_charge));
        }

        if (recommendations.isEmpty()) {
            recommendations.add(mContext.getString(R.string.battery_recommend_good));
        }

        return recommendations.toArray(new String[0]);
    }

    private boolean hasBattery() {
        // All Android devices have a battery, so return true
        // FEATURE_BATTERY is not a standard PackageManager feature
        return true;
    }

    private void loadBatteryInfo() {
        // Get initial battery data
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = mContext.registerReceiver(null, filter);
        if (batteryStatus != null) {
            updateBatteryData(batteryStatus);
        }

        // Load design capacity from system
        mDesignCapacity = getDesignCapacityFromSystem();

        // Load cycle count from system
        mCycleCount = getCycleCountFromSystem();

        // Calculate health percentage
        calculateHealthPercentage();
    }

    private void updateBatteryData(@NonNull Intent intent) {
        // Temperature (convert from tenths of degree Celsius)
        mTemperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0f;

        // Technology
        mBatteryTechnology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        if (mBatteryTechnology == null) {
            mBatteryTechnology = "Unknown";
        }

        // Charge counter (current charge level in microampere-hours)
        mChargeCounter = intent.getIntExtra(BatteryManager.EXTRA_CHARGE_COUNTER, -1);

        // Update current capacity estimate
        if (mChargeCounter > 0 && mDesignCapacity > 0) {
            mCurrentCapacity = (mChargeCounter * mDesignCapacity) / 100;
        }
    }

    private void updateBatteryStats() {
        // Refresh cycle count
        mCycleCount = getCycleCountFromSystem();

        // Recalculate health
        calculateHealthPercentage();
    }

    private long getDesignCapacityFromSystem() {
        // Try to get from BatteryManager
        if (mBatteryManager != null) {
            long capacity = mBatteryManager.getLongProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (capacity > 0) {
                // This is percentage, need actual capacity from system
            }
        }

        // Read from system file (common locations)
        String[] capacityPaths = {
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/bms/charge_full_design",
            "/sys/class/power_supply/main/charge_full_design"
        };

        for (String path : capacityPaths) {
            try {
                java.io.File file = new java.io.File(path);
                if (file.exists()) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.FileReader(file));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null) {
                        // Value is typically in microampere-hours
                        return Long.parseLong(line.trim()) / 1000;
                    }
                }
            } catch (Exception e) {
                // Ignore and try next path
            }
        }

        // Default fallback (typical smartphone battery)
        return 4000; // 4000 mAh
    }

    private int getCycleCountFromSystem() {
        String[] cycleCountPaths = {
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/bms/cycle_count",
            "/sys/class/power_supply/main/cycle_count"
        };

        for (String path : cycleCountPaths) {
            try {
                java.io.File file = new java.io.File(path);
                if (file.exists()) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.FileReader(file));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null) {
                        return Integer.parseInt(line.trim());
                    }
                }
            } catch (Exception e) {
                // Ignore and try next path
            }
        }

        return -1; // Unavailable
    }

    private void calculateHealthPercentage() {
        if (mCurrentCapacity > 0 && mDesignCapacity > 0) {
            mHealthPercentage = (int) ((mCurrentCapacity * 100) / mDesignCapacity);
        } else if (mBatteryManager != null) {
            // Fallback: use BatteryManager health property
            int health = mBatteryManager.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_STATUS);
            // This doesn't give percentage, so estimate based on cycle count
            if (mCycleCount > 0) {
                // Typical battery degrades ~20% after 500 cycles
                float degradation = Math.min(0.5f, mCycleCount / 2500.0f);
                mHealthPercentage = (int) ((1 - degradation) * 100);
            } else {
                mHealthPercentage = 100; // Assume good if no data
            }
        }
    }

    private float calculateCapacityDegradation() {
        if (mDesignCapacity <= 0 || mCurrentCapacity <= 0) {
            return 0;
        }
        return ((mDesignCapacity - mCurrentCapacity) * 100.0f) / mDesignCapacity;
    }

    private String estimateRemainingLife() {
        if (mHealthPercentage < 0) {
            return mContext.getString(R.string.battery_life_unknown);
        }

        // Estimate remaining useful life based on health
        if (mHealthPercentage >= HEALTH_EXCELLENT) {
            return mContext.getString(R.string.battery_life_excellent);
        } else if (mHealthPercentage >= HEALTH_GOOD) {
            return mContext.getString(R.string.battery_life_good);
        } else if (mHealthPercentage >= HEALTH_FAIR) {
            return mContext.getString(R.string.battery_life_fair);
        } else if (mHealthPercentage >= HEALTH_POOR) {
            return mContext.getString(R.string.battery_life_poor);
        } else {
            return mContext.getString(R.string.battery_life_replace);
        }
    }

    private String getHealthStatusString() {
        return mContext.getString(getHealthStatusResId());
    }

    private int getHealthStatusResId() {
        if (mHealthPercentage >= HEALTH_EXCELLENT) {
            return R.string.battery_health_status_excellent;
        } else if (mHealthPercentage >= HEALTH_GOOD) {
            return R.string.battery_health_status_good;
        } else if (mHealthPercentage >= HEALTH_FAIR) {
            return R.string.battery_health_status_fair;
        } else if (mHealthPercentage >= HEALTH_POOR) {
            return R.string.battery_health_status_poor;
        } else {
            return R.string.battery_health_status_replace;
        }
    }

    private void registerReceiver() {
        if (!mIsRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            mContext.registerReceiver(mBatteryReceiver, filter);
            mIsRegistered = true;
        }
    }

    private void unregisterReceiver() {
        if (mIsRegistered) {
            mContext.unregisterReceiver(mBatteryReceiver);
            mIsRegistered = false;
        }
    }

    private void startPeriodicUpdate() {
        mHandler.postDelayed(mUpdateRunnable, UPDATE_INTERVAL_MS);
    }

    private void stopPeriodicUpdate() {
        mHandler.removeCallbacks(mUpdateRunnable);
    }

    private void refreshUi() {
        if (mPreference != null) {
            mPreference.setSummary(getSummary());
        }
    }
}
