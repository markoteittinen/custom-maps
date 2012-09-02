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

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

import com.custommapsapp.android.CustomMaps;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * WarpedImageOverlay draws an image over a MapView and warps it so that a list
 * of image points match with a list of map coordinates.
 *
 * @author Marko Teittinen
 */
public class WarpedImageOverlay extends Overlay {
  private List<Point> imagePoints;
  private List<GeoPoint> geoPoints;
  private List<Point> screenPoints;
  private Bitmap image;
  private Paint transparency;
  private Paint pointPaint;
  private Matrix imageMatrix;
  private GeoPoint lastDrawnLocation = null;
  private float lastDrawnZoom = -1;

  public WarpedImageOverlay(Bitmap image) {
    this.image = image;
    transparency = new Paint();
    transparency.setAlpha(255);
    pointPaint = new Paint();
    pointPaint.setColor(0xFF000000);
    pointPaint.setAntiAlias(true);
    pointPaint.setStrokeWidth(5.0f);
    pointPaint.setStyle(Paint.Style.STROKE);

    imageMatrix = new Matrix();
    imagePoints = new ArrayList<Point>();
    geoPoints = new ArrayList<GeoPoint>();
    screenPoints = new ArrayList<Point>();
  }

  public Bitmap getImage() {
    return image;
  }

  public void setImage(Bitmap image) {
    this.image = image;
  }

  public void setTiepoints(List<Point> imagePoints, List<GeoPoint> geoPoints) {
    if (imagePoints.size() != geoPoints.size()) {
      throw new IllegalArgumentException("Point lists must be of same size");
    }
    this.imagePoints.clear();
    this.geoPoints.clear();
    this.imagePoints.addAll(imagePoints);
    this.geoPoints.addAll(geoPoints);
    // Make the number of screen points match image and geopoints, values don't
    // matter
    while (screenPoints.size() < imagePoints.size()) {
      screenPoints.add(new Point());
    }
    while (screenPoints.size() > imagePoints.size()) {
      screenPoints.remove(screenPoints.size() - 1);
    }
    // Ensure we recompute matrix when painting next
    lastDrawnLocation = null;
  }

  public void setTransparency(int percent) {
    if (percent < 0 || percent > 100) {
      throw new IllegalArgumentException("Transparency must be in range [0, 100], was: " + percent);
    }
    transparency.setAlpha(255 * (100 - percent) / 100);
  }

  @Override
  public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {
    if (image == null || shadow) {
      return false;
    }
    GeoPoint mapCenter = mapView.getMapCenter();
    float zoom = mapView.getProjection().metersToEquatorPixels(1.0f);
    if (zoom != lastDrawnZoom || !mapCenter.equals(lastDrawnLocation)) {
      computeImageWarp(mapView);
      lastDrawnZoom = zoom;
      lastDrawnLocation = mapCenter;
    }
    // Draw translucent map image
    canvas.drawBitmap(image, imageMatrix, transparency);
    // Highlight specified tie points with circles
    for (Point p : screenPoints) {
      canvas.drawCircle(p.x, p.y, 10, pointPaint);
    }
    return false;
  }

  /**
   * Computes the matrix required to warp the image over MapView to match image
   * points with geo points.
   *
   * @return {@code true} if the imageMatrix was successfully resolved
   */
  public boolean computeImageWarp(MapView map) {
    Projection projection = map.getProjection();
    int i = 0;
    for (GeoPoint gp : geoPoints) {
      Point sp = screenPoints.get(i++);
      projection.toPixels(gp, sp);
    }

    float[] imageCoords = createPointFloatArray(imagePoints);
    float[] screenCoords = createPointFloatArray(screenPoints);
    // Use at most 4 tiepoints
    int n = Math.min(4, imagePoints.size());
    boolean ok = imageMatrix.setPolyToPoly(imageCoords, 0, screenCoords, 0, n);
    if (!ok) {
      // If we failed using 4 points, drop last one
      if (n == 4) {
        ok = imageMatrix.setPolyToPoly(imageCoords, 0, screenCoords, 0, n - 1);
      }
    }
    return ok;
  }

  /**
   * Resolves matrix mapping image points to geo points based on the MapView and
   * previously computed image to screen matrix (in computeImageWarp()).
   */
  public Matrix computeImageToGeoMatrix(MapView map) {
    if (image == null) {
      return new Matrix();
    }
    int w = image.getWidth();
    int h = image.getHeight();
    float[] imageCorners = new float[] {0, 0, w, 0, w, h, 0, h};
    float[] geoCorners = new float[8];
    imageMatrix.mapPoints(geoCorners, imageCorners);
    for (int i = 0; i < 8; i += 2) {
      int x = Math.round(geoCorners[i]);
      int y = Math.round(geoCorners[i + 1]);
      GeoPoint gp = map.getProjection().fromPixels(x, y);
      geoCorners[i] = gp.getLongitudeE6() / 1E6f;
      geoCorners[i + 1] = gp.getLatitudeE6() / 1E6f;
    }
    Matrix result = new Matrix();
    if (!result.setPolyToPoly(imageCorners, 0, geoCorners, 0, 4)) {
      Log.w(CustomMaps.LOG_TAG, "Failed to create image-to-geo matrix");
    }
    return result;
  }

  /**
   * Converts a list of points to array of floats arranged as [x0, y0, x1, y1,
   * ...]
   */
  private float[] createPointFloatArray(List<Point> points) {
    float[] coords = new float[points.size() * 2];
    int i = 0;
    for (Point p : points) {
      coords[i++] = p.x;
      coords[i++] = p.y;
    }
    return coords;
  }
}
