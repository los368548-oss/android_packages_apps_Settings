/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.settings.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnPause;
import com.android.settingslib.core.lifecycle.events.OnResume;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Controller for displaying real-time network speed in settings.
 * Shows download/upload speeds and data usage statistics.
 */
public class NetworkSpeedMonitorController extends BasePreferenceController
        implements LifecycleObserver, OnResume, OnPause {

    private static final String TAG = "NetworkSpeedMonitor";
    private static final long UPDATE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long HISTORY_DURATION_MS = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_HISTORY_SIZE = 300; // 5 minutes at 1 second intervals

    private final ConnectivityManager mConnectivityManager;
    private final Handler mHandler;
    private final List<NetworkSpeedSample> mSpeedHistory;
    private final Object mHistoryLock = new Object();

    private Preference mPreference;
    private long mLastRxBytes;
    private long mLastTxBytes;
    private long mLastTimestamp;
    private boolean mIsMonitoring;
    private Network mCurrentNetwork;

    private final Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsMonitoring) {
                return;
            }
            updateNetworkSpeed();
            mHandler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };

    public NetworkSpeedMonitorController(@NonNull Context context, @NonNull String preferenceKey) {
        super(context, preferenceKey);
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mHandler = new Handler(Looper.getMainLooper());
        mSpeedHistory = new ArrayList<>();
        initializeStats();
    }

    private void initializeStats() {
        mLastRxBytes = TrafficStats.getTotalRxBytes();
        mLastTxBytes = TrafficStats.getTotalTxBytes();
        mLastTimestamp = SystemClock.elapsedRealtime();
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(@NonNull PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onResume() {
        mIsMonitoring = true;
        initializeStats();
        mHandler.post(mUpdateRunnable);
    }

    @Override
    public void onPause() {
        mIsMonitoring = false;
        mHandler.removeCallbacks(mUpdateRunnable);
    }

    private void updateNetworkSpeed() {
        final long currentRxBytes = TrafficStats.getTotalRxBytes();
        final long currentTxBytes = TrafficStats.getTotalTxBytes();
        final long currentTimestamp = SystemClock.elapsedRealtime();

        final long timeDelta = currentTimestamp - mLastTimestamp;
        if (timeDelta <= 0) {
            return;
        }

        final long rxSpeed = ((currentRxBytes - mLastRxBytes) * 1000) / timeDelta;
        final long txSpeed = ((currentTxBytes - mLastTxBytes) * 1000) / timeDelta;

        // Store sample in history
        synchronized (mHistoryLock) {
            mSpeedHistory.add(new NetworkSpeedSample(currentTimestamp, rxSpeed, txSpeed));
            trimHistory();
        }

        // Update UI
        updatePreferenceDisplay(rxSpeed, txSpeed);

        // Store current values for next calculation
        mLastRxBytes = currentRxBytes;
        mLastTxBytes = currentTxBytes;
        mLastTimestamp = currentTimestamp;
    }

    private void trimHistory() {
        final long cutoffTime = SystemClock.elapsedRealtime() - HISTORY_DURATION_MS;
        while (mSpeedHistory.size() > MAX_HISTORY_SIZE ||
                (!mSpeedHistory.isEmpty() && mSpeedHistory.get(0).timestamp < cutoffTime)) {
            mSpeedHistory.remove(0);
        }
    }

    private void updatePreferenceDisplay(long rxSpeed, long txSpeed) {
        if (mPreference == null) {
            return;
        }

        final String downloadSpeed = formatSpeed(rxSpeed);
        final String uploadSpeed = formatSpeed(txSpeed);
        final String summary = mContext.getString(
                com.android.settings.R.string.network_speed_format,
                downloadSpeed, uploadSpeed);

        mPreference.setSummary(summary);

        // Update title with connection type
        final String connectionType = getConnectionType();
        mPreference.setTitle(mContext.getString(
                com.android.settings.R.string.network_speed_title_format, connectionType));
    }

    @NonNull
    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 0) {
            return "0 B/s";
        }
        return Formatter.formatFileSize(mContext, bytesPerSecond).replace(" ", "") + "/s";
    }

    @NonNull
    private String getConnectionType() {
        if (mConnectivityManager == null) {
            return mContext.getString(com.android.settings.R.string.network_type_unknown);
        }

        mCurrentNetwork = mConnectivityManager.getActiveNetwork();
        if (mCurrentNetwork == null) {
            return mContext.getString(com.android.settings.R.string.network_type_disconnected);
        }

        final NetworkCapabilities caps = mConnectivityManager.getNetworkCapabilities(mCurrentNetwork);
        if (caps == null) {
            return mContext.getString(com.android.settings.R.string.network_type_unknown);
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            final int wifiSpeed = caps.getLinkDownstreamBandwidthKbps();
            return mContext.getString(com.android.settings.R.string.network_type_wifi) +
                    " (" + formatWifiSpeed(wifiSpeed) + ")";
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return getCellularType(caps);
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return mContext.getString(com.android.settings.R.string.network_type_ethernet);
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return mContext.getString(com.android.settings.R.string.network_type_bluetooth);
        }

        return mContext.getString(com.android.settings.R.string.network_type_other);
    }

    @NonNull
    private String formatWifiSpeed(int kbps) {
        if (kbps >= 1000000) {
            return String.format("%.1f Gbps", kbps / 1000000.0);
        } else if (kbps >= 1000) {
            return String.format("%.0f Mbps", kbps / 1000.0);
        }
        return kbps + " Kbps";
    }

    @NonNull
    private String getCellularType(@NonNull NetworkCapabilities caps) {
        // Check for 5G
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_MMTEL)) {
            return mContext.getString(com.android.settings.R.string.network_type_5g);
        }

        // Check link speed for 4G/LTE/3G estimation
        final int downSpeed = caps.getLinkDownstreamBandwidthKbps();
        if (downSpeed >= 100000) { // 100 Mbps+
            return mContext.getString(com.android.settings.R.string.network_type_4g_plus);
        } else if (downSpeed >= 10000) { // 10 Mbps+
            return mContext.getString(com.android.settings.R.string.network_type_4g);
        } else if (downSpeed >= 1000) { // 1 Mbps+
            return mContext.getString(com.android.settings.R.string.network_type_3g);
        } else {
            return mContext.getString(com.android.settings.R.string.network_type_2g);
        }
    }

    /**
     * Get average download speed over the specified duration.
     *
     * @param durationMs Duration in milliseconds
     * @return Average download speed in bytes per second, or -1 if no data available
     */
    public long getAverageDownloadSpeed(long durationMs) {
        return calculateAverageSpeed(durationMs, true);
    }

    /**
     * Get average upload speed over the specified duration.
     *
     * @param durationMs Duration in milliseconds
     * @return Average upload speed in bytes per second, or -1 if no data available
     */
    public long getAverageUploadSpeed(long durationMs) {
        return calculateAverageSpeed(durationMs, false);
    }

    private long calculateAverageSpeed(long durationMs, boolean isDownload) {
        synchronized (mHistoryLock) {
            if (mSpeedHistory.isEmpty()) {
                return -1;
            }

            final long cutoffTime = SystemClock.elapsedRealtime() - durationMs;
            long totalSpeed = 0;
            int count = 0;

            for (int i = mSpeedHistory.size() - 1; i >= 0; i--) {
                final NetworkSpeedSample sample = mSpeedHistory.get(i);
                if (sample.timestamp < cutoffTime) {
                    break;
                }
                totalSpeed += isDownload ? sample.rxSpeed : sample.txSpeed;
                count++;
            }

            return count > 0 ? totalSpeed / count : -1;
        }
    }

    /**
     * Get peak download speed from history.
     *
     * @return Peak download speed in bytes per second
     */
    public long getPeakDownloadSpeed() {
        synchronized (mHistoryLock) {
            long peak = 0;
            for (NetworkSpeedSample sample : mSpeedHistory) {
                if (sample.rxSpeed > peak) {
                    peak = sample.rxSpeed;
                }
            }
            return peak;
        }
    }

    /**
     * Get peak upload speed from history.
     *
     * @return Peak upload speed in bytes per second
     */
    public long getPeakUploadSpeed() {
        synchronized (mHistoryLock) {
            long peak = 0;
            for (NetworkSpeedSample sample : mSpeedHistory) {
                if (sample.txSpeed > peak) {
                    peak = sample.txSpeed;
                }
            }
            return peak;
        }
    }

    /**
     * Get total data transferred since monitoring started.
     *
     * @return DataUsage object containing total rx and tx bytes
     */
    @NonNull
    public DataUsage getTotalDataUsage() {
        return new DataUsage(mLastRxBytes, mLastTxBytes);
    }

    /**
     * Clear speed history.
     */
    public void clearHistory() {
        synchronized (mHistoryLock) {
            mSpeedHistory.clear();
        }
    }

    /**
     * Check if currently connected to a network.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        if (mConnectivityManager == null) {
            return false;
        }
        final Network activeNetwork = mConnectivityManager.getActiveNetwork();
        return activeNetwork != null;
    }

    /**
     * Check if current connection is unmetered (e.g., WiFi).
     *
     * @return true if unmetered
     */
    public boolean isUnmetered() {
        if (mConnectivityManager == null) {
            return false;
        }
        final Network activeNetwork = mConnectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }
        final NetworkCapabilities caps = mConnectivityManager.getNetworkCapabilities(activeNetwork);
        return caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    /**
     * Data class representing a network speed sample at a point in time.
     */
    private static class NetworkSpeedSample {
        final long timestamp;
        final long rxSpeed;  // bytes per second
        final long txSpeed;  // bytes per second

        NetworkSpeedSample(long timestamp, long rxSpeed, long txSpeed) {
            this.timestamp = timestamp;
            this.rxSpeed = rxSpeed;
            this.txSpeed = txSpeed;
        }
    }

    /**
     * Data class representing total data usage.
     */
    public static class DataUsage {
        public final long rxBytes;
        public final long txBytes;

        public DataUsage(long rxBytes, long txBytes) {
            this.rxBytes = rxBytes;
            this.txBytes = txBytes;
        }

        @NonNull
        @Override
        public String toString() {
            return "DataUsage{rx=" + rxBytes + ", tx=" + txBytes + "}";
        }
    }
}
