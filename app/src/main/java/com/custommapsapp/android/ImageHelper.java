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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.util.Log;
import androidx.exifinterface.media.ExifInterface;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.custommapsapp.android.MapDisplay.MapImageTooLargeException;
import com.custommapsapp.android.storage.PreferenceStore;

/**
 * ImageHelper contains several image related static helper methods, like
 * EXIF orientation detection, reading images and their sizes from files, etc.
 *
 * @author Marko Teittinen
 */
public class ImageHelper {
  // Default Bitmap.Config to be used for all images
  private static Bitmap.Config preferredBitmapConfig = Bitmap.Config.RGB_565;

  /**
   * @return number of degrees the image needs to be rotated clockwise to be
   *     displayed the right way up. 0 means no rotation is necessary, 90
   *     indicates image needs to be rotated 90 degrees clockwise to display
   *     correctly, and so on.
   */
  public static int readOrientation(String imageName) {
    if (!isJpegName(imageName)) {
      // Assume all non-jpg images have normal orientation
      return 0;
    }

    // Get ExifInterface to requested image data
    ExifInterface imageExif;
    try {
      imageExif = new ExifInterface(imageName);
    } catch (IOException ex) {
      Log.w(CustomMaps.LOG_TAG, "Failed to get EXIF data from image: " + imageName, ex);
      // Assume normal orientation
      return 0;
    }

    int orientation = imageExif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
    switch (orientation) {
      case ExifInterface.ORIENTATION_UNDEFINED:
      case ExifInterface.ORIENTATION_NORMAL:
        return 0;
      case ExifInterface.ORIENTATION_ROTATE_90:
        return 90;
      case ExifInterface.ORIENTATION_ROTATE_180:
        return 180;
      case ExifInterface.ORIENTATION_ROTATE_270:
        return 270;
      default:
        Log.w(CustomMaps.LOG_TAG, "Unrecognized image orientation: " + orientation);
        // Return "not rotated"
        return 0;
    }
  }

  /**
   * Gets the preferred BitmapConfig from the shared preferences.
   *
   * @param context Current context
   */
  public static void initializePreferredBitmapConfig(Context context) {
    if (PreferenceStore.instance(context).isUseArgb_8888()) {
      preferredBitmapConfig = Bitmap.Config.ARGB_8888;
    } else {
      preferredBitmapConfig = Bitmap.Config.RGB_565;
    }
  }

  /**
   * Returns the preferred Bitmap.Config. This value defaults to RGB_565 unless
   * initializePreferredBitmapConfig() has been called. In that case this method returns the
   * selected Bitmap.Config for current device.
   *
   * @return Bitmap.Config that should be used for all images
   */
  public static Bitmap.Config getPreferredBitmapConfig() {
    return preferredBitmapConfig;
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
   * @param filename Name of the file containing image
   * @param ignoreDpi Flag indicating if image should be scaled to display density.
   * @return Bitmap from named file, or 'null' in case of errors
   */
  public static Bitmap loadImage(String filename, boolean ignoreDpi) {
    InputStream in = null;
    try {
      in = new BufferedInputStream(new FileInputStream(filename));
      return loadImage(in, ignoreDpi);
    } catch (Exception ex) {
      Log.e(CustomMaps.LOG_TAG, "Failed to load image: " + filename, ex);
      return null;
    } finally {
      FileUtil.tryToClose(in);
    }
  }

  /**
   * Load a bitmap from a resource with given id.
   *
   * @param context Context to read the resource from.
   * @param resourceId Resource ID to be read (must be png, gif, or jpg image).
   * @param ignoreDpi Flag selecting if image should be scaled to display density.
   * @return Bitmap from the resource, or 'null' in case of errors like invalid image format or
   *     if the image is too large to fit in memory.
   */
  public static Bitmap loadImage(Context context, int resourceId, boolean ignoreDpi) {
    InputStream in = null;
    try {
      in = context.getResources().openRawResource(resourceId);
      return loadImage(in, ignoreDpi);
    } catch (Exception ex) {
      Log.e(CustomMaps.LOG_TAG, "Failed to load image resource: " + resourceId, ex);
      return null;
    } finally {
      FileUtil.tryToClose(in);
    }
  }

  /**
   * Load a bitmap from InputStream and catch OutOfMemoryErrors.
   *
   * @param in InputStream containing the bitmap
   * @param ignoreDpi Flag selecting if image should be scaled to display density.
   * @return Bitmap from InputStream, or 'null' in case of errors like invalid image format.
   *
   * @throws MapImageTooLargeException if image is too large to be loaded.
   */
  @SuppressWarnings("deprecation")
  public static Bitmap loadImage(InputStream in, boolean ignoreDpi)
      throws MapImageTooLargeException {
    System.gc();
    if (in == null) {
      return null;
    }
    try {
      BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
      bitmapOptions.inScaled = false;
      if (ignoreDpi) {
        bitmapOptions.inTargetDensity = 0;
      }
      bitmapOptions.inPurgeable = true;
      bitmapOptions.inInputShareable = true;
      bitmapOptions.inPreferredConfig = getPreferredBitmapConfig();
      return BitmapFactory.decodeStream(in, null, bitmapOptions);
    } catch (OutOfMemoryError err) {
      Log.w(CustomMaps.LOG_TAG, "Out of memory loading map image", err);
      System.gc();
      throw new MapImageTooLargeException("Out of memory loading an image");
    }
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
    Bitmap sample = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    sample.eraseColor(0x00000000); // Transparent
    // Grab content from map image
    int halfEdge = size / 2;
    int x = Math.max(0, p.x - halfEdge);
    int y = Math.max(0, p.y - halfEdge);
    int width = Math.min(p.x + halfEdge, image.getWidth()) - x;
    int height = Math.min(p.y + halfEdge, image.getHeight()) - y;
    Matrix rotate = new Matrix();
    rotate.postRotate(rotation, p.x, p.y);
    Bitmap content = Bitmap.createBitmap(image, x, y, width, height, rotate, false);
    // Draw content in sample bitmap (snippets from edge areas don't cover fully)
    boolean shiftX = false;
    boolean shiftY = false;
    if (width < size || height < size) {
      // Content doesn't cover full sample area, check for need to shift from top-left corner
      switch (rotation) {
        case 0:
        default:
          // No rotation (x grows right, y grows down)
          shiftX = content.getWidth() < size && x == 0;
          shiftY = content.getHeight() < size && y == 0;
          break;
        case 90:
          // Left edge has been rotated to top (x grows down, y grows left)
          shiftX = content.getWidth() < size && y > size;
          shiftY = content.getHeight() < size && x == 0;
          break;
        case 180:
          // Image is upside down (x grows left, y grows up)
          shiftX = content.getWidth() < size && x > size;
          shiftY = content.getHeight() < size && y > size;
          break;
        case 270:
          // Left edge has been rotated to bottom (x grows up, y grows right)
          shiftX = content.getWidth() < size && y == 0;
          shiftY = content.getHeight() < size && x > size;
      }
    }
    int contentX = shiftX ? size - content.getWidth() : 0;
    int contentY = shiftY ? size - content.getHeight() : 0;
    new Canvas(sample).drawBitmap(content, contentX, contentY, null);
    // Compress the sample into a byte[] of PNG data
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(2048);
    boolean success = sample.compress(Bitmap.CompressFormat.PNG, 100, buffer);
    content.recycle();
    sample.recycle();
    return (success ? buffer.toByteArray() : null);
  }

  /**
   * Writes EXIF orientation information to a JPEG image file.
   *
   * @param imageName path to JPEG file to be modified
   * @param degrees number of degrees image needs to be rotated to be viewed.
   *     This value should be 0, 90, 180, or 270. Any other value is treated as 0.
   * @return {@code true} if the orientation was successfully save to file
   */
  public static boolean writeOrientation(String imageName, int degrees) {
    if (!isJpegName(imageName)) {
      return false;
    }
    int orientationValue;
    switch (degrees) {
      default:
      case 0:
        orientationValue = ExifInterface.ORIENTATION_NORMAL;
        break;
      case 90:
        orientationValue = ExifInterface.ORIENTATION_ROTATE_90;
        break;
      case 180:
        orientationValue = ExifInterface.ORIENTATION_ROTATE_180;
        break;
      case 270:
        orientationValue = ExifInterface.ORIENTATION_ROTATE_270;
        break;
    }
    try {
      ExifInterface imageExif = new ExifInterface(imageName);
      imageExif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(orientationValue));
      imageExif.saveAttributes();
      return true;
    } catch (IOException ex) {
      Log.w(CustomMaps.LOG_TAG, "Failed to store image orientation to file: " + imageName, ex);
    }
    return false;
  }

  /**
   * @param filename name to be checked whether it implies a JPEG file
   * @return {@code true} if the filename is a JPEG file name (ends with .jpg or
   *     .jpeg, case insensitive)
   */
  private static boolean isJpegName(String filename) {
    if (filename == null) {
      return false;
    }
    String lowerCase = filename.toLowerCase();
    return (lowerCase.endsWith(".jpg") || lowerCase.endsWith(".jpeg"));
  }
}
