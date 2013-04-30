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

import com.custommapsapp.android.storage.PreferenceStore;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

/**
 * AboutDisplay shows a software license to the user and will not let
 * the user continue until the license is accepted or rejected. The app is
 * responsible for terminating if the activity is closed without the license
 * having been accepted.
 *
 * @author Marko Teittinen
 */
public class AboutDisplay extends Activity {
  private static final String APACHE_LICENSE_URL =
      "http://www.apache.org/licenses/LICENSE-2.0";
  private static final String GOOGLE_CODE_URL = "http://code.google.com/p/custom-maps";
  private static final String HOMEPAGE_URL = "http://www.custommapsapp.com/";

  private static final String EXTRA_PREFIX = "com.custommapsapp.android";
  public static final String CANCELLABLE = EXTRA_PREFIX + ".Cancellable";
  public static final String LICENSE_ACCEPTED = EXTRA_PREFIX + ".LicenseAccepted";

  private boolean licenseAccepted = false;
  private boolean cancellable = false;
  private TextView versionLabel;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    super.onCreate(savedInstanceState);

    cancellable = getIntent().getBooleanExtra(CANCELLABLE, false);

    boolean ptSizeFixNeeded = PtSizeFixer.isFixNeeded(this);
    setContentView(R.layout.aboutdisplay);
    prepareUI();
    if (ptSizeFixNeeded) {
      PtSizeFixer.fixView(versionLabel.getRootView());
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(CANCELLABLE, cancellable);
    outState.putBoolean(LICENSE_ACCEPTED, licenseAccepted);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedState) {
    super.onRestoreInstanceState(savedState);
    cancellable = savedState.getBoolean(CANCELLABLE, false);
    licenseAccepted = savedState.getBoolean(LICENSE_ACCEPTED, false);
  }

  @Override
  protected void onResume() {
    super.onResume();
    prepareUI();
  }

  private void prepareUI() {
    versionLabel = (TextView) findViewById(R.id.version);
    String version = PreferenceStore.instance(this).getVersion();
    versionLabel.setText(version);

    TextView webLink = (TextView) findViewById(R.id.apache_license);
    webLink.setOnClickListener(new LinkActivator(APACHE_LICENSE_URL));
    webLink = (TextView) findViewById(R.id.google_code);
    webLink.setOnClickListener(new LinkActivator(GOOGLE_CODE_URL));
    webLink = (TextView) findViewById(R.id.homepage);
    webLink.setOnClickListener(new LinkActivator(HOMEPAGE_URL));

    Button agreeButton = (Button) findViewById(R.id.agreeButton);
    agreeButton.setOnClickListener(new ButtonListener(true));
    Button doNotAgreeButton = (Button) findViewById(R.id.doNotAgreeButton);
    if (!cancellable) {
      doNotAgreeButton.setOnClickListener(new ButtonListener(false));
    } else {
      doNotAgreeButton.setVisibility(View.GONE);
      agreeButton.setText(R.string.button_close);
    }
  }

  private void finishActivity() {
    if (!cancellable) {
      getIntent().putExtra(LICENSE_ACCEPTED, licenseAccepted);
    }
    setResult(RESULT_OK, getIntent());
    finish();
  }

  private class ButtonListener implements View.OnClickListener {
    private boolean accepting;

    public ButtonListener(boolean accepting) {
      this.accepting = accepting;
    }

    @Override
    public void onClick(View v) {
      licenseAccepted = accepting;
      finishActivity();
    }
  }

  private class LinkActivator implements View.OnClickListener {
    private String url;

    public LinkActivator(String url) {
      this.url = url;
    }

    @Override
    public void onClick(View v) {
      if (url == null) {
        return;
      }
      Intent browserIntent = new Intent(Intent.ACTION_VIEW);
      browserIntent.setData(Uri.parse(url));
      startActivity(browserIntent);
    }
  }
}
