/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.homepage;

import static com.android.settings.search.actionbar.SearchMenuController.NEED_SEARCH_ICON_IN_ACTION_BAR;
import static com.android.settingslib.search.SearchIndexable.MOBILE;

import android.app.ActivityManager;
import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;
import androidx.window.embedding.SplitController;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settings.activityembedding.ActivityEmbeddingUtils;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.support.SupportPreferenceController;
import com.android.settings.widget.HomepagePreference;
import com.android.settings.widget.HomepagePreferenceLayoutHelper.HomepagePreferenceLayout;
import com.android.settingslib.core.instrumentation.Instrumentable;
import com.android.settingslib.drawer.Tile;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.LayoutPreference;
import com.android.settings.widget.EntityHeaderController;

@SearchIndexable(forTarget = MOBILE)
public class TopLevelSettings extends DashboardFragment implements SplitLayoutListener,
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private static final String TAG = "TopLevelSettings";
    private static final String SAVED_HIGHLIGHT_MIXIN = "highlight_mixin";
    private static final String PREF_KEY_SUPPORT = "top_level_support";
    private static final String KEY_USER_CARD = "top_level_usercard";
    private int mDashBoardStyle;
    private int mAboutPhoneStyle;

    private boolean mIsEmbeddingActivityEnabled;
    private TopLevelHighlightMixin mHighlightMixin;
    private int mPaddingHorizontal;
    private boolean mScrollNeeded = true;
    private boolean mFirstStarted = true;
    private boolean gAppsExists;

    public TopLevelSettings() {
        final Bundle args = new Bundle();
        // Disable the search icon because this page uses a full search view in actionbar.
        args.putBoolean(NEED_SEARCH_ICON_IN_ACTION_BAR, false);
        setArguments(args);
    }

    @Override
    protected int getPreferenceScreenResId() {
        switch (mDashBoardStyle) {
           case 0:
               return R.xml.top_level_settings;
           case 1:
               return R.xml.top_level_settings_oos;
           case 2:
               return R.xml.top_level_settings_arc;
           case 3:
               return R.xml.top_level_settings_aosp;
	   case 4:
               return R.xml.top_level_settings_mt;
	   case 5:
               return R.xml.top_level_settings_card;
           default:
               return R.xml.top_level_settings;
        }
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DASHBOARD_SUMMARY;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        HighlightableMenu.fromXml(context, getPreferenceScreenResId());
        use(SupportPreferenceController.class).setActivity(getActivity());
        setDashboardStyle(context);
        getAboutPhoneStyle(context);
    }

    @Override
    public int getHelpResource() {
        // Disable the help icon because this page uses a full search view in actionbar.
        return 0;
    }

    @Override
    public Fragment getCallbackFragment() {
        return this;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (isDuplicateClick(preference)) {
            return true;
        }

        // Register SplitPairRule for SubSettings.
        ActivityEmbeddingRulesController.registerSubSettingsPairRule(getContext(),
                true /* clearTop */);

        setHighlightPreferenceKey(preference.getKey());
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        new SubSettingLauncher(getActivity())
                .setDestination(pref.getFragment())
                .setArguments(pref.getExtras())
                .setSourceMetricsCategory(caller instanceof Instrumentable
                        ? ((Instrumentable) caller).getMetricsCategory()
                        : Instrumentable.METRICS_CATEGORY_UNKNOWN)
                .setTitleRes(-1)
                .setIsSecondLayerPage(true)
                .launch();
        return true;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mIsEmbeddingActivityEnabled =
                ActivityEmbeddingUtils.isEmbeddingActivityEnabled(getContext());
        if (!mIsEmbeddingActivityEnabled) {
            return;
        }

        boolean activityEmbedded = SplitController.getInstance().isActivityEmbedded(getActivity());
        if (icicle != null) {
            mHighlightMixin = icicle.getParcelable(SAVED_HIGHLIGHT_MIXIN);
            mScrollNeeded = !mHighlightMixin.isActivityEmbedded() && activityEmbedded;
            mHighlightMixin.setActivityEmbedded(activityEmbedded);
        }
        if (mHighlightMixin == null) {
            mHighlightMixin = new TopLevelHighlightMixin(activityEmbedded);
        }
    }

    @Override
    public void onStart() {
        if (mFirstStarted) {
            mFirstStarted = false;
            FeatureFactory.getFactory(getContext()).getSearchFeatureProvider().sendPreIndexIntent(
                    getContext());
        } else if (mIsEmbeddingActivityEnabled && isOnlyOneActivityInTask()
                && !SplitController.getInstance().isActivityEmbedded(getActivity())) {
            // Set default highlight menu key for 1-pane homepage since it will show the placeholder
            // page once changing back to 2-pane.
            Log.i(TAG, "Set default menu key");
            setHighlightMenuKey(getString(SettingsHomepageActivity.DEFAULT_HIGHLIGHT_MENU_KEY),
                    /* scrollNeeded= */ false);
        }
        super.onStart();
        onUserCard();
    }

    private boolean isOnlyOneActivityInTask() {
        final ActivityManager.RunningTaskInfo taskInfo = getSystemService(ActivityManager.class)
                .getRunningTasks(1).get(0);
        return taskInfo.numActivities == 1;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mHighlightMixin != null) {
            outState.putParcelable(SAVED_HIGHLIGHT_MIXIN, mHighlightMixin);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        int tintColor = Utils.getHomepageIconColor(getContext());
        iteratePreferences(preference -> {
            Drawable icon = preference.getIcon();
            if (mDashBoardStyle == 3 || mDashBoardStyle == 5) {
              if (icon != null) {
                  icon.setTint(tintColor);
              }
            }
            onSetPrefCard();
        });
    }


    private void onSetPrefCard() {
	final PreferenceScreen screen = getPreferenceScreen();
        final int count = screen.getPreferenceCount();
        for (int i = 0; i < count; i++) {

            final Preference preference = screen.getPreference(i);
 	    String key = preference.getKey();
 	    
 	    boolean isLarge = mAboutPhoneStyle == 2;
 	    boolean isDefault = mAboutPhoneStyle == 0;

	switch (mDashBoardStyle) {
	    case 0:
	    if (key.equals("top_level_usercard")){
	        preference.setLayoutResource(R.layout.usercard);
            } else if (key.equals("top_level_network")
            	|| key.equals("top_level_rising")
            	|| key.equals("top_level_apps")
            	|| key.equals("top_level_accessibility")
            	|| key.equals("top_level_emergency")){
                preference.setLayoutResource(R.layout.top_level_preference_top);
            } else if (key.equals("top_level_battery")
            	|| key.equals("top_level_display")
            	|| key.equals("top_level_security")
            	|| key.equals("top_level_privacy")
            	|| key.equals("top_level_storage")
            	|| key.equals("top_level_notifications")){
                preference.setLayoutResource(R.layout.top_level_preference_middle);
            } else if (key.equals("top_level_about_device")){
                preference.setLayoutResource(isDefault ? R.layout.top_level_preference_bottom : (isLarge ? R.layout.top_level_preference_about_high : R.layout.top_level_preference_about));
                preference.setOrder(isDefault ? 20 : -180);
            } else if (key.equals("top_level_system")){
                preference.setLayoutResource(isDefault ? R.layout.top_level_preference_top : R.layout.top_level_preference_middle);
                preference.setOrder(isDefault ? 10 : -45);
            } else if (key.equals("dashboard_tile_pref_com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity")
            	|| key.equals("dashboard_tile_pref_com.google.android.apps.wellbeing.home.TopLevelSettingsActivity")
            	|| key.equals("top_level_wellbeing")){
                preference.setLayoutResource(R.layout.top_level_preference_wellbeing);
            } else if (key.equals("dashboard_tile_pref_com.google.android.gms.app.settings.GoogleSettingsIALink")
            	|| key.equals("top_level_google")){
                preference.setLayoutResource(R.layout.top_level_preference_google);
                gAppsExists = true;
            } else if (key.equals("top_level_accounts") && gAppsExists){
                preference.setLayoutResource(R.layout.top_level_preference_middle);
            } else {
                preference.setLayoutResource(R.layout.top_level_preference_bottom);
            }
            break;
       case 1:
	    if (key.equals("top_level_usercard")){
	        preference.setLayoutResource(R.layout.usercard_round);
	    } else if (key.equals("top_level_divider_one")){
                // nothing to do here
            } else if (key.equals("top_level_network")
            	|| key.equals("top_level_rising")
            	|| key.equals("top_level_apps")
            	|| key.equals("top_level_accessibility")
            	|| key.equals("top_level_emergency")){
                preference.setLayoutResource(R.layout.top_level_preference_oos_top);
            } else if (key.equals("top_level_battery")
            	|| key.equals("top_level_display")
            	|| key.equals("top_level_security")
            	|| key.equals("top_level_privacy")
            	|| key.equals("top_level_storage")
            	|| key.equals("top_level_notifications")){
                preference.setLayoutResource(R.layout.top_level_preference_oos_middle);
            } else if (key.equals("top_level_about_device")){
                preference.setLayoutResource(isDefault ? R.layout.top_level_preference_oos_bottom : (isLarge ? R.layout.top_level_preference_about_high : R.layout.top_level_preference_about));
                preference.setOrder(isDefault ? 20 : -180);
            } else if (key.equals("top_level_system")){
                preference.setLayoutResource(isDefault ? R.layout.top_level_preference_oos_top : R.layout.top_level_preference_oos_middle);
                preference.setOrder(isDefault ? 10 : -45);
            } else if (key.equals("dashboard_tile_pref_com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity")
            	|| key.equals("dashboard_tile_pref_com.google.android.apps.wellbeing.home.TopLevelSettingsActivity")
            	|| key.equals("top_level_wellbeing")){
                preference.setLayoutResource(R.layout.top_level_preference_wellbeing_oos);
            } else if (key.equals("dashboard_tile_pref_com.google.android.gms.app.settings.GoogleSettingsIALink")
            	|| key.equals("top_level_google")){
                preference.setLayoutResource(R.layout.top_level_preference_google_oos);
                gAppsExists = true;
            } else if (key.equals("top_level_accounts") && gAppsExists){
                preference.setLayoutResource(R.layout.top_level_preference_oos_middle);
            } else {
                preference.setLayoutResource(R.layout.top_level_preference_oos_bottom);
            }
            break;
	case 2:
	    if (key.equals("top_level_usercard")){
	        preference.setLayoutResource(R.layout.usercard_transparent);
            } else if (key.equals("dashboard_tile_pref_com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity")
            	|| key.equals("dashboard_tile_pref_com.google.android.apps.wellbeing.home.TopLevelSettingsActivity")
            	|| key.equals("top_level_wellbeing")){
                preference.setLayoutResource(R.layout.top_level_preference_wellbeing_arc);
            } else if (key.equals("dashboard_tile_pref_com.google.android.gms.app.settings.GoogleSettingsIALink")
            	|| key.equals("top_level_google")){
                preference.setLayoutResource(R.layout.top_level_preference_google_arc);
            } else if (key.equals("top_level_about_device")){
                preference.setLayoutResource(isDefault ? R.layout.top_level_preference_arc : (isLarge ? R.layout.top_level_preference_about_high : R.layout.top_level_preference_about));
                preference.setOrder(isDefault ? 20 : -180);
            } else if (key.equals("top_level_divider_one_rui")) {
            	// do nothing
            } else {
                preference.setLayoutResource(R.layout.top_level_preference_arc);
            }
            break;
	case 3:
	    if (key.equals("top_level_usercard")){
	        preference.setLayoutResource(R.layout.usercard_transparent);
            } else if (key.equals("top_level_about_device")){
                preference.setLayoutResource(isLarge ? R.layout.top_level_preference_about_high : R.layout.top_level_preference_about);
                preference.setOrder(isDefault ? 20 : -180);
            } else if (key.equals("top_level_divider_one_rui")) {
            	// do nothing
            }
	    break;
        case 4:
	    if (key.equals("top_level_usercard")){
	        preference.setLayoutResource(R.layout.usercard_round);
            } else if (key.equals("top_level_network")
            	|| key.equals("top_level_rising")
            	|| key.equals("top_level_apps")
            	|| key.equals("top_level_accessibility")
            	|| key.equals("top_level_emergency")){
                preference.setLayoutResource(R.layout.top_level_preference_mt_top);
            } else if (key.equals("top_level_battery")
            	|| key.equals("top_level_display")
            	|| key.equals("top_level_security")
            	|| key.equals("top_level_privacy")
            	|| key.equals("top_level_storage")
            	|| key.equals("top_level_notifications")){
                preference.setLayoutResource(R.layout.top_level_preference_mt_middle);
            } else if (key.equals("dashboard_tile_pref_com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity")
            	|| key.equals("dashboard_tile_pref_com.google.android.apps.wellbeing.home.TopLevelSettingsActivity")
            	|| key.equals("top_level_wellbeing")){
                preference.setLayoutResource(R.layout.top_level_preference_wellbeing_mt);
            } else if (key.equals("top_level_about_device")){
                preference.setLayoutResource(isDefault ? R.layout.top_level_preference_mt_bottom : (isLarge ? R.layout.top_level_preference_about_high : R.layout.top_level_preference_about));
                preference.setOrder(isDefault ? 20 : -180);
            } else if (key.equals("top_level_system")){
                preference.setLayoutResource(isDefault ? R.layout.top_level_preference_mt_top : R.layout.top_level_preference_mt_middle);
                preference.setOrder(isDefault ? 10 : -45);
            } else if (key.equals("dashboard_tile_pref_com.google.android.gms.app.settings.GoogleSettingsIALink")
            	|| key.equals("top_level_google")){
                preference.setLayoutResource(R.layout.top_level_preference_google_mt);
                gAppsExists = true;
            } else if (key.equals("top_level_accounts") && gAppsExists){
                preference.setLayoutResource(R.layout.top_level_preference_mt_middle);
            } else if (key.equals("top_level_divider_one")) {
            	// do nothing
            } else {
                preference.setLayoutResource(R.layout.top_level_preference_mt_bottom);
            }
            break;
	case 5:
	    if (key.equals("top_level_usercard")){
	        preference.setLayoutResource(R.layout.usercard_round_circle);
            } else if (key.equals("top_level_network")
            	|| key.equals("top_level_rising")
            	|| key.equals("top_level_apps")
            	|| key.equals("top_level_accessibility")
            	|| key.equals("top_level_emergency")){
                preference.setLayoutResource(R.layout.top_level_preference_top_card);
            } else if (key.equals("top_level_battery")
            	|| key.equals("top_level_display")
            	|| key.equals("top_level_security")
            	|| key.equals("top_level_privacy")
            	|| key.equals("top_level_storage")
            	|| key.equals("top_level_notifications")){
                preference.setLayoutResource(R.layout.top_level_preference_middle_card);
            } else if (key.equals("dashboard_tile_pref_com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity")
            	|| key.equals("dashboard_tile_pref_com.google.android.apps.wellbeing.home.TopLevelSettingsActivity")
            	|| key.equals("top_level_wellbeing")){
                preference.setLayoutResource(R.layout.top_level_preference_wellbeing_card);
            } else if (key.equals("dashboard_tile_pref_com.google.android.gms.app.settings.GoogleSettingsIALink")
            	|| key.equals("top_level_google")){
                preference.setLayoutResource(R.layout.top_level_preference_google_card);
                gAppsExists = true;
            } else if (key.equals("top_level_about_device")){
                preference.setLayoutResource(isDefault ? R.layout.top_level_preference_bottom_card : (isLarge ? R.layout.top_level_preference_about_high : R.layout.top_level_preference_about));
                preference.setOrder(isDefault ? 20 : -180);   
            } else if (key.equals("top_level_system")){
                preference.setLayoutResource(isDefault ? R.layout.top_level_preference_top_card : R.layout.top_level_preference_middle_card );
                preference.setOrder(isDefault ? 10 : -45);
            } else if (key.equals("top_level_accounts") && gAppsExists){
                preference.setLayoutResource(R.layout.top_level_preference_middle_card);
            } else {
                preference.setLayoutResource(R.layout.top_level_preference_bottom_card);
            }
            break;
        default:
            break;
        }
      }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        highlightPreferenceIfNeeded();
    }

    @Override
    public void onSplitLayoutChanged(boolean isRegularLayout) {
        iteratePreferences(preference -> {
            if (preference instanceof HomepagePreferenceLayout) {
                ((HomepagePreferenceLayout) preference).getHelper().setIconVisible(isRegularLayout);
            }
        });
    }

    @Override
    public void highlightPreferenceIfNeeded() {
        if (mHighlightMixin != null) {
            mHighlightMixin.highlightPreferenceIfNeeded();
        }
    }

    @Override
    public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        RecyclerView recyclerView = super.onCreateRecyclerView(inflater, parent,
                savedInstanceState);
        recyclerView.setPadding(mPaddingHorizontal, 0, mPaddingHorizontal, 0);
        return recyclerView;
    }

    /** Sets the horizontal padding */
    public void setPaddingHorizontal(int padding) {
        mPaddingHorizontal = padding;
        RecyclerView recyclerView = getListView();
        if (recyclerView != null) {
            recyclerView.setPadding(padding, 0, padding, 0);
        }
    }

    /** Updates the preference internal paddings */
    public void updatePreferencePadding(boolean isTwoPane) {
        iteratePreferences(new PreferenceJob() {
            private int mIconPaddingStart;
            private int mTextPaddingStart;

            @Override
            public void init() {
                mIconPaddingStart = getResources().getDimensionPixelSize(isTwoPane
                        ? R.dimen.homepage_preference_icon_padding_start_two_pane
                        : R.dimen.homepage_preference_icon_padding_start);
                mTextPaddingStart = getResources().getDimensionPixelSize(isTwoPane
                        ? R.dimen.homepage_preference_text_padding_start_two_pane
                        : R.dimen.homepage_preference_text_padding_start);
            }

            @Override
            public void doForEach(Preference preference) {
                if (preference instanceof HomepagePreferenceLayout) {
                    ((HomepagePreferenceLayout) preference).getHelper()
                            .setIconPaddingStart(mIconPaddingStart);
                    ((HomepagePreferenceLayout) preference).getHelper()
                            .setTextPaddingStart(mTextPaddingStart);
                }
            }
        });
    }

    /** Returns a {@link TopLevelHighlightMixin} that performs highlighting */
    public TopLevelHighlightMixin getHighlightMixin() {
        return mHighlightMixin;
    }

    /** Highlight a preference with specified preference key */
    public void setHighlightPreferenceKey(String prefKey) {
        // Skip Tips & support since it's full screen
        if (mHighlightMixin != null && !TextUtils.equals(prefKey, PREF_KEY_SUPPORT)) {
            mHighlightMixin.setHighlightPreferenceKey(prefKey);
        }
    }

    /** Returns whether clicking the specified preference is considered as a duplicate click. */
    public boolean isDuplicateClick(Preference pref) {
        /* Return true if
         * 1. the device supports activity embedding, and
         * 2. the target preference is highlighted, and
         * 3. the current activity is embedded */
        return mHighlightMixin != null
                && TextUtils.equals(pref.getKey(), mHighlightMixin.getHighlightPreferenceKey())
                && SplitController.getInstance().isActivityEmbedded(getActivity());
    }

    /** Show/hide the highlight on the menu entry for the search page presence */
    public void setMenuHighlightShowed(boolean show) {
        if (mHighlightMixin != null) {
            mHighlightMixin.setMenuHighlightShowed(show);
        }
    }

    /** Highlight and scroll to a preference with specified menu key */
    public void setHighlightMenuKey(String menuKey, boolean scrollNeeded) {
        if (mHighlightMixin != null) {
            mHighlightMixin.setHighlightMenuKey(menuKey, scrollNeeded);
        }
    }

    private void onUserCard() {
        final LayoutPreference headerPreference =
                (LayoutPreference) getPreferenceScreen().findPreference(KEY_USER_CARD);
        final Activity context = getActivity();
        final boolean DisableUserCard = Settings.System.getIntForUser(context.getContentResolver(),
                "hide_user_card", 0, UserHandle.USER_CURRENT) != 0;
        if (DisableUserCard && headerPreference != null) {
        getPreferenceScreen().removePreference(headerPreference);
        } else {
        if (headerPreference != null) {
        final View userCard = headerPreference.findViewById(R.id.entity_header);
        final TextView textview = headerPreference.findViewById(R.id.summary);
        final Bundle bundle = getArguments();
        final EntityHeaderController controller = EntityHeaderController
                .newInstance(context, this, userCard)
                .setRecyclerView(getListView(), getSettingsLifecycle())
                .setButtonActions(EntityHeaderController.ActionType.ACTION_NONE,
                        EntityHeaderController.ActionType.ACTION_NONE);

        userCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new ComponentName("com.android.settings","com.android.settings.Settings$UserSettingsActivity"));
                startActivity(intent);
            }
        });

        final int iconId = bundle.getInt("icon_id", 0);
        if (iconId == 0) {
            final UserManager userManager = (UserManager) getActivity().getSystemService(
                    Context.USER_SERVICE);
            final UserInfo info = Utils.getExistingUser(userManager,
                    android.os.Process.myUserHandle());
            controller.setLabel(info.name);
            controller.setIcon(
                    com.android.settingslib.Utils.getUserIcon(getActivity(), userManager, info));
        }

        controller.done(context, true /* rebindActions */);
        }
      }
    }

    @Override
    protected boolean shouldForceRoundedIcon() {
        return getContext().getResources()
                .getBoolean(R.bool.config_force_rounded_icon_TopLevelSettings);
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        if (!mIsEmbeddingActivityEnabled || !(getActivity() instanceof SettingsHomepageActivity)) {
            return super.onCreateAdapter(preferenceScreen);
        }
        return mHighlightMixin.onCreateAdapter(this, preferenceScreen, mScrollNeeded);
    }

    @Override
    protected Preference createPreference(Tile tile) {
        return new HomepagePreference(getPrefContext());
    }

    void reloadHighlightMenuKey() {
        if (mHighlightMixin != null) {
            mHighlightMixin.reloadHighlightMenuKey(getArguments());
        }
    }

    private void iteratePreferences(PreferenceJob job) {
        if (job == null || getPreferenceManager() == null) {
            return;
        }
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        job.init();
        int count = screen.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference preference = screen.getPreference(i);
            if (preference == null) {
                break;
            }
            job.doForEach(preference);
        }
    }

    private interface PreferenceJob {
        default void init() {
        }

        void doForEach(Preference preference);
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.top_level_settings) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    // Never searchable, all entries in this page are already indexed elsewhere.
                    return false;
                }
            };
            
    private void setDashboardStyle(Context context) {
        mDashBoardStyle = Settings.System.getIntForUser(context.getContentResolver(),
                    Settings.System.SETTINGS_DASHBOARD_STYLE, 0, UserHandle.USER_CURRENT);
    }
    
    private void getAboutPhoneStyle(Context context) {
        mAboutPhoneStyle = Settings.System.getIntForUser(context.getContentResolver(),
                    "about_card_style", 0, UserHandle.USER_CURRENT);
    }
}
