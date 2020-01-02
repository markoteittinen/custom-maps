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
package com.custommapsapp.android;

import android.location.Location;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class ScaleDisplay {
  /** Index for X coordinate in arrays. */
  private static final int X = 0;
  /** Index of Y coordinate in arrays. */
  private static final int Y = 1;

  private static final int ICON_SPAN_SHORT = R.drawable.ic_span_short_24dp;
  private static final int ICON_SPAN_LONG = R.drawable.ic_span_long_24dp;

  private DisplayState displayState;
  private ImageView scaleIcon;
  private TextView scaleText;
  private boolean isHorizontal = false;
  private int magnitude;

  /** screenPoint is reused whenever a screen point is converted to Location. */
  private float[] screenPoint = new float[2];
  // These location object are re-used each time scale is computed to avoid memory allocation. */
  private Location upperLeft = new Location("tmp");
  private Location upperRight = new Location("tmp");
  private Location lowerLeft = new Location("tmp");
  private Location lowerRight = new Location("tmp");

  public ScaleDisplay(ImageView icon, TextView label, DisplayState displayState) {
    scaleIcon = icon;
    scaleText = label;
    this.displayState = displayState;
    scaleIcon.setImageResource(isHorizontal ? ICON_SPAN_SHORT : ICON_SPAN_LONG);
    scaleIcon.setOnClickListener(v -> toggleScaleDirection());
    scaleText.setOnClickListener(v -> toggleScaleDirection());
  }

  public void setHorizontal(boolean horizontal) {
    if (isHorizontal != horizontal) {
      isHorizontal = horizontal;
      scaleIcon.setImageResource(isHorizontal ? ICON_SPAN_SHORT : ICON_SPAN_LONG);
    }
  }

  public boolean isHorizontal() {
    return isHorizontal;
  }

  public void update() {
    Location upperLeftGeo = getUpperLeftLocation();
    Location upperRightGeo = getUpperRightLocation();
    Location lowerLeftGeo = getLowerLeftLocation();
    Location lowerRightGeo = getLowerRightLocation();

    // Span distance in meters
    double distanceM;
    if (isHorizontal) {
      // Use average distance of (top-left to top-right) and (bottom-left to bottom-right)
      double distanceTopM = upperLeftGeo.distanceTo(upperRightGeo);
      double distanceBottomM = lowerLeftGeo.distanceTo(lowerRightGeo);
      distanceM = (distanceTopM + distanceBottomM) / 2.0;
    } else {
      // Use average distance of (top-left to bottom-left) and (top-right to bottom-right)
      double distanceLeftM = upperLeftGeo.distanceTo(lowerLeftGeo);
      double distanceRightM = upperRightGeo.distanceTo(lowerRightGeo);
      distanceM = (distanceLeftM + distanceRightM) / 2.0;
    }

    UnitsManager.updateScaleText(scaleText, distanceM);
    updateMagnitude(scaleText.getText().length());
  }

  private void toggleScaleDirection() {
    setHorizontal(!isHorizontal);
    update();
  }

  private void updateMagnitude(int newMagnitude) {
    if (newMagnitude != magnitude) {
      magnitude = newMagnitude;
      // Magnitude of the scale value changed, trigger re-layout of the ScaleDisplay ViewGroup
      ((View) scaleText.getParent()).invalidate();
    }
  }

  /** Returns the geographic location of the upper left corner of map display. */
  private Location getUpperLeftLocation() {
    screenPoint[X] = 0f;
    screenPoint[Y] = 0f;
    return updateScreenPointLocation(screenPoint, upperLeft);
  }

  /** Returns the geographic location of the upper right corner of map display. */
  private Location getUpperRightLocation() {
    screenPoint[X] = displayState.getViewWidth();
    screenPoint[Y] = 0f;
    return updateScreenPointLocation(screenPoint, upperRight);
  }

  /** Returns the geographic location of the lower left corner of map display. */
  private Location getLowerLeftLocation() {
    screenPoint[X] = 0f;
    screenPoint[Y] = displayState.getViewHeight();
    return updateScreenPointLocation(screenPoint, lowerLeft);
  }

  /** Returns the geographic location of the lower right corner of map display. */
  private Location getLowerRightLocation() {
    screenPoint[X] = displayState.getViewWidth();
    screenPoint[Y] = displayState.getViewHeight();
    return updateScreenPointLocation(screenPoint, lowerRight);
  }

  /** Updates a given Location object with given screen point (x,y). */
  private Location updateScreenPointLocation(float[] xy, Location location) {
    float[] geoPoint = displayState.convertScreenToGeoCoordinates(xy);
    location.setLongitude(geoPoint[X]);
    location.setLatitude(geoPoint[Y]);
    return location;
  }
}
