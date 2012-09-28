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
package com.custommapsapp.android.kml;

import com.google.android.maps.GeoPoint;

import android.graphics.Point;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KmlParser is a partial XML pull parser implementation of KML parsing. It
 * interprets only the parts of KML that matter to Custom Maps, and ignores the
 * rest of the file.
 *
 * @author Marko Teittinen
 */
public class KmlParser {
  /**
   * Parses a KML file using the reader and returns an Iterable for found
   * KmlFeatures. KmlFeatures are KmlFolders, GroundOverlays, and Placemarks.
   * KmlFolders in turn can contain GroundOverlays and Placemarks.
   */
  public Iterable<KmlFeature> readFile(Reader reader)
      throws XmlPullParserException, IOException {
    return parseStream(reader);
  }

  /**
   * Parses a KML file and returns an Iterable for found KmlFeatures.
   * KmlFeatures are KmlFolders, GroundOverlays, and Placemarks. KmlFolders
   * in turn can contain GroundOverlays and Placemarks.
   */
  public Iterable<KmlFeature> readFile(File kmlFile) throws XmlPullParserException, IOException {
    FileReader reader = new FileReader(kmlFile);
    try {
      return parseStream(reader);
    } finally {
      try {
        reader.close();
      } catch (IOException ex) {
        Log.w("KmlParser", "Failed to close kml file", ex);
      }
    }
  }

  private Iterable<KmlFeature> parseStream(Reader in)
      throws XmlPullParserException, IOException {
    // Ignore possible leading U+feff char (Byte Order Mark in UTF some files)
    // XmlPullParser should ignore it as whitespace, but actually chokes on it
    BufferedReader inBuf =
        (in instanceof BufferedReader ? (BufferedReader) in : new BufferedReader(in));
    inBuf.mark(4);
    int ch = inBuf.read();
    if (ch != 0xFEFF) {
      // Not Byte Order Mark, reset to beginning
      inBuf.reset();
    }

    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
    // factory.setNamespaceAware(true);
    XmlPullParser xpp = factory.newPullParser();
    xpp.setInput(inBuf);

    Iterable<KmlFeature> result = null;
    int event = xpp.getEventType();
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG && xpp.getName().equals("kml")) {
        result = parseKml(xpp);
      }
      event = xpp.next();
    }
    return result;
  }

  private Iterable<KmlFeature> parseKml(XmlPullParser xpp) throws XmlPullParserException,
      IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG || !xpp.getName().equals("kml")) {
      throw new IllegalStateException("XML parser is not at <kml> tag");
    }
    List<KmlFeature> result = new ArrayList<KmlFeature>();
    int event = xpp.next();
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("kml")) {
      if (event == XmlPullParser.START_TAG) {
        if (xpp.getName().equals("Document")) {
          for (KmlFeature item : parseDocument(xpp)) {
            result.add(item);
          }
        } else if (xpp.getName().equals("Folder")) {
          result.add(parseFolder(xpp));
        } else if (xpp.getName().equals("GroundOverlay")) {
          result.add(parseGroundOverlay(xpp));
        } else {
          skipBranch(xpp);
        }
      }
      event = xpp.next();
    }
    return result;
  }

  /**
   * Parses a Document tag in a kml file. Document can contain IconStyles
   * for PlaceMarks and Folders containing GroundOverlays and Placemarks.
   *
   * @return Iterable<KmlFeature> containing PlaceMarks and GroundOverlays
   */
  private Iterable<KmlFeature> parseDocument(XmlPullParser xpp) throws XmlPullParserException,
      IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG || !xpp.getName().equals("Document")) {
      throw new IllegalStateException("XML parser is not at <Document> tag");
    }
    List<KmlFeature> result = new ArrayList<KmlFeature>();
    Map<String, String> styleNameMap = new HashMap<String, String>();
    Map<String, IconStyle> iconStyleMap = new HashMap<String, IconStyle>();
    int event = xpp.next();
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("Document")) {
      if (event == XmlPullParser.START_TAG) {
        if (xpp.getName().equals("StyleMap")) {
          // Get style map id (key)
          String styleId = getAttributeValue("id", xpp);
          // Get style name for normal style (value)
          String normalId = parseStyleMapNormal(xpp);
          styleNameMap.put(styleId, normalId);
        } else if (xpp.getName().equals("Style")) {
          // Map style id to style defined in tag
          String styleId = getAttributeValue("id", xpp);
          IconStyle style = parseStyle(xpp);
          if (style != null) {
            iconStyleMap.put(styleId, style);
          }
        } else if (xpp.getName().equals("Folder")) {
          result.add(parseFolder(xpp));
        } else if (xpp.getName().equals("GroundOverlay")) {
          result.add(parseGroundOverlay(xpp));
        } else if (xpp.getName().equals("Placemark")) {
          result.add(parsePlacemark(xpp));
        } else {
          skipBranch(xpp);
        }
      }
      event = xpp.next();
    }
    // Resolve all placemark icons
    if (!iconStyleMap.isEmpty()) {
      resolvePlacemarkIcons(result, styleNameMap, iconStyleMap);
    }
    return result;
  }

  /**
   * Parses a Folder tag in a kml file. Does not support nested folders.
   *
   * @return parsed KmlFolder
   */
  private KmlFolder parseFolder(XmlPullParser xpp) throws XmlPullParserException, IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG || !xpp.getName().equals("Folder")) {
      throw new IllegalStateException("XML parser is not at <Folder> tag");
    }
    KmlFolder folder = new KmlFolder();
    Map<String, String> styleNameMap = new HashMap<String, String>();
    Map<String, IconStyle> iconStyleMap = new HashMap<String, IconStyle>();
    int event = xpp.next();
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("Folder")) {
      if (event == XmlPullParser.START_TAG) {
        if (xpp.getName().equals("name")) {
          folder.setName(xpp.nextText());
        } else if (xpp.getName().equals("description")) {
          folder.setDescription(xpp.nextText());
        } else if (xpp.getName().equals("StyleMap")) {
          // Map style map ID to normal value in map
          String styleId = getAttributeValue("id", xpp);
          String normalId = parseStyleMapNormal(xpp);
          styleNameMap.put(styleId, normalId);
        } else if (xpp.getName().equals("Style")) {
          // Map style id to style defined in tag
          String styleId = getAttributeValue("id", xpp);
          IconStyle style = parseStyle(xpp);
          if (style != null) {
            iconStyleMap.put(styleId, style);
          }
        } else if (xpp.getName().equals("GroundOverlay")) {
          folder.addFeature(parseGroundOverlay(xpp));
        } else if (xpp.getName().equals("Placemark")) {
          folder.addFeature(parsePlacemark(xpp));
        } else {
          skipBranch(xpp);
        }
      }
      event = xpp.next();
    }
    // Resolve all placemark icons
    if (!iconStyleMap.isEmpty()) {
      resolvePlacemarkIcons(folder.getFeatures(), styleNameMap, iconStyleMap);
    }
    return folder;
  }

  /**
   * Resolves all unresolved placemark icons to their values based on given
   * style mappings.
   */
  private void resolvePlacemarkIcons(Iterable<KmlFeature> features,
      Map<String, String> styleNameMap, Map<String, IconStyle> iconStyleMap) {
    for (KmlFeature feature : features) {
      // Resolve placemarks in subfolders recursively
      if (feature instanceof KmlFolder) {
        KmlFolder folder = (KmlFolder) feature;
        resolvePlacemarkIcons(folder.getFeatures(), styleNameMap, iconStyleMap);
      }
      if (!(feature instanceof Placemark)) {
        continue;
      }

      Placemark placemark = (Placemark) feature;
      String styleId = placemark.getStyleId();
      // If icon has not been resolved yet, attempt to resolve it now
      if (styleId != null && placemark.getIconStyle() == null) {
        if (!iconStyleMap.containsKey(styleId)) {
          // StyleID does not refer directly to one of the icon styles,
          // try to resolve through styleNameMap
          styleId = styleNameMap.get(styleId);
          if (styleId == null) {
            continue;
          }
        }
        IconStyle icon = iconStyleMap.get(styleId);
        placemark.setIconStyle(icon);
      }
    }
  }

  /**
   * Parses StyleMap tag in a kml file. Returns the id value of key "normal".
   *
   * @return id value (characters following #-sign) for key 'normal'. Does not
   *         support full external URLs.
   */
  private String parseStyleMapNormal(XmlPullParser xpp) throws XmlPullParserException, IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG || !xpp.getName().equals("StyleMap")) {
      throw new IllegalStateException("XML parser is not at <StyleMap> tag");
    }
    int event = xpp.next();
    String key = null;
    String value = null;
    String normalValue = null;
    String anyValue = null;
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("StyleMap")) {
      if (event == XmlPullParser.START_TAG) {
        if (xpp.getName().equals("Pair")) {
          // parse contents within this method, ignore this enclosing Pair-tag
        } else if (xpp.getName().equals("key")) {
          key = xpp.nextText().trim();
        } else if (xpp.getName().equals("styleUrl")) {
          // Store only the part following last '#' sign
          value = xpp.nextText().trim();
          int hash = value.lastIndexOf('#');
          if (hash >= 0) {
            value = value.substring(hash + 1);
          }
          if (anyValue == null && value.length() > 0) {
            anyValue = value;
          }
        } else {
          skipBranch(xpp);
        }
      } else if (event == XmlPullParser.END_TAG && xpp.getName().equals("Pair")) {
        if (key.equals("normal") && value != null) {
          normalValue = value;
        }
        // Reset inner fields of Pair
        key = null;
        value = null;
      }
      event = xpp.next();
    }
    // Return mapping for 'normal' if it was found, otherwise return any value
    return (normalValue != null ? normalValue : anyValue);
  }

  /**
   * Parses a Style tag in a kml file. Returns a fully initialized IconStyle
   * object (other styles are ignored) or null if IconStyle is not defined.
   *
   * @return fully constructed IconStyle object or null if IconStyle was not
   *         defined
   */
  private IconStyle parseStyle(XmlPullParser xpp) throws XmlPullParserException, IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG || !xpp.getName().equals("Style")) {
      throw new IllegalStateException("XML parser is not at <Style> tag");
    }
    IconStyle result = null;
    boolean isInIconStyle = false;
    int event = xpp.next();
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("Style")) {
      if (event == XmlPullParser.START_TAG) {
        if (!isInIconStyle) {
          if (xpp.getName().equals("IconStyle")) {
            // parse contents within this method
            if (result == null) {
              result = new IconStyle();
            }
            isInIconStyle = true;
          } else {
            // Only IconStyle branches are parsed, skip others
            skipBranch(xpp);
          }
        } else {
          if (xpp.getName().equals("Icon")) {
            // icon contains href to image (icon palettes are not supported)
            // ignore this enclosing "Icon" tag
          } else if (xpp.getName().equals("href")) {
            String iconPath = xpp.nextText();
            result.setIconPath(iconPath);
          } else if (xpp.getName().equals("scale")) {
            // parse scale value
            String scaleStr = xpp.nextText().trim();
            float scale = Float.parseFloat(scaleStr);
            result.setScale(scale);
          } else if (xpp.getName().equals("hotSpot")) {
            // parse hotspot attributes
            for (int i = 0; i < xpp.getAttributeCount(); i++) {
              String attrName = xpp.getAttributeName(i);
              String attrValue = xpp.getAttributeValue(i);
              if (attrName.equals("x")) {
                result.setX(Float.parseFloat(attrValue));
              } else if (attrName.equals("y")) {
                result.setY(Float.parseFloat(attrValue));
              } else if (attrName.equals("xunits")) {
                IconStyle.Units units = IconStyle.Units.parseUnits(attrValue);
                if (units != null) {
                  result.setXUnits(units);
                }
              } else if (attrName.equals("yunits")) {
                IconStyle.Units units = IconStyle.Units.parseUnits(attrValue);
                if (units != null) {
                  result.setYUnits(units);
                }
              }
            }
          } else {
            skipBranch(xpp);
          }
        }
      } else if (event == XmlPullParser.END_TAG && xpp.getName().equals("IconStyle")) {
        isInIconStyle = false;
      }
      event = xpp.next();
    }
    return result;
  }

  private Placemark parsePlacemark(XmlPullParser xpp) throws XmlPullParserException, IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG || !xpp.getName().equals("Placemark")) {
      throw new IllegalStateException("XML parser is not at <Placemark> tag");
    }
    Placemark placemark = new Placemark();
    int event = xpp.next();
    boolean isInPoint = false;
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("Placemark")) {
      if (event == XmlPullParser.START_TAG) {
        if (xpp.getName().equals("name")) {
          placemark.setName(xpp.nextText());
        } else if (xpp.getName().equals("description")) {
          placemark.setDescription(xpp.nextText());
        } else if (xpp.getName().equals("Point")) {
          isInPoint = true;
        } else if (xpp.getName().equals("coordinates") && isInPoint) {
          // comma separated list: longitude, latitude, altitude (altitude optional)
          String coordinates = xpp.nextText().trim();
          String[] values = coordinates.split("\\s*,\\s*");
          float longitude = Float.parseFloat(values[0]);
          float latitude = Float.parseFloat(values[1]);
          placemark.setPoint(latitude, longitude);
        } else if (xpp.getName().equals("styleUrl")) {
          placemark.setStyleUrl(xpp.nextText().trim());
        } else {
          skipBranch(xpp);
        }
      } else if (event == XmlPullParser.END_TAG) {
        if (xpp.getName().equals("Point")) {
          isInPoint = false;
        }
      }
      event = xpp.next();
    }
    return placemark;
  }

  /**
   * Parses a GroundOverlay tag in a kml file. Does not allow nesting.
   *
   * @return parsed GroundOverlay
   */
  private GroundOverlay parseGroundOverlay(XmlPullParser xpp) throws XmlPullParserException,
      IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG || !xpp.getName().equals("GroundOverlay")) {
      throw new IllegalStateException("XML parser is not at <GroundOverlay> tag");
    }
    GroundOverlay overlay = new GroundOverlay();
    int event = xpp.next();
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("GroundOverlay")) {
      if (event == XmlPullParser.START_TAG) {
        if (xpp.getName().equals("name")) {
          overlay.setName(xpp.nextText());
        } else if (xpp.getName().equals("description")) {
          overlay.setDescription(xpp.nextText());
        } else if (xpp.getName().equals("Icon")) {
          parseOverlayIcon(xpp, overlay);
        } else if (xpp.getName().equals("LatLonBox")) {
          parseOverlayLatLonBox(xpp, overlay);
        } else if (xpp.getName().equals("gx:LatLonQuad")) {
          parseOverlayLatLonQuad(xpp, overlay);
        } else if (xpp.getName().equals("ExtendedData")) {
          parseOverlayExtendedData(xpp, overlay);
        } else {
          skipBranch(xpp);
        }
      }
      event = xpp.next();
    }
    return overlay;
  }

  /**
   * Parses Icon URL for a GroundOverlay
   */
  private void parseOverlayIcon(XmlPullParser xpp, GroundOverlay overlay)
      throws XmlPullParserException, IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG || !xpp.getName().equals("Icon")) {
      throw new IllegalStateException("XML parser is not at <Icon> tag");
    }
    int event = xpp.next();
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("Icon")) {
      if (event == XmlPullParser.START_TAG) {
        if (xpp.getName().equals("href")) {
          overlay.setImage(xpp.nextText());
        } else {
          skipBranch(xpp);
        }
      }
      event = xpp.next();
    }
  }

  private void parseOverlayLatLonBox(XmlPullParser xpp, GroundOverlay overlay)
      throws XmlPullParserException, IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG || !xpp.getName().equals("LatLonBox")) {
      throw new IllegalStateException("XML parser is not at <LatLonBox> tag");
    }
    int event = xpp.next();
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("LatLonBox")) {
      if (event == XmlPullParser.START_TAG) {
        if (xpp.getName().equals("north")) {
          overlay.setNorth(Float.parseFloat(xpp.nextText()));
        } else if (xpp.getName().equals("south")) {
          overlay.setSouth(Float.parseFloat(xpp.nextText()));
        } else if (xpp.getName().equals("east")) {
          overlay.setEast(Float.parseFloat(xpp.nextText()));
        } else if (xpp.getName().equals("west")) {
          overlay.setWest(Float.parseFloat(xpp.nextText()));
        } else if (xpp.getName().equals("rotation")) {
          overlay.setRotateAngle(Float.parseFloat(xpp.nextText()));
        } else {
          skipBranch(xpp);
        }
      }
      event = xpp.next();
    }
  }

  private void parseOverlayLatLonQuad(XmlPullParser xpp, GroundOverlay overlay)
      throws XmlPullParserException, IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG || !xpp.getName().equals("gx:LatLonQuad")) {
      throw new IllegalStateException("XML parser is not at <gx:LatLonQuad> tag");
    }
    int event = xpp.next();
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("gx:LatLonQuad")) {
      if (event == XmlPullParser.START_TAG) {
        if (xpp.getName().equals("coordinates")) {
          // coordinates tag contains four lon, lat, altitude sets
          String coordinates = xpp.nextText().trim();
          String[] fields = coordinates.split("[\\s,]+");
          float longitude, latitude;
          longitude = Float.parseFloat(fields[0]);
          latitude = Float.parseFloat(fields[1]);
          overlay.setSouthWestCornerLocation(longitude, latitude);
          longitude = Float.parseFloat(fields[3]);
          latitude = Float.parseFloat(fields[4]);
          overlay.setSouthEastCornerLocation(longitude, latitude);
          longitude = Float.parseFloat(fields[6]);
          latitude = Float.parseFloat(fields[7]);
          overlay.setNorthEastCornerLocation(longitude, latitude);
          longitude = Float.parseFloat(fields[9]);
          latitude = Float.parseFloat(fields[10]);
          overlay.setNorthWestCornerLocation(longitude, latitude);
        } else {
          skipBranch(xpp);
        }
      }
      event = xpp.next();
    }
  }

  private void parseOverlayExtendedData(XmlPullParser xpp, GroundOverlay overlay)
      throws XmlPullParserException, IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG || !xpp.getName().equals("ExtendedData")) {
      throw new IllegalStateException("XML parser is not at <ExtendedData> tag");
    }
    int event = xpp.next();
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("ExtendedData")) {
      if (event == XmlPullParser.START_TAG) {
        if (xpp.getName().equals("tie:tiepoint")) {
          parseOverlayTiepoint(xpp, overlay);
        } else {
          skipBranch(xpp);
        }
      }
      event = xpp.next();
    }
  }

  private void parseOverlayTiepoint(XmlPullParser xpp, GroundOverlay overlay)
      throws XmlPullParserException, IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG || !xpp.getName().equals("tie:tiepoint")) {
      throw new IllegalStateException("XML parser is not at <tie:tiepoint> tag");
    }
    GeoPoint geoPoint = null;
    Point imagePoint = null;
    int event = xpp.next();
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("tie:tiepoint")) {
      if (event == XmlPullParser.START_TAG) {
        if (xpp.getName().equals("tie:geo")) {
          if (geoPoint != null) {
            throw new IllegalStateException("Error: second geo point found for a tiepoint");
          }
          // tie:geo tag contains geo coordinates in lon,lat order
          String coordinates = xpp.nextText().trim();
          String[] fields = coordinates.split("[\\s,]+");
          float longitude, latitude;
          longitude = Float.parseFloat(fields[0]);
          latitude = Float.parseFloat(fields[1]);
          geoPoint = new GeoPoint(Math.round(1E6f * latitude), Math.round(1E6f * longitude));
        } else if (xpp.getName().equals("tie:image")) {
          if (imagePoint != null) {
            throw new IllegalStateException("Error: second image point found for a tiepoint");
          }
          // tie:image tag contains image coordinates in x,y order
          String coordinates = xpp.nextText().trim();
          String[] fields = coordinates.split("[\\s,]+");
          int x, y;
          x = Integer.parseInt(fields[0]);
          y = Integer.parseInt(fields[1]);
          imagePoint = new Point(x, y);
        } else {
          skipBranch(xpp);
        }
      }
      event = xpp.next();
    }
    if (geoPoint == null) {
      throw new IllegalStateException("Tiepoint is missing geo point");
    }
    if (imagePoint == null) {
      throw new IllegalStateException("Tiepoint is missing image point");
    }
    overlay.addTiepoint(new GroundOverlay.Tiepoint(geoPoint, imagePoint));
  }

  /**
   * Skips all parsing events until the current start tag ends. Must be called
   * when current event type is START_TAG.
   */
  private void skipBranch(XmlPullParser xpp) throws XmlPullParserException, IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG) {
      throw new IllegalStateException("XML parser is not at START_TAG");
    }
    String tagName = xpp.getName();
    int depth = 1;
    while (depth > 0) {
      int event = xpp.next();
      switch (event) {
        case XmlPullParser.START_TAG:
          if (xpp.getName().equals(tagName)) {
            depth++;
          }
          break;
        case XmlPullParser.END_TAG:
          if (xpp.getName().equals(tagName)) {
            depth--;
          }
          break;
      }
    }
  }

  /**
   * Finds value of a named attribute. xpp should be currently at a opening tag.
   *
   * @param name Name of the attribute
   * @param xpp XmlPullParser pointing at the tag for which the value is wanted
   * @return The value of the named attribute, or 'null' if attribute is missing
   */
  private String getAttributeValue(String name, XmlPullParser xpp) {
    for (int i = 0; i < xpp.getAttributeCount(); i++) {
      if (xpp.getAttributeName(i).equals(name)) {
        return xpp.getAttributeValue(i);
      }
    }
    return null;
  }
}
