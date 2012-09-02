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
import com.custommapsapp.android.kml.KmlInfo;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;

/**
 * MapDisplay is a base class for different kinds of MapDisplays. Nowadays there
 * is only MapUpMapDisplay, but there used to be NorthUpMapDisplay as well.
 *
 * @author Marko Teittinen
 */
public abstract class MapDisplay extends View {
  protected boolean followMode = false;

  public MapDisplay(Context context) {
    super(context);
  }

  public MapDisplay(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public MapDisplay(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
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
   * Sets the DisplayState object used to track geo to screen location
   * conversions.
   *
   * @param displayState
   */
  public abstract void setDisplayState(DisplayState displayState);

  /**
   * @return the zoom level the map is being displayed at
   */
  public abstract float getZoomLevel();

  /**
   * Set the zoom level of the map to the given scale factor.
   */
  public abstract void setZoomLevel(float zoomLevel);

  /**
   * @return the longitude and latitude of the center of the display
   */
  public abstract float[] getScreenCenterGeoLocation();

  /**
   * Sets a new map to be displayed.
   *
   * @param map GroundOverlay containing image and rotation info for the map
   * @throws MapImageTooLargeException if the map image is too large to be kept in memory
   */
  public abstract void setMap(GroundOverlay map) throws MapImageTooLargeException;

  /**
   * @return GroundOverlay being displayed currently
   */
  public abstract GroundOverlay getMap();

  /**
   * Move the map image on screen.
   *
   * @param xv amount of movement in horizontal direction
   * @param yv amount of movement in vertical direction
   * @return {@code boolean} indicating if move was successful and the image
   * is not about to scroll off screen. Returning {@code false} indicates
   * additional scrolling to this direction will not result in additional
   * movement of the map image.
   */
  public abstract boolean translateMap(float xv, float yv);

  /**
   * Zooms the map image by a factor. Values larger than 1 make the image grow
   * and values smaller than 1 make the image shrink.
   *
   * @param factor to to zoom the map by. Must be larger than 0.
   */
  public abstract void zoomMap(float factor);

  /**
   * Sets the user's GPS location to be drawn on the map.
   *
   * @param longitude of the user's location
   * @param latitude of the user's location
   * @param accuracy of the location coordinates (in meters)
   * @param heading the compass direction user is facing or moving towards
   */
  public abstract void setGpsLocation(
      float longitude, float latitude, float accuracy, float heading);

  /**
   * If the latest GPS location is within the map boundaries, center it in the
   * map display without changing zoom level.
   *
   * @return {@code true} if the map was centered, {@code false} otherwise
   */
  public abstract boolean centerOnGpsLocation();

  /**
   * Centers the map display on the center of the map.
   */
  public abstract void centerOnMapCenterLocation();

  /**
   * If the given GPS location is within the map boundaries, center it in the
   * map display without changing zoom level.
   *
   * @return {@code true} if the map was centered, {@code false} otherwise
   */
  public abstract boolean centerOnLocation(float longitude, float latitude);

  /**
   * Loads the bitmap image used as a map in a GroundOverlay.
   *
   * @param map GroundOverlay whose bitmap is going to be read
   * @return {@code Bitmap} used by the GroundOverlay
   * @throws IOException if image loading fails because of I/O problem
   * @throws MapImageTooLargeException if the map image is too large to keep in memory
   */
  protected Bitmap loadMapImage(GroundOverlay map) throws IOException, MapImageTooLargeException {
    System.gc();  // Maximize memory available for the image
    if (map == null) {
      return null;
    }
    KmlInfo data = map.getKmlInfo();
    InputStream in = null;
    try {
      in = data.getImageStream(map.getImage());
      Bitmap image = ImageHelper.loadImage(in);
      if (image != null) {
        return image;
      } else {
        // Out of memory exception occurred while loading
        throw new MapImageTooLargeException(map.getImage());
      }
    } finally {
      FileUtil.tryToClose(in);
    }
  }

  /**
   * Custom exception used when map image cannot fit into memory.
   */
  @SuppressWarnings("serial")
  public static class MapImageTooLargeException extends Exception {
    public MapImageTooLargeException(String msg) {
      super(msg);
    }
  }
}
