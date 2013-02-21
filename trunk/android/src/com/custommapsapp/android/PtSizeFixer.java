/*
 * Copyright 2013 Google Inc. All Rights Reserved.
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

import android.app.Activity;
import android.app.Dialog;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Class PtSizeFixer is needed to detect defective versions of Android OS that
 * report incorrect DPI (dots per inch) values to graphics system, making all
 * text sized based on pt units (points, 1/72 of an inch) appear significantly
 * too small. For example, some Samsung Galaxy Ace models report ~30 DPI when
 * in reality their screens are 160 DPI.
 *
 * @author Marko Teittinen
 */
public class PtSizeFixer {
  private static Boolean fixIsNeeded = null;
  private static float ptMultiplier;

  /**
   * Checks if the current device handles point sizes incorrectly displaying
   * point size specified text at incorrect size. NOTE: This function changes
   * the visible content of the given activity, so it should be called before
   * activity becomes visible.
   *
   * @param activity Activity used, null can be used after first evaluation.
   * @return true, if pt sized TextViews need resizing.
   */
  public static boolean isFixNeeded(Activity activity) {
    if (fixIsNeeded == null) {
      if (activity == null) {
        // No activity provided on first call, can't check, don't fix
        return false;
      }
      activity.setContentView(R.layout.ptsizetest);
      TextView label = (TextView) activity.findViewById(R.id.pt20);
      DisplayMetrics metrics = new DisplayMetrics();
      activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
      testPtSize(metrics, label);
    }
    return fixIsNeeded.booleanValue();
  }

  /**
   * Dialog version of {@link #isFixNeeded(Activity)}.
   *
   * @param dialog Dialog used, null can be used after first evaluation.
   * @return true, if pt sized TextViews need resizing.
   */
  public static boolean isFixNeeded(Dialog dialog) {
    if (fixIsNeeded == null) {
      if (dialog == null) {
        // No dialog provided on first call, can't check, don't fix
        return false;
      }
      dialog.setContentView(R.layout.ptsizetest);
      TextView label = (TextView) dialog.findViewById(R.id.pt20);
      DisplayMetrics metrics = new DisplayMetrics();
      dialog.getWindow().getWindowManager().getDefaultDisplay().getMetrics(metrics);
      testPtSize(metrics, label);
    }
    return fixIsNeeded.booleanValue();
  }

  /**
   * Evaluate whether the fix is needed. If display size is off by 15% or more
   * consider fixing necessary.
   *
   * @param metrics DisplayMetrics to be evaluated
   * @param label20Pt Label that should contain 20pt text (loaded resource)
   */
  private static void testPtSize(DisplayMetrics metrics, TextView label20Pt) {
    float densityDpi = 160f * metrics.density;
    float expectedPtSize = densityDpi * 20f / 72f;

    float pt20Size = label20Pt.getTextSize();
    ptMultiplier = expectedPtSize / pt20Size;
    fixIsNeeded = Math.abs(ptMultiplier - 1) > 0.15f;
  }

  /**
   * If it is necessary to fix the size of point sized labels, perform it
   * to the given view and its subviews.
   *
   * @param v View to be fixed.
   */
  public static void fixView(View v) {
    // Avoid testing the fix flag at every level of recursion
    if (fixIsNeeded == null || !fixIsNeeded) {
      return;
    }
    // Call internal recursive function to fix view hierarchy
    fixViewInternal(v);
  }

  // Recursive function to fix view hierarchy
  private static void fixViewInternal(View v) {
    if (v instanceof ViewGroup) {
      ViewGroup parent = (ViewGroup) v;
      for (int i = 0; i < parent.getChildCount(); i++) {
        fixViewInternal(parent.getChildAt(i));
      }
    } else if (v instanceof TextView) {
      fixTextView((TextView) v);
    }
  }

  // Fixes text size in a single TextView
  private static void fixTextView(TextView textView) {
    float size = textView.getTextSize();
    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, ptMultiplier * size);
  }
}
