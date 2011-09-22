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
 * HelpDialog displays a small amount of help information to the user with a
 * single OK button.
 *
 * @author Marko Teittinen
 */
public class HelpDialog extends Dialog {
  private TextView helpText;
  private TextView webLink;
  private TextView redisplayInfo;
  private Uri webLinkUri;

  public HelpDialog(Context context) {
    super(context);
    setCancelable(true);
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    // TODO: place helpText into a scroll pane to avoid hiding "OK"
    setContentView(R.layout.helpdialog);

    helpText = (TextView) findViewById(R.id.helpText);
    webLink = (TextView) findViewById(R.id.webLink);
    redisplayInfo = (TextView) findViewById(R.id.redisplayInfo);

    Button okButton = (Button) findViewById(R.id.okButton);
    okButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        dismiss();
      }
    });
  }

  public void setHelpText(String text) {
    helpText.setText(sanitizeString(text));
  }

  public void setWebLink(String webLinkText, String webLinkUrl) {
    webLinkText = sanitizeString(webLinkText);
    webLink.setText(webLinkText);
    webLinkUri = (webLinkUrl != null ? Uri.parse(webLinkUrl) : null);
    if (webLinkText.length() > 0 && webLinkUri != null) {
      webLink.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          launchBrowser();
        }
      });
    }
  }

  private void launchBrowser() {
    if (webLinkUri == null) {
      return;
    }
    Intent browser = new Intent(Intent.ACTION_VIEW);
    browser.setData(webLinkUri);
    dismiss();
    getContext().startActivity(browser);
  }

  public void showWebLink(boolean show) {
    webLink.setVisibility(show ? View.VISIBLE : View.GONE);
  }

  public void showRedisplayInfo(boolean show) {
    redisplayInfo.setVisibility(show ? View.VISIBLE : View.GONE);
  }

  private String sanitizeString(String s) {
    if (s == null) {
      return "";
    }
    return s.trim();
  }
}
