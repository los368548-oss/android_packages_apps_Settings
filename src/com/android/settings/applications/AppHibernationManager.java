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

package com.android.settings.applications;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manager for app hibernation functionality.
 * Provides intelligent app hibernation based on usage patterns to save battery and resources.
 */
public class AppHibernationManager {

    private static final String TAG = "AppHibernationManager";

    // Thresholds for hibernation recommendations
    private static final long UNUSED_APP_THRESHOLD_MS = TimeUnit.DAYS.toMillis(30); // 30 days
    private static final long RARELY_USED_THRESHOLD_MS = TimeUnit.DAYS.toMillis(14); // 14 days
    private static final long HIBERNATION_CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(6);

    private final Context mContext;
    private final PackageManager mPackageManager;
    private final ActivityManager mActivityManager;
    private final UsageStatsManager mUsageStatsManager;
    private final PowerManager mPowerManager;
    private final AppOpsManager mAppOpsManager;
    private final UserManager mUserManager;

    private List<HibernationCandidate> mHibernationCandidates;
    private long mLastAnalysisTime;

    public AppHibernationManager(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mPackageManager = context.getPackageManager();
        mActivityManager = context.getSystemService(ActivityManager.class);
        mUsageStatsManager = context.getSystemService(UsageStatsManager.class);
        mPowerManager = context.getSystemService(PowerManager.class);
        mAppOpsManager = context.getSystemService(AppOpsManager.class);
        mUserManager = context.getSystemService(UserManager.class);
        mHibernationCandidates = new ArrayList<>();
    }

    /**
     * Analyze apps and generate hibernation candidates.
     *
     * @return List of apps that can be hibernated
     */
    @NonNull
    public List<HibernationCandidate> analyzeAppsForHibernation() {
        mHibernationCandidates.clear();

        // Get usage stats for the last 30 days
        final Calendar calendar = Calendar.getInstance();
        final long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_YEAR, -30);
        final long startTime = calendar.getTimeInMillis();

        final Map<String, UsageStats> usageStatsMap = mUsageStatsManager.queryAndAggregateUsageStats(
                startTime, endTime);

        // Get all installed apps
        final List<ApplicationInfo> installedApps = mPackageManager.getInstalledApplications(
                PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : installedApps) {
            // Skip system apps and essential apps
            if (shouldSkipApp(appInfo)) {
                continue;
            }

            final String packageName = appInfo.packageName;
            final UsageStats stats = usageStatsMap.get(packageName);

            // Calculate usage metrics
            final long lastUsedTime = stats != null ? stats.getLastTimeUsed() : 0;
            final long totalUsedTime = stats != null ? stats.getTotalTimeInForeground() : 0;
            final int launchCount = stats != null ? stats.getAppLaunchCount() : 0;

            final long timeSinceLastUse = System.currentTimeMillis() - lastUsedTime;

            // Determine hibernation eligibility
            final HibernationEligibility eligibility = determineEligibility(
                    appInfo, timeSinceLastUse, launchCount, totalUsedTime);

            if (eligibility != HibernationEligibility.NOT_ELIGIBLE) {
                final HibernationCandidate candidate = new HibernationCandidate(
                        packageName,
                        appInfo.loadLabel(mPackageManager).toString(),
                        appInfo.loadIcon(mPackageManager),
                        eligibility,
                        timeSinceLastUse,
                        totalUsedTime,
                        launchCount,
                        calculatePotentialSavings(appInfo),
                        isCurrentlyHibernated(packageName)
                );
                mHibernationCandidates.add(candidate);
            }
        }

        // Sort by eligibility priority and time since last use
        Collections.sort(mHibernationCandidates, (a, b) -> {
            // Prioritize unused apps
            final int eligibilityCompare = Integer.compare(
                    b.getEligibility().getPriority(), a.getEligibility().getPriority());
            if (eligibilityCompare != 0) {
                return eligibilityCompare;
            }
            // Then by time since last use (longer time first)
            return Long.compare(b.getTimeSinceLastUse(), a.getTimeSinceLastUse());
        });

        mLastAnalysisTime = System.currentTimeMillis();
        return new ArrayList<>(mHibernationCandidates);
    }

    private boolean shouldSkipApp(@NonNull ApplicationInfo appInfo) {
        // Skip if it's a system app without update
        if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
            return true;
        }

        // Skip if it's the current app
        if (appInfo.packageName.equals(mContext.getPackageName())) {
            return true;
        }

        // Skip essential system packages
        if (appInfo.packageName.startsWith("com.android.")
                || appInfo.packageName.startsWith("android")) {
            return true;
        }

        // Skip launcher apps
        final Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);
        final List<ResolveInfo> launchers = mPackageManager.queryIntentActivities(
                launcherIntent, 0);
        for (ResolveInfo resolveInfo : launchers) {
            if (resolveInfo.activityInfo.packageName.equals(appInfo.packageName)) {
                return true;
            }
        }

        // Skip input method apps
        if (isInputMethodApp(appInfo.packageName)) {
            return true;
        }

        // Skip accessibility services
        if (isAccessibilityService(appInfo.packageName)) {
            return true;
        }

        // Skip device admin apps
        if (isDeviceAdminApp(appInfo.packageName)) {
            return true;
        }

        return false;
    }

    private boolean isInputMethodApp(@NonNull String packageName) {
        final Intent intent = new Intent("android.view.InputMethod");
        final List<ResolveInfo> services = mPackageManager.queryIntentServices(intent, 0);
        for (ResolveInfo resolveInfo : services) {
            if (resolveInfo.serviceInfo.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAccessibilityService(@NonNull String packageName) {
        // Check if this package provides accessibility services
        // Simplified check - in production would query AccessibilityManager
        return false;
    }

    private boolean isDeviceAdminApp(@NonNull String packageName) {
        // Check if this package is a device admin
        // Simplified check - in production would query DevicePolicyManager
        return false;
    }

    @NonNull
    private HibernationEligibility determineEligibility(
            @NonNull ApplicationInfo appInfo,
            long timeSinceLastUse,
            int launchCount,
            long totalUsedTime) {

        // Check if app is unused (30+ days)
        if (timeSinceLastUse >= UNUSED_APP_THRESHOLD_MS) {
            return HibernationEligibility.UNUSED;
        }

        // Check if app is rarely used (14+ days)
        if (timeSinceLastUse >= RARELY_USED_THRESHOLD_MS) {
            return HibernationEligibility.RARELY_USED;
        }

        // Check if app has low usage (less than 5 launches in 30 days)
        if (launchCount < 5 && totalUsedTime < TimeUnit.MINUTES.toMillis(30)) {
            return HibernationEligibility.LOW_USAGE;
        }

        // Check if app is a candidate for background restriction
        if (hasBackgroundActivity(appInfo.packageName)) {
            return HibernationEligibility.BACKGROUND_RESTRICT;
        }

        return HibernationEligibility.NOT_ELIGIBLE;
    }

    private boolean hasBackgroundActivity(@NonNull String packageName) {
        try {
            final List<AppOpsManager.OpEntry> ops = mAppOpsManager.getOpsForPackage(
                    UserHandle.myUserId(), packageName, null);
            if (ops != null) {
                for (AppOpsManager.OpEntry op : ops) {
                    if (op.getOp() == AppOpsManager.OP_RUN_IN_BACKGROUND
                            || op.getOp() == AppOpsManager.OP_RUN_ANY_IN_BACKGROUND) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private long calculatePotentialSavings(@NonNull ApplicationInfo appInfo) {
        // Estimate potential battery/memory savings
        // This is a simplified calculation - in production would use more sophisticated metrics
        long savings = 0;

        // Base savings for hibernation
        savings += 10L * 1024L * 1024L; // 10MB memory

        // Add cache size if available
        try {
            final File dataDir = new File(appInfo.dataDir);
            if (dataDir.exists()) {
                savings += dataDir.length() / 10; // Estimate 10% reclaimable
            }
        } catch (Exception e) {
            // Ignore
        }

        return savings;
    }

    /**
     * Check if an app is currently hibernated.
     *
     * @param packageName The package name to check
     * @return true if the app is hibernated
     */
    public boolean isCurrentlyHibernated(@NonNull String packageName) {
        // Check if app is in hibernation state
        // This would typically check AppStandby bucket or similar
        try {
            final int standbyBucket = mUsageStatsManager.getAppStandbyBucket(packageName);
            return standbyBucket == UsageStatsManager.STANDBY_BUCKET_RARE
                    || standbyBucket == UsageStatsManager.STANDBY_BUCKET_RESTRICTED;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Hibernate an app.
     *
     * @param packageName The package name to hibernate
     * @return true if hibernation was successful
     */
    public boolean hibernateApp(@NonNull String packageName) {
        try {
            // Put app in restricted standby bucket
            mUsageStatsManager.setAppStandbyBucket(packageName,
                    UsageStatsManager.STANDBY_BUCKET_RESTRICTED);

            // Force stop the app
            mActivityManager.forceStopPackage(packageName);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Wake an app from hibernation.
     *
     * @param packageName The package name to wake
     * @return true if wake was successful
     */
    public boolean wakeApp(@NonNull String packageName) {
        try {
            // Put app in active standby bucket
            mUsageStatsManager.setAppStandbyBucket(packageName,
                    UsageStatsManager.STANDBY_BUCKET_ACTIVE);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get all hibernation candidates.
     *
     * @return List of hibernation candidates
     */
    @NonNull
    public List<HibernationCandidate> getHibernationCandidates() {
        if (mHibernationCandidates.isEmpty()
                || System.currentTimeMillis() - mLastAnalysisTime > HIBERNATION_CHECK_INTERVAL_MS) {
            return analyzeAppsForHibernation();
        }
        return new ArrayList<>(mHibernationCandidates);
    }

    /**
     * Get hibernation candidates by eligibility type.
     *
     * @param eligibility The eligibility type to filter by
     * @return List of candidates with the specified eligibility
     */
    @NonNull
    public List<HibernationCandidate> getCandidatesByEligibility(
            @NonNull HibernationEligibility eligibility) {
        final List<HibernationCandidate> filtered = new ArrayList<>();
        for (HibernationCandidate candidate : getHibernationCandidates()) {
            if (candidate.getEligibility() == eligibility) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    /**
     * Get the count of apps that can be hibernated.
     *
     * @return Count of hibernation candidates
     */
    public int getHibernatableAppCount() {
        return getHibernationCandidates().size();
    }

    /**
     * Get the total potential savings from hibernating all candidates.
     *
     * @return Total potential savings in bytes
     */
    public long getTotalPotentialSavings() {
        long total = 0;
        for (HibernationCandidate candidate : getHibernationCandidates()) {
            total += candidate.getPotentialSavings();
        }
        return total;
    }

    /**
     * Get hibernation statistics.
     *
     * @return HibernationStats object with current statistics
     */
    @NonNull
    public HibernationStats getHibernationStats() {
        final List<HibernationCandidate> candidates = getHibernationCandidates();

        int unusedCount = 0;
        int rarelyUsedCount = 0;
        int lowUsageCount = 0;
        int backgroundRestrictCount = 0;
        int hibernatedCount = 0;

        for (HibernationCandidate candidate : candidates) {
            switch (candidate.getEligibility()) {
                case UNUSED:
                    unusedCount++;
                    break;
                case RARELY_USED:
                    rarelyUsedCount++;
                    break;
                case LOW_USAGE:
                    lowUsageCount++;
                    break;
                case BACKGROUND_RESTRICT:
                    backgroundRestrictCount++;
                    break;
                default:
                    break;
            }
            if (candidate.isHibernated()) {
                hibernatedCount++;
            }
        }

        return new HibernationStats(
                candidates.size(),
                unusedCount,
                rarelyUsedCount,
                lowUsageCount,
                backgroundRestrictCount,
                hibernatedCount,
                getTotalPotentialSavings()
        );
    }

    /**
     * Hibernation eligibility enum.
     */
    public enum HibernationEligibility {
        NOT_ELIGIBLE(0),
        BACKGROUND_RESTRICT(1),
        LOW_USAGE(2),
        RARELY_USED(3),
        UNUSED(4);

        private final int mPriority;

        HibernationEligibility(int priority) {
            mPriority = priority;
        }

        public int getPriority() {
            return mPriority;
        }
    }

    /**
     * Data class for hibernation candidates.
     */
    public static class HibernationCandidate {
        private final String mPackageName;
        private final String mAppName;
        private final android.graphics.drawable.Drawable mAppIcon;
        private final HibernationEligibility mEligibility;
        private final long mTimeSinceLastUse;
        private final long mTotalUsedTime;
        private final int mLaunchCount;
        private final long mPotentialSavings;
        private final boolean mIsHibernated;

        public HibernationCandidate(@NonNull String packageName, @NonNull String appName,
                @Nullable android.graphics.drawable.Drawable appIcon,
                @NonNull HibernationEligibility eligibility,
                long timeSinceLastUse, long totalUsedTime, int launchCount,
                long potentialSavings, boolean isHibernated) {
            mPackageName = packageName;
            mAppName = appName;
            mAppIcon = appIcon;
            mEligibility = eligibility;
            mTimeSinceLastUse = timeSinceLastUse;
            mTotalUsedTime = totalUsedTime;
            mLaunchCount = launchCount;
            mPotentialSavings = potentialSavings;
            mIsHibernated = isHibernated;
        }

        @NonNull
        public String getPackageName() {
            return mPackageName;
        }

        @NonNull
        public String getAppName() {
            return mAppName;
        }

        @Nullable
        public android.graphics.drawable.Drawable getAppIcon() {
            return mAppIcon;
        }

        @NonNull
        public HibernationEligibility getEligibility() {
            return mEligibility;
        }

        public long getTimeSinceLastUse() {
            return mTimeSinceLastUse;
        }

        public long getTotalUsedTime() {
            return mTotalUsedTime;
        }

        public int getLaunchCount() {
            return mLaunchCount;
        }

        public long getPotentialSavings() {
            return mPotentialSavings;
        }

        public boolean isHibernated() {
            return mIsHibernated;
        }
    }

    /**
     * Data class for hibernation statistics.
     */
    public static class HibernationStats {
        public final int totalCandidates;
        public final int unusedCount;
        public final int rarelyUsedCount;
        public final int lowUsageCount;
        public final int backgroundRestrictCount;
        public final int hibernatedCount;
        public final long totalPotentialSavings;

        public HibernationStats(int totalCandidates, int unusedCount, int rarelyUsedCount,
                int lowUsageCount, int backgroundRestrictCount, int hibernatedCount,
                long totalPotentialSavings) {
            this.totalCandidates = totalCandidates;
            this.unusedCount = unusedCount;
            this.rarelyUsedCount = rarelyUsedCount;
            this.lowUsageCount = lowUsageCount;
            this.backgroundRestrictCount = backgroundRestrictCount;
            this.hibernatedCount = hibernatedCount;
            this.totalPotentialSavings = totalPotentialSavings;
        }
    }
}
