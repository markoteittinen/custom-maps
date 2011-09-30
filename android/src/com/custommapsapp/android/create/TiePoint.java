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
package com.custommapsapp.android.create;

import com.google.android.maps.GeoPoint;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * TiePoint is a class holding an image point, its geo coordinates, and a small
 * image showing the surroundings around the image point.
 *
 * @author Marko Teittinen
 */
public class TiePoint implements Parcelable {
  private final Point imagePoint;
  private final Point offset;
  private byte[] pngData;
  private GeoPoint geoPoint;
  private transient Bitmap image;

  public TiePoint(Point imagePoint, byte[] pngData, Point offset) {
    this.imagePoint = imagePoint;
    this.pngData = pngData;
    this.offset = offset;
  }

  public Bitmap getImage() {
    if (image == null && pngData != null) {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = 2;
      image = BitmapFactory.decodeByteArray(pngData, 0, pngData.length, options);
    }
    return image;
  }

  public byte[] getPngData() {
    return pngData;
  }

  public Point getOffset() {
    return offset;
  }

  public Point getImagePoint() {
    return imagePoint;
  }

  public void setGeoPoint(GeoPoint location) {
    this.geoPoint = location;
  }

  public GeoPoint getGeoPoint() {
    return geoPoint;
  }

  /** Release resources used by the bitmap */
  public void releaseBitmap() {
    // Release memory used by the bitmap
    if (image != null && !image.isRecycled()) {
      image.recycle();
      pngData = null;
      image = null;
    }
  }

  @Override
  protected void finalize() throws Throwable {
    releaseBitmap();
    super.finalize();
  }

  // --------------------------------------------------------------------------
  // Parcelable implementation

  public static final Parcelable.Creator<TiePoint> CREATOR = new Parcelable.Creator<TiePoint>() {
    @Override
    public TiePoint createFromParcel(Parcel source) {
      return new TiePoint(source);
    }

    @Override
    public TiePoint[] newArray(int size) {
      return new TiePoint[size];
    }
  };

  private TiePoint(Parcel data) {
    imagePoint = readPoint(data);
    offset = readPoint(data);
    int pngDataSize = data.readInt();
    if (pngDataSize == 0) {
      pngData = null;
    } else {
      pngData = new byte[pngDataSize];
      data.readByteArray(pngData);
    }
    if (data.readByte() > 0) {
      geoPoint = new GeoPoint(data.readInt(), data.readInt());
    }
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    writePoint(dest, imagePoint);
    writePoint(dest, offset);
    if (pngData == null) {
      dest.writeInt(0);
    } else {
      dest.writeInt(pngData.length);
      dest.writeByteArray(pngData);
    }
    if (geoPoint == null) {
      dest.writeByte((byte) 0);
    } else {
      dest.writeByte((byte) 1);
      dest.writeInt(geoPoint.getLatitudeE6());
      dest.writeInt(geoPoint.getLongitudeE6());
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  private void writePoint(Parcel data, Point p) {
    if (p == null) {
      data.writeByte((byte) 0);
    } else {
      data.writeByte((byte) 1);
      data.writeInt(p.x);
      data.writeInt(p.y);
    }
  }

  private Point readPoint(Parcel data) {
    if (data.readByte() == 0) {
      return null;
    } else {
      return new Point(data.readInt(), data.readInt());
    }
  }
}
