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

import com.custommapsapp.android.CustomMaps;
import com.custommapsapp.android.FileUtil;
import com.custommapsapp.android.ImageDiskCache;
import com.custommapsapp.android.ImageHelper;
import com.custommapsapp.android.MapDisplay.MapImageTooLargeException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * IconStyle stores an icon and its hotspot for placemarks. Icon palettes are
 * not supported (icon palette is a single image that contains multiple icons).
 *
 * @author Marko Teittinen
 */
public class IconStyle extends KmlData {
  private static final long serialVersionUID = 1L;

  public static enum Units {
    FRACTION,
    PIXELS,
    INSET_PIXELS;

    public static Units parseUnits(String text) {
      if (text.equals("fraction")) {
        return FRACTION;
      }
      if (text.equals("pixels")) {
        return PIXELS;
      }
      if (text.equals("insetPixels")) {
        return INSET_PIXELS;
      }
      return null;
    }
  }

  private float scale = 1.5f;
  private String iconPath;
  private float x = 0.5f;
  private float y = 0.5f;
  private Units xUnits = Units.FRACTION;
  private Units yUnits = Units.FRACTION;

  // Transient fields for icon and its alignment on screen
  private transient Bitmap icon;
  private transient Integer iconWidth;
  private transient Integer iconHeight;
  private transient PointF iconOffset;
  private transient boolean iconLoaded = false;

  public float getScale() {
    return scale;
  }
  public void setScale(float scale) {
    this.scale = scale;
  }

  public String getIconPath() {
    return iconPath;
  }
  public void setIconPath(String iconPath) {
    if (iconPath != null) {
      iconPath = iconPath.trim();
    }
    this.iconPath = iconPath;
    // Reset transient icon info fields
    icon = null;
    iconWidth = null;
    iconHeight = null;
    iconOffset = null;
  }

  public boolean isIconReady() {
    return iconLoaded;
  }
  public synchronized Bitmap getIcon() {
    if (icon == null) {
      try {
        icon = loadIcon();
      } finally {
        iconLoaded = true;
      }
    }
    return icon;
  }
  public void setIcon(Bitmap icon) {
    this.icon = icon;
    // Initialize/reset icon info fields
    iconWidth = (icon != null ? icon.getWidth() : null);
    iconHeight = (icon != null ? icon.getHeight() : null);
    iconOffset = null;
  }

  public float getX() {
    return x;
  }
  public void setX(float x) {
    this.x = x;
    iconOffset = null;
  }

  public float getY() {
    return y;
  }
  public void setY(float y) {
    this.y = y;
    iconOffset = null;
  }

  public Units getXUnits() {
    return xUnits;
  }
  public void setXUnits(Units xUnits) {
    this.xUnits = xUnits;
    iconOffset = null;
  }

  public Units getYUnits() {
    return yUnits;
  }
  public void setYUnits(Units yUnits) {
    this.yUnits = yUnits;
    iconOffset = null;
  }

  /**
   * Returns non-scaled pixel offset for placing the bitmap. Coordinate values
   * grow right and down.
   *
   * @return non-scaled pixel offset from map location where upper left corner
   *         of the icon should be drawn
   */
  public synchronized PointF getIconOffset() {
    if (iconOffset == null) {
      computeIconOffset();
      // If icon size is still unknown, return (0, 0)
      if (iconOffset == null) {
        return new PointF(0, 0);
      }
    }
    // Return a copy to prevent external modifications to computed offset
    return new PointF(iconOffset.x, iconOffset.y);
  }

  private void computeIconOffset() {
    if (iconWidth == null || iconHeight == null) {
      if (icon == null) {
        readIconSize();
      } else {
        iconWidth = icon.getWidth();
        iconHeight = icon.getHeight();
      }

      if (iconWidth == null || iconHeight == null) {
        // Something failed, can't do math
        return;
      }
    }

    iconOffset = new PointF();
    switch (xUnits) {
      case PIXELS:
        iconOffset.x = -x;
        break;
      case INSET_PIXELS:
        iconOffset.x = x - iconWidth;
        break;
      case FRACTION:
        iconOffset.x = -x * iconWidth;
        break;
    }
    switch (yUnits) {
      case PIXELS:
        iconOffset.y = y - iconHeight;
        break;
      case INSET_PIXELS:
        iconOffset.y = iconHeight - y;
        break;
      case FRACTION:
        iconOffset.y = iconHeight * (y - 1);
        break;
    }
  }

  private void readIconSize() {
    if (iconPath == null) {
      return;
    }

    InputStream in = null;
    try {
      in = openIconFile(iconPath);
      BitmapFactory.Options options = ImageHelper.decodeImageBounds(in);
      iconWidth = options.outWidth;
      iconHeight = options.outHeight;
    } catch (Exception ex) {
      Log.e(CustomMaps.LOG_TAG, "Failed to read icon size: " + iconPath, ex);
    } finally {
      FileUtil.tryToClose(in);
    }
  }

  private Bitmap loadIcon() {
    if (iconPath == null) {
      return null;
    }

    InputStream in = null;
    try {
      in = openIconFile(iconPath);
      return ImageHelper.loadImage(in, false);
    } catch (IOException ex) {
      Log.e(CustomMaps.LOG_TAG, "Failed to load icon: " + iconPath, ex);
      return null;
    } catch (MapImageTooLargeException ex) {
      Log.e(CustomMaps.LOG_TAG, "Icon too large: " + iconPath, ex);
      return null;
    } finally {
      FileUtil.tryToClose(in);
    }
  }

  private InputStream openIconFile(String iconPath) throws IOException {
    if (!iconPath.startsWith("http://")) {
      // Local file, KmlInfo knows to open
      return getKmlInfo().getImageStream(iconPath);
    }
    // Remote file, get from cache
    ImageDiskCache cache = ImageDiskCache.instance(null);
    URL imageUrl = new URL(iconPath);
    File cachedFile = cache.getImage(imageUrl);
    return new FileInputStream(cachedFile);
  }
}
