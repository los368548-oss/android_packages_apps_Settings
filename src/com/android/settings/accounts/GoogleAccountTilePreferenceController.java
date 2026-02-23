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

package com.android.settings.accounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.utils.ThreadUtils;

/**
 * Controller for the Google Account tile on the Settings homepage.
 * Displays the current Google account with avatar and allows switching accounts.
 */
public class GoogleAccountTilePreferenceController extends BasePreferenceController
        implements LifecycleObserver, OnStart {

    private static final String TAG = "GoogleAccountTile";
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";

    private static final String METHOD_GET_ACCOUNT_AVATAR = "getAccountAvatar";
    private static final String KEY_AVATAR_BITMAP = "account_avatar";
    private static final String KEY_ACCOUNT_NAME = "account_name";

    private GoogleAccountTilePreference mPreference;
    private Account[] mGoogleAccounts;
    private Account mSelectedAccount;

    public GoogleAccountTilePreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        loadGoogleAccounts();
    }

    @Override
    public int getAvailabilityStatus() {
        // Show the tile only if there is at least one Google account
        if (mGoogleAccounts != null && mGoogleAccounts.length > 0) {
            return AVAILABLE;
        }
        return CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        if (mPreference != null) {
            updatePreference();
        }
    }

    @Override
    public void onStart() {
        // Refresh account data when the screen starts
        loadGoogleAccounts();
        if (mPreference != null) {
            updatePreference();
        }
    }

    @Override
    public boolean handlePreferenceTreeClick(Preference preference) {
        if (!TextUtils.equals(preference.getKey(), getPreferenceKey())) {
            return false;
        }

        // If multiple accounts, show account picker dialog
        if (mGoogleAccounts != null && mGoogleAccounts.length > 1) {
            showAccountPickerDialog();
        } else {
            // Single account - open account settings
            openAccountSettings();
        }
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        loadGoogleAccounts();
        updatePreference();
    }

    private void loadGoogleAccounts() {
        try {
            AccountManager accountManager = AccountManager.get(mContext);
            mGoogleAccounts = accountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE);

            if (mGoogleAccounts != null && mGoogleAccounts.length > 0) {
                // Select the first account by default
                mSelectedAccount = mGoogleAccounts[0];
            } else {
                mSelectedAccount = null;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "SecurityException while getting accounts", e);
            mGoogleAccounts = null;
            mSelectedAccount = null;
        }
    }

    private void updatePreference() {
        if (mPreference == null || mSelectedAccount == null) {
            return;
        }

        // Set account email
        mPreference.setAccountEmail(mSelectedAccount.name);

        // Try to get account display name (usually the person's name)
        String displayName = getAccountDisplayName(mSelectedAccount);
        mPreference.setAccountName(displayName);

        // Set the list of accounts for switcher visibility
        mPreference.setGoogleAccounts(mGoogleAccounts);

        // Load avatar asynchronously
        loadAccountAvatar(mSelectedAccount);
    }

    private String getAccountDisplayName(Account account) {
        // Extract the name part from email (before @)
        if (account != null && !TextUtils.isEmpty(account.name)) {
            int atIndex = account.name.indexOf('@');
            if (atIndex > 0) {
                return account.name.substring(0, atIndex);
            }
        }
        return account.name;
    }

    private void loadAccountAvatar(Account account) {
        if (account == null) {
            return;
        }

        ThreadUtils.postOnBackgroundThread(() -> {
            try {
                final String authority = queryProviderAuthority();
                if (TextUtils.isEmpty(authority)) {
                    return;
                }

                final Uri uri = new Uri.Builder()
                        .scheme(ContentResolver.SCHEME_CONTENT)
                        .authority(authority)
                        .build();

                final Bundle bundle = mContext.getContentResolver().call(
                        uri, METHOD_GET_ACCOUNT_AVATAR, account.name, null);

                if (bundle != null) {
                    final Bitmap bitmap = bundle.getParcelable(KEY_AVATAR_BITMAP);
                    if (bitmap != null && mPreference != null) {
                        ThreadUtils.postOnMainThread(() -> {
                            if (mPreference != null) {
                                mPreference.setAvatar(bitmap);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error loading account avatar", e);
            }
        });
    }

    private String queryProviderAuthority() {
        // Use the same provider authority as AvatarViewMixin
        try {
            return mContext.getResources().getString(
                    R.string.config_account_provider_authority);
        } catch (Exception e) {
            return null;
        }
    }

    private void showAccountPickerDialog() {
        // Create and show account picker dialog
        // This will be implemented with a dialog or bottom sheet
        // For now, open the account dashboard
        openAccountSettings();
    }

    private void openAccountSettings() {
        // Log the click event
        FeatureFactory.getFeatureFactory().getMetricsFeatureProvider()
                .logSettingsTileClick(getPreferenceKey(), SettingsEnums.SETTINGS_HOMEPAGE);

        // The actual navigation is handled by the preference's fragment or intent
    }

    /**
     * Selects a different Google account.
     */
    public void selectAccount(Account account) {
        if (account != null && account.type.equals(GOOGLE_ACCOUNT_TYPE)) {
            mSelectedAccount = account;
            updatePreference();
        }
    }

    /**
     * Returns the currently selected Google account.
     */
    public Account getSelectedAccount() {
        return mSelectedAccount;
    }

    /**
     * Returns all Google accounts on the device.
     */
    public Account[] getGoogleAccounts() {
        return mGoogleAccounts;
    }
}
