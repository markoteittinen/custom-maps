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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * FileUtil provides some file location and I/O related utility methods.
 *
 * @author Marko Teittinen
 */
public class FileUtil {
  public static final String SD_ROOT_PATH = "/sdcard";
  public static final String DATA_DIR = SD_ROOT_PATH + "/CustomMaps";
  public static final String IMAGE_DIR = DATA_DIR + "/images";
  public static final String TMP_IMAGE = IMAGE_DIR + "/mapimage.jpg";
  private static final String SD_PHOTOS = SD_ROOT_PATH + "/DCIM/Camera";
  private static final String SD_PHOTOS_2 = SD_ROOT_PATH + "/DCIM/100MEDIA";
  private static final String SD_DOWNLOADS = SD_ROOT_PATH + "/download";
  private static final String SD_DOWNLOADS_2 = SD_ROOT_PATH + "/downloads";

  public static final String KMZ_IMAGE_DIR = "images/";

  public static File getSdRoot() {
    return new File(SD_ROOT_PATH);
  }

  public static File getPhotosDirectory() {
    // G1, Nexus One, and Nexus S use this folder
    File photoDir = new File(SD_PHOTOS);
    if (photoDir.exists() && photoDir.isDirectory()) {
      return photoDir;
    }
    // At least Droid Eris uses this folder
    return new File(SD_PHOTOS_2);
  }

  public static File getDownloadsDirectory() {
    File downloadDir = new File(SD_DOWNLOADS);
    if (downloadDir.exists() && downloadDir.isDirectory()) {
      return downloadDir;
    }
    return new File(SD_DOWNLOADS_2);
  }

  public static File getDataDirectory() {
    return new File(DATA_DIR);
  }

  public static boolean verifyDataDir() {
    File dataDir = getDataDirectory();
    if (dataDir.exists()) {
      return true;
    }
    return dataDir.mkdirs();
  }

  public static File getImageDirectory() {
    File imageDir = new File(IMAGE_DIR);
    if (!imageDir.exists()) {
      imageDir.mkdirs();
    }
    return imageDir;
  }

  /**
   * @param file
   * @return {@code true} if 'file' is in browser's download directory
   */
  public static boolean isDownloadFile(File file) {
    return isFileInDirectory(file, getDownloadsDirectory());
  }

  /**
   * @param file
   * @return {@code true} if 'file' is in this app's data directory
   */
  public static boolean isInDataDirectory(File file) {
    return isFileInDirectory(file, getDataDirectory());
  }

  /**
   * @param file
   * @param dir
   * @return {@code true} if 'file' is in 'dir' or one of its subdirs
   */
  private static boolean isFileInDirectory(File file, File dir) {
    String filePath = getBestPath(file);
    String dirPath = getBestPath(dir);
    return filePath.startsWith(dirPath);
  }

  /**
   * @param file
   * @return Canonical path for file, or if that fails, absolute path for it
   */
  private static String getBestPath(File file) {
    if (file == null) {
      return null;
    }
    try {
      return file.getCanonicalPath();
    } catch (IOException ex) {
      Log.w(CustomMaps.LOG_TAG, "Failed to resolve canonical path for: " + file, ex);
    }
    return file.getAbsolutePath();
  }

  /**
   * Verifies that image directory exists for storing resized images.
   *
   * @return {@code true} if it existed or was created successfully
   */
  public static boolean verifyImageDir() {
    File imageDir = getImageDirectory();
    if (imageDir.exists()) {
      return true;
    }
    return imageDir.mkdirs();
  }

  /**
   * Generates a new file reference to a non-existing file in the app's data
   * directory.
   *
   * @param nameFormat String.format to be used in generating the filename.
   *  Must contain a single '%d' field to indicate where number is inserted.
   * @return File reference to non-existing file (safe to create)
   */
  public static File newFileInDataDirectory(String nameFormat) {
    int i = 1;
    File dataDir = getDataDirectory();
    File file = new File(dataDir, String.format(nameFormat, i));
    while (file.exists()) {
      file = new File(dataDir, String.format(nameFormat, ++i));
    }
    return file;
  }

  /**
   * Copies file to data directory if it is not there already.
   *
   * @param file
   * @return File pointing at the file in data directory, or 'null' in case of
   *         failure
   */
  public static File copyToDataDirectory(File file) {
    if (isInDataDirectory(file)) {
      return file;
    }
    InputStream in = null;
    OutputStream out = null;
    try {
      File destination = new File(getDataDirectory(), file.getName());
      in = new FileInputStream(file);
      out = new FileOutputStream(destination);

      copyContents(in, out);

      return destination;
    } catch (IOException e) {
      Log.w(CustomMaps.LOG_TAG, "Failed to copy file to data directory: " + file, e);
    } finally {
      tryToClose(in);
      tryToClose(out);
    }
    return null;
  }

  /**
   * Moves file to data directory if it is not there already.
   *
   * @param file
   * @return File pointing at the file in data directory, or 'null' in case of
   *         failure
   */
  public static File moveToDataDirectory(File file) {
    if (isInDataDirectory(file)) {
      return file;
    }
    try {
      file = file.getCanonicalFile();
      File destination = new File(getDataDirectory(), file.getName());
      if (file.renameTo(destination)) {
        return destination;
      }
    } catch (IOException e) {
      Log.w(CustomMaps.LOG_TAG, "Failed to move file to data directory: " + file, e);
    }
    return null;
  }

  /**
   * Saves the contents of the given content-scheme Uri to a file and returns
   * a File object pointing to the saved file (or {@code null} in case of
   * failure).
   *
   * @param contentUri Android Uri beginning with "content://"
   * @return {@code File} reference to saved file or {@code null} if failed
   */
  public static File saveKmzContentUri(Context context, Uri contentUri) {
    InputStream in = null;
    OutputStream out = null;
    try {
      in = context.getContentResolver().openInputStream(contentUri);
      File resultFile = newFileInDataDirectory("map-%03d.kmz");
      out = new FileOutputStream(resultFile);

      copyContents(in, out);

      return resultFile;
    } catch (Exception e) {
      // Failed to save file, log failure and return false
      Log.w(CustomMaps.LOG_TAG, "Failed to save KMZ Content from Uri: " + contentUri.toString(), e);
      return null;
    } finally {
      // Close streams
      tryToClose(in);
      tryToClose(out);
    }
  }

  /**
   * Copies all contents of 'from' to 'to'.
   *
   * @param from
   * @param to
   * @throws IOException
   */
  private static void copyContents(InputStream from, OutputStream to) throws IOException {
    if (!(from instanceof BufferedInputStream)) {
      from = new BufferedInputStream(from);
    }
    if (!(to instanceof BufferedOutputStream)) {
      to = new BufferedOutputStream(to);
    }

    byte[] buf = new byte[1024];
    int n;
    while ((n = from.read(buf)) != -1) {
      if (n > 0) {
        to.write(buf, 0, n);
      }
    }
    to.flush();
  }

  public static void tryToClose(Closeable stream) {
    if (stream == null) {
      return;
    }
    try {
      stream.close();
    } catch (IOException ex) {
      // Ignore
    }
  }

  /**
   * Sends a map using another application installed on the device (e.g. gmail).
   *
   * @param sender currently active Activity
   * @param map GroundOverlay to be sent
   * @return {@code true} if the map was sent successfully
   */
  public static boolean shareMap(Activity sender, GroundOverlay map) {
    Intent sendMap = new Intent();
    sendMap.setAction(Intent.ACTION_SEND);
    sendMap.setType("application/vnd.google-earth.kmz");
    sendMap.putExtra(Intent.EXTRA_SUBJECT, sender.getString(R.string.share_message_subject));
    File mapFile = map.getKmlInfo().getFile();
    sendMap.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(mapFile));
    try {
      sender.startActivity(Intent.createChooser(sendMap,
                                                sender.getString(R.string.share_chooser_title)));
      return true;
    } catch (Exception e) {
      Log.w(CustomMaps.LOG_TAG, "Sharing of map failed", e);
      return false;
    }
  }
}
