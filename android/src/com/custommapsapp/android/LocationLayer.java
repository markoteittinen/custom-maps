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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;

/**
 * LocationLayer is responsible for drawing the user's location icon on the map.
 *
 * @author Marko Teittinen
 */
public class LocationLayer extends View {
  private Paint solidPaint;
  private Paint fillPaint;
  private DisplayState displayState;
  private float mapAngle;

  private float accuracy;
  private AnimationDrawable animation;

  private boolean locationSet = false;
  private float[] geoLocation = new float[2];
  private float[] location = new float[2];
  private float heading;
  private float speedMps = 0f;  // m/s

  public LocationLayer(Context context, AttributeSet attrs) {
    super(context, attrs);

    solidPaint = new Paint();
    solidPaint.setColor(0xE00080FF);
    solidPaint.setStrokeWidth(2.0f);
    solidPaint.setAntiAlias(true);
    solidPaint.setFilterBitmap(true);
    solidPaint.setStyle(Paint.Style.STROKE);
    solidPaint.setStrokeCap(Paint.Cap.SQUARE);

    fillPaint = new Paint();
    fillPaint.setColor(0x400080FF);
    fillPaint.setAntiAlias(true);
    fillPaint.setStyle(Paint.Style.FILL);

    animation = (AnimationDrawable) getResources().getDrawable(R.drawable.blinking_arrow);
  }

  @Override
  protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    if (visibility == View.VISIBLE) {
      triggerAnimation();
    } else if (visibility == View.GONE) {
      cancelAnimation();
    }
  }

  public void setDisplayState(DisplayState displayState) {
    this.displayState = displayState;
  }

  public void updateMapAngle() {
    if (displayState != null) {
      mapAngle = displayState.computeNorthHeading();
    } else {
      mapAngle = 0f;
    }
  }

  public void setMapAngle(float angle) {
    this.mapAngle = angle;
  }

  public void setHeading(float heading) {
    // Only update heading if we have a place to draw the arrowhead, the handset
    // is stationary (speed < 1km/h), and the heading has changed at least 1 degree
    if (locationSet && speedMps < 0.3f && Math.abs(heading - this.heading) >= 1) {
      this.heading = heading;
      if (displayState != null) {
        invalidate();
      }
    }
  }

  public float getHeading() {
    return this.heading;
  }

  public void setGpsLocation(Location location) {
    geoLocation[0] = (float) location.getLongitude();
    geoLocation[1] = (float) location.getLatitude();
    if (location.hasBearing()) {
      heading = location.getBearing();
    }
    if (location.hasAccuracy()) {
      accuracy = location.getAccuracy();
    }
    if (location.hasSpeed()) {
      speedMps = location.getSpeed();
    }
    locationSet = true;

    if (displayState != null) {
      invalidate();
    }
  }

  @Override
  public void onDraw(Canvas canvas) {
    if (!locationSet) {
      return;
    }

    // Find screen coordinates of geo location
    System.arraycopy(geoLocation, 0, location, 0, 2);
    displayState.convertGeoToScreenCoordinates(location);

    // Find how many pixels from the location the arrow or accuracy reach
    float reach = 20f;
    float radius = 1f;
    if (accuracy > 0) {
      float metersPerPixel = displayState.getMetersPerPixel();
      radius = displayState.getImageToScreenMatrix().mapRadius(accuracy / metersPerPixel);
      if (radius > reach) {
        reach = radius;
      }
    }
    // Check if current location is within 'reach' pixels from screen
    if (location[0] < -reach || location[0] > getWidth() + reach ||
        location[1] < -reach || location[1] > getHeight() + reach) {
      return;
    }
    // Draw accuracy circle
    if (accuracy >  0) {
      canvas.drawCircle(location[0], location[1], radius, fillPaint);
      canvas.drawCircle(location[0], location[1], radius, solidPaint);
    }
    // Location indicator
    Bitmap image = ((BitmapDrawable) animation.getCurrent()).getBitmap();
    int w = image.getWidth();
    int h = image.getHeight();
    // Rotate arrow head to correct orientation
    Matrix headingMatrix = new Matrix();
    headingMatrix.postTranslate(-(w / 2f), -(h / 2f));
    headingMatrix.postRotate(heading + mapAngle);
    // Subtly scale pointer based on zoom level, so zoom 0.25->0.4 and zoom 4->1
    float z = Math.max(0.25f, Math.min(4.0f, displayState.getZoomLevel()));
    float scale = 0.7f + 0.15f * (float) (Math.log(z) / Math.log(2));
    headingMatrix.postScale(scale, scale);
    headingMatrix.postTranslate(location[0], location[1]);
    // solidPaint has anti alias and bitmap filtering flags on so image transforms are smooth.
    canvas.drawBitmap(image, headingMatrix, solidPaint);
  }

  // --------------------------------------------------------------------------
  // Animation management

  /* Advances animation by one step, invalidates view, and queues next update */
  private Runnable animationStep = new Runnable() {
    private int idx = 0;

    public void run() {
      idx = (idx + 1) % animation.getNumberOfFrames();
      animation.selectDrawable(idx);
      invalidate();
      postDelayed(this, animation.getDuration(idx));
    }
  };

  private void triggerAnimation() {
    // Remove existing animation "threads" if any
    cancelAnimation();
    // Start a new animation loop
    animation.selectDrawable(0);
    postDelayed(animationStep, animation.getDuration(0));
  }

  private void cancelAnimation() {
    removeCallbacks(animationStep);
  }
}
