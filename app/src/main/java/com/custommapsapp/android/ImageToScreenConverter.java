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

import android.graphics.Matrix;
import android.util.Log;
import android.view.View;

/**
 * ImageToScreenConverter converts image coordinates to screen coordinates and vice versa.
 *
 * @author Marko Teittinen
 */
public class ImageToScreenConverter {
  private View screenView;
  private int imageWidth = 0;
  private int imageHeight = 0;
  private int imageOrientation = 0;
  private Matrix imageToScreenMatrix;
  private Matrix screenToImageMatrix;
  private float zoomLevel = 1;

  public ImageToScreenConverter() {
  }

  public View getScreenView() {
    return screenView;
  }

  public void setScreenView(View screenView) {
    this.screenView = screenView;
    initMatrix();
  }

  public int getImageWidth() {
    return imageWidth;
  }

  public int getImageHeight() {
    return imageHeight;
  }

  public int getImageOrientation() {
    return imageOrientation;
  }

  public void setImageSize(int width, int height) {
    setImageAttributes(width, height, 0);
  }

  public void setImageAttributes(int width, int height, int orientation) {
    imageWidth = width;
    imageHeight = height;
    imageOrientation = orientation;
    initMatrix();
  }

  public void resetConversion() {
    initMatrix();
  }

  public Matrix getImageToScreenMatrix() {
    return imageToScreenMatrix;
  }

  public float getZoomLevel() {
    return zoomLevel;
  }

  public void setZoomLevel(float zoom) {
    if (imageToScreenMatrix == null) {
      return;
    }
    float factor = zoom / zoomLevel;  // factor to convert current zoom to new zoom.
    zoomLevel = zoom;
    imageToScreenMatrix.postScale(factor, factor,
        screenView.getWidth() / 2f, screenView.getHeight() / 2f);
  }

  public void zoom(float factor) {
    if (imageToScreenMatrix == null) {
      return;
    }
    zoomLevel *= factor;
    imageToScreenMatrix.postScale(factor, factor,
        screenView.getWidth() / 2f, screenView.getHeight() / 2f);
  }

  public void zoom(float factor, float focusX, float focusY) {
    // Don't allow negative zoom factors that would turn the image upside down
    if (imageToScreenMatrix == null || factor < 0) {
      return;
    }
    // Convert focus point to image coordinates, and force it inside the image
    float[] focusPoint = { focusX, focusY };
    convertScreenToImageCoordinates(focusPoint);
    if (focusPoint[0] < 0 || imageWidth < focusPoint[0] ||
        focusPoint[1] < 0 || imageHeight < focusPoint[1]) {
      // Focus point is outside image, force it inside
      focusPoint[0] = Math.max(0, Math.min(focusPoint[0], imageWidth));
      focusPoint[1] = Math.max(0, Math.min(focusPoint[1], imageHeight));
      convertImageToScreenCoordinates(focusPoint);
      focusX = focusPoint[0];
      focusY = focusPoint[1];
    }
    // Apply zoom with potentially adjusted focus point
    zoomLevel *= factor;
    imageToScreenMatrix.postScale(factor, factor, focusX, focusY);
    checkImageOnScreen();
  }

  /**
   * Translates the image by (dx, dy). Returns 'true' if full translation was performed, 'false' if
   * the translation was limited to keep image from going off screen.
   *
   * @param dx amount of horizontal translation
   * @param dy amount of vertical translation
   * @return 'true' if translation wasn't limited to keep image on screen
   */
  public boolean translate(float dx, float dy) {
    if (imageToScreenMatrix == null) {
      return true;
    }
    imageToScreenMatrix.postTranslate(dx, dy);
    return checkImageOnScreen();
  }

  public float[] getScreenCenterCoordinates(float[] result) {
    if (screenView == null) {
      return null;
    }
    if (result == null) {
      result = new float[2];
    }
    result[0] = screenView.getWidth() / 2f;
    result[1] = screenView.getHeight() / 2f;
    return result;
  }

  public float[] getImageCenterCoordinates(float[] result) {
    if (imageWidth <= 0 || imageHeight <= 0) {
      return null;
    }
    if (result == null) {
      result = new float[2];
    }
    result[0] = imageWidth / 2f;
    result[1] = imageHeight / 2f;
    return result;
  }

  /**
   * Converts image coordinates to screen coordinates in place.
   *
   * @param imageCoords Array of image coordinates to be converted to screen space. The even
   *     indexes are x coordinates of the points, each of which should be followed by the y
   *     coordinate of the same point. That is, for three points the array would contain [x0, y0,
   *     x1, y1, x2, y2].
   * @return Passed-in float array containing screen coordinates, or null if the conversion cannot
   *     be computed.
   */
  public float[] convertImageToScreenCoordinates(float[] imageCoords) {
    if (imageToScreenMatrix == null) {
      return null;
    }
    imageToScreenMatrix.mapPoints(imageCoords);
    return imageCoords;
  }

  public float[] convertScreenToImageCoordinates(float[] screenCoords) {
    if (imageToScreenMatrix == null) {
      return null;
    }
    imageToScreenMatrix.invert(screenToImageMatrix);
    screenToImageMatrix.mapPoints(screenCoords);
    return screenCoords;
  }

  private void initMatrix() {
    if (screenView == null || imageWidth <= 0 || imageHeight <= 0) {
      imageToScreenMatrix = null;
      return;
    }
    // First rotate image keeping upper left at (0, 0)
    imageToScreenMatrix = new Matrix();
    if (imageOrientation != 0) {
      imageToScreenMatrix.setRotate(imageOrientation);
      int translateX = 0;
      int translateY = 0;
      if (imageOrientation == 90) {
        // Image was rotated clockwise 90 degrees around top-left corner, so it is left of its
        // original position by its previous height (which is now its width). Translate it back.
        //noinspection SuspiciousNameCombination
        translateX = imageHeight;
      } else if (imageOrientation == 180) {
        // Image was rotated 180 degrees around top-left corner, so it is upside down left and above
        // its original position. Translate it so that the new top-left corner is where the original
        // was before rotation (shift right and down by its width and height).
        translateX = imageWidth;
        translateY = imageHeight;
      } else if (imageOrientation == 270) {
        // Image was rotated counter-clockwise 90 degrees around top-left corner, so it is above its
        // original position by its previous width (which is now its height). Translate it back.
        //noinspection SuspiciousNameCombination
        translateY = imageWidth;
      }
      imageToScreenMatrix.postTranslate(translateX, translateY);
    }
    // Find rotated image center coordinates
    float[] imageCenter = getImageCenterCoordinates(null);
    imageToScreenMatrix.mapPoints(imageCenter);
    // Translate imageToScreenMatrix so that image and screen centers match
    float xd = imageCenter[0] - (screenView.getWidth() / 2f);
    float yd = imageCenter[1] - (screenView.getHeight() / 2f);
    imageToScreenMatrix.postTranslate(xd, yd);
    zoomLevel = 1f;

    screenToImageMatrix = new Matrix();
  }

  /**
   * Returns 'false' if image was "forced" to stay on screen
   */
  private boolean checkImageOnScreen() {
    float[] screenCenter = getScreenCenterCoordinates(null);
    convertScreenToImageCoordinates(screenCenter);

    float dx = 0f;
    float dy = 0f;
    if (screenCenter[0] < 0) {
      dx = screenCenter[0];
    } else if (imageWidth < screenCenter[0]) {
      dx = screenCenter[0] - imageWidth;
    }
    if (screenCenter[1] < 0) {
      dy = screenCenter[1];
    } else if (imageHeight < screenCenter[1]) {
      dy = screenCenter[1] - imageHeight;
    }

    if (dx != 0f || dy != 0f) {
      // dx and dy are in image coordinates, map to screen (w/o translation)
      float[] screenDiff = new float[]{dx, dy};
      imageToScreenMatrix.mapVectors(screenDiff);
      // Now adjust translation to keep map on screen
      imageToScreenMatrix.postTranslate(screenDiff[0], screenDiff[1]);
      return false;
    }
    return true;
  }
}
