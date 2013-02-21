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

import com.custommapsapp.android.create.MapEditor;
import com.custommapsapp.android.kml.GroundOverlay;
import com.custommapsapp.android.kml.KmlFolder;
import com.custommapsapp.android.storage.EditPreferences;
import com.custommapsapp.android.storage.PreferenceStore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * SelectMap allows user to select a map to be displayed, or to launch
 * CreateMap activity through the menu.
 *
 * @author Marko Teittinen
 */
public class SelectMap extends ListActivity {
  public static final String EXTRA_PREFIX = "com.custommapsapp.android";
  public static final String LAST_LOCATION = EXTRA_PREFIX + ".LastLocation";
  public static final String LOCAL_FILE = EXTRA_PREFIX + ".LocalFile";
  public static final String SELECTED_MAP = EXTRA_PREFIX + ".SelectedMap";
  public static final String AUTO_SELECT = EXTRA_PREFIX + ".AutoSelect";

  // Option menu and activity result constants
  private static final int MENU_CREATE_MAP = 1;
  private static final int MENU_PREFERENCES = 2;
  private static final int CREATE_MAP = 1;
  // Context (item) menu constants
  private static final int ITEM_SELECT_MAP = 1;
  private static final int ITEM_MODIFY_MAP = 2;
  private static final int ITEM_SEND_MAP = 3;
  private static final int ITEM_DELETE_MAP = 4;
  private static final int ITEM_CANNOT_MODIFY = 5;

  private static final long OLDEST_OK_LOCATION_MS = 60 * 60 * 1000; // 1 hour

  private LocationManager locator;
  private LocationTracker locationTracker;
  private boolean autoSelectRequested;
  private String localPathRequest;

  private MapCatalog mapCatalog;
  private HelpDialogManager helpDialogManager;

  private Runnable refreshCatalog = new Runnable() {
    @Override
    public void run() {
      mapCatalog.refreshCatalog();
      groupMapsList();
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    File dataDir = FileUtil.getDataDirectory();
    if (!dataDir.exists()) {
      Log.e(CustomMaps.LOG_TAG, "Creation of data dir failed. Name: " + dataDir.getAbsolutePath());
    }
    // Update default map name in case language has changed
    MapCatalog.setDefaultMapName(getString(R.string.unnamed_map));
    mapCatalog = new MapCatalog(dataDir);
    boolean ptSizeFixNeeded = PtSizeFixer.isFixNeeded(this);
    setContentView(R.layout.selectmap);
    if (ptSizeFixNeeded) {
      PtSizeFixer.fixView(getListView().getRootView());
    }

    autoSelectRequested = getIntent().getBooleanExtra(AUTO_SELECT, false);
    localPathRequest = getIntent().getStringExtra(LOCAL_FILE);

    locator = (LocationManager) getSystemService(LOCATION_SERVICE);
    locationTracker = new LocationTracker(getApplicationContext());

    setListAdapter(new MapListAdapter(this, mapCatalog.getAllMapsSortedByName()));
    // Create long press menu for menu items
    ListView mapList = getListView();
    mapList.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
      @Override
      public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        createMapContextMenu(menu, v, menuInfo);
      }
    });

    helpDialogManager = new HelpDialogManager(this, HelpDialogManager.HELP_SELECT_MAP,
        getString(R.string.create_map_help));
    helpDialogManager.addWebLink(getString(R.string.create_map_help_link),
        "http://www.custommapsapp.com/sample-maps");
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    helpDialogManager.onSaveInstanceState(outState);
    super.onSaveInstanceState(outState);
  }

  @Override
  protected void onRestoreInstanceState(Bundle state) {
    super.onRestoreInstanceState(state);
    helpDialogManager.onRestoreInstanceState(state);
  }

  @Override
  protected void onResume() {
    super.onResume();
    mapCatalog.refreshCatalog();
    helpDialogManager.onResume();
    // Use latest GPS location only if it is fresh enough
    Location last = locator.getLastKnownLocation(LocationManager.GPS_PROVIDER);
    if (last != null && isNewerThan(last.getTime(), OLDEST_OK_LOCATION_MS)) {
      locationTracker.onLocationChanged(last);
    }
    // Use latest network location only if it is fresh enough
    last = locator.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
    if (last != null && isNewerThan(last.getTime(), OLDEST_OK_LOCATION_MS)) {
      locationTracker.onLocationChanged(last);
    }
    locationTracker.setQuitting(false);
    // Avoid crashing on some systems without GPS_PROVIDER
    if (locator.getProvider(LocationManager.GPS_PROVIDER) != null) {
      locator.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationTracker);
    }
    // Avoid crashing on some systems without NETWORK_PROVIDER
    if (locator.getProvider(LocationManager.NETWORK_PROVIDER) != null) {
      locator.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, locationTracker);
    }

    if (autoSelectRequested) {
      // If specific file was requested, open it
      if (localPathRequest != null && openLocalFile(localPathRequest)) {
        return;
      }
      // Find most accurate map containing user's current location
      if (findBestMap()) {
        return;
      }
      // If location is not known, attempt to open last used map
      Location current = locationTracker.getCurrentLocation(null);
      if (current == null && selectLastMap()) {
        return;
      }
      // Auto selection failed, fall-through to show list of maps
    }
    groupMapsList();
  }

  @Override
  protected void onPause() {
    super.onPause();
    helpDialogManager.onPause();
    locationTracker.setQuitting(true);
    locator.removeUpdates(locationTracker);
    mapCatalog.clearCatalog();
  }

  private boolean isNewerThan(long time, long ageInMillis) {
    return System.currentTimeMillis() - ageInMillis < time;
  }

  private boolean openLocalFile(String localPath) {
    if (localPath == null) {
      return false;
    }
    File localFile = new File(localPath);
    // Copy or move file to data directory if not already there
    if (!FileUtil.isInDataDirectory(localFile)) {
      File result = null;
      if (FileUtil.isDownloadFile(localFile)) {
        result = FileUtil.moveToDataDirectory(localFile);
      } else {
        result = FileUtil.copyToDataDirectory(localFile);
      }
      if (result != null) {
        localFile = result;
      }
    }

    KmlFolder selected = mapCatalog.parseLocalFile(localFile);
    if (selected != null) {
      returnMap(selected);
      return true;
    }
    return false;
  }

  private boolean findBestMap() {
    Location current = locationTracker.getCurrentLocation(null);
    if (current == null) {
      // Current location is not known, best map cannot be determined
      return false;
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

    // If a suitable map was found, return it to intent caller
    if (selected != null) {
      returnMap(selected);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Attempts to find the last used map and return it from activity.
   *
   * @return {@code true} if the map was found and returned
   */
  private boolean selectLastMap() {
    String lastUsedMap = PreferenceStore.instance(this).getLastUsedMap();
    if (lastUsedMap == null) {
      return false;
    }
    // Find last used map
    for (KmlFolder mapHolder : mapCatalog.getAllMapsSortedByName()) {
      GroundOverlay map = mapHolder.getFirstMap();
      if (lastUsedMap.equals(map.getName())) {
        returnMap(mapHolder);
        return true;
      }
    }
    return false;
  }

  private void groupMapsList() {
    Location current = locationTracker.getCurrentLocation(null);
    if (current == null) {
      return;
    }
    // Verify the maps are sorted alphabetically
    mapCatalog.getAllMapsSortedByName();
    // Group maps by current location
    float longitude = (float) current.getLongitude();
    float latitude = (float) current.getLatitude();
    mapCatalog.groupMapsByDistance(longitude, latitude);
    GroupedMapAdapter listAdapter = new GroupedMapAdapter();
    listAdapter.setLocalMaps(mapCatalog.getLocalMaps());
    listAdapter.setNearMaps(mapCatalog.getNearMaps());
    listAdapter.setFarMaps(mapCatalog.getFarMaps());
    setListAdapter(listAdapter);
  }

  private void displayMessage(final String message, final boolean showLong) {
    Runnable postMessage = new Runnable() {
      @Override
      public void run() {
        int duration = (showLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        Toast.makeText(SelectMap.this, message, duration).show();
      }
    };
    getListView().post(postMessage);
  }

  // -------------------------------------------------------------------------------------
  // Menus

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    menu.add(Menu.NONE, MENU_CREATE_MAP, Menu.NONE, R.string.create_map)
        .setIcon(android.R.drawable.ic_menu_gallery);
    menu.add(Menu.NONE, MENU_PREFERENCES, Menu.NONE, R.string.settings)
        .setIcon(android.R.drawable.ic_menu_preferences);
    helpDialogManager.onCreateOptionsMenu(menu);
    return true;
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
      case MENU_PREFERENCES:
        // Invoke preferences activity
        Intent preferences = new Intent(this, EditPreferences.class);
        startActivity(preferences);
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
        if (mapFilename != null) {
          File file = new File(mapFilename);
          mapCatalog.addCreatedFile(file.getName());
        }
        getListView().post(refreshCatalog);
        break;
    }
  }

  // -------------------------------------------------------------------------------------
  // Dialog management methods

  @Override
  protected Dialog onCreateDialog(int id) {
    return helpDialogManager.onCreateDialog(id);
  }

  @Override
  protected void onPrepareDialog(int id, Dialog dialog) {
    helpDialogManager.onPrepareDialog(id, dialog);
  }

  // -------------------------------------------------------------------------------------

  private void createMapContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
    Object item = getListAdapter().getItem(adapterMenuInfo.position);
    if (!(item instanceof KmlFolder) ||
        !getListAdapter().isEnabled(adapterMenuInfo.position))  {
      // Map category, no menu available
      menu.clear();
      return;
    } else {
      KmlFolder mapHolder = (KmlFolder) item;
      GroundOverlay map = mapHolder.getFirstMap();
      if (map == null) {
        menu.clear();
        return;
      }
      menu.setHeaderTitle(map.getName());
      menu.add(Menu.NONE, ITEM_SELECT_MAP, Menu.NONE, R.string.select_map);
      // check if the map is part of a set
      boolean isSoloMap = !mapCatalog.isPartOfMapSet(mapHolder);
      boolean containsPlacemarks = mapHolder.hasPlacemarks();
      if (isSoloMap && !containsPlacemarks) {
        // Files containing single maps are easy to edit and delete
        // TODO: make these menu items available for map sets
        menu.add(Menu.NONE, ITEM_MODIFY_MAP, Menu.NONE, R.string.modify_map);
        menu.add(Menu.NONE, ITEM_SEND_MAP, Menu.NONE, R.string.share_map);
        menu.add(Menu.NONE, ITEM_DELETE_MAP, Menu.NONE, R.string.delete_map);
      } else {
        menu.add(Menu.NONE, ITEM_SEND_MAP, Menu.NONE, R.string.share_map);
        if (!isSoloMap) {
          // Part of map set, prevent editing and deletion. Saving maps does not
          // support map sets, and deletion would remove all maps in the set
          displayMessage(getString(R.string.select_cannot_modify_map_set), true);
        } else {
          // Single map that contains placemarks, delete is allowed, modify not
          menu.add(Menu.NONE, ITEM_DELETE_MAP, Menu.NONE, R.string.delete_map);
          // TODO: remove modification limitation (i.e. implement placemark saving)
          displayMessage(getString(R.string.select_cannot_modify_placemarks), true);
        }
      }
    }
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
    int position = menuInfo.position;
    KmlFolder map = (KmlFolder) getListAdapter().getItem(position);
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

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    super.onListItemClick(l, v, position, id);
    KmlFolder map = (KmlFolder) getListAdapter().getItem(position);
    if (map.getFirstMap() != null) {
      returnMap(map);
    }
  }

  private void modifyMap(KmlFolder map) {
    Intent editMap = new Intent(this, MapEditor.class);
    editMap.putExtra(MapEditor.KMZ_FILE, map.getKmlInfo().getFile().getAbsolutePath());
    editMap.putExtra(MapEditor.KML_FOLDER, map);
    startActivityForResult(editMap, CREATE_MAP);
  }

  private void shareMap(KmlFolder map) {
    if (!FileUtil.shareMap(this, map)) {
      displayMessage(getString(R.string.no_map_sharing_apps), true);
    }
  }

  private void confirmDeleteMap(final KmlFolder map) {
    // Delete file
    DialogInterface.OnClickListener buttonHandler = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
          deleteMap(map);
        }
      }
    };
    // Confirm deletion
    AlertDialog dialog = new AlertDialog.Builder(this)
        .setTitle(R.string.delete_map_dialog_title)
        .setMessage(getString(R.string.delete_map_dialog, map.getName()))
        .setNegativeButton(R.string.delete_map_dialog_negative, buttonHandler)
        .setPositiveButton(R.string.delete_map_dialog_positive, buttonHandler)
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
      displayMessage(getString(R.string.deleting_map_failed, map.getName()), true);
    } else {
      // Markup file deleted successfully, delete image if necessary
      if (image != null && !image.delete()) {
        // Log image deletion failure only, map will be gone from UI
        Log.w(CustomMaps.LOG_TAG, "Failed to delete map image file: " + image.getAbsolutePath());
      }
    }
    getListView().post(refreshCatalog);
  }

  private void returnMap(KmlFolder mapHolder) {
    GroundOverlay map = mapHolder.getFirstMap();
    PreferenceStore.instance(this).setLastUsedMap(map.getName());
    getIntent().putExtra(SELECTED_MAP, mapHolder);
    setResult(RESULT_OK, getIntent());
    locator.removeUpdates(locationTracker);
    mapCatalog.clearCatalog();
    finish();
  }

  // -------------------------------------------------------------------------------------

  private class MapListAdapter extends ArrayAdapter<KmlFolder> {
    public MapListAdapter(Context context, Iterable<KmlFolder> maps) {
      super(context, 0);
      for (KmlFolder map : maps) {
        add(map);
      }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        convertView = LayoutInflater.from(getContext()).inflate(R.layout.listrow, parent, false);
        if (PtSizeFixer.isFixNeeded((Activity) null)) {
          PtSizeFixer.fixView(convertView);
        }
      }
      TextView title = (TextView) convertView.findViewById(R.id.titleField);
      TextView description = (TextView) convertView.findViewById(R.id.descriptionField);

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
    private List<KmlFolder> localMaps = new ArrayList<KmlFolder>();
    private List<KmlFolder> nearMaps = new ArrayList<KmlFolder>();
    private List<KmlFolder> farMaps = new ArrayList<KmlFolder>();

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
        return getItem(localMaps, getString(R.string.local_maps), position);
      }
      position -= menuItemsIn(localMaps);
      if (position < menuItemsIn(nearMaps)) {
        return getItem(nearMaps, getString(R.string.nearby_maps), position);
      }
      position -= menuItemsIn(nearMaps);
      if (position < menuItemsIn(farMaps)) {
        String title = getString(position < originalPosition ?
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
      TextView title = null;
      TextView description = null;
      String titleText = null;
      String descriptionText = null;
      if (item instanceof KmlFolder) {
        // Map item in the list
        if (convertView == null) {
          convertView =
              LayoutInflater.from(SelectMap.this).inflate(R.layout.listrow, parent, false);
          if (PtSizeFixer.isFixNeeded((Activity) null)) {
            PtSizeFixer.fixView(convertView);
          }
        }
        title = (TextView) convertView.findViewById(R.id.titleField);
        description = (TextView) convertView.findViewById(R.id.descriptionField);

        KmlFolder mapHolder = (KmlFolder) item;
        GroundOverlay map = mapHolder.getFirstMap();
        if (map != null) {
          titleText = map.getName();
          descriptionText = map.getDescription();
        }
      } else {
        // Section header in the list
        if (convertView == null) {
          convertView =
              LayoutInflater.from(SelectMap.this).inflate(R.layout.listheader, parent, false);
          if (PtSizeFixer.isFixNeeded((Activity) null)) {
            PtSizeFixer.fixView(convertView);
          }
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
