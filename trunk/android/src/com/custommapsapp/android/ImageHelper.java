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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.os.Build;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * ImageOrientationDetector tries to determine the orientation of a JPEG image
 * based on the EXIF data stored in the image. These methods are not available
 * in Android versions before 2.0 (SDK 5). In earlier Android versions this
 * class always reports that the images do not require rotation.
 *
 * @author Marko Teittinen
 */
public class ImageHelper {
  @SuppressWarnings("rawtypes")
  private static Class EXIF_CLASS;
  @SuppressWarnings("rawtypes")
  private static Constructor EXIF_CONSTRUCTOR;

  private static int EXIF_ORIENTATION_UNDEFINED;
  private static int EXIF_ORIENTATION_NORMAL;
  private static int EXIF_ORIENTATION_ROTATE_90;
  private static int EXIF_ORIENTATION_ROTATE_180;
  private static int EXIF_ORIENTATION_ROTATE_270;
  private static Method EXIF_GET_ATTRIBUTE_INT;
  private static Method EXIF_SET_ATTRIBUTE;
  private static Method EXIF_SAVE_ATTRIBUTES;

  static {
    try {
      EXIF_CLASS = ClassLoader.getSystemClassLoader().loadClass("android.media.ExifInterface");
      EXIF_ORIENTATION_UNDEFINED = EXIF_CLASS.getField("ORIENTATION_UNDEFINED").getInt(null);
      EXIF_ORIENTATION_NORMAL = EXIF_CLASS.getField("ORIENTATION_NORMAL").getInt(null);
      EXIF_ORIENTATION_ROTATE_90 = EXIF_CLASS.getField("ORIENTATION_ROTATE_90").getInt(null);
      EXIF_ORIENTATION_ROTATE_180 = EXIF_CLASS.getField("ORIENTATION_ROTATE_180").getInt(null);
      EXIF_ORIENTATION_ROTATE_270 = EXIF_CLASS.getField("ORIENTATION_ROTATE_270").getInt(null);
      EXIF_CONSTRUCTOR = EXIF_CLASS.getConstructor(String.class);
      EXIF_GET_ATTRIBUTE_INT = EXIF_CLASS.getMethod("getAttributeInt", String.class, int.class);
      EXIF_SET_ATTRIBUTE = EXIF_CLASS.getMethod("setAttribute", String.class, String.class);
      EXIF_SAVE_ATTRIBUTES = EXIF_CLASS.getMethod("saveAttributes");
    } catch (Exception ex) {
      EXIF_CLASS = null;
      if (Build.VERSION.SDK_INT >= 5) {
        Log.e(CustomMaps.LOG_TAG, "Failed to initialize ImageHelper. SDK: " + Build.VERSION.SDK_INT,
              ex);
      }
    }
  }

  /**
   * @return number of degrees the image needs to be rotated clockwise to be
   *         displayed the right way up. 0 means no rotation is necessary, 90
   *         indicates image needs to be rotated 90 degrees clockwise to display
   *         correctly, and so on.
   */
  public static int readOrientation(String imageName) {
    // ExifInterface is not available until SDK 5
    if (Build.VERSION.SDK_INT < 5 || EXIF_CLASS == null || !isJpegName(imageName)) {
      return 0;
    }

    // Use reflection to access class ExifInterface from SDK 5 an above
    try {
      Object exifInstance = EXIF_CONSTRUCTOR.newInstance(imageName);
      int orientation = getExifOrientation(exifInstance);
      if (orientation == EXIF_ORIENTATION_NORMAL || orientation == EXIF_ORIENTATION_UNDEFINED) {
        return 0;
      } else if (orientation == EXIF_ORIENTATION_ROTATE_90) {
        return 90;
      } else if (orientation == EXIF_ORIENTATION_ROTATE_180) {
        return 180;
      } else if (orientation == EXIF_ORIENTATION_ROTATE_270) {
        return 270;
      }
      // Unexpected values fall through past exception handling
      Log.w(CustomMaps.LOG_TAG, "Unexpected image orientation: " + orientation);
    } catch (Exception ex) {
      Log.e(CustomMaps.LOG_TAG, "Failed to determine image orientation. SDK: " +
            Build.VERSION.SDK_INT, ex);
    }
    // Return "not rotated" image in all error cases
    return 0;
  }

  /**
   * Decodes only image size for InputStream.
   *
   * @param in InputStream containing the bitmap
   * @return BitmapFactory.Options with size fields populated.
   */
  public static BitmapFactory.Options decodeImageBounds(InputStream in) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inScaled = false;
    options.inTargetDensity = 0;
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeStream(in, null, options);
    return options;
  }

  /**
   * Load a bitmap from a file.
   *
   * @param filename
   * @return Bitmap from named file, or 'null' in case of errors
   */
  public static Bitmap loadImage(String filename) {
    InputStream in = null;
    try {
      in = new BufferedInputStream(new FileInputStream(filename));
      return loadImage(in);
    } catch (Exception ex) {
      Log.e(CustomMaps.LOG_TAG, "Failed to load image: " + filename, ex);
      return null;
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException ex) {
          // Ignore
        }
      }
    }
  }

  /**
   * Load a bitmap from InputStream and catch OutOfMemoryErrors.
   *
   * @param in InputStream containing the bitmap
   * @return Bitmap from InputStream, or 'null' in case of errors
   */
  public static Bitmap loadImage(InputStream in) {
    System.gc();
    if (in == null) {
      return null;
    }
    try {
      BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
      bitmapOptions.inScaled = false;
      bitmapOptions.inTargetDensity = 0;
      bitmapOptions.inPurgeable = true;
      bitmapOptions.inInputShareable = true;
      bitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565;
      return BitmapFactory.decodeStream(in, null, bitmapOptions);
    } catch (OutOfMemoryError err) {
      Log.w(CustomMaps.LOG_TAG, "Out of memory loading map image", err);
      System.gc();
    }
    return null;
  }

  /**
   * Copies a small region of pixels around a point into a separate bitmap and
   * returns it PNG compressed. The PNG image will be rotated around the point
   * if requested.
   *
   * @param image Bitmap from which to take the sample
   * @param p Center point of the sample
   * @param size Width and height of the sample
   * @param rotation Degrees to rotate the original image around center point
   * @return byte[] containing PNG compressed sample at the point.
   */
  public static byte[] createPngSample(Bitmap image, Point p, int size, int rotation) {
    int halfEdge = size / 2;
    int x = Math.max(0, p.x - halfEdge);
    int y = Math.max(0, p.y - halfEdge);
    int width = Math.min(p.x + halfEdge, image.getWidth()) - x;
    int height = Math.min(p.y + halfEdge, image.getHeight()) - y;
    Matrix rotate = new Matrix();
    rotate.postRotate(rotation, p.x, p.y);
    Bitmap sample = Bitmap.createBitmap(image, x, y, width, height, rotate, false);
    // Compress the sample into a byte[] of PNG data
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
    boolean success = sample.compress(Bitmap.CompressFormat.PNG, 100, buffer);
    sample.recycle();
    return (success ? buffer.toByteArray() : null);
  }

  /**
   * Writes EXIF orientation information to a JPEG image file.
   *
   * @param imageName path to JPEG file to be modified
   * @param degrees number of degrees image needs to be rotated to be viewed.
   *        This value should be 0, 90, 180, or 270.
   * @return {@code true} if the orientation was successfully save to file
   */
  public static boolean writeOrientation(String imageName, int degrees) {
    // ExifInterface is not available until SDK 5
    if (Build.VERSION.SDK_INT < 5 || EXIF_CLASS == null || !isJpegName(imageName)) {
      return false;
    }
    int orientationValue = 1;
    switch (degrees) {
      default:
      case 0:
        return true;
      case 90:
        orientationValue = EXIF_ORIENTATION_ROTATE_90;
        break;
      case 180:
        orientationValue = EXIF_ORIENTATION_ROTATE_180;
        break;
      case 270:
        orientationValue = EXIF_ORIENTATION_ROTATE_270;
        break;
    }
    try {
      Object exifInstance = EXIF_CONSTRUCTOR.newInstance(imageName);
      writeExifOrientation(exifInstance, orientationValue);
      return true;
    } catch (Exception ex) {
      Log.e(CustomMaps.LOG_TAG, "Failed to store image orientation to file", ex);
    }
    return false;
  }

  /**
   * @param filename
   * @return {@code true} if the filename is a JPEG file name (ends with .jpg or
   *         .jpeg, case insensitive)
   */
  private static boolean isJpegName(String filename) {
    if (filename == null) {
      return false;
    }
    String lowerCase = filename.toLowerCase();
    return (lowerCase.endsWith(".jpg") || lowerCase.endsWith(".jpeg"));
  }

  private static int getExifOrientation(Object exifInstance) throws IllegalArgumentException,
      IllegalAccessException, InvocationTargetException {
    return (Integer) EXIF_GET_ATTRIBUTE_INT.invoke(exifInstance, "Orientation", 0);
  }

  private static void writeExifOrientation(Object exifInstance, int orientation)
      throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
    EXIF_SET_ATTRIBUTE.invoke(exifInstance, "Orientation", String.valueOf(orientation));
    EXIF_SAVE_ATTRIBUTES.invoke(exifInstance);
  }
}
