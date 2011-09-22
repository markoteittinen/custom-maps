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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;

import java.io.IOException;

/**
 * MapUpMapDisplay displays a bitmap as a map in its native orientation and
 * maps users' GPS coordinates to the image coordinates to show their location.
 *
 * @author Marko Teittinen
 */
public class MapUpMapDisplay extends MapDisplay {
  private Bitmap mapImage;
  private GroundOverlay mapData;
  private DisplayState displayState = new DisplayState();

  public MapUpMapDisplay(Context context) {
    super(context);
  }

  public MapUpMapDisplay(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void setDisplayState(DisplayState displayState) {
    this.displayState = displayState;
  }

  @Override
  public void setFollowMode(boolean followMode) {
    super.setFollowMode(followMode);
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
  @Override
  public boolean translateMap(float tx, float ty) {
    boolean result = displayState.translate(tx, ty);
    invalidate();
    return result;
  }

  /**
   * Returns float[] containing longitude and latitude of the screen center point.
   * If the system fails to convert screen to image coordinates or image to geo
   * coordinates, returns 'null' (that should never happen).
   *
   * @return geo coordinates (longitude and latitude, in that order) of the screen
   * center point
   */
  @Override
  public float[] getScreenCenterGeoLocation() {
    return displayState.getScreenCenterGeoLocation();
  }

  @Override
  public float getZoomLevel() {
    return displayState.getZoomLevel();
  }

  @Override
  public void zoomMap(float factor) {
    displayState.zoom(factor);
    invalidate();
  }

  @Override
  public void setZoomLevel(float zoom) {
    displayState.setZoomLevel(zoom);
    invalidate();
  }

  @Override
  public GroundOverlay getMap() {
    return mapData;
  }

  @Override
  public void setMap(GroundOverlay newMap) throws MapImageTooLargeException {
    if (mapData == newMap || (mapData != null && mapData.equals(newMap))) {
      return;
    }
    if (mapImage != null) {
      // Release memory used by the old map image
      mapImage.recycle();
      mapImage = null;
      mapData = null;
    }
    try {
      mapImage = loadMapImage(newMap);
    } catch (IOException ex) {
      mapImage = null;
    }
    if (mapImage == null) {
      spotSet = false;
      mapData = null;
      return;
    }
    mapData = newMap;

    displayState.setMapData(mapData);
    displayState.setScreenView(this);
    invalidate();
  }

  @Override
  public boolean centerOnGpsLocation() {
    // Check if geo location has been set
    if (!spotSet) {
      // No GPS location available, center map until we know GPS location
      centerOnMapCenterLocation();
      return true;
    }
    return centerOnLocation(geoLocation[0], geoLocation[1]);
  }

  @Override
  public void centerOnMapCenterLocation() {
    float[] location = displayState.getMapCenterGeoLocation(null);
    centerOnLocation(location[0], location[1]);
  }

  @Override
  public boolean centerOnLocation(float longitude, float latitude) {
    boolean result = displayState.centerOnGeoLocation(longitude, latitude);
    if (result) {
      invalidate();
    }
    return result;
  }

  @Override
  public void onDraw(Canvas canvas) {
    if (mapImage == null || mapImage.isRecycled()) {
      return;
    }
    canvas.drawBitmap(mapImage, displayState.getImageToScreenMatrix(), null);
  }

  @Override
  public void onSizeChanged(int w, int h, int oldW, int oldH) {
    super.onSizeChanged(w, h, oldW, oldH);
    // Keep the same point centered in the view
    displayState.translate((w - oldW) / 2f, (h - oldH) / 2);
  }

  //--------------------------------------------------------------------------------------------
  // Latitude/longitude

  private boolean spotSet = false;
  private float[] geoLocation = new float[2];

  @Override
  public void setGpsLocation(float longitude, float latitude, float accuracy, float heading) {
    if (mapImage == null) {
      return;
    }
    geoLocation[0] = longitude;
    geoLocation[1] = latitude;
    spotSet = true;
    if (getFollowMode()) {
      setFollowMode(centerOnGpsLocation());
      invalidate();
    }
  }
}
