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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;

/**
 * A custom preference for displaying Google account information with avatar
 * and account switcher functionality on the Settings homepage.
 */
public class GoogleAccountTilePreference extends Preference {

    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";

    private Bitmap mAvatarBitmap;
    private String mAccountName;
    private String mAccountEmail;
    private Account[] mGoogleAccounts;
    private boolean mHasGoogleAccount = false;

    public GoogleAccountTilePreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.google_account_tile);
    }

    public GoogleAccountTilePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.google_account_tile);
    }

    public GoogleAccountTilePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.google_account_tile);
    }

    public GoogleAccountTilePreference(Context context) {
        super(context);
        setLayoutResource(R.layout.google_account_tile);
    }

    /**
     * Sets the avatar bitmap for the account.
     */
    public void setAvatar(Bitmap avatar) {
        mAvatarBitmap = avatar;
        notifyChanged();
    }

    /**
     * Sets the account name to display.
     */
    public void setAccountName(String name) {
        mAccountName = name;
        notifyChanged();
    }

    /**
     * Sets the account email to display.
     */
    public void setAccountEmail(String email) {
        mAccountEmail = email;
        notifyChanged();
    }

    /**
     * Sets the list of Google accounts available.
     */
    public void setGoogleAccounts(Account[] accounts) {
        mGoogleAccounts = accounts;
        mHasGoogleAccount = accounts != null && accounts.length > 0;
        notifyChanged();
    }

    /**
     * Returns whether there is at least one Google account on the device.
     */
    public boolean hasGoogleAccount() {
        return mHasGoogleAccount;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        ImageView avatarView = (ImageView) holder.findViewById(R.id.account_avatar);
        TextView nameView = (TextView) holder.findViewById(R.id.account_name);
        TextView emailView = (TextView) holder.findViewById(R.id.account_email);
        View switcherContainer = holder.findViewById(R.id.switcher_container);
        ImageView switcherIcon = (ImageView) holder.findViewById(R.id.switcher_icon);

        // Set avatar
        if (mAvatarBitmap != null) {
            avatarView.setImageBitmap(mAvatarBitmap);
        } else {
            // Use default avatar icon
            avatarView.setImageResource(R.drawable.ic_account_circle_24dp);
        }

        // Set account name
        if (mAccountName != null) {
            nameView.setText(mAccountName);
        } else {
            nameView.setText(R.string.google_account_tile_title);
        }

        // Set account email
        if (mAccountEmail != null) {
            emailView.setText(mAccountEmail);
            emailView.setVisibility(View.VISIBLE);
        } else {
            emailView.setVisibility(View.GONE);
        }

        // Show/hide switcher based on number of accounts
        if (mGoogleAccounts != null && mGoogleAccounts.length > 1) {
            switcherContainer.setVisibility(View.VISIBLE);
        } else {
            switcherContainer.setVisibility(View.GONE);
        }
    }
}
