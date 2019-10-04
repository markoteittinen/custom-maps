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
    ActivityManager actMgr = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    totalAppMemoryMB = actMgr.getMemoryClass();
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
    if (memMB >= 96) {
      // Some Jelly Bean devices
      return 24_000_000;
    } else if (memMB >= 64) {
      // Honeycomb & Ice Cream Sandwich tablets and phones
      return 12_000_000;
    } else if (memMB >= 32) {
      // Gingerbread phones
      return 5_000_000;
    } else if (memMB >= 24) {
      // Eclair phones
      return 4_000_000;
    } else {
      // Donut phones
      return 3_000_000;
    }
  }
}
