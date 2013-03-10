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
import com.custommapsapp.android.kml.KmlFeature;
import com.custommapsapp.android.kml.KmlFile;
import com.custommapsapp.android.kml.KmlFolder;
import com.custommapsapp.android.kml.KmlInfo;
import com.custommapsapp.android.kml.KmlParser;
import com.custommapsapp.android.kml.KmzFile;
import com.custommapsapp.android.kml.Placemark;

import android.util.Log;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * MapCatalog keeps track of maps (GroundOverlays) stored in a directory.
 *
 * @author Marko Teittinen
 */
public class MapCatalog {
  private static String defaultMapName = "Map without name";

  /**
   * Sets the name used for maps that have no name.
   *
   * @param defaultName Localized name to be used for unnamed maps
   */
  public static void setDefaultMapName(String defaultName) {
    defaultMapName = (defaultName != null ? defaultName : "Map without name");
  }

  /**
   * Loads a named map from a KML or KMZ file. If name is not provided,
   * returns any one map stored in the file.
   *
   * @param kmlInfo KML or KMZ file containing the map
   * @param mapName the map to be read, or 'null' for any map
   * @return KmlFolder containing one GroundOverlay and possibly multiple
   *         Placemarks, or 'null' if the map couldn't be found
   */
  public static KmlFolder loadMap(KmlInfo kmlInfo, String mapName) {
    // Store relevant contents of the file in these variables
    GroundOverlay foundMap = null;
    KmlFolder foundFolder = null;
    List<Placemark> placemarks = new ArrayList<Placemark>();

    try {
      // Parse the file
      KmlParser parser = new KmlParser();
      Iterable<KmlFeature> features = parser.readFile(kmlInfo.getKmlReader());
      // Parsing successful, scan features for GroundOverlays and Placemarks
      for (KmlFeature feature : features) {
        // Collect top-level placemarks to a list
        if (feature instanceof Placemark) {
          placemarks.add((Placemark) feature);
          continue;
        }
        // If map has been found, we're only scanning for Placemarks
        if (foundMap != null) {
          continue;
        }
        if (feature instanceof KmlFolder) {
          // Scan included KmlFolders for the named map
          KmlFolder folder = (KmlFolder) feature;
          for (KmlFeature folderFeature : folder.getFeatures()) {
            if (folderFeature instanceof GroundOverlay) {
              // Select this map if the name matches
              if (mapName == null || folderFeature.getName().equals(mapName)) {
                foundMap = (GroundOverlay) folderFeature;
                foundFolder = folder;
                break; // folder scan
              }
            }
          }
        } else if (feature instanceof GroundOverlay) {
          // Select this map if the name matches
          if (mapName == null || feature.getName().equals(mapName)) {
            foundMap = (GroundOverlay) feature;
          }
        }
      }
    } catch (Exception ex) {
      Log.w(CustomMaps.LOG_TAG, "Failed to parse KML file: " + kmlInfo.toString(), ex);
    }

    if (foundMap == null) {
      return null;
    }

    foundMap.setKmlInfo(kmlInfo);
    if (foundFolder != null) {
      foundFolder.setKmlInfo(kmlInfo);
    }
    // Create a copy of the found folder, and add top level placemarks too
    KmlFolder result = createResultFolder(foundFolder, foundMap);
    for (Placemark placemark : placemarks) {
      placemark.setKmlInfo(kmlInfo);
      result.addFeature(placemark);
    }
    return result;
  }

  /**
   * Returns a KmlFolder that contains only a single GroundOverlay (map) with
   * optionally multiple Placemarks as well. Note that if the KmlFolder was
   * inside a Document tag, the icons stored at Document level should be added
   * separately to the KmlFolder returned from this method.
   *
   * @param folder where the map was originally located (null is OK)
   * @param map that should be the only GroundOverlay in the returned KmlFolder
   * @return a newly created KmlFolder containing only a single GroundOverlay
   *         and its associated Placemarks
   */
  private static KmlFolder createResultFolder(KmlFolder folder, GroundOverlay map) {
    KmlFolder result = new KmlFolder();
    result.addFeature(map);

    // Initialize KmlInfo and description
    KmlFeature basicInfo = (folder != null ? folder : map);
    result.setKmlInfo(basicInfo.getKmlInfo());
    result.setDescription(basicInfo.getDescription());

    // Verify that the map and result folder have non-empty names
    String folderName = (folder != null ? folder.getName().trim() : "");
    String mapName = map.getName();
    if (mapName == null || mapName.trim().length() == 0) {
      map.setName(folderName.length() > 0 ? folderName : defaultMapName);
    }
    result.setName(folderName.length() > 0 ? folderName : map.getName());

    // Copy possible Placemarks
    if (folder != null) {
      for (KmlFeature feature : folder.getFeatures()) {
        if (feature instanceof Placemark) {
          result.addFeature(feature);
        }
      }
    }
    return result;
  }

  // --------------------------------------------------------------------------
  // Instance variables and methods

  private File dataDir;
  private Collator stringComparer = Collator.getInstance();
  private Set<String> createdFiles = new HashSet<String>();
  private List<KmlFolder> allMaps = new ArrayList<KmlFolder>();
  private List<KmlFolder> inMaps = new ArrayList<KmlFolder>();
  private List<KmlFolder> nearMaps = new ArrayList<KmlFolder>();
  private List<KmlFolder> farMaps = new ArrayList<KmlFolder>();

  /**
   * Creates a new MapCatalog that contains all maps in a directory.
   *
   * @param dataDir directory that holds the maps of this catalog
   */
  public MapCatalog(File dataDir) {
    this.dataDir = dataDir;
  }

  /**
   * Adds a known created filename to catalog since some versions of Android
   * have issues refreshing map catalog after new maps are created.
   *
   * @param filename Name of a map file in data directory (w/o path).
   */
  public void addCreatedFile(String filename) {
    createdFiles.add(filename);
  }

  /**
   * Parses the given KML/KMZ file and returns a map from there. This method
   * is intended to be used when a user opens a KML attachment in mail, or
   * uses a file manager to open such file. The map is returned as a KmlFolder
   * containing exactly one GroundOverlay and optionally multiple Placemarks.
   *
   * @param mapFile containing at least one GroundOverlay definition
   * @return KmlFolder containing only a single GroundOverlay (first found in
   *         the file) and optionally multiple Placemarks, or {@code null} if
   *         no GroundOverlays were found in the file. The map is the first
   *         item stored in the returned KmlFolder features.
   */
  public KmlFolder parseLocalFile(File mapFile) {
    KmlFolder map = null;
    Collection<KmlInfo> siblings = findKmlData(mapFile.getParentFile());
    for (KmlInfo sibling : siblings) {
      if (sibling.getFile().getName().equals(mapFile.getName())) {
        map = MapCatalog.loadMap(sibling, null);
        break;
      }
    }
    return map;
  }

  /**
   * Checks if a map is part of multiple maps stored in single file.
   *
   * @param map KmlFolder to be checked
   * @return {@code true} if this catalog contains another map stored in the
   *         same KML or KMZ file
   */
  public boolean isPartOfMapSet(KmlFolder map) {
    File mapFile = map.getKmlInfo().getFile();
    for (KmlFolder candidate : allMaps) {
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
   * @return Iterable<KmlFolder> listing all maps in catalog alphabetically
   */
  public Iterable<KmlFolder> getAllMapsSortedByName() {
    sortMapsByName(allMaps);
    return allMaps;
  }

  /**
   * Sorts list of maps in alphabetical order by name
   */
  public void sortMapsByName(List<KmlFolder> maps) {
    // Before sorting, make sure the stringComparer matches current locale selected by user
    stringComparer = Collator.getInstance(Locale.getDefault());
    Collections.sort(maps, mapSorter);
  }

  /**
   * Finds all maps that contain a location. This is slightly faster than
   * calling groupMapsByDistance(longitude, latitude) followed by
   * getLocalMaps(). Result is the same though.
   *
   * @param longitude of the location
   * @param latitude of the location
   * @return Iterable<KmlFolder> of the maps that contain the location
   */
  public Iterable<KmlFolder> getMapsContainingPoint(float longitude, float latitude) {
    List<KmlFolder> result = new ArrayList<KmlFolder>();
    for (KmlFolder mapHolder : allMaps) {
      GroundOverlay map = mapHolder.getFirstMap();
      if (map != null && map.contains(longitude, latitude)) {
        result.add(mapHolder);
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
    for (KmlFolder mapHolder : allMaps) {
      GroundOverlay map = mapHolder.getFirstMap();
      if (map == null) {
        continue;
      }
      if (mapIsFartherAway(50000, map, latitude, longitude)) {
        farMaps.add(mapHolder);
      } else {
        float distance = map.getDistanceFrom(longitude, latitude);
        if (distance == 0f) {
          inMaps.add(mapHolder);
        } else if (distance < 50000f) {
          nearMaps.add(mapHolder);
        } else {
          farMaps.add(mapHolder);
        }
      }
    }
  }

  /**
   * Quickly check if a map is certainly farther away than given distance. Even
   * if this function returns false, it is still possible that the map is
   * farther away than the distance. This function only quickly determines maps
   * that certainly are farther away.
   *
   * @param distanceM Distance in meters to compare to.
   * @param map Map to measure distance to.
   * @param lat Latitude of the location to measure from.
   * @param lon Longitude of the location to measure from.
   * @return true if the map is at least given distance away from the closest
   *     point of the map.
   */
  private boolean mapIsFartherAway(double distanceM, GroundOverlay map, float lat, float lon) {
    float latMin = 90;
    float latMax = -90;
    float lonMin = 180;
    float lonMax = -180;
    if (!map.hasCornerTiePoints()) {
      latMin = map.getSouth();
      latMax = map.getNorth();
      lonMin = map.getWest();
      lonMax = map.getEast();
    } else {
      float[] point = map.getNorthEastCornerLocation();
      latMax = Math.max(latMax, point[1]);
      lonMax = Math.max(lonMax, point[0]);
      point = map.getSouthEastCornerLocation();
      latMin = Math.min(latMin, point[1]);
      lonMax = Math.max(lonMax, point[0]);
      point = map.getSouthWestCornerLocation();
      latMin = Math.min(latMin, point[1]);
      lonMin = Math.min(lonMin, point[0]);
      point = map.getNorthWestCornerLocation();
      latMax = Math.max(latMax, point[1]);
      lonMin = Math.min(lonMin, point[0]);
    }
    // Check if location is farther in straight North-South direction
    // * in latitudes, 90 degrees ~ 10,000 km = 10,000,000 m
    double distanceInLatDegrees = 90.0 * distanceM / 10000000;
    if (lat < latMin - distanceInLatDegrees || lat > latMax + distanceInLatDegrees) {
      return true;
    }
    // Check if location is farther in straight East-West direction
    // * at equator, 90 degrees ~ 10,000 km
    // * at latitude x, 90 degrees ~ cos(x) * 10,000 km
    double distanceInLonDegrees = 90.0 * distanceM / (Math.cos(Math.toRadians(lat)) * 10000000);
    return (lon < lonMin - distanceInLonDegrees || lon > lonMax + distanceInLonDegrees);
  }

  /**
   * Note: Call groupMapsByDistance(longitude, latitude) first.
   *
   * @return Iterable<KmlFolder> of maps containing the location
   */
  public Iterable<KmlFolder> getLocalMaps() {
    return inMaps;
  }

  /**
   * Note: Call groupMapsByDistance(longitude, latitude) first.
   *
   * @return Iterable<KmlFolder> of maps within 50 km (30 mi) of the
   *         location
   */
  public Iterable<KmlFolder> getNearMaps() {
    return nearMaps;
  }

  /**
   * Note: Call groupMapsByDistance(longitude, latitude) first.
   *
   * @return Iterable<KmlFolder> of maps farther than 50 km (30 mi) of the
   *         location
   */
  public Iterable<KmlFolder> getFarMaps() {
    return farMaps;
  }

  /**
   * Updates the contents of this catalog by re-reading files in data directory.
   */
  public void refreshCatalog() {
    clearCatalog();
    Iterable<KmlInfo> kmlEntries = findKmlData();
    KmlParser parser = new KmlParser();
    for (KmlInfo kmlInfo : kmlEntries) {
      allMaps.addAll(parseMapsFrom(kmlInfo, parser));
    }
  }

  public void clearCatalog() {
    // Close all ZipFiles in kmz files
    for (KmlFolder map : allMaps) {
      KmlInfo info = map.getKmlInfo();
      if (info instanceof KmzFile) {
        ((KmzFile) info).close();
      }
    }
    allMaps.clear();
    inMaps.clear();
    nearMaps.clear();
    farMaps.clear();
  }

  private Collection<KmlFolder> parseMapsFrom(KmlInfo kmlInfo, KmlParser parser) {
    if (parser == null) {
      parser = new KmlParser();
    }
    List<KmlFolder> maps = new ArrayList<KmlFolder>();
    List<Placemark> sharedPlacemarks = new ArrayList<Placemark>();
    try {
      Iterable<KmlFeature> features = parser.readFile(kmlInfo.getKmlReader());
      for (KmlFeature feature : features) {
        feature.setKmlInfo(kmlInfo);
        if (feature instanceof KmlFolder) {
          // Scan folder for maps, assign kmlinfo to them
          KmlFolder folder = (KmlFolder) feature;
          for (KmlFeature folderFeature : folder.getFeatures()) {
            folderFeature.setKmlInfo(kmlInfo);
            if (folderFeature instanceof GroundOverlay) {
              folder.setKmlInfo(kmlInfo);
              maps.add(createResultFolder(folder, (GroundOverlay) folderFeature));
            }
          }
        } else if (feature instanceof GroundOverlay) {
          maps.add(createResultFolder(null, (GroundOverlay) feature));
        } else if (feature instanceof Placemark) {
          sharedPlacemarks.add((Placemark) feature);
        }
      }
      if (!sharedPlacemarks.isEmpty()) {
        for (KmlFolder map : maps) {
          map.addFeatures(sharedPlacemarks);
        }
      }
    } catch (Exception ex) {
      Log.w(CustomMaps.LOG_TAG, "Failed to parse KML file: " + kmlInfo.toString(), ex);
    }
    return maps;
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
    // Add all files in given directory
    File[] files = directory.listFiles();
    for (File file : files) {
      String filename = file.getName();
      // Remove file from newly created files if it was found in listing
      createdFiles.remove(filename);
      if (filename.endsWith(".kml")) {
        kmlData.add(new KmlFile(file));
      } else if (filename.endsWith(".kmz")) {
        kmlData.addAll(scanKmz(file));
      }
    }
    // If we know of any created files that were not listed, add them too
    for (String name : createdFiles) {
      File file = new File(directory, name);
      if (name.endsWith(".kml")) {
        kmlData.add(new KmlFile(file));
      } else if (name.endsWith(".kmz")) {
        kmlData.addAll(scanKmz(file));
      }
    }
    return kmlData;
  }

  /**
   * Finds all kml entries inside a kmz file.
   *
   * @param file Kmz file to scan for kml entries.
   * @return Collection<KmzFile> containing all kml entries in the kmz file.
   *         Returns an empty list if the file is not a zip file.
   */
  private Collection<KmzFile> scanKmz(File file) {
    ZipFile kmzFile;
    try {
      kmzFile = new ZipFile(file);
    } catch (Exception ex) {
      // TODO: Add a notification dialog (?)
      Log.w(CustomMaps.LOG_TAG, "Not a valid KMZ file: " + file.getName(), ex);
      return Collections.emptyList();
    }
    List<KmzFile> kmlEntries = new ArrayList<KmzFile>();
    Enumeration<? extends ZipEntry> kmzContents = kmzFile.entries();
    while (kmzContents.hasMoreElements()) {
      ZipEntry kmzItem = kmzContents.nextElement();
      if (kmzItem.getName().endsWith(".kml")) {
        kmlEntries.add(new KmzFile(kmzFile, kmzItem));
      }
    }
    return kmlEntries;
  }

  /**
   * Compares KmlFolder by the name of the first stored map (case insensitive).
   */
  private Comparator<KmlFolder> mapSorter = new Comparator<KmlFolder>() {
    @Override
    public int compare(KmlFolder f1, KmlFolder f2) {
      GroundOverlay m1 = f1.getFirstMap();
      GroundOverlay m2 = f2.getFirstMap();
      if (m1 == null) {
        return (m2 == null ? 0 : 1);
      } else if (m2 == null) {
        return -1;
      }
      String name1 = m1.getName();
      String name2 = m2.getName();
      if (name1 == null) {
        return (name2 == null ? 0 : 1);
      } else if (name2 == null) {
        return -1;
      }
      return stringComparer.compare(name1, name2);
    }
  };
}
