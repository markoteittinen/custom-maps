/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.custommapsapp.android;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * MemoryUtil provides some memory related utility methods. Android platform
 * limits the total memory available for an app to protect the OS itself. This
 * class implements heuristics for evaluating how large an image a device can
 * hold in memory while running Custom Maps without running out of memory.
 *
 * @author Marko Teittinen
 */
public class MemoryUtil {
  private static int totalAppMemoryMB = -1;

  /**
   * Finds out the total RAM available (in MB) for an activity running in the
   * given context.
   *
   * @param context of the activity
   * @return Max amount of megabytes of RAM available for the activity (not all
   *         free)
   */
  public static synchronized int getTotalAppMemoryMB(Context context) {
    // If the total app memory was checked earlier, return the cached value
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
      Log.w(CustomMaps.LOG_TAG, "Unknown available memory, using 16 MB", ex);
    }
    return totalAppMemoryMB;
  }

  /**
   * Returns a guesstimate of the maximum size image (in number of pixels) that
   * can fit into the memory for an activity running in the given context.
   *
   * @param context of the activity
   * @return Max number of pixels in an image that can be loaded into memory all
   *         at once. For example, 5000000 for 5 megapixel estimate.
   */
  public static int getMaxImagePixelCount(Context context) {
    int memMB = getTotalAppMemoryMB(context);
    if (memMB >= 64) {
      // Honeycomb tablets & Ice Cream Sandwich and later phones
      return 12000000;
    } else if (memMB >= 32) {
      // Gingerbread phones
      return 5000000;
    } else if (memMB >= 24) {
      // Eclair phones
      return 4000000;
    } else {
      // Donut phones
      return 3000000;
    }
  }
}
