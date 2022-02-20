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

import java.io.File;
import java.util.Locale;
import java.util.Set;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.custommapsapp.android.CustomMaps;
import com.custommapsapp.android.FileUtil;
import com.custommapsapp.android.MapApiKeys;
import com.custommapsapp.android.UnitsManager;

/**
 * PreferenceStore provides read access to Custom Maps application preferences.
 *
 * @author Marko Teittinen
 */
public class PreferenceStore {
  public static final String PREFS_METRIC = "isMetric";
  public static final String PREFS_DISTANCE_UNITS = "distanceUnits";
  public static final String PREFS_LASTMAP = "lastMap";
  public static final String PREFS_SHOW_DETAILS = "showDetails";
  public static final String PREFS_SHOW_DISTANCE = "showDistance";
  public static final String PREFS_SHOW_HEADING = "showHeading";
  public static final String PREFS_SHOW_SCALE = "showScale";
  public static final String PREFS_LICENSE_ACCEPTED = "licenseAccepted";
  public static final String PREFS_SHOW_REMINDER = "showReminder";
  public static final String PREFS_LANGUAGE = "language";
  public static final String PREFS_USE_ARGB_8888 = "useArgb_8888";
  public static final String PREFS_USE_GPU = "useGpu";
  public static final String PREFS_LEGACY_STORAGE = "legacyStorage2";
  public static final String PREFS_MAP_STORAGE_DIR = "mapStorageDir";
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

  private final SharedPreferences prefs;
  private final Context context;

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
        version = String.format(Locale.US, "%s (beta #%d)", info.versionName, info.versionCode);
      }
    } catch (NameNotFoundException e) {
      Log.w(CustomMaps.LOG_TAG, "Failed to find version info for app", e);
    }
    return version;
  }

  /**
   * This method is deprecated, use {@link #getDistanceUnits()} instead.
   *
   * @return boolean indicating if user prefers metric distance units over miles and feet.
   */
  @Deprecated
  private boolean isMetric() {
    return prefs.getBoolean(PREFS_METRIC, PreferenceStore.isMetricLocale());
  }

  /**
   * Returns user's preferred distance units, one of KM, MI, or NMI.
   */
  public UnitsManager.DistanceUnits getDistanceUnits() {
    if (prefs.contains(PREFS_METRIC)) {
      // If old two-option preference exists, migrate its value
      UnitsManager.DistanceUnits units =
          isMetric() ? UnitsManager.DistanceUnits.KM : UnitsManager.DistanceUnits.MI;
      setDistanceUnits(units);
      // Remove old preference value, and return
      prefs.edit().remove(PREFS_METRIC).apply();
      return units;
    }
    // Get new multi-option preference value
    String undefined = "undefined";
    String unitsName = prefs.getString(PREFS_DISTANCE_UNITS, undefined);
    try {
      return UnitsManager.DistanceUnits.valueOf(unitsName);
    } catch (IllegalArgumentException ex) {
      // Invalid unitsName, most likely value has never been saved
      if (!undefined.equals(unitsName)) {
        // Something unexpected was wrong, log a warning
        Log.w(CustomMaps.LOG_TAG, "Invalid distance units preference: " + unitsName);
      }
    }
    return isMetricLocale() ? UnitsManager.DistanceUnits.KM : UnitsManager.DistanceUnits.MI;
  }

  public void setDistanceUnits(UnitsManager.DistanceUnits distanceUnits) {
    if (distanceUnits == null) {
      return;
    }
    prefs.edit().putString(PREFS_DISTANCE_UNITS, distanceUnits.name()).apply();
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

  public boolean isShowScale() {
    return prefs.getBoolean(PREFS_SHOW_SCALE, true);
  }

  public void setShowScale(boolean showScale) {
    prefs.edit().putBoolean(PREFS_SHOW_SCALE, showScale).apply();
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
   * Returns 'true' if the maps are stored in folder named CustomMaps in internal public storage,
   * and 'false' if the user is new or if they have been migrated to new model that does not need
   * WRITE_EXTERNAL_STORAGE permission.
   */
  public boolean isUsingLegacyStorage() {
    if (prefs.contains(PREFS_LEGACY_STORAGE)) {
      // Note: given default value doesn't matter, key was verified to exist
      return prefs.getBoolean(PREFS_LEGACY_STORAGE, false);
    }
    // Value has not been set, use 'true' if license has been approved previously, false otherwise
    boolean previousUse = isAnyLicenseAccepted();
    prefs.edit().putBoolean(PREFS_LEGACY_STORAGE, previousUse).apply();
    return previousUse;
  }

  /**
   * Clears the legacy storage flag. This method should be called when the user's maps have been
   * migrated to internal storage.
   */
  public void setStopUsingLegacyStorage() {
    prefs.edit().putBoolean(PREFS_LEGACY_STORAGE, false).apply();
  }

  // DO NOT SUBMIT: for testing purposes only
  public void returnToLegacyStorage() {
    // Delete all files in internal map directory
    File mapDir = FileUtil.getInternalMapDirectory();
    File[] mapFiles = mapDir.listFiles();
    if (mapFiles != null) {
      for (File mapFile : mapFiles) {
        if (mapFile.isFile()) {
          mapFile.delete();
        }
      }
    }
    // Clear any selected value for map storage directory, and clear the flag for legacy storage
    setMapStorageDirectory(null);
    prefs.edit().remove(PREFS_LEGACY_STORAGE).apply();
  }

  /**
   * Returns 'true' if user has accepted license for any app version. If a license has been
   * accepted, but legacy storage value has not been initialized, the user most likely has existing
   * maps in CustomMaps folder in internal public storage.
   */
  private boolean isAnyLicenseAccepted() {
    Set<String> keys = prefs.getAll().keySet();
    for (String key: keys) {
      if (key.startsWith(PREFS_LICENSE_ACCEPTED)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the Uri pointing to the directory storing the user's maps, or null if the maps are
   * stored into the app's internal data directory.
   */
  public Uri getMapStorageDirectory() {
    String mapStorageDirectory = prefs.getString(PREFS_MAP_STORAGE_DIR, null);
    return mapStorageDirectory != null ? Uri.parse(mapStorageDirectory) : null;
  }

  /**
   * Sets the Uri pointing to the directory where user's map will be stored. Use value 'null' for
   * storing the maps in a private internal directory.
   *
   * @param mapStorageDirectoryUri Uri from pointing
   */
  public void setMapStorageDirectory(Uri mapStorageDirectoryUri) {
    if (mapStorageDirectoryUri == null) {
      prefs.edit().remove(PREFS_MAP_STORAGE_DIR).apply();
    } else {
      prefs.edit().putString(PREFS_MAP_STORAGE_DIR, mapStorageDirectoryUri.toString()).apply();
    }
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
