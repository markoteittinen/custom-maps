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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Placemark stores a single location marker for maps.
 *
 * @author Marko Teittinen
 */
public class Placemark extends KmlFeature {
  private static final long serialVersionUID = 1L;

  private GeoPoint point;
  private String styleUrl;
  private IconStyle iconStyle;

  public GeoPoint getPoint() {
    return point;
  }
  public void setPoint(GeoPoint point) {
    this.point = point;
  }
  public void setPoint(float latitude, float longitude) {
    this.point = new GeoPoint(Math.round(1E6f * latitude), Math.round(1E6f * longitude));
  }

  /** StyleId is the final part of styleUrl (following last #) */
  public String getStyleId() {
    if (styleUrl == null) {
      return null;
    }
    return styleUrl.substring(styleUrl.lastIndexOf('#') + 1);
  }
  public String getStyleUrl() {
    return styleUrl;
  }
  public void setStyleUrl(String styleUrl) {
    this.styleUrl = styleUrl;
  }

  public IconStyle getIconStyle() {
    // Make sure iconStyle has KmlInfo associated with it
    if (iconStyle != null && iconStyle.getKmlInfo() == null) {
      iconStyle.setKmlInfo(this.getKmlInfo());
    }
    return iconStyle;
  }
  public void setIconStyle(IconStyle iconStyle) {
    this.iconStyle = iconStyle;
  }

  // --------------------------------------------------------------------------
  // Serializable implementation for Placemark

  private void writeObject(ObjectOutputStream out) throws IOException {
    // No need to write superclass fields, they are done automagically
    out.writeInt(point.getLatitudeE6());
    out.writeInt(point.getLongitudeE6());
    out.writeUTF(styleUrl);
    out.writeObject(iconStyle);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    // No need to read superclass fields, they are done automagically
    int latitudeE6 = in.readInt();
    int longitudeE6 = in.readInt();
    point = new GeoPoint(latitudeE6, longitudeE6);
    styleUrl = in.readUTF();
    iconStyle = (IconStyle) in.readObject();
  }
}
