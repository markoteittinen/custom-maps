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

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * MemoryUtil provides some memory related utility methods.
 *
 * @author Marko Teittinen
 */
public class MemoryUtil {
  private static final String LOG_TAG = "Custom Maps";
  private static int totalAppMemoryMB = -1;

  public static synchronized int getTotalAppMemoryMB(Context context) {
    if (totalAppMemoryMB > 0) {
      return totalAppMemoryMB;
    }
    try {
      Method getMemClass = ActivityManager.class.getMethod("getMemoryClass");
      ActivityManager actMgr = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      totalAppMemoryMB = (Integer) getMemClass.invoke(actMgr);
    } catch (NoSuchMethodException e) {
      // Pre-2.0 device, assume 16 MB
      totalAppMemoryMB = 16;
    } catch (Exception ex) {
      // Something failed, assume safely 16 MB
      totalAppMemoryMB = 16;
      Log.w(LOG_TAG, "Unknown available memory, using 16 MB", ex);
    }
    return totalAppMemoryMB;
  }

  public static int getMaxImagePixelCount(Context context) {
    int memMB = getTotalAppMemoryMB(context);
    if (memMB >= 32) {
      return 5000000;
    } else if (memMB >= 24) {
      return 4000000;
    } else {
      return 3000000;
    }
  }
}
