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
package com.custommapsapp.android;

import com.google.android.maps.MapView;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class that helps managing Google Maps API keys.
 *
 * @author Marko Teittinen
 */
public class MapApiKeys {
  // Marko's personal release API key
  private static final String MARKO_RELEASE_API_KEY = "0ZlhFjlu41t90_E0MJzSFfldUgbbjowXaiQZzlQ";
  private static final Map<Integer, String> signatureHashToApiKey;

  static {
    signatureHashToApiKey = new HashMap<Integer, String>();
    // Marko's release key
    signatureHashToApiKey.put(0x1476E4EA, MARKO_RELEASE_API_KEY);
    // Add your personal signature hash to ApiKey mappings here
    // But do not submit them to source code repository

    // Example: Marko's debug key
    signatureHashToApiKey.put(0x0132E8A3, "0ZlhFjlu41t-P9PnBmxwGQdp-4E_drxWLZ4xmPQ");
  }

  /**
   * @return {@code true} if the application is released version, i.e. not
   *         signed with a debug key
   */
  public static boolean isReleasedVersion(Context context) {
    String apiKey = getApiKey(context);
    return MARKO_RELEASE_API_KEY.equals(apiKey);
  }

  /**
   * @param context application context
   * @return Google Maps API key matching the signature used to sign the app, or
   *         {@code null} if the signature is unknown
   */
  public static String getApiKey(Context context) {
    PackageManager pm = context.getPackageManager();
    String name = context.getPackageName();
    try {
      PackageInfo info = pm.getPackageInfo(name, PackageManager.GET_SIGNATURES);
      for (Signature signature : info.signatures) {
        Integer sigHash = signature.hashCode();
        String apiKey = signatureHashToApiKey.get(sigHash);
        if (apiKey != null) {
          return apiKey;
        }
        Log.i("MapApiKeys", String.format("Unknown signature hash: 0x%X", sigHash));
      }
    } catch (NameNotFoundException e) {
      Log.e("MapApiKeys", "Context package name was invalid", e);
    }
    return null;
  }

  /**
   * Creates a new MapView object that uses a Google Maps API key matching the
   * signature used to sign the app.
   *
   * @param context application context
   * @return MapView object initialized with an API key matching the context
   * @throws IllegalArgumentException if the API key for the signature is not
   *         known (you must add it to the static initializer method)
   */
  public static MapView createMapView(Context context) {
    String apiKey = getApiKey(context);
    if (apiKey == null) {
      throw new IllegalArgumentException("No known API keys for app signature");
    }
    return new MapView(context, apiKey);
  }
}
