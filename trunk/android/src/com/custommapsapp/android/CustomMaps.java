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
import com.custommapsapp.android.kml.KmlFeature;
import com.custommapsapp.android.kml.KmlFinder;
import com.custommapsapp.android.kml.KmlFolder;
import com.custommapsapp.android.kml.KmlInfo;
import com.custommapsapp.android.kml.KmlParser;
import com.custommapsapp.android.kml.Placemark;
import com.custommapsapp.android.storage.EditPreferences;
import com.custommapsapp.android.storage.PreferenceStore;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

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
  private static final String SAVED_MARKER = PREFIX + ".Placemark";
  private static final String SAVED_ZOOMLEVEL = PREFIX + ".ZoomLevel";
  private static final String SAVED_FOLLOWMODE = PREFIX + ".FollowMode";
  private static final String SAVED_CENTER = PREFIX + ".Center";
  private static final String SAVED_INSTANCESTATE = PREFIX + ".InstanceState";
  private static final String SAVED_SAFETY_REMINDER = PREFIX + ".SafetyReminder";

  private static final String DOWNLOAD_URL_PREFIX = "http://www.custommapsapp.com/qr?";

  private enum MapError {
    NO_ERROR, IMAGE_TOO_LARGE, IMAGE_NOT_FOUND, INVALID_IMAGE
  };

  // Activity IDs
  private static final int ACCEPT_LICENSE = 10;
  private static final int SELECT_MAP = 1;
  private static final int DOWNLOAD_MAP = 2;
  private static final int EDIT_PREFS = 3;

  private static final int MENU_SELECT_MAP = 1;
  private static final int MENU_MY_LOCATION = 2;
  private static final int MENU_LOCATION_DETAILS = 3;
  private static final int MENU_SHARE_MAP = 4;
  private static final int MENU_PREFERENCES = 5;

  private MapDisplay mapDisplay;
  private LocationLayer locationLayer;
  private DistanceLayer distanceLayer;
  private KmlFolder selectedMap = null;
  private GroundOverlay mapImage = null;
  private List<Placemark> placemarks = new ArrayList<Placemark>();
  private DetailsDisplay detailsDisplay;
  private InertiaScroller inertiaScroller;
  private ImageButton zoomIn;
  private ImageButton zoomOut;
  private DisplayState displayState;

  private SafetyWarningDialog safetyReminder = null;
  private boolean updateMenuItems = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.i(LOG_TAG, "App memory available (MB): " + MemoryUtil.getTotalAppMemoryMB(this));

    // Initialize disk cache
    ImageDiskCache.instance(this);
    GeoidHeightEstimator.initialize(getAssets());

    // Never display Holo actionbar in landscape mode
    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      requestWindowFeature(Window.FEATURE_NO_TITLE);
    }

    boolean ptSizeFixNeeded = PtSizeFixer.isFixNeeded(this);
    reloadUI();
    if (ptSizeFixNeeded) {
      PtSizeFixer.fixView(detailsDisplay.getRootView());
    }

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
      loadMapForDisplay(mapImage, null);
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
      launchSelectMap(getLastKnownLocation(0));
    }
  }

  @Override
  protected void onResume() {
    super.onResume();

    // Do not display anything if license is not accepted yet
    if (!PreferenceStore.instance(this).isLicenseAccepted() && !isFinishing()) {
      Log.i(LOG_TAG, "Showing license activity");
      launchLicenseActivity();
      return;
    }

    if (mapDisplay.getMap() == null && getIntent().getBundleExtra(SAVED_INSTANCESTATE) != null) {
      selectedMap = null;
      onRestoreInstanceState(getIntent().getBundleExtra(SAVED_INSTANCESTATE));
    }
    locationTracker.setContext(getApplicationContext());
    locationTracker.setDisplay(getWindowManager().getDefaultDisplay());
    locationTracker.setQuitting(false);
    locationTracker.resetCompass();

    // Avoid crashing on some systems where location providers are disabled
    // This is possible on some open source Android variants.
    boolean gpsAvailable = false;
    if (locator.getProvider(LocationManager.GPS_PROVIDER) != null) {
      locator.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0.0f, locationTracker);
      gpsAvailable = true;
    }
    boolean locationAvailable = gpsAvailable;
    if (locator.getProvider(LocationManager.NETWORK_PROVIDER) != null) {
      locator.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 0f, locationTracker);
      locationAvailable = true;
    }
    String message = null;
    if (!gpsAvailable) {
      message = getString(R.string.gps_not_available);
      if (!locationAvailable) {
        message = getString(R.string.location_not_available);
      }
    }
    locationLayer.setWarningMessage(message);

    Sensor sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    sensors.registerListener(locationTracker, sensor, 50000);
    sensor = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    sensors.registerListener(locationTracker, sensor, 50000);
    PreferenceStore prefs = PreferenceStore.instance(getApplicationContext());
    detailsDisplay.setUseMetric(prefs.isMetric());
    inertiaScroller.setUseMultitouch(prefs.useMultitouch());
    int visibility = (prefs.isShowDetails() ? View.VISIBLE : View.GONE);
    detailsDisplay.setVisibility(visibility);
    visibility = (prefs.isShowDistance() ? View.VISIBLE : View.GONE);
    distanceLayer.setVisibility(visibility);
    distanceLayer.setShowHeading(prefs.isShowHeading());

    // On honeycomb and newer, display a button to open options menu
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      ImageButton more = (ImageButton) findViewById(R.id.optionsMenuButton);
      if (more != null) {
        more.setVisibility(View.VISIBLE);
        more.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            openOptionsMenu();
          }
        });
      }
    }
  }

  @Override
  protected void onPause() {
    locationTracker.setQuitting(true);
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
    if (selectedMap != null) {
      outState.putSerializable(SAVED_MAP, selectedMap);
    }
    outState.putBoolean(SAVED_FOLLOWMODE, displayState.getFollowMode());
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
    KmlFolder savedMap = (KmlFolder) inState.getSerializable(SAVED_MAP);
    if (savedMap != null) {
      initializeMapVariables(savedMap);
      if (loadMapForDisplay(mapImage, null) == MapError.NO_ERROR) {
        selectedMap = savedMap;
        mapDisplay.addMapMarkers(placemarks);
        float[] geoCenter = (float[]) inState.getSerializable(SAVED_CENTER);
        if (geoCenter != null) {
          mapDisplay.centerOnLocation(geoCenter[0], geoCenter[1]);
        }
        mapDisplay.zoomMap(inState.getFloat(SAVED_ZOOMLEVEL, 1f));
        displayState.setFollowMode(inState.getBoolean(SAVED_FOLLOWMODE));
        Location savedLocation = inState.getParcelable(SAVED_LOCATION);
        if (savedLocation != null) {
          locationTracker.onLocationChanged(savedLocation);
        }
      } else {
        // TODO: Do we need a failure message by error result from loadMap...()?
        if (savedMap.getKmlInfo().getFile().exists()) {
          // map image exists but is too large, notify user
          displayMapLoadWarning();
        } else {
          // map has been deleted, remove its info
          inState.remove(SAVED_MAP);
          savedMap = null;
          selectedMap = null;
        }
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

  /**
   * Initializes mapImage (first GroundOverlay in KmlFolder) and list of
   * placemarks stored with map.
   *
   * @param map KmlFolder containing the data
   */
  private void initializeMapVariables(KmlFolder map) {
    selectedMap = map;
    placemarks.clear();
    mapImage = null;
    if (map != null) {
      mapImage = map.getFirstMap();
      Log.d(LOG_TAG, "Selected map: " + (mapImage == null ? "- none -" : mapImage.getName()));
      for (KmlFeature feature : map.getFeatures()) {
        if (feature instanceof Placemark) {
          placemarks.add((Placemark) feature);
        }
      }
    }
    Log.d(LOG_TAG,
        String.format("CustomMaps initialized map. Found %d placemarks.", placemarks.size()));
  }

  // --------------------------------------------------------------------------
  // Menus

  @SuppressLint("NewApi")
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    menu.add(Menu.NONE, MENU_SELECT_MAP, Menu.NONE, R.string.select_map)
        .setIcon(android.R.drawable.ic_menu_mapmode);
    MenuItem item = menu.add(Menu.NONE, MENU_MY_LOCATION, Menu.NONE, R.string.my_location)
        .setIcon(android.R.drawable.ic_menu_mylocation);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    }
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
    // Can't share a null map or one that doesn't contain KmlInfo
    boolean enableShare = (selectedMap != null && selectedMap.getKmlInfo() != null);
    menu.findItem(MENU_SHARE_MAP).setEnabled(enableShare);
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
      displayState.setFollowMode(true);
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
    if (selectedMap == null || !FileUtil.shareMap(this, selectedMap)) {
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
      Log.w(LOG_TAG, "User did not select a map.");
      Bundle instanceState = getIntent().getBundleExtra(SAVED_INSTANCESTATE);
      if (instanceState != null && instanceState.containsKey(SAVED_MAP)) {
        selectedMap = null;
        onRestoreInstanceState(instanceState);
        if (selectedMap == null) {
          // Previous map is no longer available, auto-select another one
          mapDisplay.post(new Runnable() {
            @Override
            public void run() {
              launchSelectMap(getLastKnownLocation(0));
            }
          });
          return;
        }
      }
      if (selectedMap == null) {
        // User canceled initial selection, exit app
        finish();
      }
    } else {
      initializeMapVariables((KmlFolder) data.getSerializableExtra(SelectMap.SELECTED_MAP));
      MapError status = loadMapForDisplay(mapImage, mapDisplay.getMap());
      if (status == MapError.NO_ERROR) {
        mapDisplay.addMapMarkers(placemarks);
        if (PreferenceStore.instance(this).isReminderRequested()) {
          displaySafetyReminder(null);
        }
      } else {
        // TODO: display error message based on errorCode
        displayMapLoadWarning();
      }
      // Start with last known location if it is fresher than 15 minutes
      long _15Minutes = 15 * 60 * 1000;
      Location location = getLastKnownLocation(_15Minutes);
      locationTracker.onLocationChanged(location);
      if (location == null) {
        displayUserMessage(getString(R.string.waiting_for_gps));
        displayState.setFollowMode(true);
        mapDisplay.centerOnMapCenterLocation();
      } else {
        // Location known, center on user location if within map boundaries
        displayState.setFollowMode(mapDisplay.centerOnGpsLocation());
        if (!displayState.getFollowMode()) {
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
      processSelectMapResult(resultCode == RESULT_OK ? data : null);
      return;
    }
    if (requestCode == EDIT_PREFS) {
      processEditPreferencesResult(data);
      return;
    }
    if (requestCode == ACCEPT_LICENSE) {
      processLicenseActivityResult(resultCode, data);
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
   * @return MapError.NO_ERROR if newMap was loaded successfully, otherwise some
   *     other MapError value indicating nature of problem
   */
  private MapError loadMapForDisplay(GroundOverlay newMap, GroundOverlay oldMap) {
    try {
      mapDisplay.setMap(newMap);
      if (newMap != null) {
        // Force failure if map file has been deleted
        if (!newMap.getKmlInfo().getFile().exists()) {
          return MapError.IMAGE_NOT_FOUND;
        }
        locationLayer.updateMapAngle();
      }
      return MapError.NO_ERROR;
    } catch (MapImageTooLargeException ex) {
      // map was too large, restore old map
      if (newMap != null) {
        loadMapForDisplay(oldMap, null);
        if (oldMap != null) {
          locationLayer.updateMapAngle();
        }
        return MapError.IMAGE_TOO_LARGE;
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

  private void launchLicenseActivity() {
    Intent acceptLicense = new Intent(this, AboutDisplay.class);
    acceptLicense.putExtra(AboutDisplay.CANCELLABLE, false);
    startActivityForResult(acceptLicense, ACCEPT_LICENSE);
  }

  private void processLicenseActivityResult(int resultCode, Intent data) {
    if (resultCode == RESULT_OK && data != null &&
        data.getBooleanExtra(AboutDisplay.LICENSE_ACCEPTED, false)) {
      licenseAccepted();
    } else {
      Log.i(LOG_TAG, "User rejected software license, exiting app");
      finish();
    }
  }

  private void licenseAccepted() {
    Log.i(LOG_TAG, "Software license was accepted");
    PreferenceStore.instance(this).setLicenseAccepted(true);
    // Continue app launch
    processLaunchIntent(null);
  }

  // --------------------------------------------------------------------------

  /**
   * Get last known location that is not older than given maximum age. Age
   * value 0 is considered unlimited.
   *
   * @param maxAgeMs Maximum age of acceptable known location in milliseconds.
   * @return Last known location that is not older than maxAgeMs, or null if
   *         there is no known location that is fresh enough. GPS location is
   *         preferred over network location.
   */
  private Location getLastKnownLocation(long maxAgeMs) {
    long oldestAllowed = (maxAgeMs == 0 ? 0 : System.currentTimeMillis() - maxAgeMs);
    Location lastKnown = null;
    // Some Android versions can hide GPS and NETWORK location providers from
    // apps: must check availability despite manifest requiring them :-(
    if (locator.getProvider(LocationManager.GPS_PROVIDER) != null) {
      lastKnown = locator.getLastKnownLocation(LocationManager.GPS_PROVIDER);
    }
    // Try to use network location if GPS wasn't available, or it was too old
    if ((lastKnown == null || lastKnown.getTime() < oldestAllowed) &&
        locator.getProvider(LocationManager.NETWORK_PROVIDER) != null) {
      lastKnown = locator.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
      if (lastKnown != null && lastKnown.getTime() < oldestAllowed) {
        lastKnown = null;
      }
    }
    return lastKnown;
  }

  private void loadKmlFile() {
    try {
      selectedMap = findAnyMap();
    } catch (Exception ex) {
      Log.w(LOG_TAG, "Failed to load kml file", ex);
    }
  }

  private KmlFolder findAnyMap() throws IOException, XmlPullParserException {
    if (selectedMap != null) {
      return selectedMap;
    }

    Iterable<KmlInfo> kmlFiles = KmlFinder.findKmlFiles(FileUtil.getDataDirectory());
    KmlInfo data = kmlFiles.iterator().next();
    KmlParser parser = new KmlParser();
    Iterable<KmlFeature> features = parser.readFile(data.getKmlReader());
    // Find first map in the KML file
    KmlFolder mapFolder = null;
    List<KmlFeature> placemarks = new ArrayList<KmlFeature>();
    GroundOverlay map = null;
    for (KmlFeature feature : features) {
      if (feature instanceof Placemark) {
        feature.setKmlInfo(data);
        placemarks.add(feature);
        continue;
      }
      if (map != null) {
        continue;
      }
      if (feature instanceof GroundOverlay) {
        map = (GroundOverlay) feature;
      } else if (feature instanceof KmlFolder) {
        // Scan folder for a possible map
        KmlFolder folder = (KmlFolder) feature;
        for (KmlFeature folderFeature : folder.getFeatures()) {
          if (folderFeature instanceof GroundOverlay) {
            map = (GroundOverlay) folderFeature;
            mapFolder = folder;
            break;
          }
        }
      }
    }
    KmlFolder result = null;
    if (map != null) {
      map.setKmlInfo(data);
      result = new KmlFolder();
      result.setKmlInfo(data);
      result.addFeature(map);
      result.addFeatures(placemarks);
      if (mapFolder != null) {
        result.setName(mapFolder.getName());
        result.setDescription(mapFolder.getDescription());
        for (KmlFeature feature : mapFolder.getFeatures()) {
          if (feature instanceof Placemark) {
            feature.setKmlInfo(data);
            result.addFeature(feature);
          }
        }
      }
    }
    return result;
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
      super.onSensorChanged(event);
      if (isQuitting) {
        return;
      }
      locationLayer.setHeading(compassHeading);
      if (detailsDisplay.isShown()) {
        detailsDisplay.setHeading(locationLayer.getHeading());
      }
    }
  };
}
