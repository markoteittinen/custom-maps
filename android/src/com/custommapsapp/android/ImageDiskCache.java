/*
 * Copyright 2012 Google Inc. All Rights Reserved.
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

import com.custommapsapp.android.storage.ImageCacheIndex;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * ImageDiskCache caches images from the internet to local storage so they
 * can be used offline.
 *
 * @author Marko Teittinen
 */
public class ImageDiskCache {
  private static ImageDiskCache instance;   // singleton

  public static synchronized ImageDiskCache instance(Context context) {
    if (instance == null) {
      instance = new ImageDiskCache(context);
    }
    return instance;
  }

  // --------------------------------------------------------------------------
  // Instance variables and methods

  private ImageCacheIndex index;

  private ImageDiskCache(Context context) {
    index = ImageCacheIndex.instance(context);
  }

  /**
   * Returns a File reference to a local file containing the same image as
   * 'imageUrl'. If the file is already cached, returns that reference
   * immediately, otherwise downloads the image from the internet before
   * returning the reference to the downloaded copy. If the download fails,
   * returns 'null'.
   *
   * @param imageUrl URL where the image is stored in the internet
   * @return File reference to the local file in cache directory, or 'null'
   *         if the file is not locally cached and the download failed.
   */
  public File getImage(URL imageUrl) {
    if (imageUrl == null) {
      return null;
    }

    // If file is already cached, return reference to it
    String key = toKey(imageUrl);
    String value = index.getValue(key);
    File cacheFile = toFile(value);
    if (cacheFile != null && cacheFile.exists()) {
      return cacheFile;
    }

    // Add entry to index if necessary
    if (value == null) {
      value = index.addKey(key);
      cacheFile = toFile(value);
    }

    // Download the image to cache directory
    InputStream in = null;
    OutputStream out = null;
    try {
      in = imageUrl.openStream();
      out = new FileOutputStream(cacheFile);
      FileUtil.copyContents(in, out);
      out.flush();
    } catch (Exception e) {
      // Download failed, log failure, and clear return value
      Log.e(CustomMaps.LOG_TAG, "Failed to download: " + imageUrl, e);
      cacheFile = null;
    } finally {
      FileUtil.tryToClose(out);
      FileUtil.tryToClose(in);
    }

    // If download failed, remove entry from index and disk
    if (cacheFile == null) {
      removeImage(imageUrl);
    }

    return cacheFile;
  }

  /**
   * Removes an image from local disk cache.
   *
   * @param imageUrl URL where the image is stored in the internet
   * @return 'true' if the image was actually removed, 'false' if it was not
   *         stored in cache
   */
  public boolean removeImage(URL imageUrl) {
    if (imageUrl == null) {
      return false;
    }
    String value = index.removeKey(toKey(imageUrl));
    File cachedFile = toFile(value);
    if (cachedFile != null && cachedFile.exists()) {
      cachedFile.delete();
    }
    return (value != null);
  }

  public int getCacheItemCount() {
    return 0;
  }

  public int getCacheDiskUsedMb() {
    return 0;
  }

  private String toKey(URL url) {
    return (url != null ? url.toString() : null);
  }

  private File toFile(String cacheValue) {
    return (cacheValue != null ? new File(FileUtil.getCacheDirectory(), cacheValue) : null);
  }
}
