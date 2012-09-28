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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ImageCacheIndex keeps track of local file names for
 */
public class ImageCacheIndex {
  private static final String INDEX_NAME = "com.custommapsapp.android.cache";
  private static ImageCacheIndex instance;  // singleton

  public static synchronized ImageCacheIndex instance(Context context) {
    if (instance == null) {
      instance = new ImageCacheIndex(context);
    }
    return instance;
  }

  // --------------------------------------------------------------------------
  // Instance variables and methods

  private SharedPreferences cacheIndex;

  private ImageCacheIndex(Context context) {
    cacheIndex = context.getSharedPreferences(INDEX_NAME, Activity.MODE_PRIVATE);
  }

  public String getValue(String url) {
    return cacheIndex.getString(url, null);
  }

  public String addKey(String url) {
    if (cacheIndex.contains(url)) {
      return cacheIndex.getString(url, null);
    }
    return generateValue(url);
  }

  public synchronized String removeKey(String url) {
    String value = getValue(url);
    if (value != null) {
      cacheIndex.edit().remove(url).commit();
    }
    return value;
  }

  private synchronized String generateValue(String key) {
    // Find the set of values currently in use (to avoid duplicates)
    Map<String, ?> allData = cacheIndex.getAll();
    Set<String> valuesInUse = new HashSet<String>();
    for (Object inUse : allData.values()) {
      valuesInUse.add((String) inUse);
    }

    // Assign hex value of current timestamp
    long value = System.currentTimeMillis();
    String valueStr = null;
    while (valueStr == null) {
      valueStr = Long.toHexString(value);
      if (valuesInUse.contains(valueStr)) {
        valueStr = null;
        value++;
      }
    }
    cacheIndex.edit().putString(key, valueStr).commit();
    return valueStr;
  }
}
