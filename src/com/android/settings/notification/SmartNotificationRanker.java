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

package com.android.settings.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smart notification ranking system that prioritizes notifications based on
 * user behavior, app importance, and notification characteristics.
 */
public class SmartNotificationRanker {

    private static final String TAG = "SmartNotificationRanker";

    // Priority levels
    public static final int PRIORITY_CRITICAL = 5;
    public static final int PRIORITY_HIGH = 4;
    public static final int PRIORITY_NORMAL = 3;
    public static final int PRIORITY_LOW = 2;
    public static final int PRIORITY_MIN = 1;

    // Importance weights for scoring
    private static final float WEIGHT_APP_USAGE = 0.25f;
    private static final float WEIGHT_NOTIFICATION_IMPORTANCE = 0.30f;
    private static final float WEIGHT_CHANNEL_IMPORTANCE = 0.20f;
    private static final float WEIGHT_TIME_SENSITIVITY = 0.15f;
    private static final float WEIGHT_USER_INTERACTION = 0.10f;

    // Time thresholds
    private static final long RECENT_USAGE_THRESHOLD_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final long FREQUENT_USAGE_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final PackageManager mPackageManager;
    private final UsageStatsManager mUsageStatsManager;

    private Map<String, AppUsageScore> mAppUsageScores;
    private Map<String, Float> mNotificationInteractionRates;

    public SmartNotificationRanker(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mNotificationManager = context.getSystemService(NotificationManager.class);
        mPackageManager = context.getPackageManager();
        mUsageStatsManager = context.getSystemService(UsageStatsManager.class);
        mAppUsageScores = new HashMap<>();
        mNotificationInteractionRates = new HashMap<>();
    }

    /**
     * Calculate priority score for a notification.
     *
     * @param sbn The StatusBarNotification to score
     * @return Priority score (0-100)
     */
    public float calculateNotificationScore(@NonNull StatusBarNotification sbn) {
        final String packageName = sbn.getPackageName();

        // Get individual scores
        final float appUsageScore = getAppUsageScore(packageName);
        final float notificationImportanceScore = getNotificationImportanceScore(sbn);
        final float channelImportanceScore = getChannelImportanceScore(sbn);
        final float timeSensitivityScore = getTimeSensitivityScore(sbn);
        final float userInteractionScore = getUserInteractionScore(packageName);

        // Calculate weighted score
        return (appUsageScore * WEIGHT_APP_USAGE +
                notificationImportanceScore * WEIGHT_NOTIFICATION_IMPORTANCE +
                channelImportanceScore * WEIGHT_CHANNEL_IMPORTANCE +
                timeSensitivityScore * WEIGHT_TIME_SENSITIVITY +
                userInteractionScore * WEIGHT_USER_INTERACTION) * 100;
    }

    /**
     * Rank notifications by priority.
     *
     * @param notifications List of notifications to rank
     * @return Ranked list of notifications (highest priority first)
     */
    @NonNull
    public List<RankedNotification> rankNotifications(
            @NonNull List<StatusBarNotification> notifications) {
        // Update app usage scores
        updateAppUsageScores();

        final List<RankedNotification> rankedList = new ArrayList<>();

        for (StatusBarNotification sbn : notifications) {
            final float score = calculateNotificationScore(sbn);
            final int priority = determinePriority(score);

            rankedList.add(new RankedNotification(sbn, score, priority));
        }

        // Sort by score (highest first)
        Collections.sort(rankedList, (a, b) -> Float.compare(b.getScore(), a.getScore()));

        return rankedList;
    }

    private void updateAppUsageScores() {
        final Calendar calendar = Calendar.getInstance();
        final long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_YEAR, -7);
        final long startTime = calendar.getTimeInMillis();

        final Map<String, UsageStats> usageStats = mUsageStatsManager.queryAndAggregateUsageStats(
                startTime, endTime);

        mAppUsageScores.clear();

        for (Map.Entry<String, UsageStats> entry : usageStats.entrySet()) {
            final String packageName = entry.getKey();
            final UsageStats stats = entry.getValue();

            final long timeInForeground = stats.getTotalTimeInForeground();
            final int launchCount = stats.getAppLaunchCount();
            final long lastUsedTime = stats.getLastTimeUsed();

            // Calculate usage score based on frequency and recency
            float frequencyScore = Math.min(1.0f, launchCount / 50.0f); // Normalize to 50 launches
            float recencyScore = calculateRecencyScore(lastUsedTime);
            float durationScore = Math.min(1.0f, timeInForeground / (60 * 60 * 1000.0f)); // Normalize to 1 hour

            final float combinedScore = (frequencyScore * 0.4f + recencyScore * 0.4f + durationScore * 0.2f);

            mAppUsageScores.put(packageName, new AppUsageScore(
                    packageName, frequencyScore, recencyScore, durationScore, combinedScore
            ));
        }
    }

    private float calculateRecencyScore(long lastUsedTime) {
        final long now = System.currentTimeMillis();
        final long timeSinceUse = now - lastUsedTime;

        if (timeSinceUse < RECENT_USAGE_THRESHOLD_MS) {
            return 1.0f;
        } else if (timeSinceUse < FREQUENT_USAGE_THRESHOLD_MS) {
            return 0.5f;
        }
        return 0.1f;
    }

    private float getAppUsageScore(@NonNull String packageName) {
        final AppUsageScore score = mAppUsageScores.get(packageName);
        return score != null ? score.getCombinedScore() : 0.1f;
    }

    private float getNotificationImportanceScore(@NonNull StatusBarNotification sbn) {
        final int importance = sbn.getNotification().priority;

        switch (importance) {
            case NotificationManager.IMPORTANCE_HIGH:
                return 1.0f;
            case NotificationManager.IMPORTANCE_DEFAULT:
                return 0.7f;
            case NotificationManager.IMPORTANCE_LOW:
                return 0.4f;
            case NotificationManager.IMPORTANCE_MIN:
                return 0.2f;
            default:
                return 0.5f;
        }
    }

    private float getChannelImportanceScore(@NonNull StatusBarNotification sbn) {
        final NotificationChannel channel = mNotificationManager.getNotificationChannel(
                sbn.getPackageName(), sbn.getNotification().getChannelId());

        if (channel == null) {
            return 0.5f;
        }

        switch (channel.getImportance()) {
            case NotificationManager.IMPORTANCE_HIGH:
                return 1.0f;
            case NotificationManager.IMPORTANCE_DEFAULT:
                return 0.7f;
            case NotificationManager.IMPORTANCE_LOW:
                return 0.4f;
            case NotificationManager.IMPORTANCE_MIN:
                return 0.2f;
            default:
                return 0.5f;
        }
    }

    private float getTimeSensitivityScore(@NonNull StatusBarNotification sbn) {
        // Check if notification has time-sensitive content
        final long when = sbn.getNotification().when;
        final long timeoutAfter = sbn.getNotification().getTimeoutAfter();

        // Notifications with specific timing are more important
        if (when > 0) {
            final long now = System.currentTimeMillis();
            final long timeDiff = Math.abs(now - when);

            // Very recent or upcoming events are more important
            if (timeDiff < 30 * 60 * 1000L) { // 30 minutes
                return 1.0f;
            } else if (timeDiff < 2 * 60 * 60 * 1000L) { // 2 hours
                return 0.8f;
            }
        }

        // Notifications that auto-dismiss are likely time-sensitive
        if (timeoutAfter > 0 && timeoutAfter < 60 * 60 * 1000L) {
            return 0.9f;
        }

        // Check for ongoing notifications (calls, navigation, etc.)
        if ((sbn.getNotification().flags & android.app.Notification.FLAG_ONGOING_EVENT) != 0) {
            return 1.0f;
        }

        // Check for foreground service notifications
        if ((sbn.getNotification().flags & android.app.Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            return 0.6f;
        }

        return 0.5f;
    }

    private float getUserInteractionScore(@NonNull String packageName) {
        final Float rate = mNotificationInteractionRates.get(packageName);
        return rate != null ? rate : 0.5f;
    }

    private int determinePriority(float score) {
        if (score >= 80) {
            return PRIORITY_CRITICAL;
        } else if (score >= 60) {
            return PRIORITY_HIGH;
        } else if (score >= 40) {
            return PRIORITY_NORMAL;
        } else if (score >= 20) {
            return PRIORITY_LOW;
        }
        return PRIORITY_MIN;
    }

    /**
     * Get notification recommendations for an app.
     *
     * @param packageName The package name to analyze
     * @return NotificationRecommendation for the app
     */
    @NonNull
    public NotificationRecommendation getAppNotificationRecommendation(@NonNull String packageName) {
        final AppUsageScore usageScore = mAppUsageScores.get(packageName);
        final float interactionRate = getUserInteractionScore(packageName);

        // Determine if notifications should be adjusted
        if (usageScore != null && usageScore.getCombinedScore() < 0.2f && interactionRate < 0.2f) {
            return new NotificationRecommendation(
                    packageName,
                    RecommendationType.REDUCE_NOTIFICATIONS,
                    "This app sends notifications but is rarely used. Consider reducing notification frequency.",
                    RecommendationPriority.MEDIUM
            );
        }

        if (interactionRate > 0.8f) {
            return new NotificationRecommendation(
                    packageName,
                    RecommendationType.KEEP_IMPORTANT,
                    "You frequently interact with this app's notifications. Keep them enabled.",
                    RecommendationPriority.LOW
            );
        }

        return new NotificationRecommendation(
                packageName,
                RecommendationType.NO_CHANGE,
                "Notification settings are appropriate for your usage pattern.",
                RecommendationPriority.LOW
        );
    }

    /**
     * Get all notification recommendations.
     *
     * @return List of notification recommendations
     */
    @NonNull
    public List<NotificationRecommendation> getAllRecommendations() {
        final List<NotificationRecommendation> recommendations = new ArrayList<>();

        for (String packageName : mAppUsageScores.keySet()) {
            final NotificationRecommendation rec = getAppNotificationRecommendation(packageName);
            if (rec.getType() != RecommendationType.NO_CHANGE) {
                recommendations.add(rec);
            }
        }

        // Sort by priority
        Collections.sort(recommendations, (a, b) ->
                Integer.compare(b.getPriority().getLevel(), a.getPriority().getLevel()));

        return recommendations;
    }

    /**
     * Update interaction rate for an app.
     *
     * @param packageName The package name
     * @param interacted Whether the user interacted with the notification
     */
    public void recordNotificationInteraction(@NonNull String packageName, boolean interacted) {
        final Float currentRate = mNotificationInteractionRates.get(packageName);
        final float newRate;

        if (currentRate == null) {
            newRate = interacted ? 1.0f : 0.0f;
        } else {
            // Exponential moving average
            newRate = currentRate * 0.9f + (interacted ? 0.1f : 0.0f);
        }

        mNotificationInteractionRates.put(packageName, newRate);
    }

    /**
     * Get notification summary for an app.
     *
     * @param packageName The package name
     * @return NotificationSummary for the app
     */
    @Nullable
    public NotificationSummary getAppNotificationSummary(@NonNull String packageName) {
        final AppUsageScore usageScore = mAppUsageScores.get(packageName);
        final Float interactionRate = mNotificationInteractionRates.get(packageName);

        if (usageScore == null) {
            return null;
        }

        String appName = packageName;
        try {
            final ApplicationInfo appInfo = mPackageManager.getApplicationInfo(packageName, 0);
            appName = mPackageManager.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            // Use package name
        }

        return new NotificationSummary(
                packageName,
                appName,
                usageScore.getCombinedScore(),
                interactionRate != null ? interactionRate : 0.0f,
                determinePriority(usageScore.getCombinedScore())
        );
    }

    /**
     * Recommendation type enum.
     */
    public enum RecommendationType {
        REDUCE_NOTIFICATIONS,
        KEEP_IMPORTANT,
        NO_CHANGE
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
     * Data class for app usage scores.
     */
    public static class AppUsageScore {
        private final String mPackageName;
        private final float mFrequencyScore;
        private final float mRecencyScore;
        private final float mDurationScore;
        private final float mCombinedScore;

        public AppUsageScore(@NonNull String packageName, float frequencyScore,
                float recencyScore, float durationScore, float combinedScore) {
            mPackageName = packageName;
            mFrequencyScore = frequencyScore;
            mRecencyScore = recencyScore;
            mDurationScore = durationScore;
            mCombinedScore = combinedScore;
        }

        @NonNull
        public String getPackageName() {
            return mPackageName;
        }

        public float getFrequencyScore() {
            return mFrequencyScore;
        }

        public float getRecencyScore() {
            return mRecencyScore;
        }

        public float getDurationScore() {
            return mDurationScore;
        }

        public float getCombinedScore() {
            return mCombinedScore;
        }
    }

    /**
     * Data class for ranked notifications.
     */
    public static class RankedNotification {
        private final StatusBarNotification mNotification;
        private final float mScore;
        private final int mPriority;

        public RankedNotification(@NonNull StatusBarNotification notification,
                float score, int priority) {
            mNotification = notification;
            mScore = score;
            mPriority = priority;
        }

        @NonNull
        public StatusBarNotification getNotification() {
            return mNotification;
        }

        public float getScore() {
            return mScore;
        }

        public int getPriority() {
            return mPriority;
        }
    }

    /**
     * Data class for notification recommendations.
     */
    public static class NotificationRecommendation {
        private final String mPackageName;
        private final RecommendationType mType;
        private final String mMessage;
        private final RecommendationPriority mPriority;

        public NotificationRecommendation(@NonNull String packageName,
                @NonNull RecommendationType type, @NonNull String message,
                @NonNull RecommendationPriority priority) {
            mPackageName = packageName;
            mType = type;
            mMessage = message;
            mPriority = priority;
        }

        @NonNull
        public String getPackageName() {
            return mPackageName;
        }

        @NonNull
        public RecommendationType getType() {
            return mType;
        }

        @NonNull
        public String getMessage() {
            return mMessage;
        }

        @NonNull
        public RecommendationPriority getPriority() {
            return mPriority;
        }
    }

    /**
     * Data class for notification summary.
     */
    public static class NotificationSummary {
        private final String mPackageName;
        private final String mAppName;
        private final float mUsageScore;
        private final float mInteractionRate;
        private final int mSuggestedPriority;

        public NotificationSummary(@NonNull String packageName, @NonNull String appName,
                float usageScore, float interactionRate, int suggestedPriority) {
            mPackageName = packageName;
            mAppName = appName;
            mUsageScore = usageScore;
            mInteractionRate = interactionRate;
            mSuggestedPriority = suggestedPriority;
        }

        @NonNull
        public String getPackageName() {
            return mPackageName;
        }

        @NonNull
        public String getAppName() {
            return mAppName;
        }

        public float getUsageScore() {
            return mUsageScore;
        }

        public float getInteractionRate() {
            return mInteractionRate;
        }

        public int getSuggestedPriority() {
            return mSuggestedPriority;
        }
    }
}
