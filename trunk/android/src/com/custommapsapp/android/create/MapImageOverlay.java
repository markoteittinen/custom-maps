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

import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

/**
 * MapImageOverlay displays a small image in the middle of the map display for
 * the user to align with the map.
 *
 * @author Marko Teittinen
 */
public class MapImageOverlay extends Overlay {
  private Bitmap overlayImage;
  private int centerX;
  private int centerY;
  private float rotation = 0.0f;
  private float scale = 1.0f;
  private Paint transparency;
  private Matrix imageMatrix = new Matrix();

  public MapImageOverlay() {
    transparency = new Paint();
    transparency.setAlpha(255);
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
    if (scale < 1 || scale > 2) {
      throw new IllegalArgumentException("Scale must be in range [1, 2], was: " + scale);
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
  public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {
    if (overlayImage == null || overlayImage.isRecycled()) {
      return false;
    }
    float x = mapView.getWidth() / 2f;
    float y = mapView.getHeight() / 2f;

    imageMatrix.reset();
    imageMatrix.postTranslate(-centerX + x, -centerY + y);
    imageMatrix.postRotate(rotation, x, y);
    imageMatrix.postScale(scale, scale, x, y);

    canvas.drawBitmap(overlayImage, imageMatrix, transparency);
    return false;
  }
}
