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
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.PermissionManager;
import android.permission.PermissionManager.PermissionUsageFlag;
import android.text.TextUtils;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnResume;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Controller for displaying a timeline of permission usage by apps.
 * Shows when apps accessed sensitive permissions like location, camera, microphone.
 */
public class PermissionUsageTimelineController extends BasePreferenceController
        implements LifecycleObserver, OnResume {

    private static final String TAG = "PermissionUsageTimeline";

    // Sensitive permissions to track
    private static final String[] SENSITIVE_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION
    };

    // Permission group names for display
    private static final Map<String, String> PERMISSION_GROUP_MAP = new HashMap<>();
    static {
        PERMISSION_GROUP_MAP.put(Manifest.permission.ACCESS_FINE_LOCATION, "location");
        PERMISSION_GROUP_MAP.put(Manifest.permission.ACCESS_COARSE_LOCATION, "location");
        PERMISSION_GROUP_MAP.put(Manifest.permission.CAMERA, "camera");
        PERMISSION_GROUP_MAP.put(Manifest.permission.RECORD_AUDIO, "microphone");
        PERMISSION_GROUP_MAP.put(Manifest.permission.READ_CONTACTS, "contacts");
        PERMISSION_GROUP_MAP.put(Manifest.permission.READ_CALENDAR, "calendar");
        PERMISSION_GROUP_MAP.put(Manifest.permission.READ_CALL_LOG, "call_log");
        PERMISSION_GROUP_MAP.put(Manifest.permission.READ_PHONE_STATE, "phone");
        PERMISSION_GROUP_MAP.put(Manifest.permission.BODY_SENSORS, "sensors");
        PERMISSION_GROUP_MAP.put(Manifest.permission.ACTIVITY_RECOGNITION, "activity");
    }

    private final PackageManager mPackageManager;
    private final PermissionManager mPermissionManager;
    private final AppOpsManager mAppOpsManager;
    private final UserManager mUserManager;

    private PreferenceCategory mTimelineCategory;
    private List<PermissionUsageEvent> mUsageEvents;

    public PermissionUsageTimelineController(@NonNull Context context, @NonNull String preferenceKey) {
        super(context, preferenceKey);
        mPackageManager = context.getPackageManager();
        mPermissionManager = context.getSystemService(PermissionManager.class);
        mAppOpsManager = context.getSystemService(AppOpsManager.class);
        mUserManager = context.getSystemService(UserManager.class);
        mUsageEvents = new ArrayList<>();
    }

    @Override
    public int getAvailabilityStatus() {
        return mPermissionManager != null ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(@NonNull PreferenceScreen screen) {
        super.displayPreference(screen);
        mTimelineCategory = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onResume() {
        refreshUsageTimeline();
    }

    /**
     * Refresh the permission usage timeline with latest data.
     */
    public void refreshUsageTimeline() {
        mUsageEvents.clear();
        loadPermissionUsageEvents();
        Collections.sort(mUsageEvents, Comparator.comparingLong(PermissionUsageEvent::getTimestamp).reversed());
        updateTimelineDisplay();
    }

    private void loadPermissionUsageEvents() {
        final long now = System.currentTimeMillis();
        final long startTime = now - TimeUnit.HOURS.toMillis(24); // Last 24 hours

        for (String permission : SENSITIVE_PERMISSIONS) {
            try {
                loadEventsForPermission(permission, startTime, now);
            } catch (Exception e) {
                // Continue with other permissions if one fails
            }
        }
    }

    private void loadEventsForPermission(@NonNull String permission, long startTime, long endTime) {
        if (mPermissionManager == null) {
            return;
        }

        // Get all packages that have accessed this permission
        final List<String> accessingPackages = getPackageNamesWithPermissionUsage(
                permission, startTime, endTime);

        for (String packageName : accessingPackages) {
            try {
                final ApplicationInfo appInfo = mPackageManager.getApplicationInfo(packageName, 0);
                final long lastAccessTime = getLastPermissionAccessTime(packageName, permission);

                if (lastAccessTime > 0) {
                    final PermissionUsageEvent event = new PermissionUsageEvent(
                            packageName,
                            appInfo.loadLabel(mPackageManager).toString(),
                            appInfo.loadIcon(mPackageManager),
                            permission,
                            PERMISSION_GROUP_MAP.getOrDefault(permission, "other"),
                            lastAccessTime,
                            getAccessDuration(packageName, permission),
                            getAccessType(packageName, permission)
                    );
                    mUsageEvents.add(event);
                }
            } catch (PackageManager.NameNotFoundException e) {
                // Package no longer exists, skip
            }
        }
    }

    @NonNull
    private List<String> getPackageNamesWithPermissionUsage(
            @NonNull String permission, long startTime, long endTime) {
        final List<String> packages = new ArrayList<>();

        try {
            // Get packages that have this permission granted
            final List<ApplicationInfo> installedApps = mPackageManager.getInstalledApplications(
                    PackageManager.GET_META_DATA);

            for (ApplicationInfo appInfo : installedApps) {
                if (appInfo.packageName.equals("android") || appInfo.packageName.startsWith("com.android.")) {
                    continue; // Skip system packages
                }

                if (mPackageManager.checkPermission(permission, appInfo.packageName)
                        == PackageManager.PERMISSION_GRANTED) {
                    // Check if there was recent usage via AppOps
                    if (hasRecentAppOpUsage(appInfo.packageName, permission, startTime)) {
                        packages.add(appInfo.packageName);
                    }
                }
            }
        } catch (Exception e) {
            // Return empty list on error
        }

        return packages;
    }

    private boolean hasRecentAppOpUsage(@NonNull String packageName, @NonNull String permission, long startTime) {
        final String appOp = permissionToAppOp(permission);
        if (appOp == null) {
            return false;
        }

        try {
            final List<AppOpsManager.OpEntry> ops = mAppOpsManager.getOpsForPackage(
                    UserHandle.myUserId(), packageName, new String[]{appOp});

            if (ops != null && !ops.isEmpty()) {
                for (AppOpsManager.OpEntry op : ops) {
                    if (op.getLastAccessTime() >= startTime) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }

        return false;
    }

    @Nullable
    private String permissionToAppOp(@NonNull String permission) {
        return AppOpsManager.permissionToOp(permission);
    }

    private long getLastPermissionAccessTime(@NonNull String packageName, @NonNull String permission) {
        final String appOp = permissionToAppOp(permission);
        if (appOp == null) {
            return 0;
        }

        try {
            final List<AppOpsManager.OpEntry> ops = mAppOpsManager.getOpsForPackage(
                    UserHandle.myUserId(), packageName, new String[]{appOp});

            if (ops != null && !ops.isEmpty()) {
                return ops.get(0).getLastAccessTime();
            }
        } catch (Exception e) {
            // Ignore errors
        }

        return 0;
    }

    private long getAccessDuration(@NonNull String packageName, @NonNull String permission) {
        final String appOp = permissionToAppOp(permission);
        if (appOp == null) {
            return 0;
        }

        try {
            final List<AppOpsManager.OpEntry> ops = mAppOpsManager.getOpsForPackage(
                    UserHandle.myUserId(), packageName, new String[]{appOp});

            if (ops != null && !ops.isEmpty()) {
                return ops.get(0).getDuration();
            }
        } catch (Exception e) {
            // Ignore errors
        }

        return 0;
    }

    @NonNull
    private AccessType getAccessType(@NonNull String packageName, @NonNull String permission) {
        final String appOp = permissionToAppOp(permission);
        if (appOp == null) {
            return AccessType.UNKNOWN;
        }

        try {
            final List<AppOpsManager.OpEntry> ops = mAppOpsManager.getOpsForPackage(
                    UserHandle.myUserId(), packageName, new String[]{appOp});

            if (ops != null && !ops.isEmpty()) {
                final int mode = ops.get(0).getMode();
                if (mode == AppOpsManager.MODE_FOREGROUND) {
                    return AccessType.FOREGROUND;
                } else if (mode == AppOpsManager.MODE_ALLOWED) {
                    return AccessType.BACKGROUND;
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }

        return AccessType.UNKNOWN;
    }

    private void updateTimelineDisplay() {
        if (mTimelineCategory == null) {
            return;
        }

        mTimelineCategory.removeAll();

        if (mUsageEvents.isEmpty()) {
            final Preference emptyPref = new Preference(mContext);
            emptyPref.setTitle(mContext.getString(com.android.settings.R.string.permission_usage_no_recent_access));
            emptyPref.setSelectable(false);
            mTimelineCategory.addPreference(emptyPref);
            return;
        }

        // Group events by time period
        final List<PermissionUsageEvent> recentEvents = new ArrayList<>();
        final List<PermissionUsageEvent> olderEvents = new ArrayList<>();
        final long oneHourAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);

        for (PermissionUsageEvent event : mUsageEvents) {
            if (event.getTimestamp() >= oneHourAgo) {
                recentEvents.add(event);
            } else {
                olderEvents.add(event);
            }
        }

        // Add recent events section
        if (!recentEvents.isEmpty()) {
            addTimelineSection(
                    mContext.getString(com.android.settings.R.string.permission_usage_last_hour),
                    recentEvents);
        }

        // Add older events section
        if (!olderEvents.isEmpty()) {
            addTimelineSection(
                    mContext.getString(com.android.settings.R.string.permission_usage_older),
                    olderEvents);
        }
    }

    private void addTimelineSection(@NonNull String title, @NonNull List<PermissionUsageEvent> events) {
        final PreferenceCategory section = new PreferenceCategory(mContext);
        section.setTitle(title);
        mTimelineCategory.addPreference(section);

        for (PermissionUsageEvent event : events) {
            final Preference eventPref = createEventPreference(event);
            section.addPreference(eventPref);
        }
    }

    @NonNull
    private Preference createEventPreference(@NonNull PermissionUsageEvent event) {
        final Preference pref = new Preference(mContext);
        pref.setTitle(event.getAppName());
        pref.setIcon(event.getAppIcon());

        final String timeStr = formatTimestamp(event.getTimestamp());
        final String permissionStr = getPermissionDisplayName(event.getPermissionGroup());
        final String accessTypeStr = getAccessTypeDisplay(event.getAccessType());

        pref.setSummary(mContext.getString(
                com.android.settings.R.string.permission_usage_event_summary,
                permissionStr, timeStr, accessTypeStr));

        // Set intent to app permission details
        pref.setOnPreferenceClickListener(p -> {
            openAppPermissionSettings(event.getPackageName());
            return true;
        });

        return pref;
    }

    @NonNull
    private String formatTimestamp(long timestamp) {
        final long now = System.currentTimeMillis();
        final long diff = now - timestamp;

        if (diff < TimeUnit.MINUTES.toMillis(1)) {
            return mContext.getString(com.android.settings.R.string.permission_usage_just_now);
        } else if (diff < TimeUnit.HOURS.toMillis(1)) {
            final int minutes = (int) TimeUnit.MILLISECONDS.toMinutes(diff);
            return mContext.getString(
                    com.android.settings.R.string.permission_usage_minutes_ago, minutes);
        } else if (diff < TimeUnit.DAYS.toMillis(1)) {
            final int hours = (int) TimeUnit.MILLISECONDS.toHours(diff);
            return mContext.getString(
                    com.android.settings.R.string.permission_usage_hours_ago, hours);
        } else {
            return DateUtils.formatDateTime(mContext, timestamp,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE);
        }
    }

    @NonNull
    private String getPermissionDisplayName(@NonNull String permissionGroup) {
        switch (permissionGroup) {
            case "location":
                return mContext.getString(com.android.settings.R.string.permission_group_location);
            case "camera":
                return mContext.getString(com.android.settings.R.string.permission_group_camera);
            case "microphone":
                return mContext.getString(com.android.settings.R.string.permission_group_microphone);
            case "contacts":
                return mContext.getString(com.android.settings.R.string.permission_group_contacts);
            case "calendar":
                return mContext.getString(com.android.settings.R.string.permission_group_calendar);
            case "call_log":
                return mContext.getString(com.android.settings.R.string.permission_group_call_log);
            case "phone":
                return mContext.getString(com.android.settings.R.string.permission_group_phone);
            case "sensors":
                return mContext.getString(com.android.settings.R.string.permission_group_sensors);
            case "activity":
                return mContext.getString(com.android.settings.R.string.permission_group_activity);
            default:
                return mContext.getString(com.android.settings.R.string.permission_group_other);
        }
    }

    @NonNull
    private String getAccessTypeDisplay(@NonNull AccessType accessType) {
        switch (accessType) {
            case FOREGROUND:
                return mContext.getString(com.android.settings.R.string.permission_access_foreground);
            case BACKGROUND:
                return mContext.getString(com.android.settings.R.string.permission_access_background);
            default:
                return "";
        }
    }

    private void openAppPermissionSettings(@NonNull String packageName) {
        // This would typically open the app's permission settings page
        // Implementation depends on the specific Settings activity structure
    }

    /**
     * Get all permission usage events.
     *
     * @return List of permission usage events
     */
    @NonNull
    public List<PermissionUsageEvent> getUsageEvents() {
        return new ArrayList<>(mUsageEvents);
    }

    /**
     * Get usage events for a specific permission group.
     *
     * @param permissionGroup The permission group to filter by
     * @return List of events for that permission group
     */
    @NonNull
    public List<PermissionUsageEvent> getEventsForPermissionGroup(@NonNull String permissionGroup) {
        final List<PermissionUsageEvent> filtered = new ArrayList<>();
        for (PermissionUsageEvent event : mUsageEvents) {
            if (permissionGroup.equals(event.getPermissionGroup())) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    /**
     * Get usage events for a specific app.
     *
     * @param packageName The package name to filter by
     * @return List of events for that app
     */
    @NonNull
    public List<PermissionUsageEvent> getEventsForPackage(@NonNull String packageName) {
        final List<PermissionUsageEvent> filtered = new ArrayList<>();
        for (PermissionUsageEvent event : mUsageEvents) {
            if (packageName.equals(event.getPackageName())) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    /**
     * Access type enum for permission usage.
     */
    public enum AccessType {
        FOREGROUND,
        BACKGROUND,
        UNKNOWN
    }

    /**
     * Data class representing a permission usage event.
     */
    public static class PermissionUsageEvent {
        private final String mPackageName;
        private final String mAppName;
        private final Drawable mAppIcon;
        private final String mPermission;
        private final String mPermissionGroup;
        private final long mTimestamp;
        private final long mDuration;
        private final AccessType mAccessType;

        public PermissionUsageEvent(@NonNull String packageName, @NonNull String appName,
                @Nullable Drawable appIcon, @NonNull String permission,
                @NonNull String permissionGroup, long timestamp, long duration,
                @NonNull AccessType accessType) {
            mPackageName = packageName;
            mAppName = appName;
            mAppIcon = appIcon;
            mPermission = permission;
            mPermissionGroup = permissionGroup;
            mTimestamp = timestamp;
            mDuration = duration;
            mAccessType = accessType;
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
        public Drawable getAppIcon() {
            return mAppIcon;
        }

        @NonNull
        public String getPermission() {
            return mPermission;
        }

        @NonNull
        public String getPermissionGroup() {
            return mPermissionGroup;
        }

        public long getTimestamp() {
            return mTimestamp;
        }

        public long getDuration() {
            return mDuration;
        }

        @NonNull
        public AccessType getAccessType() {
            return mAccessType;
        }
    }
}