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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;

/**
 * DistanceLayer draws a translucent label on the bottom of the MapDisplay
 * to display distance (and heading) to the center of the screen. It also draws
 * a little circle at the center of the screen to make it easier to see where
 * the distance (and heading) is measured to.
 *
 * @author Marko Teittinen
 */
public class DistanceLayer extends View {
  private Paint backgroundPaint;
  private Paint textPaint;
  private DisplayState displayState;
  private Location userLocation;
  private Location screenCenterLocation;
  private boolean showHeading = false;

  private transient Rect infoBox = new Rect();
  private transient RectF roundInfoBox = new RectF();

  public DistanceLayer(Context context, AttributeSet attrs) {
    super(context, attrs);

    userLocation = new Location("tmp");
    screenCenterLocation = new Location("tmp");

    backgroundPaint = new Paint();
    backgroundPaint.setColor(0xC0FFFFFF);
    backgroundPaint.setAntiAlias(true);
    backgroundPaint.setAlpha(192);
    textPaint = new Paint();
    textPaint.setColor(0xFF000000);
    textPaint.setAntiAlias(true);
    textPaint.setTypeface(Typeface.DEFAULT_BOLD);
    textPaint.setTextAlign(Paint.Align.CENTER);
    textPaint.setTextSize(getResources().getDimensionPixelSize(R.dimen.distance_layer_text_size));
  }

  /**
   * Sets DisplayState where the selected screen location is read
   *
   * @param displayState
   */
  public void setDisplayState(DisplayState displayState) {
    this.displayState = displayState;
  }

  /**
   * Decides whether heading to screen center should be shown in addition to
   * distance.
   *
   * @param showHeading true if heading info should be shown on screen
   */
  public void setShowHeading(boolean showHeading) {
    this.showHeading = showHeading;
  }

  /**
   * Updates current user location for measuring.
   *
   * @param location user's current location
   */
  public void setUserLocation(Location location) {
    userLocation.set(location);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    // Don't display if user location is missing or user location is centered
    if (userLocation == null || !userLocation.hasAccuracy() || displayState == null
        || displayState.getFollowMode()) {
      return;
    }
    // Figure out distance from user location to screen center point
    float[] mapLonLat = displayState.getScreenCenterGeoLocation();
    if (screenCenterLocation == null || mapLonLat == null) {
      return;
    }
    screenCenterLocation.setLatitude(mapLonLat[1]);
    screenCenterLocation.setLongitude(mapLonLat[0]);
    int distanceM = Math.round(userLocation.distanceTo(screenCenterLocation));
    int accuracyM = Math.round(userLocation.getAccuracy());

    // Format the distance and accuracy nicely
    String distanceStr = UnitsManager.getDistanceToCenter(getContext(), distanceM, accuracyM);

    // Add heading to center if requested
    String displayStr;
    if (showHeading) {
      int heading = Math.round(userLocation.bearingTo(screenCenterLocation));
      heading = (heading + 360) % 360;
      displayStr = String.format(Locale.getDefault(), "%s, %d\u00B0", distanceStr, heading);
    } else {
      displayStr = distanceStr;
    }

    Resources res = getResources();

    // Compute the pixel size of the distanceStr
    // -- set font size based on canvas density (dpi)
    infoBox.setEmpty();
    textPaint.getTextBounds(displayStr, 0, displayStr.length(), infoBox);
    infoBox.offsetTo(0, 0);
    int paddingPx = res.getDimensionPixelOffset(R.dimen.distance_layer_padding);
    infoBox.right += 2 * paddingPx;
    infoBox.bottom += 2 * paddingPx;
    int marginPx = res.getDimensionPixelOffset(R.dimen.distance_layer_padding);
    infoBox.offset((getWidth() - infoBox.width()) / 2, getHeight() - infoBox.height() - marginPx);
    float baseline = infoBox.bottom - paddingPx;
    backgroundPaint.setStyle(Paint.Style.FILL);
    textPaint.setStrokeWidth(0f);
    textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
    roundInfoBox.set(infoBox);
    canvas.drawRoundRect(roundInfoBox, paddingPx, paddingPx, backgroundPaint);
    canvas.drawText(displayStr, infoBox.exactCenterX(), baseline, textPaint);

    // Draw center circles
    int x = getWidth() / 2;
    int y = getHeight() / 2;
    float wideLinePx = res.getDimension(R.dimen.outer_line_width);
    float thinLinePx = res.getDimension(R.dimen.inner_line_width);
    backgroundPaint.setStyle(Paint.Style.STROKE);
    backgroundPaint.setStrokeWidth(wideLinePx);
    textPaint.setStrokeWidth(thinLinePx);
    textPaint.setStyle(Paint.Style.STROKE);
    float radius = res.getDimension(R.dimen.distance_layer_small_radius);
    canvas.drawCircle(x, y, radius, backgroundPaint);
    canvas.drawCircle(x, y, radius, textPaint);
  }
}
