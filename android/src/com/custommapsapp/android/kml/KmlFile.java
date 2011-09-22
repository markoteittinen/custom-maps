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

import com.custommapsapp.android.ImageHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Serializable;

/**
 * KmlFile provides methods to read kml files.
 *
 * @author Marko Teittinen
 */
public class KmlFile implements KmlInfo, Serializable {
  private static final long serialVersionUID = 1L;

  private File kmlFile;

  public KmlFile(File kmlFile) {
    this.kmlFile = kmlFile;
  }

  public File getFile() {
    return kmlFile;
  }

  public Reader getKmlReader() throws IOException {
    return new FileReader(kmlFile);
  }

  public InputStream getImageStream(String path) throws IOException {
    File imageFile = new File(kmlFile.getParentFile(), path);
    return new FileInputStream(imageFile);
  }

  public int getImageOrientation(String path) {
    String imageFilename = new File(kmlFile.getParentFile(), path).getAbsolutePath();
    return ImageHelper.readOrientation(imageFilename);
  }

  @Override
  public String toString() {
    return "KmlFile[path='" + kmlFile.getAbsolutePath() + "']";
  }
}
