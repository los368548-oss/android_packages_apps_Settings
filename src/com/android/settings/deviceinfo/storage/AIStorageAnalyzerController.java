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

package com.android.settings.deviceinfo.storage;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.text.TextUtils;
import android.text.format.Formatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnResume;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI-powered storage analyzer that provides intelligent storage management suggestions.
 * Analyzes storage usage patterns and recommends cleanup actions.
 */
public class AIStorageAnalyzerController extends BasePreferenceController
        implements LifecycleObserver, OnResume {

    private static final String TAG = "AIStorageAnalyzer";

    // Thresholds for recommendations
    private static final long LOW_STORAGE_THRESHOLD = 1024L * 1024L * 1024L; // 1GB
    private static final long CRITICAL_STORAGE_THRESHOLD = 512L * 1024L * 1024L; // 512MB
    private static final long LARGE_FILE_THRESHOLD = 100L * 1024L * 1024L; // 100MB
    private static final long OLD_FILE_THRESHOLD_MS = TimeUnit.DAYS.toMillis(90); // 90 days
    private static final long DUPLICATE_FILE_THRESHOLD = 5; // 5 or more duplicates

    private final PackageManager mPackageManager;
    private final StorageManager mStorageManager;

    private PreferenceCategory mAnalyzerCategory;
    private StorageAnalysisResult mLastAnalysisResult;

    public AIStorageAnalyzerController(@NonNull Context context, @NonNull String preferenceKey) {
        super(context, preferenceKey);
        mPackageManager = context.getPackageManager();
        mStorageManager = context.getSystemService(StorageManager.class);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(@NonNull PreferenceScreen screen) {
        super.displayPreference(screen);
        mAnalyzerCategory = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onResume() {
        performStorageAnalysis();
    }

    /**
     * Perform comprehensive storage analysis.
     */
    public void performStorageAnalysis() {
        final StorageAnalysisResult.Builder builder = new StorageAnalysisResult.Builder();

        // Analyze storage state
        analyzeStorageState(builder);

        // Analyze app storage
        analyzeAppStorage(builder);

        // Analyze file categories
        analyzeFileCategories(builder);

        // Generate recommendations
        generateRecommendations(builder);

        mLastAnalysisResult = builder.build();
        updateAnalysisDisplay();
    }

    private void analyzeStorageState(@NonNull StorageAnalysisResult.Builder builder) {
        final File dataDir = Environment.getDataDirectory();
        final StatFs stat = new StatFs(dataDir.getPath());

        final long totalBytes = stat.getTotalBytes();
        final long availableBytes = stat.getAvailableBytes();
        final long usedBytes = totalBytes - availableBytes;
        final int usedPercentage = (int) ((usedBytes * 100) / totalBytes);

        builder.setTotalStorage(totalBytes);
        builder.setAvailableStorage(availableBytes);
        builder.setUsedStorage(usedBytes);
        builder.setUsedPercentage(usedPercentage);

        // Determine storage health
        if (availableBytes < CRITICAL_STORAGE_THRESHOLD) {
            builder.setStorageHealth(StorageHealth.CRITICAL);
        } else if (availableBytes < LOW_STORAGE_THRESHOLD) {
            builder.setStorageHealth(StorageHealth.LOW);
        } else if (usedPercentage > 90) {
            builder.setStorageHealth(StorageHealth.WARNING);
        } else {
            builder.setStorageHealth(StorageHealth.GOOD);
        }
    }

    private void analyzeAppStorage(@NonNull StorageAnalysisResult.Builder builder) {
        final List<AppStorageInfo> appStorageList = new ArrayList<>();
        long totalCacheSize = 0;
        long totalDataSize = 0;

        final List<ApplicationInfo> installedApps = mPackageManager.getInstalledApplications(
                PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : installedApps) {
            try {
                final File dataDir = new File(appInfo.dataDir);
                final File cacheDir = new File(appInfo.cacheDir);

                final long dataSize = dataDir.exists() ? dataDir.length() : 0;
                final long cacheSize = cacheDir.exists() ? cacheDir.length() : 0;

                if (dataSize > 0 || cacheSize > 0) {
                    final AppStorageInfo appStorage = new AppStorageInfo(
                            appInfo.packageName,
                            appInfo.loadLabel(mPackageManager).toString(),
                            appInfo.loadIcon(mPackageManager),
                            dataSize,
                            cacheSize,
                            appInfo.lastUpdateTime
                    );
                    appStorageList.add(appStorage);
                    totalCacheSize += cacheSize;
                    totalDataSize += dataSize;
                }
            } catch (Exception e) {
                // Skip apps with inaccessible data
            }
        }

        // Sort by total size (descending)
        Collections.sort(appStorageList, (a, b) ->
                Long.compare(b.getDataSize() + b.getCacheSize(), a.getDataSize() + a.getCacheSize()));

        builder.setAppStorageList(appStorageList);
        builder.setTotalCacheSize(totalCacheSize);
        builder.setTotalDataSize(totalDataSize);

        // Identify large apps
        final List<AppStorageInfo> largeApps = new ArrayList<>();
        for (AppStorageInfo app : appStorageList) {
            if (app.getDataSize() + app.getCacheSize() > LARGE_FILE_THRESHOLD) {
                largeApps.add(app);
            }
        }
        builder.setLargeApps(largeApps);
    }

    private void analyzeFileCategories(@NonNull StorageAnalysisResult.Builder builder) {
        final Map<FileCategory, Long> categorySizes = new HashMap<>();
        final Map<FileCategory, Integer> categoryCounts = new HashMap<>();

        // Initialize categories
        for (FileCategory category : FileCategory.values()) {
            categorySizes.put(category, 0L);
            categoryCounts.put(category, 0);
        }

        // Analyze Downloads folder
        final File downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        analyzeDirectory(downloadsDir, categorySizes, categoryCounts);

        // Analyze DCIM folder
        final File dcimDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM);
        analyzeDirectory(dcimDir, categorySizes, categoryCounts);

        // Analyze Pictures folder
        final File picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        analyzeDirectory(picturesDir, categorySizes, categoryCounts);

        // Analyze Music folder
        final File musicDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MUSIC);
        analyzeDirectory(musicDir, categorySizes, categoryCounts);

        // Analyze Movies folder
        final File moviesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES);
        analyzeDirectory(moviesDir, categorySizes, categoryCounts);

        builder.setCategorySizes(categorySizes);
        builder.setCategoryCounts(categoryCounts);
    }

    private void analyzeDirectory(@NonNull File directory,
            @NonNull Map<FileCategory, Long> categorySizes,
            @NonNull Map<FileCategory, Integer> categoryCounts) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        final File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                analyzeDirectory(file, categorySizes, categoryCounts);
            } else {
                final FileCategory category = categorizeFile(file);
                categorySizes.put(category, categorySizes.get(category) + file.length());
                categoryCounts.put(category, categoryCounts.get(category) + 1);
            }
        }
    }

    @NonNull
    private FileCategory categorizeFile(@NonNull File file) {
        final String name = file.getName().toLowerCase();
        final long lastModified = file.lastModified();
        final long age = System.currentTimeMillis() - lastModified;

        // Check for old files first
        if (age > OLD_FILE_THRESHOLD_MS) {
            return FileCategory.OLD_FILES;
        }

        // Categorize by extension
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".heic")) {
            return FileCategory.IMAGES;
        }

        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi")
                || name.endsWith(".mov") || name.endsWith(".webm")) {
            return FileCategory.VIDEOS;
        }

        if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac")
                || name.endsWith(".aac") || name.endsWith(".ogg")) {
            return FileCategory.AUDIO;
        }

        if (name.endsWith(".apk") || name.endsWith(".aab")) {
            return FileCategory.APK_FILES;
        }

        if (name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z")
                || name.endsWith(".tar") || name.endsWith(".gz")) {
            return FileCategory.ARCHIVES;
        }

        if (name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".pdf")
                || name.endsWith(".txt") || name.endsWith(".xls") || name.endsWith(".xlsx")) {
            return FileCategory.DOCUMENTS;
        }

        if (name.contains("cache") || name.contains("temp") || name.contains("tmp")) {
            return FileCategory.CACHE;
        }

        return FileCategory.OTHER;
    }

    private void generateRecommendations(@NonNull StorageAnalysisResult.Builder builder) {
        final List<StorageRecommendation> recommendations = new ArrayList<>();

        // Check cache recommendation
        if (builder.getTotalCacheSize() > 100L * 1024L * 1024L) { // 100MB+
            recommendations.add(new StorageRecommendation(
                    RecommendationType.CLEAR_CACHE,
                    mContext.getString(com.android.settings.R.string.storage_recommendation_clear_cache),
                    mContext.getString(com.android.settings.R.string.storage_recommendation_clear_cache_desc,
                            Formatter.formatFileSize(mContext, builder.getTotalCacheSize())),
                    builder.getTotalCacheSize(),
                    RecommendationPriority.HIGH
            ));
        }

        // Check old files recommendation
        final Long oldFilesSize = builder.getCategorySizes().get(FileCategory.OLD_FILES);
        if (oldFilesSize != null && oldFilesSize > 500L * 1024L * 1024L) { // 500MB+
            recommendations.add(new StorageRecommendation(
                    RecommendationType.CLEAR_OLD_FILES,
                    mContext.getString(com.android.settings.R.string.storage_recommendation_old_files),
                    mContext.getString(com.android.settings.R.string.storage_recommendation_old_files_desc,
                            Formatter.formatFileSize(mContext, oldFilesSize)),
                    oldFilesSize,
                    RecommendationPriority.MEDIUM
            ));
        }

        // Check APK files recommendation
        final Long apkSize = builder.getCategorySizes().get(FileCategory.APK_FILES);
        if (apkSize != null && apkSize > 50L * 1024L * 1024L) { // 50MB+
            recommendations.add(new StorageRecommendation(
                    RecommendationType.CLEAR_APK_FILES,
                    mContext.getString(com.android.settings.R.string.storage_recommendation_apk_files),
                    mContext.getString(com.android.settings.R.string.storage_recommendation_apk_files_desc,
                            Formatter.formatFileSize(mContext, apkSize)),
                    apkSize,
                    RecommendationPriority.LOW
            ));
        }

        // Check large apps recommendation
        if (!builder.getLargeApps().isEmpty()) {
            final long largeAppsSize = builder.getLargeApps().stream()
                    .mapToLong(a -> a.getDataSize() + a.getCacheSize())
                    .sum();
            recommendations.add(new StorageRecommendation(
                    RecommendationType.REVIEW_LARGE_APPS,
                    mContext.getString(com.android.settings.R.string.storage_recommendation_large_apps),
                    mContext.getString(com.android.settings.R.string.storage_recommendation_large_apps_desc,
                            builder.getLargeApps().size()),
                    largeAppsSize,
                    RecommendationPriority.MEDIUM
            ));
        }

        // Check storage health recommendation
        if (builder.getStorageHealth() == StorageHealth.CRITICAL) {
            recommendations.add(new StorageRecommendation(
                    RecommendationType.CRITICAL_STORAGE,
                    mContext.getString(com.android.settings.R.string.storage_recommendation_critical),
                    mContext.getString(com.android.settings.R.string.storage_recommendation_critical_desc),
                    builder.getAvailableStorage(),
                    RecommendationPriority.URGENT
            ));
        } else if (builder.getStorageHealth() == StorageHealth.LOW) {
            recommendations.add(new StorageRecommendation(
                    RecommendationType.LOW_STORAGE,
                    mContext.getString(com.android.settings.R.string.storage_recommendation_low),
                    mContext.getString(com.android.settings.R.string.storage_recommendation_low_desc),
                    builder.getAvailableStorage(),
                    RecommendationPriority.HIGH
            ));
        }

        // Sort by priority
        Collections.sort(recommendations, (a, b) ->
                Integer.compare(b.getPriority().getLevel(), a.getPriority().getLevel()));

        builder.setRecommendations(recommendations);
    }

    private void updateAnalysisDisplay() {
        if (mAnalyzerCategory == null || mLastAnalysisResult == null) {
            return;
        }

        mAnalyzerCategory.removeAll();

        // Add storage health indicator
        final Preference healthPref = createHealthPreference();
        mAnalyzerCategory.addPreference(healthPref);

        // Add recommendations
        for (StorageRecommendation recommendation : mLastAnalysisResult.getRecommendations()) {
            final Preference recPref = createRecommendationPreference(recommendation);
            mAnalyzerCategory.addPreference(recPref);
        }

        // Add category breakdown
        final Preference categoryPref = createCategoryPreference();
        mAnalyzerCategory.addPreference(categoryPref);
    }

    @NonNull
    private Preference createHealthPreference() {
        final Preference pref = new Preference(mContext);
        pref.setTitle(mContext.getString(com.android.settings.R.string.storage_health_title));

        final StorageHealth health = mLastAnalysisResult.getStorageHealth();
        final String healthStatus;
        final String healthSummary;

        switch (health) {
            case CRITICAL:
                healthStatus = mContext.getString(com.android.settings.R.string.storage_health_critical);
                healthSummary = mContext.getString(com.android.settings.R.string.storage_health_critical_summary);
                break;
            case LOW:
                healthStatus = mContext.getString(com.android.settings.R.string.storage_health_low);
                healthSummary = mContext.getString(com.android.settings.R.string.storage_health_low_summary);
                break;
            case WARNING:
                healthStatus = mContext.getString(com.android.settings.R.string.storage_health_warning);
                healthSummary = mContext.getString(com.android.settings.R.string.storage_health_warning_summary);
                break;
            default:
                healthStatus = mContext.getString(com.android.settings.R.string.storage_health_good);
                healthSummary = mContext.getString(com.android.settings.R.string.storage_health_good_summary);
        }

        pref.setSummary(mContext.getString(
                com.android.settings.R.string.storage_health_format,
                healthStatus,
                Formatter.formatFileSize(mContext, mLastAnalysisResult.getAvailableStorage())));

        return pref;
    }

    @NonNull
    private Preference createRecommendationPreference(@NonNull StorageRecommendation recommendation) {
        final Preference pref = new Preference(mContext);
        pref.setTitle(recommendation.getTitle());
        pref.setSummary(recommendation.getDescription());
        pref.setOnPreferenceClickListener(p -> {
            handleRecommendationClick(recommendation);
            return true;
        });
        return pref;
    }

    @NonNull
    private Preference createCategoryPreference() {
        final Preference pref = new Preference(mContext);
        pref.setTitle(mContext.getString(com.android.settings.R.string.storage_categories_title));

        final StringBuilder sb = new StringBuilder();
        final Map<FileCategory, Long> sizes = mLastAnalysisResult.getCategorySizes();

        for (FileCategory category : FileCategory.values()) {
            if (category == FileCategory.OTHER || category == FileCategory.OLD_FILES) {
                continue;
            }
            final Long size = sizes.get(category);
            if (size != null && size > 0) {
                if (sb.length() > 0) {
                    sb.append("  ");
                }
                sb.append(getCategoryName(category))
                        .append(": ")
                        .append(Formatter.formatFileSize(mContext, size));
            }
        }

        pref.setSummary(sb.toString());
        return pref;
    }

    @NonNull
    private String getCategoryName(@NonNull FileCategory category) {
        switch (category) {
            case IMAGES:
                return mContext.getString(com.android.settings.R.string.storage_category_images);
            case VIDEOS:
                return mContext.getString(com.android.settings.R.string.storage_category_videos);
            case AUDIO:
                return mContext.getString(com.android.settings.R.string.storage_category_audio);
            case DOCUMENTS:
                return mContext.getString(com.android.settings.R.string.storage_category_documents);
            case ARCHIVES:
                return mContext.getString(com.android.settings.R.string.storage_category_archives);
            case APK_FILES:
                return mContext.getString(com.android.settings.R.string.storage_category_apk);
            case CACHE:
                return mContext.getString(com.android.settings.R.string.storage_category_cache);
            default:
                return mContext.getString(com.android.settings.R.string.storage_category_other);
        }
    }

    private void handleRecommendationClick(@NonNull StorageRecommendation recommendation) {
        // This would typically open the relevant settings page
        // Implementation depends on the specific recommendation type
    }

    /**
     * Get the last analysis result.
     *
     * @return The last storage analysis result, or null if no analysis has been performed
     */
    @Nullable
    public StorageAnalysisResult getLastAnalysisResult() {
        return mLastAnalysisResult;
    }

    /**
     * Get storage health status.
     *
     * @return Current storage health
     */
    @NonNull
    public StorageHealth getStorageHealth() {
        return mLastAnalysisResult != null
                ? mLastAnalysisResult.getStorageHealth()
                : StorageHealth.UNKNOWN;
    }

    /**
     * Storage health enum.
     */
    public enum StorageHealth {
        GOOD,
        WARNING,
        LOW,
        CRITICAL,
        UNKNOWN
    }

    /**
     * File category enum.
     */
    public enum FileCategory {
        IMAGES,
        VIDEOS,
        AUDIO,
        DOCUMENTS,
        ARCHIVES,
        APK_FILES,
        CACHE,
        OLD_FILES,
        OTHER
    }

    /**
     * Recommendation type enum.
     */
    public enum RecommendationType {
        CLEAR_CACHE,
        CLEAR_OLD_FILES,
        CLEAR_APK_FILES,
        REVIEW_LARGE_APPS,
        CRITICAL_STORAGE,
        LOW_STORAGE
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
     * Data class for app storage information.
     */
    public static class AppStorageInfo {
        private final String mPackageName;
        private final String mAppName;
        private final android.graphics.drawable.Drawable mAppIcon;
        private final long mDataSize;
        private final long mCacheSize;
        private final long mLastUpdateTime;

        public AppStorageInfo(@NonNull String packageName, @NonNull String appName,
                @Nullable android.graphics.drawable.Drawable appIcon,
                long dataSize, long cacheSize, long lastUpdateTime) {
            mPackageName = packageName;
            mAppName = appName;
            mAppIcon = appIcon;
            mDataSize = dataSize;
            mCacheSize = cacheSize;
            mLastUpdateTime = lastUpdateTime;
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

        public long getDataSize() {
            return mDataSize;
        }

        public long getCacheSize() {
            return mCacheSize;
        }

        public long getLastUpdateTime() {
            return mLastUpdateTime;
        }
    }

    /**
     * Data class for storage recommendations.
     */
    public static class StorageRecommendation {
        private final RecommendationType mType;
        private final String mTitle;
        private final String mDescription;
        private final long mPotentialSavings;
        private final RecommendationPriority mPriority;

        public StorageRecommendation(@NonNull RecommendationType type,
                @NonNull String title, @NonNull String description,
                long potentialSavings, @NonNull RecommendationPriority priority) {
            mType = type;
            mTitle = title;
            mDescription = description;
            mPotentialSavings = potentialSavings;
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
        public String getDescription() {
            return mDescription;
        }

        public long getPotentialSavings() {
            return mPotentialSavings;
        }

        @NonNull
        public RecommendationPriority getPriority() {
            return mPriority;
        }
    }

    /**
     * Data class for storage analysis results.
     */
    public static class StorageAnalysisResult {
        private final long mTotalStorage;
        private final long mAvailableStorage;
        private final long mUsedStorage;
        private final int mUsedPercentage;
        private final StorageHealth mStorageHealth;
        private final List<AppStorageInfo> mAppStorageList;
        private final List<AppStorageInfo> mLargeApps;
        private final long mTotalCacheSize;
        private final long mTotalDataSize;
        private final Map<FileCategory, Long> mCategorySizes;
        private final Map<FileCategory, Integer> mCategoryCounts;
        private final List<StorageRecommendation> mRecommendations;

        private StorageAnalysisResult(Builder builder) {
            mTotalStorage = builder.mTotalStorage;
            mAvailableStorage = builder.mAvailableStorage;
            mUsedStorage = builder.mUsedStorage;
            mUsedPercentage = builder.mUsedPercentage;
            mStorageHealth = builder.mStorageHealth;
            mAppStorageList = builder.mAppStorageList;
            mLargeApps = builder.mLargeApps;
            mTotalCacheSize = builder.mTotalCacheSize;
            mTotalDataSize = builder.mTotalDataSize;
            mCategorySizes = builder.mCategorySizes;
            mCategoryCounts = builder.mCategoryCounts;
            mRecommendations = builder.mRecommendations;
        }

        public long getTotalStorage() {
            return mTotalStorage;
        }

        public long getAvailableStorage() {
            return mAvailableStorage;
        }

        public long getUsedStorage() {
            return mUsedStorage;
        }

        public int getUsedPercentage() {
            return mUsedPercentage;
        }

        @NonNull
        public StorageHealth getStorageHealth() {
            return mStorageHealth;
        }

        @NonNull
        public List<AppStorageInfo> getAppStorageList() {
            return mAppStorageList;
        }

        @NonNull
        public List<AppStorageInfo> getLargeApps() {
            return mLargeApps;
        }

        public long getTotalCacheSize() {
            return mTotalCacheSize;
        }

        public long getTotalDataSize() {
            return mTotalDataSize;
        }

        @NonNull
        public Map<FileCategory, Long> getCategorySizes() {
            return mCategorySizes;
        }

        @NonNull
        public Map<FileCategory, Integer> getCategoryCounts() {
            return mCategoryCounts;
        }

        @NonNull
        public List<StorageRecommendation> getRecommendations() {
            return mRecommendations;
        }

        /**
         * Builder for StorageAnalysisResult.
         */
        public static class Builder {
            private long mTotalStorage;
            private long mAvailableStorage;
            private long mUsedStorage;
            private int mUsedPercentage;
            private StorageHealth mStorageHealth = StorageHealth.UNKNOWN;
            private List<AppStorageInfo> mAppStorageList = new ArrayList<>();
            private List<AppStorageInfo> mLargeApps = new ArrayList<>();
            private long mTotalCacheSize;
            private long mTotalDataSize;
            private Map<FileCategory, Long> mCategorySizes = new HashMap<>();
            private Map<FileCategory, Integer> mCategoryCounts = new HashMap<>();
            private List<StorageRecommendation> mRecommendations = new ArrayList<>();

            public Builder setTotalStorage(long totalStorage) {
                mTotalStorage = totalStorage;
                return this;
            }

            public Builder setAvailableStorage(long availableStorage) {
                mAvailableStorage = availableStorage;
                return this;
            }

            public Builder setUsedStorage(long usedStorage) {
                mUsedStorage = usedStorage;
                return this;
            }

            public Builder setUsedPercentage(int usedPercentage) {
                mUsedPercentage = usedPercentage;
                return this;
            }

            public Builder setStorageHealth(@NonNull StorageHealth storageHealth) {
                mStorageHealth = storageHealth;
                return this;
            }

            public Builder setAppStorageList(@NonNull List<AppStorageInfo> appStorageList) {
                mAppStorageList = appStorageList;
                return this;
            }

            public Builder setLargeApps(@NonNull List<AppStorageInfo> largeApps) {
                mLargeApps = largeApps;
                return this;
            }

            public Builder setTotalCacheSize(long totalCacheSize) {
                mTotalCacheSize = totalCacheSize;
                return this;
            }

            public Builder setTotalDataSize(long totalDataSize) {
                mTotalDataSize = totalDataSize;
                return this;
            }

            public Builder setCategorySizes(@NonNull Map<FileCategory, Long> categorySizes) {
                mCategorySizes = categorySizes;
                return this;
            }

            public Builder setCategoryCounts(@NonNull Map<FileCategory, Integer> categoryCounts) {
                mCategoryCounts = categoryCounts;
                return this;
            }

            public Builder setRecommendations(@NonNull List<StorageRecommendation> recommendations) {
                mRecommendations = recommendations;
                return this;
            }

            public long getTotalCacheSize() {
                return mTotalCacheSize;
            }

            public Map<FileCategory, Long> getCategorySizes() {
                return mCategorySizes;
            }

            public List<AppStorageInfo> getLargeApps() {
                return mLargeApps;
            }

            public StorageHealth getStorageHealth() {
                return mStorageHealth;
            }

            public long getAvailableStorage() {
                return mAvailableStorage;
            }

            public StorageAnalysisResult build() {
                return new StorageAnalysisResult(this);
            }
        }
    }
}