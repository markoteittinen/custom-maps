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

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;

import com.custommapsapp.android.CustomMaps;
import com.custommapsapp.android.HelpDialogManager;
import com.custommapsapp.android.MapApiKeys;
import com.custommapsapp.android.R;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * TiePointActivity allows users to tie points on bitmap images to geo
 * coordinates on Google Maps.
 *
 * @author Marko Teittinen
 */
public class TiePointActivity extends MapActivity {
  private static final String EXTRA_PREFIX = "com.custommapsapp.android";
  public static final String BITMAP_DATA = EXTRA_PREFIX + ".BitmapData";
  public static final String IMAGE_POINT = EXTRA_PREFIX + ".ImagePoint";
  public static final String RESTORE_SETTINGS = EXTRA_PREFIX + ".RestoreSettings";
  public static final String GEO_POINT_E6 = EXTRA_PREFIX + ".GeoPointE6";

  private MapView mapView;
  private ImageButton mapModeButton;
  private Button doneButton;
  private SeekBar scaleBar;
  private SeekBar transparencyBar;
  private SeekBar rotateBar;
  private MyLocationOverlay userLocation;

  private MapImageOverlay imageOverlay;
  private HelpDialogManager helpDialogManager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.tiepoints);

    TextView mapViewLocation = (TextView) findViewById(R.id.mapviewlocation);
    ViewParent p = mapViewLocation.getParent();
    if (p instanceof ViewGroup) {
      ViewGroup layout = (ViewGroup) p;
      LayoutParams layoutParams = mapViewLocation.getLayoutParams();
      try {
        mapView = MapApiKeys.createMapView(this);
        layout.removeView(mapViewLocation);
        layout.addView(mapView, layoutParams);
        mapView.setEnabled(true);
        mapView.setClickable(true);
      } catch (IllegalArgumentException ex) {
        Log.e(CustomMaps.LOG_TAG, "Failed to create a map matching the signature key");
        setContentView(mapViewLocation);
        mapViewLocation.setTextSize(TypedValue.COMPLEX_UNIT_PT, 10);
        mapViewLocation.setText(R.string.geo_point_mapview_failure);
        return;
      }
    }
    userLocation = new MyLocationOverlay(this, mapView);

    prepareUI();

    mapView.setBuiltInZoomControls(true);
    mapView.setReticleDrawMode(MapView.ReticleDrawMode.DRAW_RETICLE_NEVER);

    Bundle extras = getIntent().getExtras();
    int[] center = extras.getIntArray(IMAGE_POINT);
    byte[] pngImage = extras.getByteArray(BITMAP_DATA);
    Bitmap image = BitmapFactory.decodeByteArray(pngImage, 0, pngImage.length);
    imageOverlay = new MapImageOverlay();
    imageOverlay.setOverlayImage(image, center[0], center[1]);
    mapView.getOverlays().add(imageOverlay);

    if (extras.getBoolean(RESTORE_SETTINGS, false)) {
      PreferenceHelper prefs = new PreferenceHelper();
      writeTransparencyUi(prefs.getTransparency());
      writeScaleUi(prefs.getScale());
      writeRotationUi(prefs.getRotation());
    } else {
      writeTransparencyUi(50);
      imageOverlay.setTransparency(50);
      writeScaleUi(1.0f);
      writeRotationUi(0);
    }

    helpDialogManager = new HelpDialogManager(this, HelpDialogManager.HELP_TIE_POINT,
                                              getString(R.string.geo_point_help));
  }

  @Override
  protected void onResume() {
    super.onResume();
    userLocation.enableMyLocation();
    Bundle extras = getIntent().getExtras();
    if (extras.containsKey(GEO_POINT_E6)) {
      // Editing a tiepoint that was previously placed, center it on view
      int[] geoLocationE6 = extras.getIntArray(GEO_POINT_E6);
      mapView.getController().setCenter(new GeoPoint(geoLocationE6[0], geoLocationE6[1]));
      // Prevent resetting of map center point on device orientation change
      extras.remove(GEO_POINT_E6);
    }
    imageOverlay.setTransparency(readTransparencyUi());
    imageOverlay.setScale(readScaleUi());
    imageOverlay.setRotate(readRotationUi());
    mapView.postInvalidate();
    helpDialogManager.onResume();
  }

  @Override
  protected void onPause() {
    helpDialogManager.onPause();
    userLocation.disableMyLocation();
    if (isFinishing() && imageOverlay != null) {
      Bitmap image = imageOverlay.getOverlayImage();
      if (image != null && !image.isRecycled()) {
        image.recycle();
        imageOverlay.setOverlayImage(null, 0, 0);
      }
    }
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    helpDialogManager.onSaveInstanceState(outState);
    // GUI widget states are automatically stored, no need to add anything
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    helpDialogManager.onRestoreInstanceState(savedInstanceState);
  }

  private void returnGeoPoint(GeoPoint location) {
    // Release memory used by the overlay image
    Bitmap image = imageOverlay.getOverlayImage();
    imageOverlay.setOverlayImage(null, 0, 0);
    if (image != null && !image.isRecycled()) {
      image.recycle();
    }
    System.gc();

    // Save UI values for next invocation
    new PreferenceHelper().saveValues();

    // Return the selected GeoPoint to calling activity in the original Intent
    Intent result = getIntent();
    int[] geoPoint = new int[] {location.getLatitudeE6(), location.getLongitudeE6()};
    result.putExtra(GEO_POINT_E6, geoPoint);
    setResult(RESULT_OK, result);
    finish();
  }

  // --------------------------------------------------------------------------
  // Options menu

  private static final int MENU_USER_LOCATION = 1;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    menu.add(Menu.NONE, MENU_USER_LOCATION, Menu.NONE, R.string.my_location)
        .setIcon(android.R.drawable.ic_menu_mylocation);
    helpDialogManager.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case MENU_USER_LOCATION:
        userLocation.runOnFirstFix(centerOnUserLocation);
        return true;
      default:
        helpDialogManager.onOptionsItemSelected(item);
        return true;
    }
  }

  // --------------------------------------------------------------------------
  // Dialog management

  @Override
  protected Dialog onCreateDialog(int id) {
    return helpDialogManager.onCreateDialog(id);
  }

  @Override
  protected void onPrepareDialog(int id, Dialog dialog) {
    helpDialogManager.onPrepareDialog(id, dialog);
  }

  // --------------------------------------------------------------------------
  // Activity UI

  private final Runnable centerOnUserLocation = new Runnable() {
    @Override
    public void run() {
      GeoPoint userGeo = userLocation.getMyLocation();
      if (userGeo != null) {
        mapView.getController().animateTo(userGeo);
      }
    }
  };

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
    doneButton = (Button) findViewById(R.id.selectPoint);
    mapModeButton = (ImageButton) findViewById(R.id.mapmode);
    rotateBar = (SeekBar) findViewById(R.id.rotateBar);
    scaleBar = (SeekBar) findViewById(R.id.scaleBar);
    transparencyBar = (SeekBar) findViewById(R.id.transparencyBar);

    // Toggle between map and satellite view
    mapModeButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mapView.setSatellite(!mapView.isSatellite());
      }
    });

    doneButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        returnGeoPoint(mapView.getMapCenter());
      }
    });

    rotateBar.setOnSeekBarChangeListener(new ImageOverlayAdjuster() {
      @Override
      protected void updateImageOverlay(int value) {
        imageOverlay.setRotate(value - 180);
        mapView.invalidate();
      }
    });

    scaleBar.setOnSeekBarChangeListener(new ImageOverlayAdjuster() {
      @Override
      protected void updateImageOverlay(int value) {
        imageOverlay.setScale(1.0f + (value / 10f));
        mapView.invalidate();
      }
    });

    transparencyBar.setOnSeekBarChangeListener(new ImageOverlayAdjuster() {
      @Override
      protected void updateImageOverlay(int value) {
        imageOverlay.setTransparency(value);
        mapView.invalidate();
      }
    });
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
  // MapActivity status methods required by Google license

  @Override
  protected boolean isRouteDisplayed() {
    return false;
  }

  // --------------------------------------------------------------------------
  // Activity preferences

  private class PreferenceHelper {
    private static final String SCALE = "Scale";
    private static final String ROTATION = "Rotation";
    private static final String TRANSPARENCY = "Transparency";

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

    private void saveValues() {
      SharedPreferences.Editor editor = preferences.edit();
      editor.putInt(ROTATION, readRotationUi());
      editor.putFloat(SCALE, readScaleUi());
      editor.putInt(TRANSPARENCY, readTransparencyUi());
      editor.commit();
    }
  }
}
