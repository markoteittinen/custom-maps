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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import com.custommapsapp.android.create.MapEditor;
import com.custommapsapp.android.kml.GroundOverlay;
import com.custommapsapp.android.kml.KmlFolder;
import com.custommapsapp.android.language.Linguist;
import com.custommapsapp.android.storage.EditPreferences;
import com.custommapsapp.android.storage.PreferenceStore;

/**
 * SelectMap allows user to select a map to be displayed, or to launch CreateMap activity through
 * the menu.
 *
 * @author Marko Teittinen
 */
public class SelectMap extends AppCompatActivity
    implements PermissionFragment.PermissionResultCallback {
  private static final String LOG_TAG = "SelectMap";

  public static final String EXTRA_PREFIX = "com.custommapsapp.android";
  public static final String LAST_LOCATION = EXTRA_PREFIX + ".LastLocation";
  public static final String LOCAL_FILE = EXTRA_PREFIX + ".LocalFile";
  public static final String SELECTED_MAP = EXTRA_PREFIX + ".SelectedMap";
  public static final String AUTO_SELECT = EXTRA_PREFIX + ".AutoSelect";

  private static final String PERMISSION_TAG = "com.custommapsapp.android.PermissionFragmentTag";

  // Option menu and activity result constants
  private static final int MENU_CREATE_MAP = 1;
  private static final int MENU_EXPORT_ALL = 2;
  private static final int MENU_PREFERENCES = 3;
  private static final int CREATE_MAP = 1;
  private static final int EDIT_PREFERENCES = 2;
  // Context (item) menu constants
  private static final int ITEM_SELECT_MAP = 1;
  private static final int ITEM_MODIFY_MAP = 2;
  private static final int ITEM_SEND_MAP = 3;
  private static final int ITEM_DELETE_MAP = 4;
  private static final int ITEM_CANNOT_MODIFY = 5;

  private static final long OLDEST_OK_LOCATION_MS = 60 * 60 * 1000; // 1 hour

  private ListView mapList;
  private TextView noMapsFoundMessage;

  private LocationManager locator;
  private LocationTracker locationTracker;
  private boolean autoSelectRequested;
  private String localPathRequest;

  private Linguist linguist;
  private boolean updateMenuItems = false;

  private MapCatalog mapCatalog;
  private HelpDialogManager helpDialogManager;

  private ExecutorService backgroundExecutor;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.selectmap);
    linguist = ((CustomMapsApp) getApplication()).getLinguist();
    linguist.translateView(findViewById(R.id.root_view));

    mapList = findViewById(R.id.map_list);
    setSupportActionBar(findViewById(R.id.toolbar));

    autoSelectRequested = getIntent().getBooleanExtra(AUTO_SELECT, false);
    localPathRequest = getIntent().getStringExtra(LOCAL_FILE);

    mapList.setAdapter(new MapListAdapter(this, Collections.emptyList()));
    mapList.setOnItemClickListener(this::onListItemClick);
    noMapsFoundMessage = findViewById(R.id.empty_list);
    noMapsFoundMessage.setText("");
    mapList.setEmptyView(noMapsFoundMessage);
    // Create long press menu for menu items
    mapList.setOnCreateContextMenuListener(this::createMapContextMenu);

    helpDialogManager = new HelpDialogManager(
        this, HelpDialogManager.HELP_SELECT_MAP, linguist.getString(R.string.create_map_help));
    helpDialogManager.setWebLink(
        linguist.getString(R.string.create_map_help_link),
        "http://www.custommapsapp.com/sample-maps");
    if (savedInstanceState != null) {
      helpDialogManager.onRestoreInstanceState(savedInstanceState);
    }
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    helpDialogManager.onSaveInstanceState(outState);
    super.onSaveInstanceState(outState);
  }

  @Override
  protected void onStart() {
    super.onStart();
    backgroundExecutor = Executors.newSingleThreadExecutor();
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Update actionbar title to match current locale
    getSupportActionBar().setTitle(linguist.getString(R.string.select_map_name));

    // Verify that all necessary permissions are granted, request them if not
    if (!PermissionFragment.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
      PermissionFragment.requestPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
      return;
    }
    // All necessary permissions have been granted, start operation
    initializeLocation();
    initializeMapCatalog();
    refreshMapCatalog();

    helpDialogManager.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    helpDialogManager.onPause();
    if (locationTracker != null) {
      locationTracker.setQuitting(true);
      locator.removeUpdates(locationTracker);
    }
    if (mapCatalog != null) {
      mapCatalog.clearCatalog();
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    backgroundExecutor.shutdown();
  }

  // --------------------------------------------------------------------------
  // Permission response methods (from PermissionFragment)

  @Override
  public void onPermissionResult(String permission, boolean isGranted) {
    if (!isGranted) {
      Log.w(CustomMaps.LOG_TAG, "Cannot continue without " + permission + " permission, exiting");
      System.exit(0);
      return;
    }
    Log.i(CustomMaps.LOG_TAG, "Permission " + permission + " was granted");
    // Permission request has completed, onResume() method is executed next
  }

  // --------------------------------------------------------------------------
  // Location methods

  /**
   * Initializes location related constructs in this Activity. Before this method is called, the
   * activity must make sure to have permission to access user's location.
   */
  @SuppressLint("MissingPermission")
  private void initializeLocation() {
    locator = (LocationManager) getSystemService(LOCATION_SERVICE);
    locationTracker = new LocationTracker(getApplicationContext());

    // Some systems don't allow access to location providers despite manifest.xml
    boolean hasGpsProvider = (locator.getProvider(LocationManager.GPS_PROVIDER) != null);
    boolean hasNetworkProvider = (locator.getProvider(LocationManager.NETWORK_PROVIDER) != null);
    // Use latest GPS location only if it is fresh enough
    Location last = null;
    if (hasGpsProvider) {
      last = locator.getLastKnownLocation(LocationManager.GPS_PROVIDER);
      if (last != null && isOlderThan(last.getTime(), OLDEST_OK_LOCATION_MS)) {
        last = null;
      }
    }
    // Use network location only if it is fresh enough and if GPS was not used
    if (last == null && hasNetworkProvider) {
      last = locator.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
      if (last != null && isOlderThan(last.getTime(), OLDEST_OK_LOCATION_MS)) {
        last = null;
      }
    }
    if (last != null) {
      locationTracker.onLocationChanged(last);
    }
    locationTracker.setQuitting(false);
    // Listen to location updates if they are available
    if (hasGpsProvider) {
      locator.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 1000, locationTracker);
    }
    if (hasNetworkProvider) {
      locator.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 1000, locationTracker);
    }
  }

  private boolean isOlderThan(long time, long ageInMillis) {
    return time < System.currentTimeMillis() - ageInMillis;
  }

  // --------------------------------------------------------------------------
  // Map selection related methods

  /** User selected map from list */
  protected void onListItemClick(AdapterView<?> parent, View view, int position, long id) {
    KmlFolder map = (KmlFolder) mapList.getAdapter().getItem(position);
    if (map.getFirstMap() != null) {
      returnMap(map);
    }
  }

  /**
   * Attempts to automatically select a map to be opened.
   *
   * @return KmlFolder that should be opened automatically without showing the map list to the user,
   *     or null if no map satisfies auto-selection criteria, and the map list should be displayed.
   */
  private KmlFolder autoSelectMap() {
    // If specific file was requested, open it
    KmlFolder result = openLocalFile(localPathRequest);
    if (result == null) {
      // No specific file was requested, or the file didn't contain a map
      // If the last used map contains user's location, select that one. Otherwise find the most
      // detailed map containing user's current location
      result = findBestMap();
    }
    if (result == null) {
      // No map has been auto-selected yet (none contain user's current location)
      // Attempt to load the last viewed map
      result = selectLastMap();
    }
    return result;
  }

  /**
   * Returns the KmlFolder map object located at given file path if it exists and can be
   * successfully loaded, or null otherwise.
   *
   * @param localPath full path to the file containing a map
   */
  private KmlFolder openLocalFile(String localPath) {
    if (localPath == null) {
      return null;
    }
    File localFile = new File(localPath);
    // Copy or move file to data directory if not already there
    if (!FileUtil.isInDataDirectory(localFile)) {
      File result = FileUtil.copyToDataDirectory(localFile);
      if (result != null) {
        localFile = result;
      }
    }
    // Attempt to parse the file and return the result (it is null in case of failure)
    return mapCatalog.parseLocalFile(localFile);
  }

  /**
   * Returns the most detailed map from the catalog that contains the user's current location. In
   * this case, "the most detailed" is determined by finding the map covering the smallest area.
   */
  private KmlFolder findBestMap() {
    Location current = locationTracker.getCurrentLocation(null);
    if (current == null) {
      // Current location is not known, best map cannot be determined
      return null;
    }
    // Find the map that contains the location and covers the smallest area
    // (likely most detailed)
    float longitude = (float) current.getLongitude();
    float latitude = (float) current.getLatitude();
    String lastUsedMap = PreferenceStore.instance(this).getLastUsedMap();
    KmlFolder selected = null;
    float minArea = Float.MAX_VALUE;
    for (KmlFolder mapHolder : mapCatalog.getMapsContainingPoint(longitude, latitude)) {
      GroundOverlay map = mapHolder.getFirstMap();
      // Open last used map if the user is still within the map area
      if (lastUsedMap != null && lastUsedMap.equals(map.getName())) {
        selected = mapHolder;
        break;
      }
      // Otherwise use the one with smallest area
      float area = map.computeAreaKm2();
      if (area < minArea) {
        selected = mapHolder;
        minArea = area;
      }
    }
    // Return the selected map or null if no suitable map was found
    return selected;
  }

  /**
   * Returns the last viewed map if it is still available.
   */
  private KmlFolder selectLastMap() {
    String lastUsedMap = PreferenceStore.instance(this).getLastUsedMap();
    if (lastUsedMap == null) {
      return null;
    }
    // Find last used map
    for (KmlFolder mapHolder : mapCatalog.getAllMapsSortedByName()) {
      GroundOverlay map = mapHolder.getFirstMap();
      if (lastUsedMap.equals(map.getName())) {
        return mapHolder;
      }
    }
    return null;
  }

  // -------------------------------------------------------------------------------------
  // Action bar menu

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    menu.add(Menu.NONE, MENU_CREATE_MAP, Menu.NONE, linguist.getString(R.string.create_map))
        .setIcon(android.R.drawable.ic_menu_gallery);
    menu.add(Menu.NONE, MENU_EXPORT_ALL, Menu.NONE, linguist.getString(R.string.export_all_maps));
    menu.add(Menu.NONE, MENU_PREFERENCES, Menu.NONE, linguist.getString(R.string.settings))
        .setIcon(android.R.drawable.ic_menu_preferences);
    helpDialogManager.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    // Update the menu titles to match currently selected language if necessary
    if (updateMenuItems) {
      menu.findItem(MENU_CREATE_MAP).setTitle(linguist.getString(R.string.create_map));
      menu.findItem(MENU_PREFERENCES).setTitle(linguist.getString(R.string.settings));
      helpDialogManager.onPrepareOptionsMenu(menu);
      updateMenuItems = false;
    }
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case MENU_CREATE_MAP:
        // Invoke an activity to create maps from bitmaps
        Intent createMap = new Intent(this, MapEditor.class);
        startActivityForResult(createMap, CREATE_MAP);
        return true;
      case MENU_EXPORT_ALL:
        // Export all maps by sharing them
        shareAllMaps();
        break;
      case MENU_PREFERENCES:
        // Invoke preferences activity
        Intent preferences = new Intent(this, EditPreferences.class);
        startActivityForResult(preferences, EDIT_PREFERENCES);
        break;
      default:
        helpDialogManager.onOptionsItemSelected(item);
    }
    return false;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode != RESULT_OK) {
      return;
    }
    switch (requestCode) {
      case CREATE_MAP:
        // New map created, refresh catalog, and include just edited file
        String mapFilename = data.getStringExtra(MapEditor.KMZ_FILE);
        if (mapFilename != null && mapCatalog != null) {
          File file = new File(mapFilename);
          mapCatalog.addCreatedFile(file.getName());
          updateMapList();
        } else {
          refreshMapCatalog();
        }
        break;
      case EDIT_PREFERENCES:
        // Reload UI and update menu items just in case the language was changed
        reloadUI();
        updateMenuItems = true;
        break;
    }
  }

  private void reloadUI() {
    // Make sure the linguist is up-to-date
    linguist = ((CustomMapsApp) getApplication()).getLinguist();
    linguist.translateView(findViewById(R.id.root_view));

    // Update help dialog manager
    helpDialogManager.updateHelpText(linguist.getString(R.string.create_map_help));
    helpDialogManager.setWebLink(
        linguist.getString(R.string.create_map_help_link),
        "http://www.custommapsapp.com/sample-maps");
  }

  /** Export all maps by using "send multiple" action. */
  private void shareAllMaps() {
    mapCatalog.refreshCatalog();
    FileUtil.exportMaps(this, mapCatalog.getAllMapsSortedByName());
  }

  // -------------------------------------------------------------------------------------
  // Dialog management methods

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

  // -------------------------------------------------------------------------------------
  // Context menu and its actions

  private void createMapContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
    Object item = mapList.getAdapter().getItem(adapterMenuInfo.position);
    if (!(item instanceof KmlFolder) ||
        !mapList.getAdapter().isEnabled(adapterMenuInfo.position)) {
      // Map category, no menu available
      menu.clear();
      return;
    }

    KmlFolder mapHolder = (KmlFolder) item;
    GroundOverlay map = mapHolder.getFirstMap();
    if (map == null) {
      menu.clear();
      return;
    }

    menu.setHeaderTitle(map.getName());
    menu.add(Menu.NONE, ITEM_SELECT_MAP, Menu.NONE, linguist.getString(R.string.select_map));
    // check if the map is part of a set
    boolean isSoloMap = !mapCatalog.isPartOfMapSet(mapHolder);
    boolean containsPlacemarks = mapHolder.hasPlacemarks();
    if (isSoloMap && !containsPlacemarks) {
      // Files containing single maps are easy to edit and delete
      // TODO: make these menu items available for map sets
      menu.add(Menu.NONE, ITEM_MODIFY_MAP, Menu.NONE, linguist.getString(R.string.modify_map));
      menu.add(Menu.NONE, ITEM_SEND_MAP, Menu.NONE, linguist.getString(R.string.share_map));
      menu.add(Menu.NONE, ITEM_DELETE_MAP, Menu.NONE, linguist.getString(R.string.delete_map));
    } else {
      menu.add(Menu.NONE, ITEM_SEND_MAP, Menu.NONE, linguist.getString(R.string.share_map));
      if (!isSoloMap) {
        // Part of map set, prevent editing and deletion. Saving maps does not
        // support map sets, and deletion would remove all maps in the set
        displayMessage(linguist.getString(R.string.select_cannot_modify_map_set), true);
      } else {
        // Single map that contains placemarks, delete is allowed, modify not
        menu.add(Menu.NONE, ITEM_DELETE_MAP, Menu.NONE, linguist.getString(R.string.delete_map));
        // TODO: remove modification limitation (i.e. implement placemark saving)
        displayMessage(linguist.getString(R.string.select_cannot_modify_placemarks), true);
      }
    }
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
    int position = menuInfo.position;
    KmlFolder map = (KmlFolder) mapList.getAdapter().getItem(position);
    if (map.getFirstMap() != null) {
      switch (item.getItemId()) {
        case ITEM_SELECT_MAP:
          returnMap(map);
          break;
        case ITEM_MODIFY_MAP:
          modifyMap(map);
          break;
        case ITEM_SEND_MAP:
          shareMap(map);
          break;
        case ITEM_DELETE_MAP:
          confirmDeleteMap(map);
          break;
        case ITEM_CANNOT_MODIFY:
          //$FALL-THROUGH$
        default:
          return super.onContextItemSelected(item);
      }
    }
    super.onContextItemSelected(item);
    return true;
  }

  private void displayMessage(final String message, final boolean showLong) {
    runOnUiThread(() -> {
      int duration = (showLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
      Toast.makeText(SelectMap.this, message, duration).show();
    });
  }

  private void modifyMap(KmlFolder map) {
    Intent editMap = new Intent(this, MapEditor.class);
    editMap.putExtra(MapEditor.KMZ_FILE, map.getKmlInfo().getFile().getAbsolutePath());
    editMap.putExtra(MapEditor.KML_FOLDER, map);
    startActivityForResult(editMap, CREATE_MAP);
  }

  private void shareMap(KmlFolder map) {
    if (!FileUtil.shareMap(this, map)) {
      displayMessage(linguist.getString(R.string.no_map_sharing_apps), true);
    }
  }

  private void confirmDeleteMap(final KmlFolder map) {
    // Delete file
    DialogInterface.OnClickListener buttonHandler = (dialog, which) -> {
      if (which == AlertDialog.BUTTON_POSITIVE) {
        deleteMap(map);
      }
    };
    // Confirm deletion
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle(linguist.getString(R.string.delete_map_dialog_title))
        .setMessage(linguist.getString(R.string.delete_map_dialog, map.getName()))
        .setNegativeButton(linguist.getString(R.string.delete_map_dialog_negative), buttonHandler)
        .setPositiveButton(linguist.getString(R.string.delete_map_dialog_positive), buttonHandler)
        .create();
    dialog.show();
  }

  private void deleteMap(KmlFolder map) {
    File markup = map.getKmlInfo().getFile();
    File image = null;
    if (markup.getAbsolutePath().toLowerCase().endsWith(".kml")) {
      GroundOverlay mapData = map.getFirstMap();
      if (mapData != null) {
        image = new File(markup.getParentFile(), mapData.getImage());
      }
    }
    if (!markup.delete()) {
      Log.w(CustomMaps.LOG_TAG, "Failed to delete map markup file: " + markup.getAbsolutePath());
      displayMessage(linguist.getString(R.string.deleting_map_failed, map.getName()), true);
    } else {
      // Markup file deleted successfully, delete image if necessary
      if (image != null && !image.delete()) {
        // Log image deletion failure only, map will be gone from UI
        Log.w(CustomMaps.LOG_TAG, "Failed to delete map image file: " + image.getAbsolutePath());
      }
    }
    refreshMapCatalog();
  }

  private void returnMap(KmlFolder mapHolder) {
    GroundOverlay map = mapHolder.getFirstMap();
    PreferenceStore.instance(this).setLastUsedMap(map.getName());
    getIntent().putExtra(SELECTED_MAP, mapHolder);
    setResult(RESULT_OK, getIntent());
    locator.removeUpdates(locationTracker);
    mapCatalog.clearCatalog();
    runOnUiThread(this::finish);
  }

  // -------------------------------------------------------------------------------------
  // MapCatalog management

  /**
   * Confirms that Custom Maps data directory exists, or if it doesn't exist creates it. Also
   * creates a new MapCatalog object pointing to the data directory.
   */
  void initializeMapCatalog() {
    PreferenceStore prefs = PreferenceStore.instance(this);
    if (prefs.isUsingLegacyStorage()) {
      @SuppressWarnings("deprecation")
      File dataDir = FileUtil.getLegacyMapDirectory();
      if (!dataDir.exists()) {
        Log.e(CustomMaps.LOG_TAG, "Legacy data dir does not exist: " + dataDir.getAbsolutePath());
      }
      mapCatalog = new MapCatalog(dataDir);
    } else {
      // Initialize mapCatalog for new storage model
      Uri mapStorageDir = prefs.getMapStorageDirectory();
      if (mapStorageDir == null) {
        // Use app's internal storage folder
        mapCatalog = new MapCatalog(FileUtil.getInternalMapDirectory());
      } else {
        // Use MapCatalog with new storage API
        mapCatalog = new MapCatalog(mapStorageDir);
      }
    }
  }

  /**
   * Starts refreshing the contents of the map catalog in a background thread, and updates the
   * visible map list when the catalog has been updated.
   */
  void refreshMapCatalog() {
    // TODO: Consider displaying a wait spinner while loading map list
    // Clear map list (and its empty message) while refreshing the list contents
    noMapsFoundMessage.setText("");
    mapList.setAdapter(new MapListAdapter(this, Collections.emptyList()));
    // Migrate maps to internal storage, if they are in legacy storage
    if (PreferenceStore.instance(this).isUsingLegacyStorage()) {
      noMapsFoundMessage.setText(linguist.getString(R.string.transferring_maps));
      backgroundExecutor.submit(this::migrateMapCatalog);
      // After migration completes, it will trigger refreshMapCatalogSync()
    } else {
      // Since maps for catalog are read from disk, update the catalog in a bg thread
      backgroundExecutor.submit(this::refreshMapCatalogSync);
    }
  }

  void migrateMapCatalog() {
    long startTimeMs = System.currentTimeMillis();
    // Copy all kmz files (i.e. map files) from sdcard/CustomMaps to internal directory
    @SuppressWarnings("deprecation")
    File legacyDataDir = FileUtil.getLegacyMapDirectory();
    File[] legacyFiles = legacyDataDir.listFiles();
    if (legacyFiles != null) {
      for (File mapFile : legacyFiles) {
        if (mapFile.getName().endsWith(".kmz")) {
          if (FileUtil.copyToDataDirectory(mapFile) != null) {
            Log.d(LOG_TAG, "Copy successful: " + mapFile.getName());
          } else {
            Log.w(LOG_TAG, "Failed to copy: " + mapFile.getName());
          }
        }
      }
    }
    // Update preferences to indicate files have been moved to internal storage
    PreferenceStore.instance(this).setStopUsingLegacyStorage();
    autoSelectRequested = false;
    // Keep the migration message on the screen at least 3 seconds
    long untilThreeSecMs = startTimeMs + 3000 - System.currentTimeMillis();
    if (untilThreeSecMs > 0) {
      try {
        Thread.sleep(untilThreeSecMs);
      } catch (InterruptedException ex) {
        // Wait interrupted, just continue
      }
    }
    // Clear the migration message and refresh the map catalog
    runOnUiThread(() -> noMapsFoundMessage.setText(""));
    refreshMapCatalogSync();
  }

  // This method accesses the disk, so it should never be called in UI thread
  void refreshMapCatalogSync() {
    // Update default map name in case language has changed since last refresh
    MapCatalog.setDefaultMapName(linguist.getString(R.string.unnamed_map));
    // Refresh catalog contents and group maps by distance if possible
    mapCatalog.refreshCatalog();

    // If auto-select was requested, attempt to auto-select the map now
    if (autoSelectRequested) {
      // Attempt to auto-select a map
      KmlFolder autoSelectedMap = autoSelectMap();
      if (autoSelectedMap != null) {
        // Display the auto-selected map to the user without showing the map list
        returnMap(autoSelectedMap);
        return;
      }
      // Auto-selection was not successful, no need to attempt again
      autoSelectRequested = false;
    }

    // Update the visible map list in UI thread
    runOnUiThread(this::updateMapList);
  }

  // This method updates the UI, so it must be run in the UI thread
  void updateMapList() {
    // Update the map list and the message displayed if the list has no entries
    ListAdapter mapListAdapter = createMapListAdapter();
    mapList.setAdapter(mapListAdapter);
    noMapsFoundMessage.setText(linguist.getString(R.string.no_maps_found_on_sd));
  }

  private ListAdapter createMapListAdapter() {
    // Verify the maps are sorted alphabetically
    Iterable<KmlFolder> allMaps = mapCatalog.getAllMapsSortedByName();
    // If locationTracker is available and current location is known, group maps by distance
    if (locationTracker != null) {
      Location current = locationTracker.getCurrentLocation(null);
      if (current != null) {
        // Group maps by distance to current location
        float longitude = (float) current.getLongitude();
        float latitude = (float) current.getLatitude();
        mapCatalog.groupMapsByDistance(longitude, latitude);
        // Create a list adapter that displays maps in groups by distance
        GroupedMapAdapter listAdapter = new GroupedMapAdapter();
        listAdapter.setLocalMaps(mapCatalog.getLocalMaps());
        listAdapter.setNearMaps(mapCatalog.getNearMaps());
        listAdapter.setFarMaps(mapCatalog.getFarMaps());
        return listAdapter;
      }
    }
    // User's location is not available, list all maps together
    return new MapListAdapter(this, allMaps);
  }

  // -------------------------------------------------------------------------------------

  private class MapListAdapter extends ArrayAdapter<KmlFolder> {
    public MapListAdapter(Context context, Iterable<KmlFolder> maps) {
      super(context, 0);
      for (KmlFolder map : maps) {
        add(map);
      }
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
      if (convertView == null) {
        convertView = getLayoutInflater().inflate(R.layout.listrow, parent, false);
      }
      TextView title = convertView.findViewById(R.id.titleField);
      TextView description = convertView.findViewById(R.id.descriptionField);

      KmlFolder mapHolder = getItem(position);
      GroundOverlay map = mapHolder.getFirstMap();
      if (map != null) {
        title.setText(map.getName());
        description.setText(map.getDescription());
      }
      return convertView;
    }
  }

  private class GroupedMapAdapter extends BaseAdapter {
    private List<KmlFolder> localMaps = new ArrayList<>();
    private List<KmlFolder> nearMaps = new ArrayList<>();
    private List<KmlFolder> farMaps = new ArrayList<>();

    void setLocalMaps(Iterable<KmlFolder> localMaps) {
      setMaps(this.localMaps, localMaps);
    }

    void setNearMaps(Iterable<KmlFolder> nearMaps) {
      setMaps(this.nearMaps, nearMaps);
    }

    void setFarMaps(Iterable<KmlFolder> farMaps) {
      setMaps(this.farMaps, farMaps);
    }

    @Override
    public boolean areAllItemsEnabled() {
      return false;
    }

    @Override
    public int getViewTypeCount() {
      return 2;
    }

    @Override
    public int getItemViewType(int position) {
      return (getItem(position) instanceof String ? 0 : 1);
    }

    @Override
    public boolean hasStableIds() {
      return false;
    }

    @Override
    public boolean isEnabled(int position) {
      return !(getItem(position) instanceof String);
    }

    private void setMaps(List<KmlFolder> maps, Iterable<KmlFolder> newMaps) {
      maps.clear();
      for (KmlFolder map : newMaps) {
        maps.add(map);
      }
      notifyDataSetChanged();
    }

    @Override
    public int getCount() {
      return menuItemsIn(localMaps) + menuItemsIn(nearMaps) + menuItemsIn(farMaps);
    }

    private int menuItemsIn(List<KmlFolder> mapList) {
      return (mapList.isEmpty() ? 0 : mapList.size() + 1);
    }

    @Override
    public Object getItem(int position) {
      int originalPosition = position;
      if (position < menuItemsIn(localMaps)) {
        return getItem(localMaps, linguist.getString(R.string.local_maps), position);
      }
      position -= menuItemsIn(localMaps);
      if (position < menuItemsIn(nearMaps)) {
        return getItem(nearMaps, linguist.getString(R.string.nearby_maps), position);
      }
      position -= menuItemsIn(nearMaps);
      if (position < menuItemsIn(farMaps)) {
        String title = linguist.getString(position < originalPosition ?
            R.string.other_maps : R.string.all_maps);
        return getItem(farMaps, title, position);
      }
      return null;
    }

    private Object getItem(List<KmlFolder> mapList, String title, int position) {
      if (position == 0) {
        return title;
      }
      return mapList.get(position - 1);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      Object item = getItem(position);
      TextView title;
      TextView description = null;
      String titleText = null;
      String descriptionText = null;
      if (item instanceof KmlFolder) {
        // Map item in the list
        if (convertView == null) {
          convertView = getLayoutInflater().inflate(R.layout.listrow, parent, false);
        }
        title = convertView.findViewById(R.id.titleField);
        description = convertView.findViewById(R.id.descriptionField);

        KmlFolder mapHolder = (KmlFolder) item;
        GroundOverlay map = mapHolder.getFirstMap();
        if (map != null) {
          titleText = map.getName();
          descriptionText = map.getDescription();
        }
      } else {
        // Section header in the list
        if (convertView == null) {
          convertView = getLayoutInflater().inflate(R.layout.listheader, parent, false);
        }
        title = (TextView) convertView;
        titleText = (String) item;
      }

      title.setText(titleText);
      if (description != null) {
        description.setText(descriptionText);
      }
      return convertView;
    }
  }
}
