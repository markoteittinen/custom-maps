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

import com.custommapsapp.android.FileUtil;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * KmzFile provides methods to read kml files directly from inside kmz files.
 *
 * @author Marko Teittinen
 */
public class KmzFile implements KmlInfo, Serializable {
  private static final long serialVersionUID = 1L;

  /** Properties file that can contain map image orientation values */
  public static final String MAP_ORIENTATION_PROPERTIES = "map_orientation.properties";

  private File file;
  private ZipFile kmzFile;
  private ZipEntry kmlEntry;

  public KmzFile(ZipFile kmzFile, ZipEntry kmlEntry) {
    this.file = new File(kmzFile.getName());
    this.kmzFile = kmzFile;
    this.kmlEntry = kmlEntry;
  }

  public File getFile() {
    return file;
  }

  public Reader getKmlReader() throws IOException {
    InputStream stream = kmzFile.getInputStream(kmlEntry);
    return new InputStreamReader(stream);
  }

  public long getImageDate(String path) throws IOException {
    ZipEntry zipEntry = kmzFile.getEntry(path);
    if (zipEntry == null) {
      throw new FileNotFoundException("Image not found in kmz file");
    }
    return zipEntry.getTime();
  }

  public InputStream getImageStream(String path) throws IOException {
    ZipEntry zipEntry = kmzFile.getEntry(path);
    if (zipEntry == null) {
      throw new FileNotFoundException("Image not found in kmz file");
    }
    return kmzFile.getInputStream(zipEntry);
  }

  public int getImageOrientation(String path) {
    // Read image orientation from kmz file if available
    ZipEntry entry = kmzFile.getEntry(MAP_ORIENTATION_PROPERTIES);
    if (entry == null) {
      // No properties file present, return no rotation
      return 0;
    }
    InputStream in = null;
    try {
      in = kmzFile.getInputStream(entry);
      Properties props = new Properties();
      props.load(in);
      String value = props.getProperty(path, "0");
      return Integer.parseInt(value);
    } catch (Exception ex) {
      Log.w("Custom Maps", "Failed to read map image orientation for: " + path, ex);
      // Failed to read file, or value is not a number, return no rotation
      return 0;
    } finally {
      FileUtil.tryToClose(in);
    }
  }

  @Override
  public String toString() {
    return "KmzFile[file='" + kmzFile.getName() + ", entry='" + kmlEntry.getName() + "']";
  }

  // --------------------------------------------------------------------------
  // Serialization

  private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeUTF(file.getAbsolutePath());
    out.writeUTF(kmzFile.getName());
    out.writeUTF(kmlEntry.getName());
  }

  private void readObject(ObjectInputStream in) throws IOException {
    file = new File(in.readUTF());
    kmzFile = new ZipFile(in.readUTF());
    kmlEntry = new ZipEntry(in.readUTF());
  }
}
