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

package com.android.settings.privacy;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced Privacy Dashboard that provides comprehensive privacy monitoring.
 * Tracks app access to sensitive permissions and provides privacy scores.
 */
public class PrivacyDashboardEnhancement {

    private static final String TAG = "PrivacyDashboardEnhancement";

    // Privacy risk levels
    private static final int RISK_LEVEL_LOW = 1;
    private static final int RISK_LEVEL_MEDIUM = 2;
    private static final int RISK_LEVEL_HIGH = 3;
    private static final int RISK_LEVEL_CRITICAL = 4;

    // Sensitive permission weights for privacy score calculation
    private static final Map<String, Integer> PERMISSION_WEIGHTS = new HashMap<>();
    static {
        PERMISSION_WEIGHTS.put(Manifest.permission.ACCESS_FINE_LOCATION, 10);
        PERMISSION_WEIGHTS.put(Manifest.permission.ACCESS_COARSE_LOCATION, 8);
        PERMISSION_WEIGHTS.put(Manifest.permission.CAMERA, 9);
        PERMISSION_WEIGHTS.put(Manifest.permission.RECORD_AUDIO, 9);
        PERMISSION_WEIGHTS.put(Manifest.permission.READ_CONTACTS, 7);
        PERMISSION_WEIGHTS.put(Manifest.permission.WRITE_CONTACTS, 7);
        PERMISSION_WEIGHTS.put(Manifest.permission.READ_CALENDAR, 6);
        PERMISSION_WEIGHTS.put(Manifest.permission.WRITE_CALENDAR, 6);
        PERMISSION_WEIGHTS.put(Manifest.permission.READ_CALL_LOG, 8);
        PERMISSION_WEIGHTS.put(Manifest.permission.WRITE_CALL_LOG, 8);
        PERMISSION_WEIGHTS.put(Manifest.permission.READ_PHONE_STATE, 7);
        PERMISSION_WEIGHTS.put(Manifest.permission.READ_SMS, 8);
        PERMISSION_WEIGHTS.put(Manifest.permission.RECEIVE_SMS, 7);
        PERMISSION_WEIGHTS.put(Manifest.permission.SEND_SMS, 7);
        PERMISSION_WEIGHTS.put(Manifest.permission.BODY_SENSORS, 8);
        PERMISSION_WEIGHTS.put(Manifest.permission.ACTIVITY_RECOGNITION, 6);
        PERMISSION_WEIGHTS.put(Manifest.permission.READ_EXTERNAL_STORAGE, 5);
        PERMISSION_WEIGHTS.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, 5);
    }

    private final Context mContext;
    private final PackageManager mPackageManager;
    private final AppOpsManager mAppOpsManager;
    private final UserManager mUserManager;
    private final LocationManager mLocationManager;
    private final AudioManager mAudioManager;

    private List<PrivacyAppInfo> mPrivacyAppList;
    private PrivacyScore mOverallPrivacyScore;

    public PrivacyDashboardEnhancement(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mPackageManager = context.getPackageManager();
        mAppOpsManager = context.getSystemService(AppOpsManager.class);
        mUserManager = context.getSystemService(UserManager.class);
        mLocationManager = context.getSystemService(LocationManager.class);
        mAudioManager = context.getSystemService(AudioManager.class);
        mPrivacyAppList = new ArrayList<>();
    }

    /**
     * Perform comprehensive privacy analysis.
     *
     * @return PrivacyAnalysisResult containing all privacy information
     */
    @NonNull
    public PrivacyAnalysisResult analyzePrivacy() {
        mPrivacyAppList.clear();

        // Analyze all installed apps
        final List<ApplicationInfo> installedApps = mPackageManager.getInstalledApplications(
                PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : installedApps) {
            if (shouldSkipApp(appInfo)) {
                continue;
            }

            final PrivacyAppInfo privacyInfo = analyzeAppPrivacy(appInfo);
            if (privacyInfo != null && privacyInfo.hasSensitivePermissions()) {
                mPrivacyAppList.add(privacyInfo);
            }
        }

        // Sort by privacy risk (highest first)
        Collections.sort(mPrivacyAppList, (a, b) ->
                Integer.compare(b.getRiskLevel(), a.getRiskLevel()));

        // Calculate overall privacy score
        mOverallPrivacyScore = calculateOverallPrivacyScore();

        // Generate privacy recommendations
        final List<PrivacyRecommendation> recommendations = generateRecommendations();

        return new PrivacyAnalysisResult(
                mPrivacyAppList,
                mOverallPrivacyScore,
                recommendations,
                getPermissionUsageSummary(),
                getPrivacyAlerts()
        );
    }

    private boolean shouldSkipApp(@NonNull ApplicationInfo appInfo) {
        // Skip system apps
        if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                && (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
            return true;
        }

        // Skip current app
        if (appInfo.packageName.equals(mContext.getPackageName())) {
            return true;
        }

        // Skip essential system packages
        if (appInfo.packageName.startsWith("com.android.")
                || appInfo.packageName.startsWith("android")) {
            return true;
        }

        return false;
    }

    @Nullable
    private PrivacyAppInfo analyzeAppPrivacy(@NonNull ApplicationInfo appInfo) {
        try {
            final String packageName = appInfo.packageName;
            final List<String> grantedPermissions = new ArrayList<>();
            final List<String> usedPermissions = new ArrayList<>();
            int totalWeight = 0;

            for (Map.Entry<String, Integer> entry : PERMISSION_WEIGHTS.entrySet()) {
                final String permission = entry.getKey();
                final int weight = entry.getValue();

                if (mPackageManager.checkPermission(permission, packageName)
                        == PackageManager.PERMISSION_GRANTED) {
                    grantedPermissions.add(permission);
                    totalWeight += weight;

                    // Check if permission was recently used
                    if (hasRecentPermissionUsage(packageName, permission)) {
                        usedPermissions.add(permission);
                    }
                }
            }

            if (grantedPermissions.isEmpty()) {
                return null;
            }

            // Calculate risk level
            final int riskLevel = calculateRiskLevel(totalWeight, grantedPermissions.size());

            // Determine if app has background access
            final boolean hasBackgroundAccess = checkBackgroundAccess(packageName);

            // Check for dangerous permission combinations
            final List<String> dangerousCombinations = findDangerousCombinations(grantedPermissions);

            return new PrivacyAppInfo(
                    packageName,
                    appInfo.loadLabel(mPackageManager).toString(),
                    appInfo.loadIcon(mPackageManager),
                    grantedPermissions,
                    usedPermissions,
                    totalWeight,
                    riskLevel,
                    hasBackgroundAccess,
                    dangerousCombinations
            );
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasRecentPermissionUsage(@NonNull String packageName, @NonNull String permission) {
        final String appOp = AppOpsManager.permissionToOp(permission);
        if (appOp == null) {
            return false;
        }

        try {
            final List<AppOpsManager.PackageOps> packageOps = mAppOpsManager.getOpsForPackage(
                    UserHandle.myUserId(), packageName, new String[]{appOp});

            if (packageOps != null && !packageOps.isEmpty()) {
                // Check if used in the last 24 hours
                final long oneDayAgo = System.currentTimeMillis() - 86400000L;
                for (AppOpsManager.OpEntry op : packageOps.get(0).getOps()) {
                    return op.getLastAccessTime(0) >= oneDayAgo;
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return false;
    }

    private int calculateRiskLevel(int totalWeight, int permissionCount) {
        // High number of sensitive permissions
        if (totalWeight >= 50 || permissionCount >= 8) {
            return RISK_LEVEL_CRITICAL;
        }

        // Multiple sensitive permissions
        if (totalWeight >= 30 || permissionCount >= 5) {
            return RISK_LEVEL_HIGH;
        }

        // Some sensitive permissions
        if (totalWeight >= 15 || permissionCount >= 3) {
            return RISK_LEVEL_MEDIUM;
        }

        return RISK_LEVEL_LOW;
    }

    private boolean checkBackgroundAccess(@NonNull String packageName) {
        try {
            // Check for background location access
            final List<AppOpsManager.PackageOps> packageOps = mAppOpsManager.getOpsForPackage(
                    UserHandle.myUserId(), packageName,
                    new String[]{
                            AppOpsManager.OPSTR_FINE_LOCATION,
                            AppOpsManager.OPSTR_RECORD_AUDIO
                    });

            if (packageOps != null) {
                for (AppOpsManager.PackageOps pkgOps : packageOps) {
                    for (AppOpsManager.OpEntry op : pkgOps.getOps()) {
                        if (op.getMode() == AppOpsManager.MODE_ALLOWED) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return false;
    }

    @NonNull
    private List<String> findDangerousCombinations(@NonNull List<String> permissions) {
        final List<String> combinations = new ArrayList<>();

        // Location + Camera combination
        if (permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)
                && permissions.contains(Manifest.permission.CAMERA)) {
            combinations.add("location_camera");
        }

        // Location + Microphone combination
        if (permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)
                && permissions.contains(Manifest.permission.RECORD_AUDIO)) {
            combinations.add("location_microphone");
        }

        // Camera + Microphone combination
        if (permissions.contains(Manifest.permission.CAMERA)
                && permissions.contains(Manifest.permission.RECORD_AUDIO)) {
            combinations.add("camera_microphone");
        }

        // Contacts + SMS combination
        if (permissions.contains(Manifest.permission.READ_CONTACTS)
                && permissions.contains(Manifest.permission.READ_SMS)) {
            combinations.add("contacts_sms");
        }

        // Location + Contacts combination
        if (permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)
                && permissions.contains(Manifest.permission.READ_CONTACTS)) {
            combinations.add("location_contacts");
        }

        return combinations;
    }

    @NonNull
    private PrivacyScore calculateOverallPrivacyScore() {
        if (mPrivacyAppList.isEmpty()) {
            return new PrivacyScore(100, RISK_LEVEL_LOW);
        }

        int totalApps = mPrivacyAppList.size();
        int criticalApps = 0;
        int highRiskApps = 0;
        int mediumRiskApps = 0;
        int lowRiskApps = 0;
        int backgroundAccessApps = 0;
        int dangerousCombinationApps = 0;

        for (PrivacyAppInfo app : mPrivacyAppList) {
            switch (app.getRiskLevel()) {
                case RISK_LEVEL_CRITICAL:
                    criticalApps++;
                    break;
                case RISK_LEVEL_HIGH:
                    highRiskApps++;
                    break;
                case RISK_LEVEL_MEDIUM:
                    mediumRiskApps++;
                    break;
                default:
                    lowRiskApps++;
                    break;
            }

            if (app.hasBackgroundAccess()) {
                backgroundAccessApps++;
            }

            if (!app.getDangerousCombinations().isEmpty()) {
                dangerousCombinationApps++;
            }
        }

        // Calculate score (0-100, higher is better)
        int score = 100;

        // Deduct for high-risk apps
        score -= criticalApps * 15;
        score -= highRiskApps * 8;
        score -= mediumRiskApps * 3;

        // Deduct for background access
        score -= backgroundAccessApps * 5;

        // Deduct for dangerous combinations
        score -= dangerousCombinationApps * 5;

        // Ensure score is within bounds
        score = Math.max(0, Math.min(100, score));

        // Determine overall risk level
        int overallRisk;
        if (score >= 80) {
            overallRisk = RISK_LEVEL_LOW;
        } else if (score >= 60) {
            overallRisk = RISK_LEVEL_MEDIUM;
        } else if (score >= 40) {
            overallRisk = RISK_LEVEL_HIGH;
        } else {
            overallRisk = RISK_LEVEL_CRITICAL;
        }

        return new PrivacyScore(score, overallRisk);
    }

    @NonNull
    private List<PrivacyRecommendation> generateRecommendations() {
        final List<PrivacyRecommendation> recommendations = new ArrayList<>();

        // Check for critical apps
        for (PrivacyAppInfo app : mPrivacyAppList) {
            if (app.getRiskLevel() == RISK_LEVEL_CRITICAL) {
                recommendations.add(new PrivacyRecommendation(
                        RecommendationType.REVIEW_CRITICAL_APP,
                        mContext.getString(com.android.settings.R.string.privacy_recommendation_critical_app,
                                app.getAppName()),
                        app.getPackageName(),
                        RecommendationPriority.URGENT
                ));
            }
        }

        // Check for background access
        for (PrivacyAppInfo app : mPrivacyAppList) {
            if (app.hasBackgroundAccess()) {
                recommendations.add(new PrivacyRecommendation(
                        RecommendationType.REVOKE_BACKGROUND_ACCESS,
                        mContext.getString(com.android.settings.R.string.privacy_recommendation_background,
                                app.getAppName()),
                        app.getPackageName(),
                        RecommendationPriority.HIGH
                ));
            }
        }

        // Check for dangerous combinations
        for (PrivacyAppInfo app : mPrivacyAppList) {
            if (!app.getDangerousCombinations().isEmpty()) {
                recommendations.add(new PrivacyRecommendation(
                        RecommendationType.REVIEW_DANGEROUS_COMBO,
                        mContext.getString(com.android.settings.R.string.privacy_recommendation_dangerous_combo,
                                app.getAppName()),
                        app.getPackageName(),
                        RecommendationPriority.HIGH
                ));
            }
        }

        // Check for unused permissions
        for (PrivacyAppInfo app : mPrivacyAppList) {
            final int unusedCount = app.getGrantedPermissions().size() - app.getUsedPermissions().size();
            if (unusedCount >= 3) {
                recommendations.add(new PrivacyRecommendation(
                        RecommendationType.REMOVE_UNUSED_PERMISSIONS,
                        mContext.getString(com.android.settings.R.string.privacy_recommendation_unused,
                                app.getAppName(), unusedCount),
                        app.getPackageName(),
                        RecommendationPriority.MEDIUM
                ));
            }
        }

        // Sort by priority
        Collections.sort(recommendations, (a, b) ->
                Integer.compare(b.getPriority().getLevel(), a.getPriority().getLevel()));

        return recommendations;
    }

    @NonNull
    private Map<String, Integer> getPermissionUsageSummary() {
        final Map<String, Integer> summary = new HashMap<>();

        for (String permission : PERMISSION_WEIGHTS.keySet()) {
            int count = 0;
            for (PrivacyAppInfo app : mPrivacyAppList) {
                if (app.getGrantedPermissions().contains(permission)) {
                    count++;
                }
            }
            if (count > 0) {
                summary.put(permission, count);
            }
        }

        return summary;
    }

    @NonNull
    private List<PrivacyAlert> getPrivacyAlerts() {
        final List<PrivacyAlert> alerts = new ArrayList<>();

        // Check for camera/microphone access
        for (PrivacyAppInfo app : mPrivacyAppList) {
            if (app.getUsedPermissions().contains(Manifest.permission.CAMERA)) {
                alerts.add(new PrivacyAlert(
                        AlertType.CAMERA_ACCESS,
                        app.getAppName(),
                        app.getPackageName()
                ));
            }
            if (app.getUsedPermissions().contains(Manifest.permission.RECORD_AUDIO)) {
                alerts.add(new PrivacyAlert(
                        AlertType.MICROPHONE_ACCESS,
                        app.getAppName(),
                        app.getPackageName()
                ));
            }
        }

        return alerts;
    }

    /**
     * Get apps with a specific permission.
     *
     * @param permission The permission to filter by
     * @return List of apps with that permission
     */
    @NonNull
    public List<PrivacyAppInfo> getAppsForPermission(@NonNull String permission) {
        final List<PrivacyAppInfo> filtered = new ArrayList<>();
        for (PrivacyAppInfo app : mPrivacyAppList) {
            if (app.getGrantedPermissions().contains(permission)) {
                filtered.add(app);
            }
        }
        return filtered;
    }

    /**
     * Get apps by risk level.
     *
     * @param riskLevel The risk level to filter by
     * @return List of apps with that risk level
     */
    @NonNull
    public List<PrivacyAppInfo> getAppsByRiskLevel(int riskLevel) {
        final List<PrivacyAppInfo> filtered = new ArrayList<>();
        for (PrivacyAppInfo app : mPrivacyAppList) {
            if (app.getRiskLevel() == riskLevel) {
                filtered.add(app);
            }
        }
        return filtered;
    }

    /**
     * Recommendation type enum.
     */
    public enum RecommendationType {
        REVIEW_CRITICAL_APP,
        REVOKE_BACKGROUND_ACCESS,
        REVIEW_DANGEROUS_COMBO,
        REMOVE_UNUSED_PERMISSIONS
    }

    /**
     * Recommendation priority enum.
     */
    public enum RecommendationPriority {
        URGENT(4),
        HIGH(3),
        MEDIUM(2),
        LOW(1);

        private final int mLevel;

        RecommendationPriority(int level) {
            mLevel = level;
        }

        public int getLevel() {
            return mLevel;
        }
    }

    /**
     * Alert type enum.
     */
    public enum AlertType {
        CAMERA_ACCESS,
        MICROPHONE_ACCESS,
        LOCATION_ACCESS,
        BACKGROUND_ACCESS
    }

    /**
     * Data class for privacy app information.
     */
    public static class PrivacyAppInfo {
        private final String mPackageName;
        private final String mAppName;
        private final android.graphics.drawable.Drawable mAppIcon;
        private final List<String> mGrantedPermissions;
        private final List<String> mUsedPermissions;
        private final int mPermissionWeight;
        private final int mRiskLevel;
        private final boolean mHasBackgroundAccess;
        private final List<String> mDangerousCombinations;

        public PrivacyAppInfo(@NonNull String packageName, @NonNull String appName,
                @Nullable android.graphics.drawable.Drawable appIcon,
                @NonNull List<String> grantedPermissions, @NonNull List<String> usedPermissions,
                int permissionWeight, int riskLevel, boolean hasBackgroundAccess,
                @NonNull List<String> dangerousCombinations) {
            mPackageName = packageName;
            mAppName = appName;
            mAppIcon = appIcon;
            mGrantedPermissions = grantedPermissions;
            mUsedPermissions = usedPermissions;
            mPermissionWeight = permissionWeight;
            mRiskLevel = riskLevel;
            mHasBackgroundAccess = hasBackgroundAccess;
            mDangerousCombinations = dangerousCombinations;
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
        public List<String> getGrantedPermissions() {
            return mGrantedPermissions;
        }

        @NonNull
        public List<String> getUsedPermissions() {
            return mUsedPermissions;
        }

        public int getPermissionWeight() {
            return mPermissionWeight;
        }

        public int getRiskLevel() {
            return mRiskLevel;
        }

        public boolean hasBackgroundAccess() {
            return mHasBackgroundAccess;
        }

        @NonNull
        public List<String> getDangerousCombinations() {
            return mDangerousCombinations;
        }

        public boolean hasSensitivePermissions() {
            return !mGrantedPermissions.isEmpty();
        }
    }

    /**
     * Data class for privacy score.
     */
    public static class PrivacyScore {
        public final int score;
        public final int riskLevel;

        public PrivacyScore(int score, int riskLevel) {
            this.score = score;
            this.riskLevel = riskLevel;
        }

        @NonNull
        public String getRiskLevelString(@NonNull Context context) {
            switch (riskLevel) {
                case RISK_LEVEL_CRITICAL:
                    return context.getString(com.android.settings.R.string.privacy_risk_critical);
                case RISK_LEVEL_HIGH:
                    return context.getString(com.android.settings.R.string.privacy_risk_high);
                case RISK_LEVEL_MEDIUM:
                    return context.getString(com.android.settings.R.string.privacy_risk_medium);
                default:
                    return context.getString(com.android.settings.R.string.privacy_risk_low);
            }
        }
    }

    /**
     * Data class for privacy recommendations.
     */
    public static class PrivacyRecommendation {
        private final RecommendationType mType;
        private final String mTitle;
        private final String mPackageName;
        private final RecommendationPriority mPriority;

        public PrivacyRecommendation(@NonNull RecommendationType type, @NonNull String title,
                @NonNull String packageName, @NonNull RecommendationPriority priority) {
            mType = type;
            mTitle = title;
            mPackageName = packageName;
            mPriority = priority;
        }

        @NonNull
        public RecommendationType getType() {
            return mType;
        }

        @NonNull
        public String getTitle() {
            return mTitle;
        }

        @NonNull
        public String getPackageName() {
            return mPackageName;
        }

        @NonNull
        public RecommendationPriority getPriority() {
            return mPriority;
        }
    }

    /**
     * Data class for privacy alerts.
     */
    public static class PrivacyAlert {
        private final AlertType mType;
        private final String mAppName;
        private final String mPackageName;

        public PrivacyAlert(@NonNull AlertType type, @NonNull String appName,
                @NonNull String packageName) {
            mType = type;
            mAppName = appName;
            mPackageName = packageName;
        }

        @NonNull
        public AlertType getType() {
            return mType;
        }

        @NonNull
        public String getAppName() {
            return mAppName;
        }

        @NonNull
        public String getPackageName() {
            return mPackageName;
        }
    }

    /**
     * Data class for privacy analysis result.
     */
    public static class PrivacyAnalysisResult {
        public final List<PrivacyAppInfo> appList;
        public final PrivacyScore overallScore;
        public final List<PrivacyRecommendation> recommendations;
        public final Map<String, Integer> permissionUsageSummary;
        public final List<PrivacyAlert> alerts;

        public PrivacyAnalysisResult(@NonNull List<PrivacyAppInfo> appList,
                @NonNull PrivacyScore overallScore,
                @NonNull List<PrivacyRecommendation> recommendations,
                @NonNull Map<String, Integer> permissionUsageSummary,
                @NonNull List<PrivacyAlert> alerts) {
            this.appList = appList;
            this.overallScore = overallScore;
            this.recommendations = recommendations;
            this.permissionUsageSummary = permissionUsageSummary;
            this.alerts = alerts;
        }
    }
}
