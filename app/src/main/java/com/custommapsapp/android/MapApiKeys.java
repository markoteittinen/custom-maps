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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

/**
 * Utility class that helps managing Google Maps API keys.
 *
 * @author Marko Teittinen
 */
public class MapApiKeys {
  private static final int RELEASE_KEY_SIGNATURE_HASH = 0x1476E4EA;

  /**
   * @return {@code true} if the application is released version, i.e. not
   *     signed with a debug key
   */
  public static boolean isReleasedVersion(Context context) {
    int signatureHash = getSignatureHash(context.getApplicationContext());
    if (signatureHash == RELEASE_KEY_SIGNATURE_HASH) {
      Log.i(CustomMaps.LOG_TAG, "This is an official release version of this app");
    } else {
      Log.i(CustomMaps.LOG_TAG, "This is not an official release version of this app");
    }
    return signatureHash == RELEASE_KEY_SIGNATURE_HASH;
  }

  /**
   * @param context application context
   * @return hash code for the signature used to sign the app, or zero (0) if there are multiple
   *     signatures, or given context package name is not found by PackageManager.
   */
  private static int getSignatureHash(Context context) {
    PackageManager pm = context.getPackageManager();
    String name = context.getPackageName();
    try {
      PackageInfo info = pm.getPackageInfo(name, PackageManager.GET_SIGNATURES);
      if (info.signatures.length > 1) {
        Log.e(CustomMaps.LOG_TAG, "Found too many signatures: " + info.signatures.length);
        return 0;
      }
      int signatureHash = info.signatures[0].hashCode();
      Log.i(CustomMaps.LOG_TAG, String.format("Signature hash: 0x%X", signatureHash));
      return signatureHash;
    } catch (NameNotFoundException ex) {
      Log.e(CustomMaps.LOG_TAG, "Context package name was invalid", ex);
    }
    return 0;
  }
}
