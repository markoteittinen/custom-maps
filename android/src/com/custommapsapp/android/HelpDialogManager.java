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

import com.custommapsapp.android.storage.PreferenceStore;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

/**
 * HelpDialogManager manages help dialog display and hiding through activity
 * life cycle.
 *
 * @author Marko Teittinen
 */
public class HelpDialogManager {
  private static final String PREFIX = "com.custommapsapp.android";
  private static final String DIALOG_STATE = PREFIX + ".DialogState";
  private static final String FIRST_TIME = PREFIX + ".FirstTime";

  public static final String HELP_SELECT_MAP = PREFIX + ".SelectMap";
  public static final String HELP_SELECT_IMAGE_FILE = PREFIX + ".SelectImageFileHelp";
  public static final String HELP_MAP_EDITOR = PREFIX + ".MapEditor";
  public static final String HELP_BITMAP_POINT = PREFIX + ".BitmapPoint";
  public static final String HELP_TIE_POINT = PREFIX + ".TiePoint";
  public static final String HELP_PREVIEW_CREATE = PREFIX + ".PreviewCreate";
  public static final String HELP_CAPTURE_MAP = PREFIX + ".CaptureMap";
  public static final String HELP_PREVIEW_CAPTURE = PREFIX + ".PreviewCapture";

  // HELP_ID is used as dialog and menu item ID
  private static final int HELP_ID = 999;

  private Activity activity;
  private String helpText;
  private String webLinkText;
  private String webLinkUrl;
  private String helpPreference;
  private boolean helpIsShowing;
  private boolean isFirstTime;
  private boolean clearFirstTime;

  public static void resetHelpDialogs(Context context) {
    String[] helpNames = {
      HELP_SELECT_IMAGE_FILE, HELP_MAP_EDITOR, HELP_BITMAP_POINT, HELP_TIE_POINT,
      HELP_PREVIEW_CREATE, HELP_CAPTURE_MAP, HELP_PREVIEW_CAPTURE
    };
    PreferenceStore prefs = PreferenceStore.instance(context);
    for (String helpName : helpNames) {
      prefs.setFirstTime(helpName, true);
    }
  }

  public HelpDialogManager(Activity activity, String helpName, String helpText) {
    this.activity = activity;
    this.helpPreference = helpName;
    this.helpText = helpText;
    webLinkText = null;
    webLinkUrl = null;
    helpIsShowing = false;
    isFirstTime = isFirstTime();
    clearFirstTime = true;
  }

  public void addWebLink(String text, String url) {
    webLinkText = text;
    webLinkUrl = url;
  }

  public void onResume() {
    if (isFirstTime || helpIsShowing) {
      helpIsShowing = true;
      activity.showDialog(HELP_ID);
    }
  }

  public void onPause() {
    if (helpIsShowing) {
      activity.dismissDialog(HELP_ID);
      helpIsShowing = false;
    }
  }

  public void onSaveInstanceState(Bundle outState) {
    outState.putBoolean(DIALOG_STATE, helpIsShowing);
    outState.putBoolean(FIRST_TIME, isFirstTime);
  }

  public void onRestoreInstanceState(Bundle inState) {
    helpIsShowing = inState.getBoolean(DIALOG_STATE, false);
    isFirstTime = inState.getBoolean(FIRST_TIME, false);
  }

  public void onCreateOptionsMenu(Menu menu) {
    MenuItem item = menu.add(Menu.NONE, HELP_ID, Menu.NONE, "Help");
    item.setIcon(android.R.drawable.ic_menu_help);
  }

  public void onOptionsItemSelected(MenuItem selected) {
    if (selected.getItemId() == HELP_ID && !helpIsShowing) {
      helpIsShowing = true;
      activity.showDialog(HELP_ID);
    }
  }

  public Dialog onCreateDialog(int id) {
    if (id != HELP_ID) {
      return null;
    }

    HelpDialog dialog = new HelpDialog(activity);
    dialog.setHelpText(helpText);
    dialog.setWebLink(webLinkText, webLinkUrl);
    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
      @Override
      public void onDismiss(DialogInterface dialog) {
        if (clearFirstTime) {
          firstTimeCompleted(true);
          isFirstTime = false;
        }
        helpIsShowing = false;
      }
    });
    return dialog;
  }

  public void onPrepareDialog(int id, Dialog dialog) {
    // Remove "redisplay info" if not the first time
    if (!isFirstTime) {
      ((HelpDialog) dialog).showRedisplayInfo(false);
    }
    if (webLinkText != null) {
      ((HelpDialog) dialog).showWebLink(true);
    }
  }

  public void clearFirstTime(boolean okToClear) {
    clearFirstTime = okToClear;
  }

  private boolean isFirstTime() {
    return PreferenceStore.instance(activity).isFirstTime(helpPreference);
  }

  private void firstTimeCompleted(boolean completed) {
    PreferenceStore.instance(activity).setFirstTime(helpPreference, !completed);
  }
}
