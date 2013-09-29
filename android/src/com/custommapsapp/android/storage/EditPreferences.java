/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.custommapsapp.android.storage;

import com.custommapsapp.android.AboutDisplay;
import com.custommapsapp.android.CustomMapsApp;
import com.custommapsapp.android.InertiaScroller;
import com.custommapsapp.android.MemoryUtil;
import com.custommapsapp.android.R;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * EditPreferences is the PreferenceActivity for Custom Maps. Currently
 * supported preferences: <ul>
 * <li> isMetric (bool) - selects between metric and English units
 * <li> useMultitouch (bool) - selects if multitouch is enabled (if available)
 * <li> distanceDisplay (bool) - selects if distance to center of map is displayed
 * <li> safetyReminder (bool) - selects if safety reminder is shown when a map is opened
 * <li> language (string) - allows user to override system language preference
 * </ul>
 *
 * @author Marko Teittinen
 */
public class EditPreferences extends PreferenceActivity {
  private static final String PREFIX = "com.custommapsapp.android";
  public static final String LANGUAGE_CHANGED = PREFIX + ".LanguageChanged";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getPreferenceManager().setSharedPreferencesName(PreferenceStore.SHARED_PREFS_NAME);
    reloadUI();

    // Prepare result so that calling activity will be notified on exit
    getIntent().putExtra(LANGUAGE_CHANGED, false);
    setResult(RESULT_OK, getIntent());
  }

  private void reloadUI() {
    setPreferenceScreen(createPreferenceScreen());
    // heading to screen center can be shown only with distance
    getPreferenceScreen().findPreference(PreferenceStore.PREFS_SHOW_HEADING)
        .setDependency(PreferenceStore.PREFS_SHOW_DISTANCE);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      // Update actionbar title to match selected locale
      getActionBar().setTitle(R.string.edit_prefs_name);
    }
  }

  private PreferenceScreen createPreferenceScreen() {
    // Root
    PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);

    // Units preference
    CheckBoxPreference isMetric = new CheckBoxPreference(this);
    isMetric.setDefaultValue(PreferenceStore.isMetricLocale());
    isMetric.setKey(PreferenceStore.PREFS_METRIC);
    isMetric.setTitle(R.string.metric_title);
    isMetric.setSummaryOff(R.string.metric_use_english);
    isMetric.setSummaryOn(R.string.metric_use_metric);
    root.addPreference(isMetric);

    // Multitouch preference, aka "pinch zoom"
    if (InertiaScroller.isMultitouchAvailable()) {
      CheckBoxPreference useMultitouch = new CheckBoxPreference(this);
      useMultitouch.setDefaultValue(Boolean.TRUE);
      useMultitouch.setKey(PreferenceStore.PREFS_MULTITOUCH);
      useMultitouch.setTitle(R.string.multitouch_title);
      useMultitouch.setSummaryOff(R.string.multitouch_disable_pinch);
      useMultitouch.setSummaryOn(R.string.multitouch_enable_pinch);
      root.addPreference(useMultitouch);
    }

    // Display distance to center of screen
    CheckBoxPreference distanceDisplay = new CheckBoxPreference(this);
    distanceDisplay.setDefaultValue(false);
    distanceDisplay.setKey(PreferenceStore.PREFS_SHOW_DISTANCE);
    distanceDisplay.setTitle(R.string.distance_title);
    distanceDisplay.setSummaryOn(R.string.distance_show);
    distanceDisplay.setSummaryOff(R.string.distance_hide);
    root.addPreference(distanceDisplay);

    CheckBoxPreference headingDisplay = new CheckBoxPreference(this);
    headingDisplay.setDefaultValue(false);
    headingDisplay.setKey(PreferenceStore.PREFS_SHOW_HEADING);
    headingDisplay.setTitle(R.string.heading_title);
    headingDisplay.setSummaryOn(R.string.heading_show);
    headingDisplay.setSummaryOff(R.string.heading_hide);
    root.addPreference(headingDisplay);

    // Display safety reminder when map is changed preference
    CheckBoxPreference safetyReminder = new CheckBoxPreference(this);
    safetyReminder.setDefaultValue(true);
    safetyReminder.setKey(PreferenceStore.PREFS_SHOW_REMINDER);
    safetyReminder.setTitle(R.string.safety_reminder_title);
    safetyReminder.setSummaryOn(R.string.safety_reminder_show);
    safetyReminder.setSummaryOff(R.string.safety_reminder_hide);
    root.addPreference(safetyReminder);

    // Display language selection option
    Preference language = createLanguagePreference();
    root.addPreference(language);

    // About dialog
    Preference about = createAboutPreference();
    if (about != null) {
      root.addPreference(about);
    }

    // Maximum image size info
    Preference imageSizeInfo = createImageSizeInfo();
    if (imageSizeInfo != null) {
      root.addPreference(imageSizeInfo);
    }

    return root;
  }

  private Preference createAboutPreference() {
    Preference aboutPreference = new Preference(this);
    aboutPreference.setTitle(R.string.about_custom_maps);
    aboutPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        launchAboutDisplay();
        return true;
      }
    });
    return aboutPreference;
  }

  private ListPreference createLanguagePreference() {
    ListPreference language = new ListPreference(this);
    language.setKey(PreferenceStore.PREFS_LANGUAGE);
    language.setTitle(R.string.language_title);
    // Create a list of all available languages
    List<Locale> languages = new ArrayList<Locale>();
    languages.add(Locale.ENGLISH);
    languages.add(Locale.GERMAN);
    languages.add(Locale.ITALIAN);
    languages.add(new Locale("pl"));
    languages.add(new Locale("ro"));
    languages.add(new Locale("fi"));
    languages.add(new Locale("ru"));
    languages.add(new Locale("hr"));
    // Sort languages by their localized display name
    final Collator stringComparator = Collator.getInstance(Locale.getDefault());
    Collections.sort(languages, new Comparator<Locale>() {
      @Override
      public int compare(Locale lhs, Locale rhs) {
        return stringComparator.compare(lhs.getDisplayLanguage(lhs), rhs.getDisplayLanguage(rhs));
      }
    });
    // Create display and value arrays, use "default" as first entry
    String[] languageNames = new String[languages.size() + 1];
    String[] languageCodes = new String[languages.size() + 1];
    languageNames[0] = getString(R.string.language_default);
    languageCodes[0] = "default";
    int idx = 1;
    StringBuilder buf = new StringBuilder();
    for (Iterator<Locale> iter = languages.iterator(); iter.hasNext(); idx++) {
      Locale locale = iter.next();
      // Make sure the first character of the language name is uppercase
      buf.setLength(0);
      buf.append(locale.getDisplayLanguage(locale));
      buf.setCharAt(0, Character.toUpperCase(buf.charAt(0)));
      languageNames[idx] = buf.toString();

      languageCodes[idx] = locale.getLanguage();
    }
    language.setEntryValues(languageCodes);
    language.setEntries(languageNames);
    // Create summary value showing current language
    language.setDefaultValue(languageCodes[0]);
    String selected = PreferenceStore.instance(this).getLanguage();
    if (selected == null || selected.length() != 2) {
      selected = languageNames[0];
    } else {
      Locale tmp = new Locale(selected);
      selected = tmp.getDisplayLanguage(tmp);
    }
    language.setSummary(getString(R.string.language_selected_format, selected));
    // Prepare dialog
    language.setDialogTitle(R.string.language_title);
    language.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        // Update language used by app on user selection
        CustomMapsApp app = (CustomMapsApp) getApplication();
        String languageCode = (String) newValue;
        app.changeLanguage(languageCode);
        PreferenceStore.instance(null).setLanguage(languageCode);
        getIntent().putExtra(LANGUAGE_CHANGED, true);

        reloadUI();
        return true;
      }
    });
    return language;
  }

  private Preference createImageSizeInfo() {
    Preference imageSizeInfo = new Preference(this);
    imageSizeInfo.setSelectable(false);
    imageSizeInfo.setTitle(R.string.max_map_img_size_title);
    float megaPixels = MemoryUtil.getMaxImagePixelCount(this) / 1E6f;
    imageSizeInfo.setSummary(getString(R.string.max_map_img_size, megaPixels));
    return imageSizeInfo;
  }

  // --------------------------------------------------------------------------
  // About activity

  private void launchAboutDisplay() {
    Intent aboutDisplay = new Intent(this, AboutDisplay.class);
    aboutDisplay.putExtra(AboutDisplay.CANCELLABLE, true);
    startActivity(aboutDisplay);
  }
}
