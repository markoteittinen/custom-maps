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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import com.custommapsapp.android.kml.KmlFolder;


/**
 * FileUtil provides some file location and I/O related utility methods. It should be initialized
 * with the application's context as early as possible in the app, so that it knows where the
 * internal files are stored.
 *
 * @see #init(Context) initialization method
 *
 * @author Marko Teittinen
 */
public class FileUtil {
  // These paths are relative to internal app storage data area
  private static final String DATA_DIR = "CustomMaps";
  private static final String IMAGE_DIR = "images";

  private static Context appContext = null;

  private static final String TMP_IMAGE_NAME = "mapimage.jpg";
  public static final String KMZ_IMAGE_DIR = "images/";

  /** This method should be called early in the app's */
  public static void init(Context context) {
    if (appContext == null) {
      appContext = context.getApplicationContext();
    }
  }

  /**
   * Returns internal directory to be used for storing map kmz files. NOTE: "FileUtil.init(context)"
   * must have been called before using this method to initialize the application context specific
   * directory reference.
   */
  public static File getInternalMapDirectory() {
    File mapDir = new File(appContext.getFilesDir(), DATA_DIR);
    verifyDir(mapDir);
    return mapDir;
  }

  /** Returns a named cache directory under app's cache root. */
  public static File getCacheDirectory(String name) {
    File cacheRoot = appContext.getCacheDir();
    File cacheDir = new File(cacheRoot, name != null ? name : "misc");
    verifyDir(cacheDir);
    return cacheDir;
  }

  /**
   * Returns the public directory in (emulated) SD card storage where the maps were held until
   * version 1.7.1. Version 1.8 and later will migrate the maps into internal memory at launch.
   */
  @Deprecated
  public static File getLegacyMapDirectory() {
    File sdRoot = Environment.getExternalStorageDirectory();
    return new File(sdRoot, DATA_DIR);
  }

  private static File getImageCacheDirectory() {
    File imageDir = getCacheDirectory(IMAGE_DIR);
    verifyDir(imageDir);
    return imageDir;
  }

  public static File getTmpImageFile() {
    return new File(getImageCacheDirectory(), TMP_IMAGE_NAME);
  }

  public static String getTmpImagePath() {
    return getTmpImageFile().getPath();
  }

  /**
   * Helper method to initialize directory. If a given directory does not exist,
   * the directory is created.
   *
   * @param dir File object pointing to directory that needs to exist.
   */
  private static void verifyDir(File dir) {
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        Log.w(CustomMaps.LOG_TAG, "Failed to create dir: " + dir.getAbsolutePath());
      }
    }
  }

  /** Returns true if given 'file' is in this app's data directory. */
  @Deprecated
  public static boolean isInDataDirectory(File file) {
    return isFileInDirectory(file, getInternalMapDirectory());
  }

  /**
   * @return {@code true} if 'file' is in 'dir' or one of its subdirs
   */
  private static boolean isFileInDirectory(File file, File dir) {
    String filePath = getBestPath(file);
    String dirPath = getBestPath(dir);
    return filePath.startsWith(dirPath);
  }

  /** Returns the canonical path for a file, or if that fails, absolute path for it. */
  public static String getBestPath(File file) {
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
   * Generates a new file reference to a non-existing file in the app's data
   * directory.
   *
   * @param nameFormat String.format to be used in generating the filename.
   *  Must contain a single '%d' field to indicate where number is inserted.
   * @return File reference to non-existing file (safe to create)
   */
  public static File newFileInDataDirectory(String nameFormat) {
    int i = 1;
    File dataDir = getInternalMapDirectory();
    File file = new File(dataDir, String.format(nameFormat, i));
    while (file.exists()) {
      file = new File(dataDir, String.format(nameFormat, ++i));
    }
    return file;
  }

  /**
   * Copies file to the data directory if it is not there already. Overwrites any existing file with
   * the same name.
   *
   * @param file File to be copied.
   * @return File pointing at the file in data directory, or 'null' in case of failure.
   */
  public static File copyToDataDirectory(File file) {
    long timestamp = file.lastModified();
    InputStream in = null;
    OutputStream out = null;
    File destination = null;
    File tmpDestination = null;
    try {
      destination = new File(getInternalMapDirectory(), file.getName());
      if (destination.exists()) {
        tmpDestination = newFileInDataDirectory("%d_" + file.getName());
      }
      in = new FileInputStream(file);
      out = new FileOutputStream(tmpDestination != null ? tmpDestination : destination);

      copyContents(in, out);

      return destination;
    } catch (IOException e) {
      Log.w(CustomMaps.LOG_TAG, "Failed to copy file to data directory: " + file, e);
    } finally {
      tryToClose(in);
      tryToClose(out);

      if (tmpDestination != null) {
        // Delete old file, and rename new to replace it
        destination.delete();
        tmpDestination.renameTo(destination);
      }
      // Copy timestamp from original
      destination.setLastModified(timestamp);
    }
    return null;
  }

  /**
   * Moves a file to data directory if it is not there already.
   *
   * @return File pointing at the file in data directory, or 'null' in case of
   *         failure
   */
  public static File moveToDataDirectory(File file) {
    try {
      file = file.getCanonicalFile();
      File destination = new File(getInternalMapDirectory(), file.getName());
      if (file.renameTo(destination)) {
        return destination;
      }
    } catch (IOException e) {
      Log.w(CustomMaps.LOG_TAG, "Failed to move file to data directory: " + file, e);
    }
    return null;
  }

  /**
   * Returns a File in catalog that matches given contentUri, or null if no match is found.
   *
   * @throws IllegalArgumentException if any exceptions occur during the process. The caller should
   * assume that the given Uri is invalid and cannot be opened.
   */
  public static File findMatchingCatalogFile(Context context, Uri contentUri)
      throws IllegalArgumentException {
    // If filename cannot be resolved, it is considered not added
    String fileName = resolveContentFileName(contentUri);
    if (fileName == null) {
      throw new IllegalArgumentException("New file name could not be determined");
    }
    // If no file with a matching name exists in catalog, the file is assumed not added
    File catalogFile = new File(getInternalMapDirectory(), fileName);
    if (!catalogFile.exists()) {
      return null;
    }
    // Now a file with a matching name exists in catalog. Compute and compare checksums.
    InputStream in = null;
    try {
      in = context.getContentResolver().openInputStream(contentUri);
      byte[] newFileChecksum = computeChecksum(in);
      in.close();
      in = null;
      in = new FileInputStream(catalogFile);
      byte[] catalogFileChecksum = computeChecksum(in);
      return Arrays.equals(newFileChecksum, catalogFileChecksum) ? catalogFile : null;
    } catch (Exception ex) {
      throw new IllegalArgumentException("Failed to compute a checksum", ex);
    } finally {
      tryToClose(in);
    }
  }

  private static byte[] computeChecksum(InputStream data) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("MD5");
    byte[] buffer = new byte[4196];
    int bytes;
    while ((bytes = data.read(buffer)) > 0) {
      digest.update(buffer, 0, bytes);
    }
    return digest.digest();
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
    String fileName = "unknown";
    try {
      fileName = resolveContentFileName(contentUri);
      File targetFile;
      if (fileName != null) {
        // Copy to internal directory with the same name, if it doesn't exist already
        targetFile = new File(getInternalMapDirectory(), fileName);
        if (targetFile.exists()) {
          // File with matching name exists, add number counter before extension
          String namePattern;
          int extensionDotIndex = fileName.lastIndexOf('.');
          if (extensionDotIndex > 0) {
            namePattern = fileName.substring(0, extensionDotIndex) + "-%03d"
                + fileName.substring(extensionDotIndex);
          } else {
            // No extension, unusual but let's allow
            namePattern = fileName + "-%03d";
          }
          targetFile = newFileInDataDirectory(namePattern);
        }
      } else {
        targetFile = newFileInDataDirectory("map-%03d.kmz");
        fileName = contentUri.toString();
      }
      in = context.getContentResolver().openInputStream(contentUri);
      out = new FileOutputStream(targetFile);

      copyContents(in, out);

      return targetFile;
    } catch (Exception ex) {
      // Failed to save file, log failure and return false
      Log.w(CustomMaps.LOG_TAG, "Failed to save KMZ content named: " + fileName, ex);
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
   * @throws IOException if any IO problems occur
   */
  public static void copyContents(InputStream from, OutputStream to) throws IOException {
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

  public static void exportMap(File kmzFile, DocumentFile destinationDir, ContentResolver resolver)
      throws IOException {
    String kmzMimeType = "application/vnd.google-earth.kmz";
    String fileName = kmzFile.getName();
    DocumentFile destinationFile = destinationDir.createFile(kmzMimeType, fileName);
    if (destinationFile == null) {
      throw new IOException("Failed to create a file in the destination folder");
    }
    Uri destinationUri = destinationFile.getUri();
    try (InputStream from = new FileInputStream(kmzFile);
        OutputStream to = resolver.openOutputStream(destinationUri)) {
      copyContents(from, to);
    }
  }

  public static String readTextFully(BufferedReader textSource) throws IOException {
    StringBuilder buf = new StringBuilder();
    String line;
    while ((line = textSource.readLine()) != null) {
      if (buf.length() > 0) {
        buf.append('\n');
      }
      buf.append(line);
    }
    return buf.toString();
  }

  /**
   * Attempts to close a closeable object, typically an input or output stream.
   * Returns 'true' if the closing was successful, and 'false' if it failed with
   * an exception (closing a null object will result in no-op success). If
   * an exception was thrown, it is logged, but never thrown for the caller.
   *
   * @param stream Closeable object (not necessarily a stream) to be closed
   * @return {@code true} if closing succeeded without errors.
   */
  public static boolean tryToClose(Closeable stream) {
    if (stream != null) {
      try {
        stream.close();
      } catch (IOException ex) {
        Log.w(CustomMaps.LOG_TAG, "Failed to close stream", ex);
        return false;
      }
    }
    return true;
  }

  /**
   * Sends a map using another application installed on the device (e.g. gmail).
   *
   * @param sender currently active Activity
   * @param map KmlFolder to be sent
   * @return {@code true} if the map was sent successfully
   */
  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public static boolean shareMap(Activity sender, KmlFolder map) {
    Intent sendMap = new Intent();
    sendMap.setAction(Intent.ACTION_SEND);
    sendMap.setType("application/vnd.google-earth.kmz");
    try {
      File mapFile = map.getKmlInfo().getFile();
      Uri mapUri =
          FileProvider.getUriForFile(sender, "com.custommapsapp.android.fileprovider", mapFile);
      sendMap.putExtra(Intent.EXTRA_SUBJECT, mapFile.getName());
      sendMap.putExtra(Intent.EXTRA_STREAM, mapUri);
      sendMap.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      sender.startActivity(sendMap);
      return true;
    } catch (Exception e) {
      String moreInfo;
      if (map == null) {
        moreInfo = "null map";
      } else if (map.getKmlInfo() == null) {
        moreInfo = "null KmlInfo for KmlFolder named " + map.getName();
      } else {
        moreInfo = map.getKmlInfo().getFile().getAbsolutePath();
      }
      Log.w(CustomMaps.LOG_TAG, String.format("Sharing of map failed (%s)", moreInfo), e);
      return false;
    }
  }

  /** Exports (shares) all maps in the given list. */
  public static void exportMaps(Activity sender, Iterable<KmlFolder> maps) {
    ArrayList<Uri> mapUris = new ArrayList<>();
    for (KmlFolder map : maps) {
      File mapFile = map.getKmlInfo().getFile();
      Uri mapUri =
          FileProvider.getUriForFile(sender, "com.custommapsapp.android.fileprovider", mapFile);
      mapUris.add(mapUri);
    }
    if (mapUris.isEmpty()) {
      return;
    }
    Intent exportMaps = new Intent(Intent.ACTION_SEND_MULTIPLE);
    exportMaps.putParcelableArrayListExtra(Intent.EXTRA_STREAM, mapUris);
    exportMaps.setType("application/vnd.google-earth.kmz");
    exportMaps.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    sender.startActivity(exportMaps);
  }

  /** Returns the name of the file in a content Uri, or null if it cannot be resolved */
  public static String resolveContentFileName(Uri contentUri) {
    ContentResolver resolver = appContext.getContentResolver();
    String[] columns = new String[] {OpenableColumns.DISPLAY_NAME};
    try (Cursor cursor = resolver.query(contentUri, columns, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getString(0);
      }
    } catch (UnsupportedOperationException ex) {
      // Thrown when URI points at a directory
      Log.w(CustomMaps.LOG_TAG, "Cannot resolve filename for Uri: " + contentUri);
    }
    return null;
  }
}
