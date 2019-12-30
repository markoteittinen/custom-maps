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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;

import com.custommapsapp.android.CustomMaps;
import com.custommapsapp.android.DMatrix;

/**
 * MapImageOverlay draws an image over a GoogleMap and warps it so that a list
 * of image points match with a list of map coordinates.
 *
 * @author Marko Teittinen
 */
public class MapImageOverlay extends View
    implements GoogleMap.OnCameraMoveStartedListener,
    GoogleMap.OnCameraMoveListener,
    GoogleMap.OnCameraIdleListener {
  private List<Point> imagePoints;
  private List<LatLng> geoPoints;
  private List<Point> screenPoints;

  private GoogleMap googleMap;
  private Bitmap mapImage;

  /** Paint used for drawing the map image as translucent overlay */
  private Paint transparency;
  /** Paint used for drawing the tiepoint locations */
  private Paint pointPaint;
  /** Matrix that maps image coordinates to screen coordinates */
  private Matrix imageMatrix;
  /** DMatrix that maps image coordinates to screen coordinates */
  private DMatrix imageMatrixDbl;
  /** LatLng location that was center (target) of the GoogleMap when overlay was last drawn */
  private LatLng lastDrawnLocation = null;
  /** Zoom level of the GoogleMap when overlay was last drawn */
  private float lastDrawnZoom = -1;

  public MapImageOverlay(Context context) {
    this(context, null);
  }

  public MapImageOverlay(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);

    // Initialize member variables
    transparency = new Paint();
    transparency.setAlpha(255);
    transparency.setAntiAlias(true);
    transparency.setFilterBitmap(true);
    pointPaint = new Paint();
    pointPaint.setColor(0xFF000000);
    pointPaint.setAntiAlias(true);
    pointPaint.setStrokeWidth(5.0f);
    pointPaint.setStyle(Paint.Style.STROKE);

    imageMatrix = new Matrix();
    imageMatrixDbl = new DMatrix();
    imagePoints = new ArrayList<>();
    geoPoints = new ArrayList<>();
    screenPoints = new ArrayList<>();
  }

  /** Sets the GoogleMap on top of which this MapImageOverlay is rendering the map image. */
  public void setGoogleMap(GoogleMap googleMap) {
    this.googleMap = googleMap;
    googleMap.setOnCameraMoveStartedListener(this);
    googleMap.setOnCameraMoveListener(this);
    googleMap.setOnCameraIdleListener(this);
  }

  /** Returns true if this instance has a reference to a GoogleMap. */
  public boolean hasGoogleMap() {
    return googleMap != null;
  }

  public Bitmap getMapImage() {
    return mapImage;
  }

  public void setMapImage(Bitmap mapImage) {
    this.mapImage = mapImage;
  }

  public void setTiepoints(List<Point> imagePoints, List<LatLng> geoPoints) {
    if (imagePoints.size() != geoPoints.size()) {
      throw new IllegalArgumentException("Point lists must be of same size");
    }
    this.imagePoints.clear();
    this.geoPoints.clear();
    this.imagePoints.addAll(imagePoints);
    this.geoPoints.addAll(geoPoints);
    // Make the size screen points list match image and geopoints
    while (screenPoints.size() < imagePoints.size()) {
      screenPoints.add(null);
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
  public void draw(Canvas canvas) {
    super.draw(canvas);
    if (mapImage == null || googleMap == null) {
      return;
    }
    CameraPosition mapCamera = googleMap.getCameraPosition();
    float zoom = mapCamera.zoom;
    if (zoom != lastDrawnZoom) {
      // Zoom level changed, recompute the whole matrix
      if (computeImageWarp(googleMap.getProjection())) {
        lastDrawnZoom = zoom;
        lastDrawnLocation = mapCamera.target;
      }
    } else if (!mapCamera.target.equals(lastDrawnLocation)) {
      // Map was panned, add translation to imageMatrix
      Projection mapProjection = googleMap.getProjection();
      Point oldCenter = mapProjection.toScreenLocation(lastDrawnLocation);
      Point center = mapProjection.toScreenLocation(mapCamera.target);
      int xDiff = oldCenter.x - center.x;
      int yDiff = oldCenter.y - center.y;
      imageMatrix.postTranslate(xDiff, yDiff);
      lastDrawnLocation = mapCamera.target;
      // Update all tie point locations
      for (Point p : screenPoints) {
        p.x += xDiff;
        p.y += yDiff;
      }
    }
    // Draw translucent map image
    canvas.drawBitmap(mapImage, imageMatrix, transparency);
    // Highlight specified tie points with circles
    for (Point p : screenPoints) {
      canvas.drawCircle(p.x, p.y, 10, pointPaint);
    }
  }

  /**
   * Computes the matrix required to warp the image over MapView to match image
   * points with geo points.
   *
   * @return {@code true} if the imageMatrix was successfully resolved
   */
  public boolean computeImageWarp(Projection projection) {
    int i = 0;
    for (LatLng latLng : geoPoints) {
      screenPoints.set(i++, projection.toScreenLocation(latLng));
    }

    double[] imageCoords = createPointDoubleArray(imagePoints);
    double[] screenCoords = createPointDoubleArray(screenPoints);
    // Use at most 4 tiepoints
    int n = Math.min(4, imagePoints.size());
    // Use DMatrix to get better accuracy when solving the mapping
    boolean ok = imageMatrixDbl.setPolyToPoly(imageCoords, 0, screenCoords, 0, n);
    if (!ok) {
      // If we failed using 4 points, drop last one
      if (n == 4) {
        ok = imageMatrixDbl.setPolyToPoly(imageCoords, 0, screenCoords, 0, n - 1);
      }
    }
    if (ok) {
      // android.graphics.Matrix is needed for drawing, convert doubles to floats
      imageMatrix = imageMatrixDbl.toMatrix();
    }
    return ok;
  }

  /**
   * Resolves matrix mapping image points to geo points based on the MapView and
   * previously computed image to screen matrix (in computeImageWarp()).
   */
  public DMatrix computeImageToGeoMatrix() {
    if (mapImage == null) {
      return new DMatrix();
    }
    int w = mapImage.getWidth();
    int h = mapImage.getHeight();
    double[] imageCorners = new double[]{0, 0, w, 0, w, h, 0, h};
    double[] geoCorners = new double[8];
    imageMatrixDbl.mapPoints(geoCorners, imageCorners);
    Point p = new Point();
    Projection projection = googleMap.getProjection();
    for (int i = 0; i < 8; i += 2) {
      p.x = (int) Math.round(geoCorners[i]);
      p.y = (int) Math.round(geoCorners[i + 1]);
      LatLng geoPoint = projection.fromScreenLocation(p);
      // geoCorners store locations in longitude, latitude order
      geoCorners[i] = geoPoint.longitude;
      geoCorners[i + 1] = geoPoint.latitude;
    }
    // Resolve mapping using double precision
    DMatrix result = new DMatrix();
    if (!result.setPolyToPoly(imageCorners, 0, geoCorners, 0, 4)) {
      Log.w(CustomMaps.LOG_TAG, "Failed to create image-to-geo matrix");
    }
    return result;
  }

  /**
   * Converts a list of points to array of doubles arranged as [x0, y0, x1, y1,...]
   */
  private double[] createPointDoubleArray(List<Point> points) {
    double[] coords = new double[points.size() * 2];
    int i = 0;
    for (Point p : points) {
      coords[i++] = p.x;
      coords[i++] = p.y;
    }
    return coords;
  }

  // --------------------------------------------------------------------------
  // GoogleMap Camera listening methods

  @Override
  public void onCameraMoveStarted(int reason) {
  }

  @Override
  public void onCameraMove() {
    invalidate();
  }

  @Override
  public void onCameraIdle() {
    // Update immediately
    invalidate();
    // Update the overlay one more time after the map has had time to "settle"
    postDelayed(() -> {
      computeImageWarp(googleMap.getProjection());
      invalidate();
    }, 250);
  }
}
