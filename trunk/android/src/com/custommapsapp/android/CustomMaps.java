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

import com.custommapsapp.android.MapDisplay.MapImageTooLargeException;
import com.custommapsapp.android.kml.GroundOverlay;
import com.custommapsapp.android.kml.KmlFinder;
import com.custommapsapp.android.kml.KmlInfo;
import com.custommapsapp.android.kml.KmlParser;
import com.custommapsapp.android.storage.EditPreferences;
import com.custommapsapp.android.storage.PreferenceStore;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * CustomMaps is the main activity of the application. It displays a bitmap
 * image tied to geo coordinates and the user's location on it.
 *
 * @author Marko Teittinen
 */
public class CustomMaps extends Activity {
  public static final String LOG_TAG = "Custom Maps";

  private static final String PREFIX = "com.custommapsapp.android";
  private static final String SAVED_LOCATION = PREFIX + ".Location";
  private static final String SAVED_MAP = PREFIX + ".Map";
  private static final String SAVED_ZOOMLEVEL = PREFIX + ".ZoomLevel";
  private static final String SAVED_FOLLOWMODE = PREFIX + ".FollowMode";
  private static final String SAVED_CENTER = PREFIX + ".Center";
  private static final String SAVED_INSTANCESTATE = PREFIX + ".InstanceState";
  private static final String SAVED_SAFETY_REMINDER = PREFIX + ".SafetyReminder";

  private static final String DOWNLOAD_URL_PREFIX = "http://www.custommapsapp.com/qr?";

  private static final int SELECT_MAP = 1;
  private static final int DOWNLOAD_MAP = 2;
  private static final int EDIT_PREFS = 3;

  private static final int MENU_SELECT_MAP = 1;
  private static final int MENU_MY_LOCATION = 2;
  private static final int MENU_LOCATION_DETAILS = 3;
  private static final int MENU_SHARE_MAP = 4;
  private static final int MENU_PREFERENCES = 5;

  private static final int DIALOG_LICENSE = 1;

  private MapDisplay mapDisplay;
  private LocationLayer locationLayer;
  private DistanceLayer distanceLayer;
  private GroundOverlay selectedMap = null;
  private DetailsDisplay detailsDisplay;
  private InertiaScroller inertiaScroller;
  private ImageButton zoomIn;
  private ImageButton zoomOut;
  private DisplayState displayState;

  private boolean licenseDialogCreated = false;
  private SafetyWarningDialog safetyReminder = null;
  private boolean updateMenuItems = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.i(LOG_TAG, "App memory available (MB): " + MemoryUtil.getTotalAppMemoryMB(this));

    reloadUI();

    GeoidHeightEstimator.initialize(getAssets());

    // Do not process launch intent if software license is not accepted yet
    if (PreferenceStore.instance(this).isLicenseAccepted()) {
      processLaunchIntent(savedInstanceState);
    } else {
      selectedMap = null;
    }
  }

  private void reloadUI() {
    float[] screenCenter = null;
    float zoomLevel = Float.NaN;
    if (displayState != null) {
      screenCenter = displayState.getScreenCenterGeoLocation();
      zoomLevel = displayState.getZoomLevel();
    }
    setContentView(R.layout.main);
    mapDisplay = (MapDisplay) findViewById(R.id.mapDisplay);
    if (inertiaScroller == null) {
      inertiaScroller = new InertiaScroller(mapDisplay);
    } else {
      inertiaScroller.setMap(mapDisplay);
    }
    locationLayer = (LocationLayer) findViewById(R.id.locationLayer);
    displayState = new DisplayState();
    mapDisplay.setDisplayState(displayState);
    locationLayer.setDisplayState(displayState);
    distanceLayer = (DistanceLayer) findViewById(R.id.distanceLayer);
    distanceLayer.setDisplayState(displayState);
    detailsDisplay = (DetailsDisplay) findViewById(R.id.detailsDisplay);

    if (locator == null) {
      locator = (LocationManager) getSystemService(LOCATION_SERVICE);
    }
    if (sensors == null) {
      sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
    }

    if (zoomIn != null) {
      zoomIn.setOnClickListener(null);
    }
    if (zoomOut != null) {
      zoomOut.setOnClickListener(null);
    }
    zoomIn = (ImageButton) findViewById(R.id.zoomIn);
    zoomOut = (ImageButton) findViewById(R.id.zoomOut);
    zoomIn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mapDisplay.zoomMap(2.0f);
      }
    });
    zoomOut.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mapDisplay.zoomMap(0.5f);
      }
    });
    if (selectedMap != null) {
      loadMapForDisplay(selectedMap, null);
      if (screenCenter != null) {
        displayState.centerOnGeoLocation(screenCenter[0], screenCenter[1]);
        displayState.setZoomLevel(zoomLevel);
      }
    }
  }

  private void processLaunchIntent(Bundle savedInstanceState) {
    Intent intent = getIntent();
    if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
      String action = intent.getAction();
      Uri data = intent.getData();
      // Clear intent action to prevent triggering download many times
      intent.setAction(null);
      if (launchIntentAction(action, data)) {
        return;
      } else {
        // Found invalid intent parameters, ignore them and launch normally
      }
    }

    if (savedInstanceState != null && savedInstanceState.getSerializable(SAVED_MAP) != null) {
      // onRestoreInstanceState(savedInstanceState);
    } else if (selectedMap != null) {
      loadKmlFile();
    } else {
      launchSelectMap(locator.getLastKnownLocation(LocationManager.GPS_PROVIDER));
    }
  }

  @Override
  protected void onResume() {
    super.onResume();

    // Do not display anything if license is not accepted yet
    if (!PreferenceStore.instance(this).isLicenseAccepted() && !licenseDialogCreated) {
      licenseDialogCreated = true;
      Log.i(LOG_TAG, "Showing license dialog");
      showDialog(DIALOG_LICENSE);
      return;
    }

    if (mapDisplay.getMap() == null && getIntent().getBundleExtra(SAVED_INSTANCESTATE) != null) {
      selectedMap = null;
      onRestoreInstanceState(getIntent().getBundleExtra(SAVED_INSTANCESTATE));
    }
    locationTracker.setContext(getApplicationContext());
    locationTracker.setDisplay(getWindowManager().getDefaultDisplay());
    locator.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0.0f, locationTracker);
    locator.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 0f, locationTracker);
    Sensor orientation = sensors.getDefaultSensor(Sensor.TYPE_ORIENTATION);
    sensors.registerListener(locationTracker, orientation, SensorManager.SENSOR_DELAY_UI);
    PreferenceStore prefs = PreferenceStore.instance(getApplicationContext());
    detailsDisplay.setUseMetric(prefs.isMetric());
    inertiaScroller.setUseMultitouch(prefs.useMultitouch());
    int visibility = (prefs.isShowDetails() ? View.VISIBLE : View.GONE);
    detailsDisplay.setVisibility(visibility);
    visibility = (prefs.isShowDistance() ? View.VISIBLE : View.GONE);
    distanceLayer.setVisibility(visibility);
  }

  @Override
  protected void onPause() {
    sensors.unregisterListener(locationTracker);
    locator.removeUpdates(locationTracker);
    loadMapForDisplay(null, null);
    super.onPause();
  }

  // --------------------------------------------------------------------------
  // Instance state saving and restoring

  @Override
  public void onSaveInstanceState(Bundle outState) {
    outState.putParcelable(SAVED_LOCATION, locationTracker.getCurrentLocation(null));
    outState.putSerializable(SAVED_MAP, selectedMap);
    outState.putBoolean(SAVED_FOLLOWMODE, mapDisplay.getFollowMode());
    float[] geoCenter = mapDisplay.getScreenCenterGeoLocation();
    if (geoCenter != null) {
      outState.putSerializable(SAVED_CENTER, geoCenter);
    }
    float zoomLevel = mapDisplay.getZoomLevel();
    outState.putFloat(SAVED_ZOOMLEVEL, zoomLevel);
    if (safetyReminder != null) {
      outState.putBundle(SAVED_SAFETY_REMINDER, safetyReminder.onSaveInstanceState());
    }
    // Save to original intent to have this available at will
    getIntent().putExtra(SAVED_INSTANCESTATE, outState);
  }

  @Override
  public void onRestoreInstanceState(Bundle inState) {
    if (PreferenceStore.instance(this).isLicenseAccepted()) {
      Bundle safetyReminderState = inState.getBundle(SAVED_SAFETY_REMINDER);
      if (safetyReminderState != null) {
        displaySafetyReminder(safetyReminderState);
      }
    }
    if (selectedMap != null) {
      // Do not restore instance state since a map is selected
      return;
    }
    GroundOverlay savedMap = (GroundOverlay) inState.getSerializable(SAVED_MAP);
    if (savedMap != null) {
      if (loadMapForDisplay(savedMap, null)) {
        selectedMap = savedMap;
        float[] geoCenter = (float[]) inState.getSerializable(SAVED_CENTER);
        if (geoCenter != null) {
          mapDisplay.centerOnLocation(geoCenter[0], geoCenter[1]);
        }
        mapDisplay.zoomMap(inState.getFloat(SAVED_ZOOMLEVEL, 1f));
        mapDisplay.setFollowMode(inState.getBoolean(SAVED_FOLLOWMODE));
        Location savedLocation = inState.getParcelable(SAVED_LOCATION);
        if (savedLocation != null) {
          locationTracker.onLocationChanged(savedLocation);
        }
      } else {
        displayMapLoadWarning();
      }
    }
    // Remove from original intent (might have been saved there)
    getIntent().removeExtra(SAVED_INSTANCESTATE);
  }

  private void displayUserMessage(final String message) {
    Runnable displayTask = new Runnable() {
      @Override
      public void run() {
        Toast.makeText(CustomMaps.this, message, Toast.LENGTH_LONG).show();
      }
    };
    runOnUiThread(displayTask);
  }

  // --------------------------------------------------------------------------
  // Menus

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    menu.add(Menu.NONE, MENU_SELECT_MAP, Menu.NONE, R.string.select_map)
        .setIcon(android.R.drawable.ic_menu_mapmode);
    menu.add(Menu.NONE, MENU_MY_LOCATION, Menu.NONE, R.string.my_location)
        .setIcon(android.R.drawable.ic_menu_mylocation);
    menu.add(Menu.NONE, MENU_LOCATION_DETAILS, Menu.NONE, R.string.location_details)
        .setIcon(android.R.drawable.ic_menu_info_details);
    menu.add(Menu.NONE, MENU_SHARE_MAP, Menu.NONE, R.string.share_map)
        .setIcon(android.R.drawable.ic_menu_share);
    menu.add(Menu.NONE, MENU_PREFERENCES, Menu.NONE, R.string.settings)
        .setIcon(android.R.drawable.ic_menu_preferences);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    if (updateMenuItems) {
      menu.findItem(MENU_SELECT_MAP).setTitle(R.string.select_map);
      menu.findItem(MENU_MY_LOCATION).setTitle(R.string.my_location);
      menu.findItem(MENU_LOCATION_DETAILS).setTitle(R.string.location_details);
      menu.findItem(MENU_SHARE_MAP).setTitle(R.string.share_map);
      menu.findItem(MENU_PREFERENCES).setTitle(R.string.settings);
      updateMenuItems = false;
    }
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case MENU_SELECT_MAP:
        launchSelectMap();
        break;
      case MENU_MY_LOCATION:
        centerUserLocation();
        break;
      case MENU_LOCATION_DETAILS:
        toggleDetailsDisplay();
        break;
      case MENU_SHARE_MAP:
        shareMap();
        break;
      case MENU_PREFERENCES:
        launchEditPreferences();
        break;
    }
    return true;
  }

  private void centerUserLocation() {
    if (!mapDisplay.centerOnGpsLocation()) {
      displayUserMessage(getString(R.string.gps_outside_map));
    } else {
      mapDisplay.setFollowMode(true);
    }
  }

  private void toggleDetailsDisplay() {
    Location location = locationTracker.getCurrentLocation(null);
    boolean showDetails = !detailsDisplay.isShown();
    if (showDetails) {
      detailsDisplay.updateValues(location);
      detailsDisplay.setVisibility(View.VISIBLE);
      detailsDisplay.getParent().requestLayout();
    } else {
      detailsDisplay.setVisibility(View.GONE);
      detailsDisplay.getParent().requestLayout();
    }
    PreferenceStore.instance(getApplicationContext()).setShowDetails(showDetails);
  }

  private void shareMap() {
    if (!FileUtil.shareMap(this, selectedMap)) {
      displayUserMessage(getString(R.string.share_map_failed));
    }
  }

  // --------------------------------------------------------------------------
  // Activities

  private boolean launchIntentAction(String action, Uri data) {
    if (!Intent.ACTION_VIEW.equals(action) || data == null) {
      return false;
    }
    // Save content:// scheme Uri data into a file using ContentResolver
    if (ContentResolver.SCHEME_CONTENT.equals(data.getScheme())) {
      File savedFile = FileUtil.saveKmzContentUri(this, data);
      if (savedFile != null) {
        // Open the saved file instead of passed-in content Uri
        data = Uri.fromFile(savedFile);
      } else {
        // Failed to save the data, display error message and quit app
        displayUserMessage(getString(R.string.external_content_failed));
        finish();
        return false;
      }
    }
    // NOTE: All errors "fall through" to return 'false'
    if ("file".equals(data.getScheme())) {
      // Open local KMZ file
      String localPath = data.getPath();
      if (localPath != null) {
        File localFile = new File(localPath);
        if (localFile.exists() && localFile.isFile() && localFile.getName().endsWith(".kmz")) {
          launchSelectMap(localFile);
          return true;
        }
      }
      Log.w(LOG_TAG, "Invalid open local file request: " + localPath);
    } else if (data.toString().startsWith(DOWNLOAD_URL_PREFIX)) {
      // Download file from the web and open it
      String targetUrl = data.getQueryParameter("url");
      if (targetUrl != null) {
        try {
          URL kmzUrl = new URL(targetUrl);
          launchKmzDownloader(kmzUrl);
          return true;
        } catch (MalformedURLException ex) {
          Log.w(LOG_TAG, "Invalid open web file request: " + targetUrl, ex);
        }
      }
    }
    return false;
  }

  private void launchKmzDownloader(URL mapUrl) {
    Intent downloadMap = new Intent(this, KmzDownloader.class);
    downloadMap.putExtra(KmzDownloader.URL, mapUrl.toString());
    startActivityForResult(downloadMap, DOWNLOAD_MAP);
  }

  private void processKmzDownloaderResult(Intent data) {
    String localPath = (data != null ? data.getStringExtra(KmzDownloader.LOCAL_FILE) : null);
    if (localPath != null) {
      File localFile = new File(localPath);
      if (localFile.exists() && localFile.isFile() && localFile.getName().endsWith(".kmz")) {
        // Web file download succeeded, auto-select it
        launchSelectMap(localFile);
        return;
      }
    }
    Log.w(LOG_TAG, "Invalid local file result from KmzDownloader: " + localPath);
    // Failed to download the web file, or it was cancelled
    launchSelectMap();
  }

  private void launchEditPreferences() {
    Intent editPrefs = new Intent(this, EditPreferences.class);
    startActivityForResult(editPrefs, EDIT_PREFS);
  }

  private void launchSelectMap(File localFile) {
    Intent selectMap = new Intent(this, SelectMap.class);
    selectMap.putExtra(SelectMap.LOCAL_FILE, localFile.getAbsolutePath());
    selectMap.putExtra(SelectMap.AUTO_SELECT, true);
    startActivityForResult(selectMap, SELECT_MAP);
  }

  private void launchSelectMap(Location location) {
    Intent selectMap = new Intent(this, SelectMap.class);
    selectMap.putExtra(SelectMap.LAST_LOCATION, location);
    selectMap.putExtra(SelectMap.AUTO_SELECT, true);
    startActivityForResult(selectMap, SELECT_MAP);
  }

  private void launchSelectMap() {
    Intent selectMap = new Intent(this, SelectMap.class);
    startActivityForResult(selectMap, SELECT_MAP);
  }

  private void processSelectMapResult(Intent data) {
    if (data == null) {
      Log.w(LOG_TAG, String.format("User did not select a map."));
      Bundle instanceState = getIntent().getBundleExtra(SAVED_INSTANCESTATE);
      if (instanceState != null && instanceState.containsKey(SAVED_MAP)) {
        selectedMap = null;
        onRestoreInstanceState(instanceState);
      }
      if (selectedMap == null) {
        // User canceled initial selection, exit app
        finish();
      }
    } else {
      selectedMap = (GroundOverlay) data.getSerializableExtra(SelectMap.SELECTED_MAP);
      if (!loadMapForDisplay(selectedMap, mapDisplay.getMap())) {
        displayMapLoadWarning();
      } else if (PreferenceStore.instance(this).isReminderRequested()) {
        displaySafetyReminder(null);
      }
      // Start with last known location if it is fresher than 15 minutes
      long _15MinutesAgo = System.currentTimeMillis() - 15 * 60 * 1000;
      Location location = locator.getLastKnownLocation(LocationManager.GPS_PROVIDER);
      if (location == null || location.getTime() < _15MinutesAgo) {
        location = locator.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (location != null && location.getTime() < _15MinutesAgo) {
          location = null;
        }
      }
      locationTracker.onLocationChanged(location);
      if (location == null) {
        displayUserMessage(getString(R.string.waiting_for_gps));
        mapDisplay.setFollowMode(true);
        mapDisplay.centerOnMapCenterLocation();
      } else {
        // Location known, center on user location if within map boundaries
        mapDisplay.setFollowMode(mapDisplay.centerOnGpsLocation());
        if (!mapDisplay.getFollowMode()) {
          mapDisplay.centerOnMapCenterLocation();
        }
      }
    }
  }

  private void processEditPreferencesResult(Intent data) {
    boolean languageChanged = data.getBooleanExtra(EditPreferences.LANGUAGE_CHANGED, false);
    if (languageChanged) {
      reloadUI();
      updateMenuItems = true;
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == DOWNLOAD_MAP) {
      if (resultCode == RESULT_OK && data != null) {
        // map downloaded successfully from web, process results
        processKmzDownloaderResult(data);
      } else {
        // download failed or was cancelled, launch default map selection
        launchSelectMap();
      }
      return;
    }
    if (requestCode == SELECT_MAP) {
      if (resultCode == RESULT_OK) {
        processSelectMapResult(data);
      } else {
        processSelectMapResult(null);
      }
      return;
    }
    if (requestCode == EDIT_PREFS) {
      processEditPreferencesResult(data);
      return;
    }
    Log.w(LOG_TAG, String.format(
      "Unexpected activity result. Request: %d, Result %d", requestCode, resultCode));
  }

  /**
   * Attempts to load a new map. If the attempt fails because the image is too
   * large, loads map given as old map. If even that attempt fails, clears map
   * completely. If clearing the map fails, will throw OutOfMemoryError.
   *
   * @param newMap to be displayed
   * @param oldMap to be restored if newMap fails to display
   * @return {@code true} if the newMap was loaded successfully
   */
  private boolean loadMapForDisplay(GroundOverlay newMap, GroundOverlay oldMap) {
    try {
      mapDisplay.setMap(newMap);
      if (newMap != null) {
        locationLayer.updateMapAngle();
      }
      return true;
    } catch (MapImageTooLargeException ex) {
      // map was too large, restore old map
      if (newMap != null) {
        loadMapForDisplay(oldMap, null);
        if (oldMap != null) {
          locationLayer.updateMapAngle();
        }
        return false;
      } else {
        throw new OutOfMemoryError("Can't load even 'null' map. Giving up.");
      }
    }
  }

  private void displayMapLoadWarning() {
    // Failed to load selected map, old was recovered, display error message
    displayUserMessage(getString(R.string.map_too_large));
  }

  private void displaySafetyReminder(Bundle savedState) {
    safetyReminder = new SafetyWarningDialog(this);
    safetyReminder.setOnDismissListener(new DialogInterface.OnDismissListener() {
      @Override
      public void onDismiss(DialogInterface dialog) {
        SafetyWarningDialog reminder = (SafetyWarningDialog) dialog;
        if (reminder.wasButtonPressed() && !reminder.getShowAgainRequested()) {
          PreferenceStore.instance(CustomMaps.this).setReminderRequested(false);
        }
        safetyReminder = null;
      }
    });
    safetyReminder.show();
    if (savedState != null) {
      safetyReminder.onRestoreInstanceState(savedState);
    }
  }

  // --------------------------------------------------------------------------
  // License handling

  private void showLicenseDialog() {
    AboutDialog licenseDialog = new AboutDialog(this);
    String version = PreferenceStore.instance(this).getVersion();
    licenseDialog.setVersion(version);
    licenseDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
      @Override
      public void onDismiss(DialogInterface dialog) {
        AboutDialog aboutDialog = (AboutDialog) dialog;
        if (aboutDialog.wasButtonPressed()) {
          // Dialog dismissed with the "accept" button
          if (aboutDialog.isLicenseAccepted()) {
            licenseAccepted();
          } else {
            // License rejected, exit app
            Log.i(LOG_TAG, "User rejected software license, exiting app");
            finish();
          }
        } else {
          // License dialog dismissed without button (phone rotate?)
          aboutDialog.setOnDismissListener(null);
          aboutDialog = null;
        }
      }
    });
    licenseDialog.show();
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    if (id == DIALOG_LICENSE) {
      return new AboutDialog(this);
    }
    return super.onCreateDialog(id);
  }

  @Override
  protected void onPrepareDialog(int id, Dialog dialog) {
    super.onPrepareDialog(id, dialog);
    if (id == DIALOG_LICENSE) {
      String version = PreferenceStore.instance(this).getVersion();
      AboutDialog dlg = (AboutDialog) dialog;
      dlg.setVersion(version);

      dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
          AboutDialog aboutDialog = (AboutDialog) dialog;
          if (aboutDialog.wasButtonPressed()) {
            // Dialog dismissed with the "accept" button
            if (aboutDialog.isLicenseAccepted()) {
              licenseAccepted();
            } else {
              // License rejected, exit app
              Log.i(LOG_TAG, "User rejected software license, exiting app");
              finish();
            }
          }
        }
      });
    }
  }

  private void licenseAccepted() {
    Log.i(LOG_TAG, "Software license was accepted");
    PreferenceStore.instance(this).setLicenseAccepted(true);
    // Continue app launch
    processLaunchIntent(null);
  }

  // --------------------------------------------------------------------------

  private void loadKmlFile() {
    try {
      selectedMap = findGroundOverlay();
    } catch (Exception ex) {
      Log.w(LOG_TAG, "Failed to load kml file", ex);
    }
  }

  private GroundOverlay findGroundOverlay() throws IOException, XmlPullParserException {
    if (selectedMap != null) {
      return selectedMap;
    }

    Iterable<KmlInfo> kmlFiles = KmlFinder.findKmlFiles(FileUtil.getDataDirectory());
    KmlInfo data = kmlFiles.iterator().next();
    KmlParser parser = new KmlParser();
    Iterable<GroundOverlay> overlays = parser.readFile(data.getKmlReader());
    GroundOverlay overlay = overlays.iterator().next();
    overlay.setKmlInfo(data);

    return overlay;
  }

  private LocationManager locator;
  private SensorManager sensors;
  private LocationTracker locationTracker = new LocationTracker(null) {
    @Override
    public void onLocationChanged(Location location) {
      if (location == null) {
        return;
      }
      super.onLocationChanged(location);
      // Get updated location w/ averaged speed and altitude to be passed to
      // mapDisplay
      getCurrentLocation(location);
      mapDisplay.setGpsLocation((float) location.getLongitude(), (float) location.getLatitude(),
          location.hasAccuracy() ? location.getAccuracy() : 10000f, location.getBearing());
      locationLayer.setGpsLocation(location);
      if (distanceLayer.getVisibility() == View.VISIBLE) {
        distanceLayer.setUserLocation(location);
      }
      if (detailsDisplay.isShown()) {
        detailsDisplay.updateValues(location);
      }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
      if (event.sensor.getType() != Sensor.TYPE_ORIENTATION) {
        return;
      }
      super.onSensorChanged(event);
      locationLayer.setHeading(compassHeading);
      if (detailsDisplay.isShown()) {
        detailsDisplay.setHeading(locationLayer.getHeading());
      }
    }
  };
}
