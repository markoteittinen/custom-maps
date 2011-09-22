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

import com.custommapsapp.android.InertiaScroller;
import com.custommapsapp.android.MapApiKeys;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import java.util.Locale;

/**
 * PreferenceStore provides read access to Custom Maps application preferences.
 *
 * @author Marko Teittinen
 */
public class PreferenceStore {
  private static final String LOG_TAG = "Custom Maps";

  public static final String PREFS_METRIC = "isMetric";
  public static final String PREFS_MULTITOUCH = "useMultitouch";
  public static final String PREFS_LASTMAP = "lastMap";
  public static final String PREFS_SHOW_DETAILS = "showDetails";
  public static final String PREFS_LICENSE_ACCEPTED = "licenseAccepted";
  public static final String PREFS_SHOW_REMINDER = "showReminder";
  public static final String SHARED_PREFS_NAME = "com.custommapsapp.android.prefs";

  private static PreferenceStore instance; // singleton

  public static synchronized PreferenceStore instance(Context context) {
    if (instance == null) {
      instance = new PreferenceStore(context);
    }
    return instance;
  }

  /**
   * Checks if metric system should be used by default. Metric is default in all
   * countries but USA
   */
  public static boolean isMetricLocale() {
    return (!"US".equals(Locale.getDefault().getCountry()));
  }

  // --------------------------------------------------------------------------
  // Instance variables and methods

  private SharedPreferences prefs;
  private Context context;

  private PreferenceStore(Context context) {
    this.context = context;
    prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Activity.MODE_PRIVATE);
  }

  public String getVersion() {
    String version = null;
    try {
      PackageManager pm = context.getPackageManager();
      String name = context.getPackageName();
      PackageInfo info = pm.getPackageInfo(name, 0);
      if (MapApiKeys.isReleasedVersion(context)) {
        version = info.versionName;
      } else {
        version = String.format("%s (beta #%d)", info.versionName, info.versionCode);
      }
    } catch (NameNotFoundException e) {
      Log.w(LOG_TAG, "Failed to find version info for app", e);
    }
    return version;
  }

  public boolean isMetric() {
    return prefs.getBoolean(PREFS_METRIC, PreferenceStore.isMetricLocale());
  }

  public boolean useMultitouch() {
    return prefs.getBoolean(PREFS_MULTITOUCH, InertiaScroller.isMultitouchAvailable());
  }

  public boolean isReminderRequested() {
    return prefs.getBoolean(PREFS_SHOW_REMINDER, true);
  }

  public void setReminderRequested(boolean showAgain) {
    prefs.edit().putBoolean(PREFS_SHOW_REMINDER, showAgain).commit();
  }

  public String getLastUsedMap() {
    return prefs.getString(PREFS_LASTMAP, null);
  }

  public void setLastUsedMap(String mapName) {
    prefs.edit().putString(PREFS_LASTMAP, mapName).commit();
  }

  public boolean isShowDetails() {
    return prefs.getBoolean(PREFS_SHOW_DETAILS, false);
  }

  public void setShowDetails(boolean showDetails) {
    prefs.edit().putBoolean(PREFS_SHOW_DETAILS, showDetails).commit();
  }

  public boolean isFirstTime(String helpName) {
    return prefs.getBoolean(helpName, true);
  }

  public void setFirstTime(String helpName, boolean firstTime) {
    prefs.edit().putBoolean(helpName, firstTime).commit();
  }

  public boolean isLicenseAccepted() {
    return prefs.getBoolean(getLicensePreferenceName(), false);
  }

  public void setLicenseAccepted(boolean accepted) {
    prefs.edit().putBoolean(getLicensePreferenceName(), accepted).commit();
  }

  /**
   * Generates a version specific license acceptance variable name. Basically
   * requires the users to accept software license again after each update.
   *
   * @return license acceptance preference name for current app version
   */
  private String getLicensePreferenceName() {
    PackageManager pm = context.getPackageManager();
    String name = context.getPackageName();
    try {
      PackageInfo info = pm.getPackageInfo(name, 0);
      return PREFS_LICENSE_ACCEPTED + "-" + info.versionName.replaceAll("\\s", "_");
    } catch (NameNotFoundException e) {
      return PREFS_LICENSE_ACCEPTED;
    }
  }
}
