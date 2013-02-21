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
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;
import java.util.List;

/**
 * LocationLayer is responsible for drawing the user's location icon on the map.
 *
 * @author Marko Teittinen
 */
public class LocationLayer extends View {
  private static final int COLOR_DISC_EDGE = 0xE00080FF;
  private static final int COLOR_TEXT = 0xFFFFFFFF;
  private static final int COLOR_TEXT_BACK = 0x80000000;

  private Paint solidPaint;
  private Paint fillPaint;
  private DisplayState displayState;
  private float mapAngle;

  private float accuracy;
  private AnimationDrawable animation;
  private String warningMessage = null;

  private boolean locationSet = false;
  private float[] geoLocation = new float[2];
  private float[] location = new float[2];
  private float heading;
  private float speedMps = 0f;  // m/s

  private transient Matrix headingMatrix = new Matrix();

  public LocationLayer(Context context, AttributeSet attrs) {
    super(context, attrs);

    solidPaint = new Paint();
    solidPaint.setColor(COLOR_DISC_EDGE);
    solidPaint.setStrokeWidth(2.0f);
    solidPaint.setAntiAlias(true);
    solidPaint.setFilterBitmap(true);
    solidPaint.setStyle(Paint.Style.STROKE);
    solidPaint.setStrokeCap(Paint.Cap.SQUARE);
    solidPaint.setTypeface(Typeface.DEFAULT);
    solidPaint.setTextAlign(Paint.Align.CENTER);

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
    if (warningMessage != null) {
      displayWarning(canvas);
    }

    if (!locationSet) {
      return;
    }

    // Find screen coordinates of geo location, quit if not ready
    System.arraycopy(geoLocation, 0, location, 0, 2);
    if (displayState.convertGeoToScreenCoordinates(location) == null) {
      return;
    }

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
    headingMatrix.reset();
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

    @Override
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

  // --------------------------------------------------------------------------
  // Possible warning message about location not being available
  // This does not happen with official Android builds, but is apparently
  // possible with some open source variants of Android.

  private class WarningInfo {
    Paint paint;
    RectF textBox;
    List<String> rows;
    float firstRowY;
    float rowHeight;
  }

  private transient WarningInfo warningInfo = null;

  public void setWarningMessage(String message) {
    warningMessage = message;
    warningInfo = null;
  }

  private void displayWarning(Canvas canvas) {
    if (warningMessage == null || canvas == null) {
      return;
    }
    if (warningInfo == null) {
      // Create an object to store warning message display info to avoid
      // recomputing values constantly
      prepareWarningInfo(canvas);
    }
    warningInfo.paint.setColor(COLOR_TEXT_BACK);
    canvas.drawRect(warningInfo.textBox, warningInfo.paint);
    warningInfo.paint.setColor(COLOR_TEXT);
    float x = 2 * warningInfo.textBox.left; //canvas.getWidth() / 2f;
    float y = warningInfo.firstRowY;
    for (String row : warningInfo.rows) {
      canvas.drawText(row, x, y, warningInfo.paint);
      y += warningInfo.rowHeight;
    }
  }

  private void prepareWarningInfo(Canvas canvas) {
    if (warningMessage == null || canvas == null) {
      return;
    }
    warningInfo = new WarningInfo();
    warningInfo.paint = new Paint();
    warningInfo.paint.setColor(COLOR_TEXT);
    warningInfo.paint.setStrokeWidth(1.0f);
    warningInfo.paint.setAntiAlias(true);
    warningInfo.paint.setStyle(Paint.Style.FILL_AND_STROKE);
    warningInfo.paint.setTypeface(Typeface.DEFAULT);
    warningInfo.paint.setTextAlign(Paint.Align.LEFT);

    // Use 8pt font size (convert to pixel size)
    float fontSizePx = canvas.getDensity() * 8f / 72f;
    warningInfo.paint.setTextSize(fontSizePx);

    float margin = canvas.getDensity() / 8f;
    float textWidth = this.getWidth() - 4 * margin;

    warningInfo.rows = new LinkedList<String>();
    String[] messageRows = warningMessage.split("\\n");
    for (int i = 0; i < messageRows.length; i++) {
      if (!warningInfo.rows.isEmpty()) {
        warningInfo.rows.add("");
      }
      breakIntoLines(warningInfo.rows, messageRows[i], textWidth);
    }

    warningInfo.firstRowY = 2 * margin + fontSizePx;
    warningInfo.rowHeight = 1.25f * fontSizePx;
    float textHeight = warningInfo.rows.size() * warningInfo.rowHeight;
    warningInfo.textBox =
        new RectF(margin, margin, this.getWidth() - margin, textHeight + 3 * margin);
  }

  private void breakIntoLines(List<String> rows, String line, float width) {
    while (line.length() > 0) {
      // Find maximum fitting length, try to break at last space
      int eol = warningInfo.paint.breakText(line, true, width, null);
      int preferred = eol;
      while (0 < preferred && preferred < line.length() && line.charAt(preferred) != ' ') {
        preferred--;
      }
      if (preferred == 0) {
        // no word breaks before eol, break the word
        preferred = eol;
      }
      rows.add(line.substring(0, preferred).trim());
      if (preferred < line.length()) {
        line = line.substring(preferred);
      } else {
        line = "";
      }
    }
  }
}
