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
import com.custommapsapp.android.kml.KmlFile;
import com.custommapsapp.android.kml.KmlInfo;
import com.custommapsapp.android.kml.KmlParser;
import com.custommapsapp.android.kml.KmzFile;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * MapCatalog keeps track of maps (GroundOverlays) stored in a directory.
 *
 * @author Marko Teittinen
 */
public class MapCatalog {
  private static final String LOG_TAG = "Custom Maps";

  private File dataDir;
  private List<GroundOverlay> allMaps = new ArrayList<GroundOverlay>();
  private List<GroundOverlay> inMaps = new ArrayList<GroundOverlay>();
  private List<GroundOverlay> nearMaps = new ArrayList<GroundOverlay>();
  private List<GroundOverlay> farMaps = new ArrayList<GroundOverlay>();

  /**
   * Creates a new MapCatalog that contains all maps in a directory.
   *
   * @param dataDir directory that holds the maps of this catalog
   */
  public MapCatalog(File dataDir) {
    this.dataDir = dataDir;
    refreshCatalog();
  }

  /**
   * Parses the given KML/KMZ file and returns a map from there.
   *
   * @param mapFile containing GroundOverlay definitions
   * @return GroundOverlay from the file, or {@code null} if none found
   */
  public GroundOverlay parseLocalFile(File mapFile) {
    Collection<KmlInfo> siblings = findKmlData(mapFile.getParentFile());
    KmlParser parser = new KmlParser();
    for (KmlInfo sibling : siblings) {
      if (sibling.getFile().getName().equals(mapFile.getName())) {
        // Found the requested file, attempt to parse it
        try {
          Iterable<GroundOverlay> overlays = parser.readFile(sibling.getKmlReader());
          // Parsing successful, if any maps were found return first
          Iterator<GroundOverlay> iter = overlays.iterator();
          if (iter.hasNext()) {
            GroundOverlay map = iter.next();
            map.setKmlInfo(sibling);
            return map;
          }
        } catch (Exception ex) {
          Log.w(LOG_TAG, "Failed to parse KML file: " + sibling.toString(), ex);
        }
      }
    }
    // No GroundOverlays were found in the file
    return null;
  }

  /**
   * Checks if a map is part of multiple maps stored in single file.
   *
   * @param map GroundOverlay to be checked
   * @return {@code true} if this catalog contains another map stored in the
   * same KML or KMZ file
   */
  public boolean isPartOfMapSet(GroundOverlay map) {
    File mapFile = map.getKmlInfo().getFile();
    for (GroundOverlay candidate : allMaps) {
      // Check if the maps are stored in same file
      if (mapFile.equals(candidate.getKmlInfo().getFile())) {
        // Ignore exact match (with self)
        if (!map.equals(candidate)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @return Iterable<GroundOverlay> listing all maps in catalog alphabetically
   */
  public Iterable<GroundOverlay> getAllMapsSortedByName() {
    Collections.sort(allMaps, mapSorter);
    return allMaps;
  }

  /**
   * Sorts list of maps in alphabetical order by name
   */
  public void sortMapsByName(List<GroundOverlay> maps) {
    Collections.sort(maps, mapSorter);
  }

  /**
   * Finds all maps that contain a location. This is slightly faster than
   * calling groupMapsByDistance(longitude, latitude) followed by
   * getLocalMaps(). Result is the same though.
   *
   * @param longitude of the location
   * @param latitude of the location
   * @return Iterable<GroundOverlay> of the maps that contain the location
   */
  public Iterable<GroundOverlay> getMapsContainingPoint(float longitude, float latitude) {
    List<GroundOverlay> result = new ArrayList<GroundOverlay>();
    for (GroundOverlay map : allMaps) {
      if (map.contains(longitude, latitude)) {
        result.add(map);
      }
    }
    return result;
  }

  /**
   * Groups maps by distance from the given point. Maps will be divided to three
   * groups: maps containing the point, maps near the point (< 50km), maps far
   * from point.
   *
   * @param longitude of the location
   * @param latitude of the location
   */
  public void groupMapsByDistance(float longitude, float latitude) {
    inMaps.clear();
    nearMaps.clear();
    farMaps.clear();
    for (GroundOverlay map : allMaps) {
      float distance = map.getDistanceFrom(longitude, latitude);
      if (distance == 0f) {
        inMaps.add(map);
      } else if (distance < 50000f) {
        nearMaps.add(map);
      } else {
        farMaps.add(map);
      }
    }
  }

  /**
   * Note: Call groupMapsByDistance(longitude, latitude) first.
   *
   * @return Iterable<GroundOverlay> of maps containing the location
   */
  public Iterable<GroundOverlay> getLocalMaps() {
    return inMaps;
  }

  /**
   * Note: Call groupMapsByDistance(longitude, latitude) first.
   *
   * @return Iterable<GroundOverlay> of maps within 50 km (30 mi) of the
   *         location
   */
  public Iterable<GroundOverlay> getNearMaps() {
    return nearMaps;
  }

  /**
   * Note: Call groupMapsByDistance(longitude, latitude) first.
   *
   * @return Iterable<GroundOverlay> of maps farther than 50 km (30 mi) of the
   *         location
   */
  public Iterable<GroundOverlay> getFarMaps() {
    return farMaps;
  }

  /**
   * Updates the contents of this catalog by re-reading files in data directory.
   */
  public void refreshCatalog() {
    allMaps.clear();
    inMaps.clear();
    nearMaps.clear();
    farMaps.clear();
    KmlParser parser = new KmlParser();
    for (KmlInfo kmlInfo : findKmlData()) {
      try {
        Iterable<GroundOverlay> overlays = parser.readFile(kmlInfo.getKmlReader());
        for (GroundOverlay overlay : overlays) {
          overlay.setKmlInfo(kmlInfo);
          allMaps.add(overlay);
        }
      } catch (Exception ex) {
        Log.w(LOG_TAG, "Failed to parse KML file: " + kmlInfo.toString(), ex);
      }
    }
  }

  /**
   * @return Iterable<KmlInfo> of all available KML and KMZ files
   */
  private Iterable<KmlInfo> findKmlData() {
    List<KmlInfo> kmlData = new ArrayList<KmlInfo>();
    kmlData.addAll(findKmlData(dataDir));
    return kmlData;
  }

  /**
   * @return Collection<KmlInfo> of all KML and KMZ files in a directory
   */
  private Collection<KmlInfo> findKmlData(File directory) {
    List<KmlInfo> kmlData = new ArrayList<KmlInfo>();
    if (directory == null || !directory.exists() || !directory.isDirectory()) {
      return kmlData;
    }
    File[] files = directory.listFiles();
    for (File file : files) {
      if (file.getName().endsWith(".kml")) {
        kmlData.add(new KmlFile(file));
      } else if (file.getName().endsWith(".kmz")) {
        ZipFile kmzFile;
        try {
          kmzFile = new ZipFile(file);
        } catch (Exception ex) {
          // TODO: Add a notification dialog (?)
          Log.w(LOG_TAG, "Not a valid KMZ file: " + file.getName(), ex);
          continue;
        }
        Enumeration<? extends ZipEntry> kmzContents = kmzFile.entries();
        while (kmzContents.hasMoreElements()) {
          ZipEntry kmzItem = kmzContents.nextElement();
          if (kmzItem.getName().endsWith(".kml")) {
            kmlData.add(new KmzFile(kmzFile, kmzItem));
          }
        }
      }
    }
    return kmlData;
  }

  /**
   * Compares GroundOverlays by name (case insensitive).
   */
  private Comparator<GroundOverlay> mapSorter = new Comparator<GroundOverlay>() {
    @Override
    public int compare(GroundOverlay m1, GroundOverlay m2) {
      return m1.getName().compareToIgnoreCase(m2.getName());
    }
  };
}
