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
package com.custommapsapp.android.create;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.custommapsapp.android.R;

/**
 * TiePointOverlay displays a small image in the middle of the map display for the user to align
 * with the map.
 *
 * @author Marko Teittinen
 */
public class TiePointOverlay extends View {
  private Bitmap overlayImage;
  private int centerX;
  private int centerY;
  private float rotation = 0.0f;
  private float scale = 1.0f;
  private Paint transparency;
  private Matrix imageMatrix = new Matrix();
  private Paint centerPaint;

  public TiePointOverlay(Context context) {
    this(context, null);
  }

  public TiePointOverlay(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);

    transparency = new Paint();
    transparency.setAlpha(255);
    centerPaint = new Paint();
    centerPaint.setAntiAlias(true);
    centerPaint.setStyle(Style.STROKE);
  }

  public Bitmap getOverlayImage() {
    return overlayImage;
  }

  public void setOverlayImage(Bitmap image, int xOffset, int yOffset) {
    overlayImage = image;
    centerX = xOffset;
    centerY = yOffset;
  }

  public void setScale(float scale) {
    if (scale < 1 || scale > 4) {
      throw new IllegalArgumentException("Scale must be in range [1, 4], was: " + scale);
    }
    this.scale = scale;
  }

  public void setRotate(float angle) {
    if (angle < -180 || angle > 180) {
      throw new IllegalArgumentException("Angle must be in range [-180, 180], was: " + angle);
    }
    this.rotation = angle;
  }

  public void setTransparency(int percent) {
    if (percent < 0 || percent > 100) {
      throw new IllegalArgumentException("Transparency must be in range [0, 100], was: " + percent);
    }
    transparency.setAlpha(255 * (100 - percent) / 100);
  }

  @Override
  public void draw(Canvas canvas) {
    super.draw(canvas);
    if (overlayImage == null || overlayImage.isRecycled()) {
      return;
    }
    float x = getWidth() / 2f;
    float y = getHeight() / 2f;

    imageMatrix.reset();
    imageMatrix.postTranslate(-centerX + x, -centerY + y);
    imageMatrix.postRotate(rotation, x, y);
    imageMatrix.postScale(scale, scale, x, y);

    canvas.drawBitmap(overlayImage, imageMatrix, transparency);

    // Draw small black ring with white edge to indicate center point
    Resources res = getResources();
    float radius = res.getDimension(R.dimen.annotation_layer_inner_circle);
    // -- white edge for ring
    float width = getResources().getDimension(R.dimen.outer_line_width);
    centerPaint.setStrokeWidth(width);
    centerPaint.setColor(0x80_FF_FF_FF);
    canvas.drawCircle(x, y, radius, centerPaint);
    // -- black ring
    width = getResources().getDimension(R.dimen.inner_line_width);
    centerPaint.setStrokeWidth(width);
    centerPaint.setColor(0xB0_00_00_00);
    canvas.drawCircle(x, y, radius, centerPaint);
  }
}
