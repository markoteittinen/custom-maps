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

import com.custommapsapp.android.kml.GroundOverlay;

import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.location.Location;
import android.util.FloatMath;
import android.util.Log;

import java.io.InputStream;

/**
 * GeoToImageConverter converts coordinates between geographical (long, lat) and
 * image (x, y) coordinate systems.
 *
 * @author Marko Teittinen
 */
public class GeoToImageConverter {
  private GroundOverlay mapData;
  private int imageWidth;
  private int imageHeight;
  private Matrix geoToImageMatrix;
  private Matrix imageToGeoMatrix;
  private float metersPerPixel;

  public GeoToImageConverter() {
  }

  public boolean hasMapData() {
    return (mapData != null);
  }

  public GroundOverlay getMapData() {
    return mapData;
  }

  public int getImageWidth() {
    return imageWidth;
  }

  public int getImageHeight() {
    return imageHeight;
  }

  /**
   * Returns the longitude and latitude for the center point of the map, or
   * 'null' if no map has been selected.
   *
   * @param result float[] that is used to return the result, 'null' is OK, new
   *        will be created
   * @return float[] containing longitude and latitude of the map center
   */
  public float[] getMapCenterGeoLocation(float[] result) {
    if (mapData == null) {
      return null;
    }
    if (result == null) {
      result = new float[2];
    }
    if (!mapData.hasCornerTiePoints()) {
      result[0] = (mapData.getEast() + mapData.getWest()) / 2f;
      if (mapData.getEast() < mapData.getWest()) {
        // Map spans 180 longitude, adjust the center coordinate to correct side of globe
        result[0] += 180f;
      }
      result[1] = (mapData.getNorth() + mapData.getSouth()) / 2f;
    } else {
      float[] corner = mapData.getNorthWestCornerLocation();
      float minLon = corner[0];
      float maxLon = corner[0];
      float minLat = corner[1];
      float maxLat = corner[1];
      corner = mapData.getNorthEastCornerLocation();
      minLon = Math.min(minLon, corner[0]);
      maxLon = Math.max(maxLon, corner[0]);
      minLat = Math.min(minLat, corner[1]);
      maxLat = Math.max(maxLat, corner[1]);
      corner = mapData.getSouthEastCornerLocation();
      minLon = Math.min(minLon, corner[0]);
      maxLon = Math.max(maxLon, corner[0]);
      minLat = Math.min(minLat, corner[1]);
      maxLat = Math.max(maxLat, corner[1]);
      corner = mapData.getSouthWestCornerLocation();
      minLon = Math.min(minLon, corner[0]);
      maxLon = Math.max(maxLon, corner[0]);
      minLat = Math.min(minLat, corner[1]);
      maxLat = Math.max(maxLat, corner[1]);
      result[0] = (minLon + maxLon) / 2f;
      result[1] = (minLat + maxLat) / 2f;
    }
    return result;
  }

  public boolean setMapData(GroundOverlay mapData) {
    // Verify the mapData is valid (can be loaded)
    if (mapData != null) {
      if (!readMapImageSize(mapData)) {
        // Failure to read bitmap size (invalid mapData definition or missing file?)
        return false;
      }
    } else {
      // Allow setting 'null' mapData
      imageWidth = 0;
      imageHeight = 0;
      metersPerPixel = 1;
    }
    this.mapData = mapData;
    geoToImageMatrix = null;
    imageToGeoMatrix = null;
    if (mapData != null) {
      computeMetersPerPixel();
    }
    return true;
  }

  /**
   * Converts geographic coordinates (longitude, latitude in that order) in
   * place to image coordinates.
   *
   * @param geoCoords longitude and latitude to be converted. Array size must be
   *        divisible by 2.
   * @return the same float[] that was passed in containing map image x and y
   *         coordinates
   */
  public float[] convertGeoToImageCoordinates(float[] geoCoords) {
    if (mapData == null) {
      return null;
    }
    if (isMercatorMap(mapData)) {
      return mercatorGeoToImageCoordinates(geoCoords);
    }
    if (geoToImageMatrix == null) {
      initGeoToImageMatrix();
    }
    geoToImageMatrix.mapPoints(geoCoords);
    return geoCoords;
  }

  /**
   * Converts image coordinates (x, y in that order) in place to geographic
   * coordinates (longitude, latitude).
   *
   * @param imageCoords x and y coordinates to be converted. Array size must be
   *        divisible by 2.
   * @return the same float[] that was passed in containing geographic
   *         coordinates. Returns 'null' if the mapping cannot be performed
   */
  public float[] convertImageToGeoCoordinates(float[] imageCoords) {
    if (mapData == null) {
      return null;
    }
    if (isMercatorMap(mapData)) {
      return mercatorImageToGeoCoordinates(imageCoords);
    }
    if (imageToGeoMatrix == null) {
      if (geoToImageMatrix == null) {
        initGeoToImageMatrix();
      }
      imageToGeoMatrix = new Matrix();
      if (!geoToImageMatrix.invert(imageToGeoMatrix)) {
        return null;
      }
    }
    imageToGeoMatrix.mapPoints(imageCoords);
    return imageCoords;
  }

  private boolean isMercatorMap(GroundOverlay mapData) {
    return !mapData.hasCornerTiePoints() && Math.abs(mapData.getRotateAngle()) < 1f;
  }

  /**
   * Converts geo coordinates (longitude, latitude - in that order) to image
   * coordinates in place.
   *
   * @param geoCoords to be converted
   * @return the same float[] as was passed in, now containing image x, y
   */
  private float[] mercatorGeoToImageCoordinates(float[] geoCoords) {
    for (int i = 0; i < geoCoords.length; i += 2) {
      mercatorSingleGeoToImageCoordinates(geoCoords, i);
    }
    return geoCoords;
  }

  private float[] mercatorSingleGeoToImageCoordinates(float[] geoCoords, int indexFirst) {
    //Log.i(LOG_TAG, "Using mercator geo to image conversion");
    float lon = geoCoords[indexFirst];
    float lat = latitudeToMercator(geoCoords[indexFirst + 1]);
    float lat0 = latitudeToMercator(mapData.getSouth());
    float lat1 = latitudeToMercator(mapData.getNorth());
    geoCoords[indexFirst] = imageWidth *
        (lon - mapData.getWest()) / (mapData.getEast() - mapData.getWest());
    geoCoords[indexFirst + 1] = imageHeight * (1 - (lat - lat0) / (lat1 - lat0));
    return geoCoords;
  }

  private float latitudeToMercator(float latitude) {
    latitude = toRadians(latitude);
    return (float) Math.log(Math.tan(latitude) + 1f / FloatMath.cos(latitude));
  }

  private float latitudeFromMercator(float mercator) {
    mercator = (float) Math.atan(Math.sinh(mercator));
    return toDegrees(mercator);
  }

  private float[] mercatorImageToGeoCoordinates(float[] imageCoords) {
    for (int i = 0; i < imageCoords.length; i += 2) {
      mercatorSingleImageToGeoCoordinates(imageCoords, i);
    }
    return imageCoords;
  }

  private float[] mercatorSingleImageToGeoCoordinates(float[] imageCoords, int indexFirst) {
    float lon = mapData.getWest() +
        (mapData.getEast() - mapData.getWest()) * (imageCoords[indexFirst] / imageWidth);
    float lat0 = latitudeToMercator(mapData.getSouth());
    float lat1 = latitudeToMercator(mapData.getNorth());
    float lat = lat0 + (1 - imageCoords[indexFirst + 1] / imageHeight) * (lat1 - lat0);
    imageCoords[indexFirst] = lon;
    imageCoords[indexFirst + 1] = latitudeFromMercator(lat);
    return imageCoords;
  }

  private float toRadians(float degrees) {
    return (float) Math.PI * degrees / 180f;
  }

  private float toDegrees(float radians) {
    return 180f * radians / (float) Math.PI;
  }

  /**
   * @return the scale of the current map in meters per pixel
   */
  public float getMetersPerPixel() {
    return metersPerPixel;
  }

  private void computeMetersPerPixel() {
    // Use average meters per pixel value from image corner to corner
    float[] imageCorners = { 0, 0, imageWidth, imageHeight, imageWidth, 0, 0, imageHeight };
    double imageDistance = Math.sqrt(imageWidth * imageWidth + imageHeight * imageHeight);

    convertImageToGeoCoordinates(imageCorners);
    Location geo1 = new Location("compute");
    geo1.setLatitude(imageCorners[1]);
    geo1.setLongitude(imageCorners[0]);
    Location geo2 = new Location("compute");
    geo2.setLatitude(imageCorners[3]);
    geo2.setLongitude(imageCorners[2]);
    double geoDistance1 = geo1.distanceTo(geo2);

    geo1.setLatitude(imageCorners[5]);
    geo1.setLongitude(imageCorners[4]);
    geo2.setLatitude(imageCorners[7]);
    geo2.setLongitude(imageCorners[6]);
    double geoDistance2 = geo1.distanceTo(geo2);

    metersPerPixel = (float) (geoDistance1 + geoDistance2) / (float) (2 * imageDistance);
  }

  // Initializes the matrix converting geo coordinates to image coordinates
  private void initGeoToImageMatrix() {
    if (!mapData.hasCornerTiePoints()) {
      initGeoToImageMatrixRotated();
    } else {
      initGeoToImageMatrixTiePoints();
    }
  }

  private void initGeoToImageMatrixRotated() {
    // TODO: Doesn't work across 180 longitude (gets incorrect longitude
    // to rotate around)
    // Geo coordinates of unrotated map corners
    float[] geoCorners = { mapData.getWest(), mapData.getNorth(),
                           mapData.getEast(), mapData.getNorth(),
                           mapData.getEast(), mapData.getSouth(),
                           mapData.getWest(), mapData.getSouth() };
    // Image corner coordinates in image space
    float[] imageCorners = { 0, 0,
                             imageWidth, 0,
                             imageWidth, imageHeight,
                             0, imageHeight };
    Matrix temp = new Matrix();
    boolean success = temp.setPolyToPoly(geoCorners, 0, imageCorners, 0, 4);
    // Add rotation around image center if necessary
    if (mapData.getRotateAngle() != 0) {
      temp.postRotate(mapData.getRotateAngle(), imageWidth / 2f, imageHeight / 2f);
    }
    geoToImageMatrix = temp;
    if (!success) {
      Log.w(CustomMaps.LOG_TAG, "FAILED to initialize geoToImageMatrix from rotation");
    }
  }

  private void initGeoToImageMatrixTiePoints() {
    float[] geoCorners = new float[8];
    float[] corner = mapData.getNorthWestCornerLocation();
    geoCorners[0] = corner[0];
    geoCorners[1] = corner[1];
    corner = mapData.getNorthEastCornerLocation();
    geoCorners[2] = corner[0];
    geoCorners[3] = corner[1];
    corner = mapData.getSouthEastCornerLocation();
    geoCorners[4] = corner[0];
    geoCorners[5] = corner[1];
    corner = mapData.getSouthWestCornerLocation();
    geoCorners[6] = corner[0];
    geoCorners[7] = corner[1];

    float[] imageCorners = { 0, 0,
                             imageWidth, 0,
                             imageWidth, imageHeight,
                             0, imageHeight };

    // Find matrix mapping geoCorners to imageCorners
    geoToImageMatrix = new Matrix();
    if (!geoToImageMatrix.setPolyToPoly(geoCorners, 0, imageCorners, 0, 4)) {
      Log.w(CustomMaps.LOG_TAG, "FAILED to initialize geoToImageMatrix from tie points");
    }
  }

  /**
   * Reads the map image size. If successful, updates member variables
   * pixelWidth and pixelHeight and returns 'true'. Otherwise leaves member
   * variables intact and returns 'false'.
   */
  private boolean readMapImageSize(GroundOverlay mapData) {
    InputStream in = null;
    try {
      in = mapData.getKmlInfo().getImageStream(mapData.getImage());

      BitmapFactory.Options options = ImageHelper.decodeImageBounds(in);
      imageWidth = options.outWidth;
      imageHeight = options.outHeight;

      return true;
    } catch (Exception ex) {
      return false;
    } finally {
      FileUtil.tryToClose(in);
    }
  }
}
