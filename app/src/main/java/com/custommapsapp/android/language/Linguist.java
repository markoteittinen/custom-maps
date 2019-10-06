/*
 * Copyright 2019 Google Inc. All Rights Reserved.
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
package com.custommapsapp.android.language;

import android.content.res.Resources;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.custommapsapp.android.CustomMaps;

/**
 * Linguist class provides translation support to Activities, Fragments, and Views. Each supported
 * language needs a class definition extending LanguageStore that can map the default resource IDs
 * to resource IDs containing the same phrase in that specific language.<p>
 *
 * To translate text in views that are defined in layout files, any view extending TextView should
 * specify android:tag attribute containing string that matches the name of the string resource
 * displayed in that view. For example, if android:text="@string/description" then include
 * android:tag="description" to the same view.
 *
 * @author Marko Teittinen
 */
public class Linguist {
  abstract static class LanguageStore {
    /**
     * resourceIdMap should be initialized in the subclass constructor to contain mappings from
     * default resource IDs to the language specific resource IDs.
     */
    SparseIntArray resourceIdMap = new SparseIntArray();

    /** Returns the resource ID of the given string resource in stored language. */
    int translateResId(int resId) {
      // If no mapping exists, return unchanged resource id
      return resourceIdMap.get(resId, resId);
    }
  }

  private final Resources resources;
  private LanguageStore language;

  public Linguist(Resources resources) {
    this.resources = resources;
    language = null;
  }

  public void setLanguage(String languageCode) {
    if (languageCode == null || languageCode.equalsIgnoreCase("default")) {
      // Do not translate
      language = null;
    } else {
      switch (languageCode.toLowerCase()) {
        case "de":
          language = new De_German();
          break;
        case "en":
          language = new En_English();
          break;
        case "fi":
          language = new Fi_Finnish();
          break;
        case "hr":
          language = new Hr_Croatian();
          break;
        case "hu":
          language = new Hu_Hungarian();
          break;
        case "it":
          language = new It_Italian();
          break;
        case "pl":
          language = new Pl_Polish();
          break;
        case "ro":
          language = new Ro_Romanian();
          break;
        case "ru":
          language = new Ru_Russian();
          break;
        default:
          // Do not change language
          Log.w(CustomMaps.LOG_TAG, "Unsupported language code: " + languageCode);
          break;
      }
    }
  }

  public String getString(int resId) {
    if (language != null) {
      resId = language.translateResId(resId);
    }
    return resources.getString(resId);
  }

  public String getString(int resId, Object... formatArgs) {
    if (language != null) {
      resId = language.translateResId(resId);
    }
    return resources.getString(resId, formatArgs);
  }

  public void translateView(View view) {
    if (view instanceof ViewGroup) {
      translateViewGroup((ViewGroup) view);
    } else if (view instanceof TextView) {
      translateTextView((TextView) view);
    } else if (view instanceof ImageButton) {
      translateImageButton((ImageButton) view);
    }
  }

  private void translateViewGroup(ViewGroup views) {
    for (int i = 0; i < views.getChildCount(); i++) {
      View v = views.getChildAt(i);
      if (v instanceof TextView) {
        translateTextView((TextView) v);
      } else if (v instanceof ViewGroup) {
        translateViewGroup((ViewGroup) v);
      } else if (v instanceof ImageButton) {
        translateImageButton((ImageButton) v);
      }
    }
  }

  private void translateTextView(TextView textView) {
    Object tag = textView.getTag();
    if (tag instanceof String) {
      int resId = getStringResourceId((String) tag);
      if (resId != 0) {
        textView.setText(getString(resId));
      }
    }
  }

  private void translateImageButton(ImageButton imageButton) {
    Object tag = imageButton.getTag();
    if (tag instanceof String) {
      int resId = getStringResourceId((String) tag);
      if (resId != 0) {
        imageButton.setContentDescription(getString(resId));
      }
    }
  }

  private int getStringResourceId(String tagValue) {
    return resources.getIdentifier(tagValue, "string", "com.custommapsapp.android");
  }
}
