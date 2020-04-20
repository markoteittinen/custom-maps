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

import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.custommapsapp.android.CustomMapsApp;
import com.custommapsapp.android.R;
import com.custommapsapp.android.language.Linguist;

/**
 * EditPreferences preference activity for Custom Maps. The actual preferences are managed by
 * SettingsFragment.
 *
 * @author Marko Teittinen
 */
public class EditPreferences extends AppCompatActivity {
  private static final String PREFIX = "com.custommapsapp.android";
  private static final String SETTINGS_FRAGMENT_TAG = PREFIX + ".SettingsFragment";
  public static final String LANGUAGE_CHANGED = PREFIX + ".LanguageChanged";

  private SettingsFragment settingsFragment;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.editpreferences);
    setSupportActionBar(findViewById(R.id.toolbar));

    reloadUI();

    // Prepare result so that calling activity will be notified on exit
    getIntent().putExtra(LANGUAGE_CHANGED, false);
    setResult(RESULT_OK, getIntent());
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Update actionbar title to match current locale
    Linguist linguist = ((CustomMapsApp) getApplication()).getLinguist();
    updateTitle(linguist);
  }

  private void reloadUI() {
    FragmentManager fragmentManager = getSupportFragmentManager();
    settingsFragment = (SettingsFragment) fragmentManager.findFragmentByTag(SETTINGS_FRAGMENT_TAG);
    if (settingsFragment == null) {
      settingsFragment = new SettingsFragment();
    } else if (settingsFragment.isAdded()) {
      return;
    }
    fragmentManager.beginTransaction()
        .add(R.id.fragment_area, settingsFragment, SETTINGS_FRAGMENT_TAG)
        .commit();
  }

  void updateTitle(Linguist linguist) {
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null && linguist != null) {
      actionBar.setTitle(linguist.getString(R.string.edit_prefs_name));
    }
  }
}
