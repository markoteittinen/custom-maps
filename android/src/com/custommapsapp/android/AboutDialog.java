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

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

/**
 * AboutDialog displays a software license to the user and will not let
 * the user continue until the license is accepted or rejected. The app is
 * responsible for terminating if the dialog is closed without the license
 * having been accepted.
 *
 * @author Marko Teittinen
 */
public class AboutDialog extends Dialog {
  private static final String APACHE_LICENSE_URL =
      "http://www.apache.org/licenses/LICENSE-2.0";
  private static final String GOOGLE_CODE_URL = "http://code.google.com/p/custom-maps";
  private static final String HOMEPAGE_URL = "http://www.custommapsapp.com/";

  private boolean buttonPressed = false;
  private boolean licenseAccepted = false;
  private TextView versionLabel;

  public AboutDialog(Context context) {
    super(context);
    setCancelable(false);
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    setContentView(R.layout.aboutdialog);
    versionLabel = (TextView) findViewById(R.id.version);
    versionLabel.setVisibility(View.GONE);

    TextView webLink = (TextView) findViewById(R.id.apache_license);
    webLink.setOnClickListener(new LinkActivator(APACHE_LICENSE_URL));
    webLink = (TextView) findViewById(R.id.google_code);
    webLink.setOnClickListener(new LinkActivator(GOOGLE_CODE_URL));
    webLink = (TextView) findViewById(R.id.homepage);
    webLink.setOnClickListener(new LinkActivator(HOMEPAGE_URL));

    Button agreeButton = (Button) findViewById(R.id.agreeButton);
    agreeButton.setOnClickListener(new ButtonListener(true));
    Button doNotAgreeButton = (Button) findViewById(R.id.doNotAgreeButton);
    doNotAgreeButton.setOnClickListener(new ButtonListener(false));
  }

  public void setVersion(String text) {
    if (text != null) {
      versionLabel.setText(text);
      versionLabel.setVisibility(View.VISIBLE);
    } else {
      versionLabel.setVisibility(View.GONE);
    }
  }

  public void useSingleButton() {
    Button doNotAgreeButton = (Button) findViewById(R.id.doNotAgreeButton);
    doNotAgreeButton.setVisibility(View.GONE);

    Button agreeButton = (Button) findViewById(R.id.agreeButton);
    agreeButton.setText("Close");

    setCancelable(true);
  }

  public boolean wasButtonPressed() {
    return buttonPressed;
  }

  public boolean isLicenseAccepted() {
    return licenseAccepted;
  }

  private class ButtonListener implements View.OnClickListener {
    private boolean accepting;

    public ButtonListener(boolean accepting) {
      this.accepting = accepting;
    }

    @Override
    public void onClick(View v) {
      licenseAccepted = accepting;
      buttonPressed = true;
      dismiss();
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
      getContext().startActivity(browserIntent);
    }
  }
}
