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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * KmlParser is a partial XML pull parser implementation of KML parsing. It
 * interprets only the parts of KML that matter to Custom Maps, and ignores the
 * rest of the file.
 *
 * @author Marko Teittinen
 */
public class KmlParser {

  public Iterable<GroundOverlay> readFile(Reader reader)
      throws XmlPullParserException, IOException {
    return parseStream(reader);
  }

  public Iterable<GroundOverlay> readFile(File kmlFile) throws XmlPullParserException, IOException {
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

  private Iterable<GroundOverlay> parseStream(Reader in)
      throws XmlPullParserException, IOException {
    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
    // factory.setNamespaceAware(true);
    XmlPullParser xpp = factory.newPullParser();

    xpp.setInput(in);

    Iterable<GroundOverlay> result = null;
    int event = xpp.getEventType();
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG && xpp.getName().equals("kml")) {
        result = parseKml(xpp);
      }
      event = xpp.next();
    }
    return result;
  }

  private Iterable<GroundOverlay> parseKml(XmlPullParser xpp) throws XmlPullParserException,
      IOException {
    if (xpp.getEventType() != XmlPullParser.START_TAG || !xpp.getName().equals("kml")) {
      throw new IllegalStateException("XML parser is not at <kml> tag");
    }
    List<GroundOverlay> result = new ArrayList<GroundOverlay>();
    int event = xpp.next();
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("kml")) {
      if (event == XmlPullParser.START_TAG) {
        if (xpp.getName().equals("GroundOverlay")) {
          result.add(parseGroundOverlay(xpp));
        } else if (xpp.getName().equals("Folder")) {
          KmlFolder folder = parseFolder(xpp);
          for (GroundOverlay overlay : folder.getOverlays()) {
            result.add(overlay);
          }
        } else {
          skipBranch(xpp);
        }
      }
      event = xpp.next();
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
    int event = xpp.next();
    while (event != XmlPullParser.END_TAG || !xpp.getName().equals("Folder")) {
      if (event == XmlPullParser.START_TAG) {
        if (xpp.getName().equals("name")) {
          folder.setName(xpp.nextText());
        } else if (xpp.getName().equals("description")) {
          folder.setDescription(xpp.nextText());
        } else if (xpp.getName().equals("GroundOverlay")) {
          folder.addOverlay(parseGroundOverlay(xpp));
        } else {
          skipBranch(xpp);
        }
      }
      event = xpp.next();
    }
    return folder;
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
}
