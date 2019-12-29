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

import java.util.Locale;

import android.content.Context;
import android.location.Location;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.custommapsapp.android.language.Linguist;

/**
 * DetailsDisplay shows detailed information about user's location in text
 * format.
 *
 * @author Marko Teittinen
 */
public class DetailsDisplay extends LinearLayout {
  private TextView longitude;
  private TextView latitude;
  private TextView altitude;
  private TextView heading;
  private TextView speed;
  private TextView accuracy;
  private Linguist linguist;

  public DetailsDisplay(Context context) {
    super(context);
    inflate(context, R.layout.detailsdisplay, this);
    findTextViews();
    linguist = new Linguist(getResources());
  }

  public DetailsDisplay(Context context, AttributeSet attrs) {
    super(context, attrs);
    inflate(context, R.layout.detailsdisplay, this);
    findTextViews();
    linguist = new Linguist(getResources());
  }

  public void setLinguist(Linguist linguist) {
    this.linguist = linguist;
  }

  public void updateValues(Location location) {
    if (location == null) {
      return;
    }
    setLongitude((float) location.getLongitude());
    setLatitude((float) location.getLatitude());
    if (location.hasAltitude()) {
      setAltitude((float) location.getAltitude());
    } else {
      setAltitude(Float.NaN);
    }
    if (location.hasBearing()) {
      setHeading(location.getBearing());
    } else {
      setHeading(Float.NaN);
    }
    if (location.hasSpeed()) {
      setSpeed(location.getSpeed());
    } else {
      setSpeed(Float.NaN);
    }
    if (location.hasAccuracy()) {
      setAccuracy(location.getAccuracy());
    } else {
      setAccuracy(Float.NaN);
    }
  }

  public void setLongitude(float value) {
    if (Float.isNaN(value)) {
      longitude.setText(linguist.getString(R.string.no_data_available));
    } else {
      longitude.setText(String.format(Locale.getDefault(), "%.5f", value));
    }
  }

  public void setLatitude(float value) {
    if (Float.isNaN(value)) {
      latitude.setText(linguist.getString(R.string.no_data_available));
    } else {
      latitude.setText(String.format(Locale.getDefault(), "%.5f", value));
    }
  }

  public void setAltitude(float meters) {
    if (Float.isNaN(meters)) {
      altitude.setText(linguist.getString(R.string.no_data_available));
    } else {
      UnitsManager.updateAltitudeLabel(altitude, meters);
    }
  }

  public void setHeading(float value) {
    if (Float.isNaN(value)) {
      heading.setText(linguist.getString(R.string.no_data_available));
    } else {
      heading.setText(String.format(Locale.getDefault(), "%.0f", value));
    }
  }

  public void setSpeed(float metersPerSecond) {
    if (Float.isNaN(metersPerSecond)) {
      speed.setText(linguist.getString(R.string.no_data_available));
    } else {
      UnitsManager.updateSpeedLabel(speed, metersPerSecond);
    }
  }

  public void setAccuracy(float accuracyInMeters) {
    if (Float.isNaN(accuracyInMeters)) {
      accuracy.setText(linguist.getString(R.string.no_data_available));
    } else {
      UnitsManager.updateAccuracyLabel(accuracy, accuracyInMeters);
    }
  }

  private void findTextViews() {
    longitude = findViewById(R.id.longitudeField);
    latitude = findViewById(R.id.latitudeField);
    altitude = findViewById(R.id.altitudeField);
    heading = findViewById(R.id.headingField);
    speed = findViewById(R.id.speedField);
    accuracy = findViewById(R.id.accuracyField);
  }
}
