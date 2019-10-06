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

import java.util.Locale;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.util.Log;

import com.custommapsapp.android.CustomMaps;
import com.custommapsapp.android.MapApiKeys;

/**
 * PreferenceStore provides read access to Custom Maps application preferences.
 *
 * @author Marko Teittinen
 */
public class PreferenceStore {
  public static final String PREFS_METRIC = "isMetric";
  public static final String PREFS_LASTMAP = "lastMap";
  public static final String PREFS_SHOW_DETAILS = "showDetails";
  public static final String PREFS_SHOW_DISTANCE = "showDistance";
  public static final String PREFS_SHOW_HEADING = "showHeading";
  public static final String PREFS_LICENSE_ACCEPTED = "licenseAccepted";
  public static final String PREFS_SHOW_REMINDER = "showReminder";
  public static final String PREFS_LANGUAGE = "language";
  public static final String PREFS_USE_ARGB_8888 = "useArgb_8888";
  public static final String PREFS_USE_GPU = "useGpu";
  public static final String SHARED_PREFS_NAME = "com.custommapsapp.android.prefs";

  private static PreferenceStore instance; // singleton

  public static synchronized PreferenceStore instance(Context context) {
    if (instance == null) {
      instance = new PreferenceStore(context.getApplicationContext());
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
      Log.w(CustomMaps.LOG_TAG, "Failed to find version info for app", e);
    }
    return version;
  }

  public boolean isMetric() {
    return prefs.getBoolean(PREFS_METRIC, PreferenceStore.isMetricLocale());
  }

  public boolean isReminderRequested() {
    return prefs.getBoolean(PREFS_SHOW_REMINDER, true);
  }

  public void setReminderRequested(boolean showAgain) {
    prefs.edit().putBoolean(PREFS_SHOW_REMINDER, showAgain).apply();
  }

  public String getLastUsedMap() {
    return prefs.getString(PREFS_LASTMAP, null);
  }

  public void setLastUsedMap(String mapName) {
    prefs.edit().putString(PREFS_LASTMAP, mapName).apply();
  }

  public boolean isShowDetails() {
    return prefs.getBoolean(PREFS_SHOW_DETAILS, false);
  }

  public void setShowDetails(boolean showDetails) {
    prefs.edit().putBoolean(PREFS_SHOW_DETAILS, showDetails).apply();
  }

  public boolean isShowDistance() {
    return prefs.getBoolean(PREFS_SHOW_DISTANCE, false);
  }

  public void setShowDistance(boolean showDistance) {
    prefs.edit().putBoolean(PREFS_SHOW_DISTANCE, showDistance).apply();
  }

  public boolean isShowHeading() {
    return prefs.getBoolean(PREFS_SHOW_HEADING, false);
  }

  public void setShowHeading(boolean showHeading) {
    prefs.edit().putBoolean(PREFS_SHOW_HEADING, showHeading).apply();
  }

  public boolean isUseArgb_8888() {
    boolean defaultValue = getArgb8888Default();
    return prefs.getBoolean(PREFS_USE_ARGB_8888, defaultValue);
  }

  public void setUseArgb_8888(boolean useArgb_8888) {
    prefs.edit().putBoolean(PREFS_USE_ARGB_8888, useArgb_8888).apply();
  }

  public boolean isUseGpu() {
    boolean defaultValue = getGpuDefault();
    return prefs.getBoolean(PREFS_USE_GPU, defaultValue);
  }

  public void setUseGpu(boolean useGpu) {
    prefs.edit().putBoolean(PREFS_USE_GPU, useGpu).apply();
  }

  // Package access allowed (for EditPreferences activity)
  static boolean getArgb8888Default() {
    // RGB_565 allows use of larger images, but Motorola's Android 6 (Marshmallow, SDK 23) has a bug
    // in it at least in software rendering. Default to ARGB_8888 on Motorola Android 6 devices.
    return Build.MANUFACTURER.equalsIgnoreCase("motorola") && Build.VERSION.SDK_INT == 23;
  }

  // Package access allowed (for EditPreferences activity)
  static boolean getGpuDefault() {
    // Not using GPU saves memory and allows larger images, but many LG models running Android 5
    // (Lollipop, SDK 21 or 22) have a bug in software rendering libraries. They should default to
    // using GPU.
    return Build.MANUFACTURER.equalsIgnoreCase("lge") &&
        (Build.VERSION.SDK_INT == 21 || Build.VERSION.SDK_INT == 22);
  }

  /**
   * @return Two-character ISO code for the selected language or null.
   */
  public String getLanguage() {
    String languageCode = prefs.getString(PREFS_LANGUAGE, null);
    if (languageCode != null && languageCode.equals("default")) {
      languageCode = null;
    }
    return languageCode;
  }

  /**
   * Overrides system language with user selected one.
   * @param languageCode Two-character ISO code for the language.
   */
  public void setLanguage(String languageCode) {
    if (languageCode == null) {
      languageCode = "default";
    }
    prefs.edit().putString(PREFS_LANGUAGE, languageCode).apply();
  }

  public boolean isFirstTime(String helpName) {
    return prefs.getBoolean(helpName, true);
  }

  public void setFirstTime(String helpName, boolean firstTime) {
    prefs.edit().putBoolean(helpName, firstTime).apply();
  }

  public boolean isLicenseAccepted() {
    return prefs.getBoolean(getLicensePreferenceName(), false);
  }

  public void setLicenseAccepted(boolean accepted) {
    prefs.edit().putBoolean(getLicensePreferenceName(), accepted).apply();
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
