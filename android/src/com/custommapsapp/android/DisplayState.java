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

import android.graphics.Matrix;
import android.view.View;

/**
 * DisplayState stores information about current state of map display.
 * Namely screen orientation, map image to screen coordinate conversion matrix,
 * and the user's current location.
 *
 * @author Marko Teittinen
 */
public class DisplayState {
  private GeoToImageConverter geoToImage = new GeoToImageConverter();
  private ImageToScreenConverter imageToScreen = new ImageToScreenConverter();
  private float imageNorthHeading = Float.NaN;
  private boolean followMode = false;

  public void setMapData(GroundOverlay mapData) {
    int orientation = mapData.getKmlInfo().getImageOrientation(mapData.getImage());
    geoToImage.setMapData(mapData);
    imageToScreen.setImageAttributes(geoToImage.getImageWidth(), geoToImage.getImageHeight(),
        orientation);
    imageNorthHeading = Float.NaN;
  }

  public void setScreenView(View view) {
    imageToScreen.setScreenView(view);
  }

  /**
   * Sets if the map display should keep the GPS location centered as it
   * gets updated.
   *
   * @param followMode {@code true} to keep GPS dot centered on the display
   */
  public void setFollowMode(boolean followMode) {
    this.followMode = followMode;
  }

  /**
   * @return {@code true} if current GPS location is kept centered
   */
  public boolean getFollowMode() {
    return followMode;
  }

  /**
   * Computes the heading in image coordinates for North. If North is directly
   * upwards (y decreasing, no change in x) this will return 0. If North is
   * directly to right (x increasing, no change in y) this method will return
   * 90, etc.
   *
   * @return Heading in image coordinates towards North
   */
  public float computeNorthHeading() {
    if (!Float.isNaN(imageNorthHeading)) {
      return imageNorthHeading;
    }
    GroundOverlay mapData = geoToImage.getMapData();
    if (mapData == null) {
      return 0f;
    }

    // Find approx map center longitude and north/south limits
    float longitude;
    float southLatitude;
    float northLatitude;
    if (!mapData.hasCornerTiePoints()) {
      // TODO: won't work across 180 longitude
      longitude = (mapData.getWest() + mapData.getEast()) / 2;
      southLatitude = mapData.getSouth();
      northLatitude = mapData.getNorth();
    } else {
      float[] nwCorner = mapData.getNorthWestCornerLocation();
      float[] seCorner = mapData.getSouthEastCornerLocation();
      southLatitude = Math.min(nwCorner[1], seCorner[1]);
      northLatitude = Math.max(nwCorner[1], seCorner[1]);
      longitude = (nwCorner[0] + seCorner[0]) / 2;
    }
    // Find out heading along longitude from south to north
    float[] southPoint = { longitude, southLatitude };
    float[] northPoint = { longitude, northLatitude };
    geoToImage.convertGeoToImageCoordinates(southPoint);
    geoToImage.convertGeoToImageCoordinates(northPoint);

    float dx = northPoint[0] - southPoint[0];
    float dy = -(northPoint[1] - southPoint[1]);
    double radianAngle = Math.atan2(dy, dx);
    float degreeAngle = 90 - (float) Math.toDegrees(radianAngle);
    imageNorthHeading = degreeAngle + mapData.getKmlInfo().getImageOrientation(mapData.getImage());
    // Normalize angle to [0, 360)
    while (imageNorthHeading >= 360f) {
      imageNorthHeading -= 360f;
    }
    while (imageNorthHeading < 0f) {
      imageNorthHeading += 360f;
    }
    return imageNorthHeading;
  }

  public Matrix getImageToScreenMatrix() {
    return imageToScreen.getImageToScreenMatrix();
  }

  /**
   * @return meters per pixel value in unzoomed map
   */
  public float getMetersPerPixel() {
    return geoToImage.getMetersPerPixel();
  }

  /**
   * Translates the map image being displayed by (tx, ty). Returns a boolean
   * indicating if the full translation was allowed. Value {@code false}
   * indicates the translation was truncated to avoid scrolling the map off
   * screen.
   *
   * @param tx amount of translation in east-west direction
   * @param ty amount of translation in north-south direction
   * @return {@code true} if the full translation was allowed, {@code false}
   *         if the full translation was not allowed to keep map on screen
   */
  public boolean translate(float tx, float ty) {
    return imageToScreen.translate(tx, ty);
  }

  /**
   * @return the zoom factor being used currently when displaying the map
   */
  public float getZoomLevel() {
    return imageToScreen.getZoomLevel();
  }

  /**
   * set the zoom factor being used currently when displaying the map
   */
  public void setZoomLevel(float zoom) {
    imageToScreen.setZoomLevel(zoom);
  }

  /**
   * Zooms the map image on screen by the given factor.
   *
   * @param factor to zoom the map. 0-1 zooms out, 1- zooms in
   */
  public void zoom(float factor) {
    imageToScreen.zoom(factor);
  }

  /**
   * Returns float[] containing longitude and latitude of the screen center
   * point. If the system fails to convert screen to image coordinates or image
   * to geo coordinates, returns 'null' (that should never happen).
   *
   * @return geo coordinates (longitude and latitude, in that order) of the
   *         screen center point
   */
  public float[] getScreenCenterGeoLocation() {
    float[] location = imageToScreen.getScreenCenterCoordinates(null);
    if (location == null) {
      return null;
    }
    // Convert screen center into image coordinates
    imageToScreen.convertScreenToImageCoordinates(location);
    return geoToImage.convertImageToGeoCoordinates(location);
  }

  public float[] getMapCenterGeoLocation(float[] result) {
    return geoToImage.getMapCenterGeoLocation(result);
  }

  public boolean centerOnGeoLocation(float longitude, float latitude) {
    // Check that the location is within the map boundaries
    float[] location = new float[] { longitude, latitude };
    location = geoToImage.convertGeoToImageCoordinates(location);
    if (location == null || location[0] < 0 || geoToImage.getImageWidth() < location[0] ||
        location[1] < 0 || geoToImage.getImageHeight() < location[1]) {
      return false;
    }

    imageToScreen.convertImageToScreenCoordinates(location);
    View screenView = imageToScreen.getScreenView();
    float dx = screenView.getWidth() / 2 - location[0];
    float dy = screenView.getHeight() / 2 - location[1];
    imageToScreen.translate(dx, dy);
    return true;
  }

  /**
   * Converts geo coordinates (lon, lat) to screen coordinates in place.
   *
   * @return The original float array containing screen coordinates (x, y) or
   *         null if the conversion cannot be performed at this time because
   *         either geo-to-image or image-to-screen conversion has not been
   *         initialized.
   */
  public float[] convertGeoToScreenCoordinates(float[] location) {
    if (geoToImage.convertGeoToImageCoordinates(location) == null) {
      return null;
    }
    return imageToScreen.convertImageToScreenCoordinates(location);
  }

  /**
   * Converts screen coordinates (x, y) to geo coordinates (lon, lat) in place.
   *
   * @return The original float array containing geo coordinates (lon, lat) or
   *         null if the conversion cannot be performed at this time.
   */
  public float[] convertScreenToGeoCoordinates(float[] location) {
    if (imageToScreen.convertScreenToImageCoordinates(location) == null) {
      return null;
    }
    return geoToImage.convertImageToGeoCoordinates(location);
  }
}
