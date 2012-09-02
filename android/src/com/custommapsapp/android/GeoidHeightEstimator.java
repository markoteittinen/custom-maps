/*
 * Copyright 2012 Google Inc. All Rights Reserved.
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

import android.content.res.AssetManager;
import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GeoidHeightEstimator uses 1 degree grid data of EGM96 geoid height data stored in
 * a file to estimate local geoid height values for arbitrary points on Earth. The
 * file must be formatted in such a way that it contains a single short int entry for
 * each location on 1 degree grid. The locations should start from (lat 90, lon 0),
 * increasing latitude by 1 degree until (lat 90, lon 359). All those values should
 * match as they all refer to the north pole. Next the file should contain data for
 * all locations at latitude 89 (from (89, 0) to (89, 359)), followed by data for
 * latitude 88 and so on until latitude -90 (the south pole, from (-90, 0) to
 * (-90, 359)).
 *
 * All in all, the file should contain 181 * 360 samples.
 *
 * @author Marko Teittinen
 */
public class GeoidHeightEstimator {
  private static final String HEIGHT_DATA = "EGM96Geoid1deg.dac";

  /** Cache for height data that has been read from the file */
  private static Map<GeoIndex, Short> heightCache = new ConcurrentHashMap<GeoIndex, Short>();

  private static AssetManager assets = null;

  public static void initialize(AssetManager assets) {
    if (GeoidHeightEstimator.assets == null) {
      GeoidHeightEstimator.assets = assets;
    }
  }

  public static boolean isInitialized() {
    return (assets != null);
  }

  /**
   * GeoIndex is used as the key in the local cache that stores the values fetched
   * from the data file. Since it is used as a key to the cache map, it is immutable.
   */
  private static class GeoIndex {
    private final int latitude;
    private final int longitude;

    GeoIndex(int latitude, int longitude) {
      if (latitude < -90 || latitude > 90) {
        throw new IllegalArgumentException("Latitude must be within range [-90, 90]");
      }
      while (longitude < -180) {
        longitude += 360;
      }
      while (longitude > 180) {
        longitude -= 360;
      }
      this.latitude = latitude;
      this.longitude = longitude;
    }

    // Convert latitude to "row" index in data file (goes from 90 - -90)
    public int getLatitudeIndex() {
      return 90 - latitude;
    }

    // Convert longitude to "column" index in data file (from 0 to 359 (= -1))
    public int getLongitudeIndex() {
      return (longitude < 0 ? longitude + 360 : longitude);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof GeoIndex) {
        GeoIndex other = (GeoIndex) obj;
        return (this.latitude == other.latitude) && (this.longitude == other.longitude);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return 360 * (latitude + 90) + (longitude + 180);
    }
  }

  /**
   * Computes the geoid height at the given location. The returned height
   * should be subtracted from GPS altitude to get the user's altitude
   * relative to MSL (mean sea level).
   *
   * @param latitude of the location
   * @param longitude of the location
   * @return estimated geoid height in cm at the given location
   */
  public static int computeGeoidHeight(double latitude, double longitude) {
    Short[] gridCorners = getHeightsAt(latitude, longitude);
    int heightSW = gridCorners[0];
    int heightNW = gridCorners[1];
    int heightNE = gridCorners[2];
    int heightSE = gridCorners[3];

    // Compute the height at each edge at the right lat or lon
    double lonFraction = (longitude - Math.floor(longitude));
    double latFraction = (latitude - Math.floor(latitude));
    double heightN = heightNW + (heightNE - heightNW) * lonFraction;
    double heightS = heightSW + (heightSE - heightSW) * lonFraction;
    double heightW = heightSW + (heightNW - heightSW) * latFraction;
    double heightE = heightSE + (heightNE - heightSE) * latFraction;

    // Compute the height at correct lat/lon in two directions
    double heightEW = heightW + (heightE - heightW) * lonFraction;
    double heightNS = heightS + (heightN - heightS) * latFraction;
    // Return the average of the two values
    return (int) Math.round((heightEW + heightNS) / 2);
  }

  /**
   * Returns heights of the 1 degree grid corners around the given point.
   *
   * @param latitude of the given point
   * @param longitude of the given point
   * @return Geoid height data at each corner of 1 degree grid surrounding
   *         the point in order (SW, NW, NE, SE). The heights are in cm.
   */
  private static Short[] getHeightsAt(double latitude, double longitude) {
    Short[] results = new Short[4];
    // Create keys and read cached values
    int minLatitude = (int) Math.floor(latitude);
    int minLongitude = (int) Math.floor(longitude);
    GeoIndex sw = new GeoIndex(minLatitude, minLongitude);
    GeoIndex nw = new GeoIndex(minLatitude + 1, minLongitude);
    GeoIndex ne = new GeoIndex(minLatitude + 1, minLongitude + 1);
    GeoIndex se = new GeoIndex(minLatitude, minLongitude + 1);
    results[0] = heightCache.get(sw);
    results[1] = heightCache.get(nw);
    results[2] = heightCache.get(ne);
    results[3] = heightCache.get(se);
    // Check if any data was missing from cache
    boolean needsData = false;
    for (Short height : results) {
      if (height == null) {
        needsData = true;
        break;
      }
    }
    if (needsData) {
      // Read values from disk and store them in cache
      try {
        readValues(results, sw, nw, ne, se);
        heightCache.put(sw, results[0]);
        heightCache.put(nw, results[1]);
        heightCache.put(ne, results[2]);
        heightCache.put(se, results[3]);
      } catch (IOException ex) {
        Log.e(CustomMaps.LOG_TAG, "Failed to read geoid height data", ex);
        // Use zeros for failed fields (don't cache)
        for (int i = 0; i < 4; i++) {
          if (results[i] == null) {
            results[i] = 0;
          }
        }
      }
    }
    return results;
  }

  /**
   * Reads the height data for requested grid points from the data file.
   *
   * @param results Array where the results will be stored in order sw, nw, ne, se
   * @param sw GeoIndex for southwest grid point
   * @param nw GeoIndex for northwest grid point
   * @param ne GeoIndex for northeast grid point
   * @param se GeoIndex for southeast grid point
   *
   * @throws IOException if any file operations fail, some or all result array slots
   *         may be left blank
   */
  private static void readValues(Short[] results, GeoIndex sw, GeoIndex nw, GeoIndex ne, GeoIndex se)
      throws IOException {
    if (assets == null) {
      throw new IllegalStateException("GeoidHeightEstimator has not been initialized");
    }
    DataInputStream in = null;
    int position;
    try {
      in = new DataInputStream(assets.open(HEIGHT_DATA, AssetManager.ACCESS_BUFFER));
      position = 0;
      if (nw.longitude != 359) {
        position = readShort(in, position, nw, results, 1);
        position = readShort(in, position, ne, results, 2);
        position = readShort(in, position, sw, results, 0);
        position = readShort(in, position, se, results, 3);
      } else {
        position = readShort(in, position, ne, results, 2);
        position = readShort(in, position, nw, results, 1);
        position = readShort(in, position, se, results, 3);
        position = readShort(in, position, sw, results, 0);
      }
    } finally {
      in.close();
    }
  }

  /**
   * Reads a short int value from the given data stream which is currently at a given
   * position. The data read from the data stream will be the height data for the given
   * GeoIndex and the value will be stored in the given array at the given slot index.
   * The method returns the current offset after the data has been read.
   *
   * @param in DataInputStream where the data will be read
   * @param position The current position (offset) of the DataInputStream
   * @param index GeoIndex of the location data that needs to be read (must be located
   *        after 'position')
   * @param store Array of Shorts where the read value should be placed
   * @param slot Index in the array where the value should be placed
   * @return current read position in the DataInputStream after the read operation
   *
   * @throws IOException if the data cannot be read
   */
  private static int readShort(DataInputStream in, int position, GeoIndex index, Short[] store,
      int slot) throws IOException {
    int offset = 2 * (360 * index.getLatitudeIndex() + index.getLongitudeIndex());
    in.skipBytes(offset - position);
    store[slot] = in.readShort();
    return offset + 2;
  }
}
