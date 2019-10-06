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

import java.io.Serializable;

/**
 * KmlData is root class for all items Custom Maps can parse from KML.
 *
 * @author Marko Teittinen
 */
public class KmlData implements Serializable {
  private static final long serialVersionUID = 1L;

  protected KmlInfo kmlInfo;

  public KmlInfo getKmlInfo() {
    return kmlInfo;
  }
  public void setKmlInfo(KmlInfo kmlInfo) {
    this.kmlInfo = kmlInfo;
  }
}
