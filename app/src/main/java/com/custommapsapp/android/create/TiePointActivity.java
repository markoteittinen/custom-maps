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

import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;

import com.custommapsapp.android.CustomMapsApp;
import com.custommapsapp.android.HelpDialogManager;
import com.custommapsapp.android.R;
import com.custommapsapp.android.language.Linguist;

/**
 * TiePointActivity allows users to tie points on bitmap images to geo
 * coordinates on Google Maps.
 *
 * @author Marko Teittinen
 */
public class TiePointActivity extends AppCompatActivity implements OnMapReadyCallback {
  private static final String EXTRA_PREFIX = "com.custommapsapp.android";
  public static final String BITMAP_DATA = EXTRA_PREFIX + ".BitmapData";
  public static final String IMAGE_POINT = EXTRA_PREFIX + ".ImagePoint";
  public static final String RESTORE_SETTINGS = EXTRA_PREFIX + ".RestoreSettings";
  /** GEO_POINT extra should contain a single LatLng object */
  public static final String GEO_POINT = EXTRA_PREFIX + ".GeoPoint";

  private GoogleMap googleMap;
  private Linguist linguist;

  private SeekBar scaleBar;
  private SeekBar transparencyBar;
  private SeekBar rotateBar;

  private TiePointOverlay tiePointOverlay;
  private HelpDialogManager helpDialogManager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.tiepoints);
    linguist = ((CustomMapsApp) getApplication()).getLinguist();
    linguist.translateView(findViewById(R.id.root_view));

    SupportMapFragment mapFragment = (SupportMapFragment)
        getSupportFragmentManager().findFragmentById(R.id.googleMap);
    mapFragment.getMapAsync(this);

    helpDialogManager = new HelpDialogManager(this, HelpDialogManager.HELP_TIE_POINT,
        linguist.getString(R.string.geo_point_help));

    prepareUI();
    setSupportActionBar(findViewById(R.id.toolbar));

    // Update actionbar title to match current locale
    getSupportActionBar().setTitle(linguist.getString(R.string.create_map_name));

    Bundle extras = getIntent().getExtras();
    int[] center = extras.getIntArray(IMAGE_POINT);
    byte[] pngImage = extras.getByteArray(BITMAP_DATA);
    Bitmap image = BitmapFactory.decodeByteArray(pngImage, 0, pngImage.length);
    tiePointOverlay.setOverlayImage(image, center[0], center[1]);

    if (extras.getBoolean(RESTORE_SETTINGS, false)) {
      PreferenceHelper prefs = new PreferenceHelper();
      writeTransparencyUi(prefs.getTransparency());
      writeScaleUi(prefs.getScale());
      writeRotationUi(prefs.getRotation());
    } else {
      writeTransparencyUi(50);
      tiePointOverlay.setTransparency(50);
      writeScaleUi(1.0f);
      writeRotationUi(0);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    Bundle extras = getIntent().getExtras();
    if (googleMap != null && extras != null && extras.containsKey(GEO_POINT)) {
      // Editing a tiepoint that was previously placed, center it on view
      LatLng geoLocation = extras.getParcelable(GEO_POINT);
      googleMap.moveCamera(CameraUpdateFactory.newLatLng(geoLocation));
      // Prevent resetting of map center point on device orientation change
      extras.remove(GEO_POINT);
    }
    tiePointOverlay.setTransparency(readTransparencyUi());
    tiePointOverlay.setScale(readScaleUi());
    tiePointOverlay.setRotate(readRotationUi());
    helpDialogManager.onResume();
  }

  @Override
  protected void onPause() {
    // Save map state for next invocation
    new PreferenceHelper().saveMapState();

    helpDialogManager.onPause();
    if (isFinishing() && tiePointOverlay != null) {
      Bitmap image = tiePointOverlay.getOverlayImage();
      if (image != null && !image.isRecycled()) {
        image.recycle();
        tiePointOverlay.setOverlayImage(null, 0, 0);
      }
    }
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    if (helpDialogManager != null) {
      helpDialogManager.onSaveInstanceState(outState);
    }
    // GUI widget states are automatically stored, no need to add anything
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    if (helpDialogManager != null) {
      helpDialogManager.onRestoreInstanceState(savedInstanceState);
    }
  }

  private void returnGeoPoint(LatLng location) {
    // Release memory used by the overlay image
    Bitmap image = tiePointOverlay.getOverlayImage();
    tiePointOverlay.setOverlayImage(null, 0, 0);
    if (image != null && !image.isRecycled()) {
      image.recycle();
    }
    System.gc();

    // Save UI control values for next invocation
    new PreferenceHelper().saveValues();

    // Return the selected geo point to calling activity in the original Intent
    Intent result = getIntent();
    result.putExtra(GEO_POINT, location);
    setResult(RESULT_OK, result);
    finish();
  }

  // --------------------------------------------------------------------------
  // Options menu

  private static final int MENU_USER_LOCATION = 1;
  private static final int MENU_MAP_MODE = 2;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    MenuItem item =
        menu.add(Menu.NONE, MENU_USER_LOCATION, Menu.NONE, linguist.getString(R.string.my_location))
            .setIcon(R.drawable.ic_my_location_white_24dp);
    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    Drawable mapModeIcon = getResources().getDrawable(R.drawable.mapmode);
    mapModeIcon.setColorFilter(0xffffffff, PorterDuff.Mode.SRC_ATOP);
    item = menu.add(Menu.NONE, MENU_MAP_MODE, Menu.NONE, linguist.getString(R.string.map_mode))
        .setIcon(mapModeIcon);
    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    helpDialogManager.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case MENU_USER_LOCATION:
        centerOnUserLocation();
        return true;
      case MENU_MAP_MODE:
        changeMapMode();
        return true;
      default:
        helpDialogManager.onOptionsItemSelected(item);
        return true;
    }
  }

  // --------------------------------------------------------------------------
  // Dialog management

  @Override
  @Deprecated
  protected Dialog onCreateDialog(int id) {
    return helpDialogManager.onCreateDialog(id);
  }

  @Override
  @Deprecated
  protected void onPrepareDialog(int id, Dialog dialog) {
    helpDialogManager.onPrepareDialog(id, dialog);
  }

  // --------------------------------------------------------------------------
  // Activity UI

  private static final int[] MAP_TYPES = {
      GoogleMap.MAP_TYPE_NORMAL, GoogleMap.MAP_TYPE_HYBRID, GoogleMap.MAP_TYPE_TERRAIN
  };

  private void centerOnUserLocation() {
    if (googleMap == null) {
      return;
    }
    LatLng userLocation = getUserLocation();
    if (userLocation != null) {
      googleMap.animateCamera(CameraUpdateFactory.newLatLng(userLocation));
    }
  }

  private void changeMapMode() {
    if (googleMap == null) {
      return;
    }
    // Rotate map type (road map, hybrid, terrain)
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

  /**
   * @return rotation value displayed in widget
   */
  private int readRotationUi() {
    return rotateBar.getProgress() - 180;
  }

  private void writeRotationUi(int degrees) {
    rotateBar.setProgress(degrees + 180);
  }

  /**
   * @return scale value displayed in widget
   */
  private float readScaleUi() {
    return 1.0f + (scaleBar.getProgress() / 10f);
  }

  private void writeScaleUi(float scale) {
    scaleBar.setProgress(Math.round(10 * (scale - 1.0f)));
  }

  /**
   * @return transparency value displayed in widget
   */
  private int readTransparencyUi() {
    return transparencyBar.getProgress();
  }

  private void writeTransparencyUi(int transparencyPercent) {
    transparencyBar.setProgress(transparencyPercent);
  }

  private void prepareUI() {
    tiePointOverlay = findViewById(R.id.tiePointOverlay);
    Button doneButton = findViewById(R.id.selectPoint);
    rotateBar = findViewById(R.id.rotateBar);
    scaleBar = findViewById(R.id.scaleBar);
    transparencyBar = findViewById(R.id.transparencyBar);

    doneButton.setOnClickListener(v -> {
      if (googleMap == null) {
        return;
      }
      LatLng mapCenter = googleMap.getCameraPosition().target;
      returnGeoPoint(mapCenter);
    });

    rotateBar.setOnSeekBarChangeListener(new ImageOverlayAdjuster() {
      @Override
      protected void updateImageOverlay(int value) {
        tiePointOverlay.setRotate(value - 180);
        tiePointOverlay.invalidate();
      }
    });

    scaleBar.setOnSeekBarChangeListener(new ImageOverlayAdjuster() {
      @Override
      protected void updateImageOverlay(int value) {
        tiePointOverlay.setScale(1.0f + (value / 10f));
        tiePointOverlay.invalidate();
      }
    });

    transparencyBar.setOnSeekBarChangeListener(new ImageOverlayAdjuster() {
      @Override
      protected void updateImageOverlay(int value) {
        tiePointOverlay.setTransparency(value);
        tiePointOverlay.invalidate();
      }
    });
  }

  @Override
  public void onMapReady(GoogleMap googleMap) {
    this.googleMap = googleMap;
    UiSettings uiSettings = googleMap.getUiSettings();
    uiSettings.setRotateGesturesEnabled(false);
    uiSettings.setTiltGesturesEnabled(false);
    uiSettings.setIndoorLevelPickerEnabled(false);
    uiSettings.setMyLocationButtonEnabled(true);
    uiSettings.setZoomControlsEnabled(true);
    uiSettings.setZoomGesturesEnabled(true);
    uiSettings.setMapToolbarEnabled(false);

    // Restore last used map type
    PreferenceHelper prefs = new PreferenceHelper();
    googleMap.setMapType(prefs.getMapType());

    // Initialize map view location and zoom level
    LatLng mapCenter;
    float zoomLevel = prefs.getZoom();
    Bundle extras = getIntent().getExtras();
    if (extras == null || !extras.getBoolean(RESTORE_SETTINGS, false)) {
      // First point for a new map, start at user's location at default zoom level
      zoomLevel = prefs.getDefaultZoom();
      mapCenter = getUserLocation();
    } else if (extras.containsKey(GEO_POINT)) {
      // Editing a tiepoint that was previously placed, center it on view
      mapCenter = extras.getParcelable(GEO_POINT);
      // Prevent resetting of map center point on device orientation change
      extras.remove(GEO_POINT);
    } else {
      // Restore map center point from last view
      mapCenter = prefs.getLocation();
    }
    // If map center was not successfully determined,
    if (mapCenter == null) {
      mapCenter = getUserLocation();
      if (mapCenter == null) {
        // First time use, center on arbitrary position zoomed out as far as possible
        mapCenter = new LatLng(0, 0);
        zoomLevel = googleMap.getMinZoomLevel();
      }
    }
    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mapCenter, zoomLevel));
  }

  /** Returns the user's last known location or null, if not available. */
  @SuppressLint("MissingPermission")
  private LatLng getUserLocation() {
    LocationManager locationMgr = (LocationManager) getSystemService(LOCATION_SERVICE);
    Location location = locationMgr.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
    return location == null ? null : new LatLng(location.getLatitude(), location.getLongitude());
  }

  // --------------------------------------------------------------------------
  // Base inner class to modify image overlay based on seekbar changes

  abstract class ImageOverlayAdjuster implements SeekBar.OnSeekBarChangeListener {
    protected abstract void updateImageOverlay(int value);

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
      if (fromUser) {
        updateImageOverlay(progress);
      }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
      updateImageOverlay(seekBar.getProgress());
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
      updateImageOverlay(seekBar.getProgress());
    }
  }

  // --------------------------------------------------------------------------
  // Activity preferences

  private class PreferenceHelper {
    private static final String SCALE = "Scale";
    private static final String ROTATION = "Rotation";
    private static final String TRANSPARENCY = "Transparency";

    private static final String MAP_TYPE = "MapType";
    private static final String LATITUDE = "Latitude";
    private static final String LONGITUDE = "Longitude";
    private static final String ZOOM = "Zoom";
    private static final float DEFAULT_ZOOM = 10f;

    private SharedPreferences preferences;

    PreferenceHelper() {
      preferences = getPreferences(MODE_PRIVATE);
    }

    private int getRotation() {
      return preferences.getInt(ROTATION, 0);
    }

    private float getScale() {
      return preferences.getFloat(SCALE, 1.0f);
    }

    private int getTransparency() {
      return preferences.getInt(TRANSPARENCY, 50);
    }

    private int getMapType() {
      return preferences.getInt(MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL);
    }

    private float getZoom() {
      return preferences.getFloat(ZOOM, DEFAULT_ZOOM);
    }

    private float getDefaultZoom() {
      return DEFAULT_ZOOM;
    }

    private LatLng getLocation() {
      Double latitude = readDouble(LATITUDE);
      Double longitude = readDouble(LONGITUDE);
      if (latitude == null || longitude == null) {
        return null;
      }
      return new LatLng(latitude, longitude);
    }

    private Double readDouble(String key) {
      String stringValue = preferences.getString(key, null);
      if (stringValue == null) {
        return null;
      }
      try {
        return Double.parseDouble(stringValue);
      } catch (NumberFormatException ex) {
        return null;
      }
    }

    private void saveValues() {
      SharedPreferences.Editor editor = preferences.edit();
      editor.putInt(ROTATION, readRotationUi());
      editor.putFloat(SCALE, readScaleUi());
      editor.putInt(TRANSPARENCY, readTransparencyUi());
      editor.apply();
    }

    private void saveMapState() {
      SharedPreferences.Editor editor = preferences.edit();
      if (googleMap != null) {
        editor.putInt(MAP_TYPE, googleMap.getMapType());
        CameraPosition cameraPos = googleMap.getCameraPosition();
        LatLng mapCenter = cameraPos.target;
        editor.putString(LATITUDE, String.format(Locale.US, "%.6f", mapCenter.latitude));
        editor.putString(LONGITUDE, String.format(Locale.US, "%.6f", mapCenter.longitude));
        editor.putFloat(ZOOM, cameraPos.zoom);
      } else {
        editor.remove(MAP_TYPE);
        editor.remove(LATITUDE);
        editor.remove(LONGITUDE);
        editor.remove(ZOOM);
      }
      editor.apply();
    }
  }
}
