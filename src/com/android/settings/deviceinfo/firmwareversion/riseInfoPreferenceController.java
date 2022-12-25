/*
 * Copyright (C) 2020 Wave-OS
 * Copyright (C) 2023 the RisingOS Android Project
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

package com.android.settings.deviceinfo.firmwareversion;

import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.widget.TextView;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.widget.LayoutPreference;

public class riseInfoPreferenceController extends AbstractPreferenceController {

    private static final String KEY_RISING_INFO = "rising_info";
    private static final String KEY_RISING_DEVICE = "rising_device";
    private static final String KEY_RISING_VERSION = "rising_version";
    private static final String KEY_BUILD_STATUS = "rom_build_status";
    private static final String KEY_BUILD_VERSION = "rising_build_version";
    
    private static final String PROP_RISING_VERSION = "ro.rising.version";
    private static final String PROP_RISING_VERSION_CODE = "ro.rising.code";
    private static final String PROP_RISING_RELEASETYPE = "ro.rising.releasetype";
    private static final String PROP_RISING_MAINTAINER = "ro.rising.maintainer";
    private static final String PROP_RISING_DEVICE = "ro.rising.device";
    private static final String PROP_RISING_BUILD_TYPE = "ro.rising.packagetype";
    private static final String PROP_RISING_BUILD_VERSION = "ro.rising.build.version";


    public riseInfoPreferenceController(Context context) {
        super(context);
    }

    private String getDeviceName() {
        String device = SystemProperties.get(PROP_RISING_DEVICE, "");
        if (device.equals("")) {
            device = Build.MANUFACTURER + " " + Build.MODEL;
        }
        return device;
    }

    private String getRisingBuildVersion() {
        final String buildVer = SystemProperties.get(PROP_RISING_BUILD_VERSION,
                this.mContext.getString(R.string.device_info_default));;

        return buildVer;
    }
    
    private String getRisingVersion() {
        final String version = SystemProperties.get(PROP_RISING_VERSION,
                this.mContext.getString(R.string.device_info_default));
        final String versionCode = SystemProperties.get(PROP_RISING_VERSION_CODE,
                this.mContext.getString(R.string.device_info_default));
        final String buildType = SystemProperties.get(PROP_RISING_BUILD_TYPE,
                this.mContext.getString(R.string.device_info_default));

        return version + " | " + versionCode + " | " + buildType;
    }

    private String getRisingReleaseType() {
        final String releaseType = SystemProperties.get(PROP_RISING_RELEASETYPE,
                this.mContext.getString(R.string.device_info_default));
	
        return releaseType.substring(0, 1).toUpperCase() +
                 releaseType.substring(1).toLowerCase();
    }
    
    private String getRisingbuildStatus() {
	final String buildType = SystemProperties.get(PROP_RISING_RELEASETYPE,
                this.mContext.getString(R.string.device_info_default));
        final String isOfficial = this.mContext.getString(R.string.build_is_official_title);
	final String isCommunity = this.mContext.getString(R.string.build_is_community_title);
	
	if (buildType.toLowerCase().equals("official")) {
		return isOfficial;
	} else {
		return isCommunity;
	}
    }

    private String getRisingMaintainer() {
	final String RisingMaintainer = SystemProperties.get(PROP_RISING_MAINTAINER,
                this.mContext.getString(R.string.device_info_default));
	final String buildType = SystemProperties.get(PROP_RISING_RELEASETYPE,
                this.mContext.getString(R.string.device_info_default));
        final String isOffFine = this.mContext.getString(R.string.build_is_official_summary, RisingMaintainer);
	final String isOffMiss = this.mContext.getString(R.string.build_is_official_summary_oopsie);
	final String isCommMiss = this.mContext.getString(R.string.build_is_community_summary_oopsie);
	final String isCommFine = this.mContext.getString(R.string.build_is_community_summary, RisingMaintainer);
	
	if (buildType.toLowerCase().equals("official") && !RisingMaintainer.equalsIgnoreCase("Unknown")) {
	    return isOffFine;
	} else if (buildType.toLowerCase().equals("official") && RisingMaintainer.equalsIgnoreCase("Unknown")) {
	     return isOffMiss;
	} else if (buildType.equalsIgnoreCase("Community") && RisingMaintainer.equalsIgnoreCase("Unknown")) {
	     return isCommMiss;
	} else {
	    return isCommFine;
	}
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        final Preference arcVerPref = screen.findPreference(KEY_RISING_VERSION);
        final Preference arcDevPref = screen.findPreference(KEY_RISING_DEVICE);
        final Preference buildStatusPref = screen.findPreference(KEY_BUILD_STATUS);
        final Preference buildVerPref = screen.findPreference(KEY_BUILD_VERSION);
        final String RisingVersion = getRisingVersion();
        final String RisingDevice = getDeviceName();
        final String RisingReleaseType = getRisingReleaseType();
        final String RisingMaintainer = getRisingMaintainer();
	final String buildStatus = getRisingbuildStatus();
	final String buildVer = getRisingBuildVersion();
	final String isOfficial = SystemProperties.get(PROP_RISING_RELEASETYPE,
                this.mContext.getString(R.string.device_info_default));
	buildStatusPref.setTitle(buildStatus);
	buildStatusPref.setSummary(RisingMaintainer);
	buildVerPref.setSummary(buildVer);
        arcVerPref.setSummary(RisingVersion);
        arcDevPref.setSummary(RisingDevice);
	if (isOfficial.toLowerCase().contains("official")) {
		 buildStatusPref.setIcon(R.drawable.verified);
	} else {
		buildStatusPref.setIcon(R.drawable.unverified);
	}
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_RISING_INFO;
    }
}
