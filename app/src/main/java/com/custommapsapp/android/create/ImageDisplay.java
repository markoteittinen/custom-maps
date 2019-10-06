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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import com.custommapsapp.android.ViewInertiaScroller;

/**
 * ImageDisplay displays a bitmap at its full resolution and allows the user to
 * drag it around in the current view.
 *
 * @author Marko Teittinen
 */
public class ImageDisplay extends View implements ViewInertiaScroller.Listener {
  private Bitmap image;
  private int imageW;
  private int imageH;
  private float centerX;    // in rotated image coordinates
  private float centerY;    // in rotated image coordinates
  private float scale = 1.0f;
  private AnnotationLayer annotations;
  private int rotation = 0;
  private Matrix imageRotation = new Matrix();
  private Matrix drawMatrix = new Matrix();

  public ImageDisplay(Context context) {
    super(context);
  }

  public ImageDisplay(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  /**
   * Sets the layer displaying annotations for the bitmap. Its offset will be
   * synchronized with the bitmap.
   */
  public void setAnnotationLayer(AnnotationLayer annotations) {
    this.annotations = annotations;
  }

  /**
   * Sets the bitmap to be displayed to the user.
   */
  public void setBitmap(Bitmap bitmap) {
    image = bitmap;
    if (rotation != 0) {
      setOrientation(rotation);
    } else if (image != null) {
      imageW = image.getWidth();
      imageH = image.getHeight();
    }
    scale = 1.0f;
    resetCenter();
    postInvalidate();
    annotations.postInvalidate();
  }

  /**
   * Sets the necessary rotation needed to display the image right side up
   *
   * @param rotateDegrees
   */
  public void setOrientation(int rotateDegrees) {
    imageRotation.reset();
    imageRotation.postRotate(rotateDegrees);
    rotation = rotateDegrees;
    if (image == null) {
      return;
    }
    switch (rotateDegrees) {
      case 0:
        imageW = image.getWidth();
        imageH = image.getHeight();
        break;
      case 90:
        imageRotation.postTranslate(image.getHeight(), 0);
        imageW = image.getHeight();
        imageH = image.getWidth();
        break;
      case 180:
        imageRotation.postTranslate(image.getWidth(), image.getHeight());
        imageW = image.getWidth();
        imageH = image.getHeight();
        break;
      case 270:
        imageRotation.postTranslate(0, image.getWidth());
        imageW = image.getHeight();
        imageH = image.getWidth();
        break;
    }
    postInvalidate();
    annotations.postInvalidate();
  }

  /**
   * Apply the given multiplier to current image scale.
   *
   * @param multiplier to zoom by (>1 zooms in, <1 zooms out)
   */
  public void zoomBy(float multiplier) {
    setScale(multiplier * getScale());
  }

  /**
   * Sets the absolute scale factor for the image.
   *
   * @param scale size multiplier (1 = no scaling)
   */
  public void setScale(float scale) {
    this.scale = scale;
    postInvalidate();
    annotations.postInvalidate();
  }

  /**
   * @return the current image scaling factor
   */
  public float getScale() {
    return scale;
  }

  /**
   * @return image coordinates that are in the center of the display
   */
  public PointF getCenterPoint() {
    PointF center = new PointF(centerX, centerY);
    Matrix m = new Matrix();
    imageRotation.invert(m);
    mapPoint(m, center);
    return center;
  }

  /**
   * Set the image coordinates at display center point
   */
  public void setCenterPoint(PointF p) {
    mapPoint(imageRotation, p);
    centerX = p.x;
    centerY = p.y;
    postInvalidate();
    annotations.postInvalidate();
  }

  @Override
  public void onDraw(Canvas canvas) {
    if (image == null || image.isRecycled()) {
      return;
    }
    checkImageOutOfBounds();
    computeDrawMatrix();
    if (annotations != null) {
      annotations.setDrawMatrix(drawMatrix);
    }
    canvas.drawBitmap(image, drawMatrix, null);
  }

  /**
   * Map a point using a matrix storing the result in the original point
   *
   * @param m Matrix used for conversion
   * @param p Point to be mapped
   */
  private void mapPoint(Matrix m, PointF p) {
    float[] point = new float[] { p.x, p.y };
    m.mapPoints(point);
    p.x = point[0];
    p.y = point[1];
  }

  /**
   * Centers the bitmap in this widget
   */
  private void resetCenter() {
    if (image != null) {
      centerX = imageW / 2f;
      centerY = imageH / 2f;
    } else {
      centerX = 0;
      centerY = 0;
    }
  }

  /**
   * Recomputes the matrix used for drawing the image (updates scale and
   * translation)
   */
  private void computeDrawMatrix() {
    drawMatrix.set(imageRotation);
    drawMatrix.postScale(scale, scale);
    drawMatrix.postTranslate(getWidth() / 2f - scale * centerX, getHeight() / 2f - scale * centerY);
  }

  /** Checks if the image has scrolled too far and keeps it in bounds. */
  private void checkImageOutOfBounds() {
    if (image == null || image.isRecycled()) {
      return;
    }
    centerX = Math.min(Math.max(0, centerX), imageW);
    centerY = Math.min(Math.max(0, centerY), imageH);
  }

  /** Converts screen X coordinate to image X coordinate. */
  private float screenXToImageX(float screenX) {
    return centerX + (screenX - getWidth() / 2f) / scale;
  }

  /** Converts screen Y coordinate to image Y coordinate. */
  private float screenYToImageY(float screenY) {
    return centerY + (screenY - getHeight() / 2f) / scale;
  }

  // --------------------------------------------------------------------------
  // ViewInertiaScroller.Listener implementation for image movement and scaling

  @Override
  public boolean move(float xd, float yd) {
    // Find center X coordinate after move and keep it within image
    float nextX = centerX + xd / scale;
    centerX = Math.min(Math.max(0, nextX), imageW);
    // Find center Y coordinate after move and keep it within image
    float nextY = centerY + yd / scale;
    centerY = Math.min(Math.max(0, nextY), imageH);
    // Repaint image and annotations
    invalidate();
    annotations.invalidate();
    // Return boolean indicating whether the move occurred completely within image bounds
    return (0 <= nextX && nextX <= imageW && 0 <= nextY && nextY <= imageH);
  }

  @Override
  public void scale(float factor, float focusX, float focusY) {
    // Prevent zooming too far out or in
    if (factor < 1.0) {
      // Zooming out, don't allow shortest side shorter than 200 pixels
      if (Math.min(imageW, imageH) * scale * factor < 200) {
        return;
      }
    } else {
      // Zooming in, don't allow more than 32x zoom
      if (scale * factor >= 32) {
        factor = 32 / scale;
      }
      // If no scaling would be performed, return now
      if (factor == 1) {
        return;
      }
    }
    // Compute shift in the screen center coordinates due to zoom
    centerX += (screenXToImageX(focusX) - centerX) * (factor - 1);
    centerY += (screenYToImageY(focusY) - centerY) * (factor - 1);
    // Apply zoom
    scale *= factor;
    // Repaint image and annotations
    invalidate();
    annotations.invalidate();
  }
}
