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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * KmlFinder scans a directory and finds all kml and kmz files in the directory
 * hierarchy.
 *
 * @author Marko Teittinen
 */
public class KmlFinder {

  public static Iterable<KmlInfo> findKmlFiles(File dataDir) throws ZipException, IOException {
    List<KmlInfo> kmlFiles = new ArrayList<KmlInfo>();
    File[] files = dataDir.listFiles();
    for (File file : files) {
      if (file.getName().endsWith(".kml")) {
        kmlFiles.add(new KmlFile(file));
      } else if (file.getName().endsWith(".kmz")) {
        ZipFile kmzFile = new ZipFile(file);
        Enumeration<? extends ZipEntry> kmzContents = kmzFile.entries();
        while (kmzContents.hasMoreElements()) {
          ZipEntry kmzItem = kmzContents.nextElement();
          if (kmzItem.getName().endsWith(".kml")) {
            kmlFiles.add(new KmzFile(kmzFile, kmzItem));
          }
        }
      }
    }
    return kmlFiles;
  }
}
