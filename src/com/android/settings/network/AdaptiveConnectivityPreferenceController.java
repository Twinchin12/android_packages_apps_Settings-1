/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

/**
 * {@link BasePreferenceController} that shows Adaptive connectivity on/off state.
 */
public class AdaptiveConnectivityPreferenceController extends BasePreferenceController {

    public boolean mIsEnabled;

    public AdaptiveConnectivityPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mIsEnabled = mContext.getResources().getBoolean(R.bool.config_show_adaptive_connectivity);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        
       final Preference preference = screen.findPreference(getPreferenceKey());
        
       if (!mIsEnabled || preference == null || !preference.isVisible()) {
            return;
        }

        preference.setTitle(R.string.adaptive_connectivity_title);
        preference.setIcon(R.drawable.ic_adaptive_connectivity);
    }

    @Override
    public int getAvailabilityStatus() {
        return mIsEnabled ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public CharSequence getSummary() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.ADAPTIVE_CONNECTIVITY_ENABLED, 1) == 1
                ? mContext.getString(R.string.adaptive_connectivity_switch_on)
                : mContext.getString(R.string.adaptive_connectivity_switch_off);
    }
}
