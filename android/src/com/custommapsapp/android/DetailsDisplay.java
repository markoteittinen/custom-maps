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

import android.content.Context;
import android.location.Location;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * DetailsDisplay shows detailed information about user's location in text
 * format.
 *
 * @author Marko Teittinen
 */
public class DetailsDisplay extends LinearLayout {
  private static final float METERS_PER_FOOT = 0.3048f;
  private static final float METERS_PER_MILE = 1609.344f;

  private boolean useMetric = false;
  private TextView longitude;
  private TextView latitude;
  private TextView altitude;
  private TextView heading;
  private TextView speed;
  private TextView accuracy;

  public DetailsDisplay(Context context) {
    super(context);
    inflate(context, R.layout.detailsdisplay, this);
    findTextViews();
  }

  public DetailsDisplay(Context context, AttributeSet attrs) {
    super(context, attrs);
    inflate(context, R.layout.detailsdisplay, this);
    findTextViews();
  }

  public void setUseMetric(boolean useMetric) {
    this.useMetric = useMetric;
  }

  public boolean getUseMetric() {
    return useMetric;
  }

  public void updateValues(Location location) {
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
      longitude.setText("N/A");
    } else {
      longitude.setText(String.format("%.5f", value));
    }
  }

  public void setLatitude(float value) {
    if (Float.isNaN(value)) {
      latitude.setText("N/A");
    } else {
      latitude.setText(String.format("%.5f", value));
    }
  }

  public void setAltitude(float meters) {
    if (Float.isNaN(meters)) {
      altitude.setText("N/A");
    } else if (useMetric) {
      altitude.setText(String.format("%.0f m", meters));
    } else {
      float feet = meters / METERS_PER_FOOT;
      altitude.setText(String.format("%.0f ft", feet));
    }
  }

  public void setHeading(float value) {
    if (Float.isNaN(value)) {
      heading.setText("N/A");
    } else {
      heading.setText(String.format("%.0f", value));
    }
  }

  public void setSpeed(float metersPerSecond) {
    if (Float.isNaN(metersPerSecond)) {
      speed.setText("N/A");
    } else  if (useMetric) {
      // Convert to km/h
      float kmph = 3.6f * metersPerSecond;
      speed.setText(String.format("%.1f km/h", kmph));
    } else {
      // Convert to mph
      float mph = 3600 * metersPerSecond / METERS_PER_MILE;
      speed.setText(String.format("%.1f mph", mph));
    }
  }

  public void setAccuracy(float accuracyInMeters) {
    if (Float.isNaN(accuracyInMeters)) {
      accuracy.setText("N/A");
      return;
    }
    float value = accuracyInMeters;
    String format = "%.0f m";
    if (useMetric) {
      if (accuracyInMeters < 1000) {
        value = accuracyInMeters;
        format = "%.0f m";
      } else {
        value = accuracyInMeters / 1000;
        format = "%.1f km";
      }
    } else {
      float feet = accuracyInMeters / METERS_PER_FOOT;
      if (feet < 10) {
        value = feet;
        format = "%.0f ft";
      } else if (feet < 100) {
        value = feet / 10;
        format = "%.0f0 ft";
      } else if (feet < 1000) {
        value = feet / 100;
        format = "%.0f00 ft";
      } else {
        value = accuracyInMeters / METERS_PER_MILE;
        format = "%.1f mi";
      }
    }
    accuracy.setText(String.format(format, value));
  }

  private void findTextViews() {
    longitude = (TextView) findViewById(R.id.longitudeField);
    latitude = (TextView) findViewById(R.id.latitudeField);
    altitude = (TextView) findViewById(R.id.altitudeField);
    heading = (TextView) findViewById(R.id.headingField);
    speed = (TextView) findViewById(R.id.speedField);
    accuracy = (TextView) findViewById(R.id.accuracyField);
  }
}
