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

import java.util.ArrayList;
import java.util.List;

/**
 * KmlFolder manages a set of GroundOverlays stored in kml and kmz files located
 * within a single source directory (folder).
 *
 * @author Marko Teittinen
 */
public class KmlFolder {
  private KmlInfo kmlInfo;
  private String name;
  private String description;
  private List<GroundOverlay> overlays = new ArrayList<GroundOverlay>();

  public KmlInfo getKmlInfo() {
    return kmlInfo;
  }
  public void setKmlInfo(KmlInfo kmlInfo) {
    this.kmlInfo = kmlInfo;
  }

  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  public void addOverlay(GroundOverlay overlay) {
    overlays.add(overlay);
  }
  public boolean hasOverlays() {
    return !overlays.isEmpty();
  }
  public Iterable<GroundOverlay> getOverlays() {
    return overlays;
  }
}
