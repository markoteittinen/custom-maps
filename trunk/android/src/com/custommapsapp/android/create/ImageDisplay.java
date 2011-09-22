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
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

/**
 * ImageDisplay displays a bitmap at its full resolution and allows the user to
 * drag it around in the current view.
 *
 * @author Marko Teittinen
 */
public class ImageDisplay extends View {
  private static final String LOG_TAG = "Custom Maps";

  private Bitmap image;
  private int imageW;
  private int imageH;
  private float offsetX;
  private float offsetY;
  private VelocityTracker velocityTracker = null;
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
    resetOffset();
    postInvalidate();
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
  }

  /**
   * @return image coordinates that are in the center of the display
   */
  public Point getCenterPoint() {
    Point p = new Point(getWidth() / 2, getHeight() / 2);
    screenToImagePoint(p);
    return p;
  }

  /**
   * Set the image coordinates at display center point
   */
  public void setCenterPoint(Point p) {
    resetOffset();
    imageToScreenPoint(p);
    offsetX += -p.x;
    offsetY += -p.y;
    postInvalidate();
  }

  @Override
  public void onDraw(Canvas canvas) {
    if (image == null || image.isRecycled()) {
      return;
    }
    checkImageOutOfBounds();
    if (annotations != null) {
      annotations.setOffset(offsetX, offsetY);
    }
    drawMatrix.set(imageRotation);
    drawMatrix.postTranslate(offsetX, offsetY);
    canvas.drawBitmap(image, drawMatrix, null);
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    // keep center point centered in the new display size
    float centerX = oldw / 2f - offsetX;
    float centerY = oldh / 2f - offsetY;
    offsetX = w / 2f - centerX;
    offsetY = h / 2f - centerY;
  }

  /**
   * Convert a point from image coordinates to screen coordinates
   *
   * @param p Point to be converted
   */
  public void imageToScreenPoint(Point p) {
    drawMatrix.set(imageRotation);
    drawMatrix.postTranslate(offsetX, offsetY);
    mapPoint(drawMatrix, p);
  }

  public void rotateImagePoint(Point p) {
    drawMatrix.set(imageRotation);
    mapPoint(drawMatrix, p);
  }

  /**
   * Convert a point from screen coordinates to image coordinates
   *
   * @param p Point to be converted
   */
  public void screenToImagePoint(Point p) {
    drawMatrix.set(imageRotation);
    drawMatrix.postTranslate(offsetX, offsetY);
    Matrix inverse = new Matrix();
    drawMatrix.invert(inverse);
    mapPoint(inverse, p);
  }

  /**
   * Map a point using a matrix storing the result in the original point
   *
   * @param m Matrix used for conversion
   * @param p Point to be mapped
   */
  private void mapPoint(Matrix m, Point p) {
    float[] point = new float[] { p.x, p.y };
    m.mapPoints(point);
    p.x = Math.round(point[0]);
    p.y = Math.round(point[1]);
  }

  /**
   * Centers the bitmap in this widget
   */
  private void resetOffset() {
    if (image != null) {
      offsetX = (getWidth() - imageW) / 2f;
      offsetY = (getHeight() - imageH) / 2f;
    } else {
      offsetX = 0;
      offsetY = 0;
    }
  }

  /**
   * Checks if the image has scrolled too far and autoscroll needs to be
   * stopped.
   */
  private void checkImageOutOfBounds() {
    if (image == null || image.isRecycled()) {
      return;
    }
    float centerX = getWidth() / 2f;
    float centerY = getHeight() / 2f;
    float translateX = 0f;
    float translateY = 0f;
    if (offsetX > centerX) {
      translateX = centerX - offsetX;
    } else if (offsetX + imageW < centerX) {
      translateX = centerX - (offsetX + imageW);
    }
    if (offsetY > centerY) {
      translateY = centerY - offsetY;
    } else if (offsetY + imageH < centerY) {
      translateY = centerY - (offsetY + imageH);
    }

    if (translateX != 0f || translateY != 0f) {
      inertiaScroller.stop();
      offsetX += translateX;
      offsetY += translateY;
    }
  }

  // --------------------------------------------------------------------------
  // Inertia scrolling

  private InertiaScroller inertiaScroller = new InertiaScroller();

  private class InertiaScroller implements Runnable {
    private static final float friction = 5f;

    private float xv = 0f;
    private float yv = 0f;
    private float xFriction = 0f;
    private float yFriction = 0f;

    public void start(float xv, float yv) {
      float speed = (float) Math.sqrt(xv * xv + yv * yv);
      float percent = friction / speed;
      xFriction = percent * xv;
      yFriction = percent * yv;
      this.xv = xv;
      this.yv = yv;
      postDelayed(this, 50);
    }

    public void stop() {
      xv = yv = xFriction = yFriction = 0;
    }

    @Override
    public void run() {
      offsetX += xv;
      offsetY += yv;
      invalidate();

      if (xv == 0 || (xv < 0 && xv >= xFriction) || (xv > 0 && xv <= xFriction)) {
        xv = yv = 0;
      } else {
        xv -= xFriction;
        yv -= yFriction;
        postDelayed(this, 50);
      }
    }
  }

  // --------------------------------------------------------------------------
  // Touch processing

  private float lastMoveX = 0f;
  private float lastMoveY = 0f;

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
        startTouch(event);
        break;
      case MotionEvent.ACTION_CANCEL:
        break;
      case MotionEvent.ACTION_UP:
        stopTouch(event);
        break;
      case MotionEvent.ACTION_MOVE:
        moveTouch(event);
    }
    return true;
  }

  private void startTouch(MotionEvent event) {
    float x = event.getX();
    float y = event.getY();
    lastMoveX = x;
    lastMoveY = y;
    velocityTracker = VelocityTracker.obtain();
    velocityTracker.clear();
    velocityTracker.addMovement(event);
  }

  private void stopTouch(MotionEvent event) {
    velocityTracker.addMovement(event);
    velocityTracker.computeCurrentVelocity(50); // per 0.05 seconds (50 ms)
    inertiaScroller.start(velocityTracker.getXVelocity(), velocityTracker.getYVelocity());
    velocityTracker.recycle();
    velocityTracker = null;
  }

  private void moveTouch(MotionEvent evt) {
    offsetX += evt.getX() - lastMoveX;
    offsetY += evt.getY() - lastMoveY;
    lastMoveX = evt.getX();
    lastMoveY = evt.getY();
    invalidate();
  }
}
