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
package com.custommapsapp.android.create;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.VisibleRegion;

import com.custommapsapp.android.CustomMaps;
import com.custommapsapp.android.CustomMapsApp;
import com.custommapsapp.android.DMatrix;
import com.custommapsapp.android.HelpDialogManager;
import com.custommapsapp.android.ImageHelper;
import com.custommapsapp.android.MouseWheelZoom;
import com.custommapsapp.android.R;
import com.custommapsapp.android.language.Linguist;

/**
 * PreviewMapActivity shows the created map aligned on top of Google Map.
 *
 * @author Marko Teittinen
 */
public class PreviewMapActivity extends AppCompatActivity implements OnMapReadyCallback {
  private static final String EXTRA_PREFIX = "com.custommapsapp.android";
  public static final String BITMAP_FILE = EXTRA_PREFIX + ".BitmapFile";
  /** Array of ints providing image point coordinates in repeating (x, y) order */
  public static final String IMAGE_POINTS = EXTRA_PREFIX + ".ImagePoints";
  /** Array of LatLngs providing geo coordinates for image points pairs */
  public static final String TIEPOINTS = EXTRA_PREFIX + ".Tiepoints";
  /**
   * Array of LatLngs providing geo coordinates for image corners, starting from lower left going
   * counter-clockwise.
   */
  public static final String CORNER_GEO_POINTS = EXTRA_PREFIX + ".CornerGeoPoints";

  private GoogleMap googleMap;
  private SeekBar transparencyBar;
  private MapImageOverlay mapImageOverlay;
  private MouseWheelZoom mouseWheelZoom;

  private DMatrix imageToGeo;
  private LatLng mapImageCenter;
  private ArrayList<LatLng> imageCornerGeoPoints;
  private double latSpan;
  private double lonSpan;

  private HelpDialogManager helpDialogManager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.createpreview);
    Linguist linguist = ((CustomMapsApp) getApplication()).getLinguist();
    linguist.translateView(findViewById(R.id.root_view));

    SupportMapFragment mapFragment = (SupportMapFragment)
        getSupportFragmentManager().findFragmentById(R.id.googleMap);
    mapFragment.getMapAsync(this);

    ImageHelper.initializePreferredBitmapConfig(this);

    prepareUI();
    setSupportActionBar(findViewById(R.id.toolbar));

    // Update actionbar title to match current locale
    getSupportActionBar().setTitle(linguist.getString(R.string.create_map_name));

    // Create overlay
    String fileName = getIntent().getStringExtra(BITMAP_FILE);
    Bitmap mapImage = ImageHelper.loadImage(fileName, true);
    if (mapImage == null) {
      // Failed to load image, cancel activity
      Toast.makeText(this, linguist.getString(R.string.editor_image_load_failed), Toast.LENGTH_LONG)
          .show();
      setResult(RESULT_CANCELED);
      finish();
      return;
    }
    mapImageOverlay.setMapImage(mapImage);

    List<Point> imagePoints = new ArrayList<>();
    int[] imagePointArray = getIntent().getIntArrayExtra(IMAGE_POINTS);
    for (int i = 0; i + 1 < imagePointArray.length; i += 2) {
      imagePoints.add(new Point(imagePointArray[i], imagePointArray[i + 1]));
    }
    List<LatLng> geoPoints = new ArrayList<>();
    Parcelable[] parcelables = getIntent().getParcelableArrayExtra(TIEPOINTS);
    LatLng[] geoPointArray = Arrays.copyOf(parcelables, parcelables.length, LatLng[].class);
    Collections.addAll(geoPoints, geoPointArray);
    mapImageOverlay.setTiepoints(imagePoints, geoPoints);

    // Initialize map center location to center of geo points
    double latSum = 0;
    double lonSum = 0;
    for (LatLng geoPoint : geoPoints) {
      latSum += geoPoint.latitude;
      lonSum += geoPoint.longitude;
    }
    mapImageCenter = new LatLng(latSum / geoPoints.size(), lonSum / geoPoints.size());

    transparencyBar.setProgress(50);
    mapImageOverlay.setTransparency(50);

    helpDialogManager = new HelpDialogManager(this, HelpDialogManager.HELP_PREVIEW_CREATE,
        linguist.getString(R.string.preview_help));
  }

  @Override
  protected void onResume() {
    super.onResume();
    centerGoogleMapOnMapImageLocation();
    helpDialogManager.onResume();
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    if (mapImageOverlay != null) {
      mapImageOverlay.setTransparency(transparencyBar.getProgress());
      helpDialogManager.onRestoreInstanceState(savedInstanceState);
    }
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    if (helpDialogManager != null) {
      helpDialogManager.onSaveInstanceState(outState);
    }
    super.onSaveInstanceState(outState);
  }

  public void computeAndReturnTiepoints() {
    mapImageOverlay.computeImageWarp(googleMap.getProjection());
    if (!mapImageOverlay.hasGoogleMap()) {
      mapImageOverlay.setGoogleMap(googleMap);
    }
    imageToGeo = mapImageOverlay.computeImageToGeoMatrix();
    computeImageCornerGeoPoints();

    Intent result = getIntent();
    result.putExtra(CORNER_GEO_POINTS, imageCornerGeoPoints);
    setResult(RESULT_OK, result);
    finish();
  }

  @Override
  protected void onPause() {
    if (helpDialogManager != null) {
      helpDialogManager.onPause();
    }
    if (isFinishing() && mapImageOverlay != null) {
      Bitmap image = mapImageOverlay.getMapImage();
      if (image != null && !image.isRecycled()) {
        image.recycle();
        mapImageOverlay.setMapImage(null);
      }
    }
    super.onPause();
  }

  // --------------------------------------------------------------------------
  // Options menu

  private static final int MENU_MAP_MODE = 1;

  @Override
  @SuppressWarnings("deprecation")
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    Linguist linguist = ((CustomMapsApp) getApplication()).getLinguist();
    Drawable mapModeIcon = getResources().getDrawable(R.drawable.mapmode);
    mapModeIcon.setColorFilter(0xffffffff, PorterDuff.Mode.SRC_ATOP);
    MenuItem item =
        menu.add(Menu.NONE, MENU_MAP_MODE, Menu.NONE, linguist.getString(R.string.map_mode))
            .setIcon(mapModeIcon);
    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    helpDialogManager.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    if (item.getItemId() == MENU_MAP_MODE) {
      changeMapMode();
      return true;
    }
    helpDialogManager.onOptionsItemSelected(item);
    return true;
  }

  private void changeMapMode() {
    if (googleMap == null) {
      return;
    }
    // Rotate map type between roadmap, satellite (hybrid), and terrain
    googleMap.setMapType(getNextMapType());
  }

  private int getNextMapType() {
    switch (googleMap.getMapType()) {
      case GoogleMap.MAP_TYPE_NORMAL:
        return GoogleMap.MAP_TYPE_HYBRID;
      case GoogleMap.MAP_TYPE_HYBRID:
        return GoogleMap.MAP_TYPE_TERRAIN;
      case GoogleMap.MAP_TYPE_TERRAIN:
      default:
        return GoogleMap.MAP_TYPE_NORMAL;
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  protected Dialog onCreateDialog(int id) {
    return helpDialogManager.onCreateDialog(id);
  }

  @Override
  @SuppressWarnings("deprecation")
  protected void onPrepareDialog(int id, Dialog dialog) {
    helpDialogManager.onPrepareDialog(id, dialog);
  }

  // --------------------------------------------------------------------------
  // Image overlay helper methods

  private void computeImageCornerGeoPoints() {
    Bitmap mapImage = mapImageOverlay.getMapImage();
    // List corners starting from lower left going counter clockwise
    List<Point> corners = new ArrayList<>();
    corners.add(new Point(0, mapImage.getHeight()));
    corners.add(new Point(mapImage.getWidth(), mapImage.getHeight()));
    corners.add(new Point(mapImage.getWidth(), 0));
    corners.add(new Point(0, 0));
    // Convert to geo points
    imageCornerGeoPoints = new ArrayList<>();
    for (Point pt : corners) {
      LatLng geoPoint = imageToGeoPoint(imageToGeo, pt);
      imageCornerGeoPoints.add(geoPoint);
    }
  }

  private LatLng imageToGeoPoint(DMatrix converter, Point imagePoint) {
    double[] coords = new double[]{imagePoint.x, imagePoint.y};
    converter.mapPoints(coords);
    return new LatLng(coords[1], coords[0]);
  }

  private void initializeMatrixes() {
    // Compute geo location of map image center
    Projection mapProjection = googleMap.getProjection();
    if (!mapImageOverlay.computeImageWarp(mapProjection)) {
      // This should never happen!
      // Map image overlay failed to compute mapping from image points to geo points
      // Initialize map image info to match current view on map
      CameraPosition mapCamera = googleMap.getCameraPosition();
      mapImageCenter = mapCamera.target;
      VisibleRegion mapArea = mapProjection.getVisibleRegion();
      latSpan = mapArea.farLeft.latitude - mapArea.nearLeft.latitude;
      lonSpan = mapArea.nearRight.longitude - mapArea.nearLeft.longitude;
      return;
    }
    // Get matrix for converting image points to geo points
    imageToGeo = mapImageOverlay.computeImageToGeoMatrix();
    // Convert image center point to geo coordinates
    Bitmap mapImage = mapImageOverlay.getMapImage();
    Point center = new Point(mapImage.getWidth() / 2, mapImage.getHeight() / 2);
    mapImageCenter = imageToGeoPoint(imageToGeo, center);
    // Find lat and lon spans
    computeImageCornerGeoPoints();
    double minLat = 90;
    double maxLat = -90;
    double minLon = 180;
    double maxLon = -180;
    for (LatLng geoPoint : imageCornerGeoPoints) {
      minLat = Math.min(minLat, geoPoint.latitude);
      maxLat = Math.max(maxLat, geoPoint.latitude);
      minLon = Math.min(minLon, geoPoint.longitude);
      maxLon = Math.max(maxLon, geoPoint.longitude);
    }
    latSpan = maxLat - minLat;
    lonSpan = maxLon - minLon;
  }

  private void centerGoogleMapOnMapImageLocation() {
    if (googleMap != null) {
      // Keep latitude range values between [-85, 85] (away from the poles)
      double minBoundsLat = Math.min(Math.max(-85, mapImageCenter.latitude - latSpan / 2.0), 85);
      double maxBoundsLat = Math.max(-85, Math.min(mapImageCenter.latitude + latSpan / 2.0, 85));
      if (minBoundsLat == maxBoundsLat) {
        minBoundsLat -= 0.1;
      }
      // Keep longitude values between [-180, 180) (prevent over/underflow)
      double minBoundsLng = mapImageCenter.longitude - lonSpan / 2.0;
      if (minBoundsLng < -180.0) {
        minBoundsLng += 360;
      }
      double maxBoundsLng = mapImageCenter.longitude + lonSpan / 2.0;
      if (maxBoundsLng >= 180.0) {
        maxBoundsLng -= 360;
      }
      LatLngBounds mapImageBounds = new LatLngBounds(
          new LatLng(minBoundsLat, minBoundsLng), new LatLng(maxBoundsLat, maxBoundsLng));
      CameraUpdate fullView = CameraUpdateFactory.newLatLngBounds(
          mapImageBounds, getResources().getDimensionPixelSize(R.dimen.quarter_inch));
      googleMap.moveCamera(fullView);
    }
  }

  // --------------------------------------------------------------------------
  // Prepare UI elements

  private void prepareUI() {
    mapImageOverlay = findViewById(R.id.mapImageOverlay);
    if (googleMap != null) {
      mouseWheelZoom = new MouseWheelZoom(mapImageOverlay, googleMap);
    }
    transparencyBar = findViewById(R.id.transparencyBar);

    Button saveButton = findViewById(R.id.save);
    saveButton.setOnClickListener(v -> computeAndReturnTiepoints());

    transparencyBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      private void updateImageOverlay(int value) {
        mapImageOverlay.setTransparency(value);
        mapImageOverlay.invalidate();
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
        updateImageOverlay(seekBar.getProgress());
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
        updateImageOverlay(seekBar.getProgress());
      }

      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
          updateImageOverlay(seekBar.getProgress());
        }
      }
    });
  }

  // --------------------------------------------------------------------------
  // OnMapReadyCallback implementation

  @Override
  public void onMapReady(GoogleMap googleMap) {
    this.googleMap = googleMap;
    if (mapImageOverlay != null) {
      mouseWheelZoom = new MouseWheelZoom(mapImageOverlay, googleMap);
    }
    UiSettings uiSettings = googleMap.getUiSettings();
    uiSettings.setRotateGesturesEnabled(false);
    uiSettings.setTiltGesturesEnabled(false);
    uiSettings.setIndoorLevelPickerEnabled(false);
    uiSettings.setMyLocationButtonEnabled(true);
    uiSettings.setZoomControlsEnabled(true);
    uiSettings.setZoomGesturesEnabled(true);
    uiSettings.setMapToolbarEnabled(false);

    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mapImageCenter, 12));
    googleMap.setOnMapLoadedCallback(this::initializeOverlay);
  }

  private void initializeOverlay() {
    mapImageOverlay.setGoogleMap(googleMap);
    initializeMatrixes();
    centerGoogleMapOnMapImageLocation();
  }
}
