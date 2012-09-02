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

import com.custommapsapp.android.CustomMaps;
import com.custommapsapp.android.FileUtil;
import com.custommapsapp.android.HelpDialogManager;
import com.custommapsapp.android.ImageHelper;
import com.custommapsapp.android.R;
import com.custommapsapp.android.kml.GroundOverlay;
import com.custommapsapp.android.kml.KmzFile;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * MapEditor manages editing of a map and its tiepoints.
 *
 * @author Marko Teittinen
 */
public class MapEditor extends Activity {
  private static final String EXTRA_PREFIX = "com.custommapsapp.android";
  private static final String TIEPOINT_INDEX = EXTRA_PREFIX + ".TiepointIndex";
  public static final String BITMAP_FILE = EXTRA_PREFIX + ".BitmapFile";
  public static final String KMZ_FILE = EXTRA_PREFIX + ".KmzFile";
  public static final String GROUND_OVERLAY = EXTRA_PREFIX + ".GroundOverlay";

  public static final int SNIPPET_SIZE = 150;

  // Sub-activity IDs
  private static final int CONVERT_PDF_FILE = 1;
  private static final int SELECT_IMAGE_FILE = 2;
  private static final int SELECT_IMAGE_POINT = 3;
  private static final int SELECT_GEO_LOCATION = 4;
  private static final int PREVIEW = 5;

  private static final int MENU_ADJUST_TIEPOINT = 1;
  private static final int MENU_DELETE_TIEPOINT = 2;

  private String bitmapFilename;
  private String kmzFilename;
  private GroundOverlay originalMap;

  private EditText nameField;
  private EditText descriptionField;
  private ListView tiePointsList;
  private Button addPointButton;
  private Button previewButton;
  private TiePointAdapter tiepointAdapter;
  private HelpDialogManager helpDialogManager;
  // when 'false', tiepoint selection will restore thumbnail orientation
  private boolean firstTiepoint;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.mapeditor);
    prepareUI();

    helpDialogManager = new HelpDialogManager(this, HelpDialogManager.HELP_MAP_EDITOR,
                                              getString(R.string.editor_help));

    if (savedInstanceState != null) {
      onRestoreInstanceState(savedInstanceState);
    }

    Intent intent = getIntent();
    if (bitmapFilename == null && kmzFilename == null) {
      if (intent.hasExtra(BITMAP_FILE)) {
        bitmapFilename = intent.getStringExtra(BITMAP_FILE);
      } else if (intent.hasExtra(KMZ_FILE)) {
        kmzFilename = intent.getStringExtra(KMZ_FILE);
        originalMap = (GroundOverlay) intent.getSerializableExtra(GROUND_OVERLAY);

        nameField.setText(originalMap.getName());
        descriptionField.setText(originalMap.getDescription());

        findTiePoints(originalMap);
        // If there was an error, this activity will quit
        if (this.isFinishing()) {
          return;
        }
      }
    }
    if (bitmapFilename == null && kmzFilename == null) {
      // Prevent from clearing "firstTime" flag for help dialog never shown
      helpDialogManager.clearFirstTime(false);
      launchSelectImageFileActivity();
    }
    firstTiepoint = true;
  }

  @Override
  protected void onResume() {
    helpDialogManager.onResume();
    super.onResume();
  }

  @Override
  protected void onPause() {
    helpDialogManager.onPause();
    if (isFinishing()) {
      // Release image resources held by tiepoints
      for (TiePoint tiepoint : tiepointAdapter.getAllTiePoints()) {
        tiepoint.releaseBitmap();
      }
      tiepointAdapter.clear();
    }
    super.onPause();
  }

  private static final String NAME = EXTRA_PREFIX + ".Name";
  private static final String DESCRIPTION = EXTRA_PREFIX + ".Description";
  private static final String TIEPOINTS = EXTRA_PREFIX + ".Tiepoints";

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(BITMAP_FILE, bitmapFilename);
    outState.putString(NAME, nameField.getText().toString());
    outState.putString(DESCRIPTION, descriptionField.getText().toString());
    outState.putParcelableArrayList(TIEPOINTS, tiepointAdapter.getAllTiePoints());
    outState.putString(KMZ_FILE, kmzFilename);
    if (originalMap != null) {
      outState.putSerializable(GROUND_OVERLAY, originalMap);
    }
    helpDialogManager.onSaveInstanceState(outState);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    bitmapFilename = savedInstanceState.getString(BITMAP_FILE);
    String name = savedInstanceState.getString(NAME);
    nameField.setText(name);
    String description = savedInstanceState.getString(DESCRIPTION);
    descriptionField.setText(description);

    kmzFilename = savedInstanceState.getString(KMZ_FILE);
    if (savedInstanceState.containsKey(GROUND_OVERLAY)) {
      originalMap = (GroundOverlay) savedInstanceState.getSerializable(GROUND_OVERLAY);
    }

    tiepointAdapter.clear();
    List<TiePoint> tiepoints = savedInstanceState.getParcelableArrayList(TIEPOINTS);
    if (tiepoints != null) {
      for (TiePoint tiepoint : tiepoints) {
        tiepointAdapter.add(tiepoint);
      }
    }
    helpDialogManager.onRestoreInstanceState(savedInstanceState);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    helpDialogManager.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    helpDialogManager.onOptionsItemSelected(item);
    return true;
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    return helpDialogManager.onCreateDialog(id);
  }

  @Override
  protected void onPrepareDialog(int id, Dialog dialog) {
    helpDialogManager.onPrepareDialog(id, dialog);
  }

  private void findTiePoints(GroundOverlay map) {
    FileUtil.verifyImageDir();
    unpackImage(map, FileUtil.TMP_IMAGE);
    bitmapFilename = FileUtil.TMP_IMAGE;

    Bitmap mapImage = ImageHelper.loadImage(FileUtil.TMP_IMAGE);
    if (mapImage == null) {
      Toast.makeText(this, R.string.editor_image_load_failed, Toast.LENGTH_LONG).show();
      setResult(RESULT_CANCELED);
      finish();
      return;
    }

    Iterable<GroundOverlay.Tiepoint> mapPoints = map.getTiepoints();
    if (!mapPoints.iterator().hasNext()) {
      // No tiepoints defined, use map corners
      List<GroundOverlay.Tiepoint> pointList = new ArrayList<GroundOverlay.Tiepoint>();
      if (map.hasCornerTiePoints()) {
        // Map is defined with LatLonQuad, use those points
        float[] geo = map.getNorthWestCornerLocation();
        GeoPoint geoPoint = toGeoPoint(geo[1], geo[0]);
        Point imagePoint = new Point(0, 0);
        pointList.add(new GroundOverlay.Tiepoint(geoPoint, imagePoint));

        geo = map.getNorthEastCornerLocation();
        geoPoint = toGeoPoint(geo[1], geo[0]);
        imagePoint = new Point(mapImage.getWidth(), 0);
        pointList.add(new GroundOverlay.Tiepoint(geoPoint, imagePoint));

        geo = map.getSouthEastCornerLocation();
        geoPoint = toGeoPoint(geo[1], geo[0]);
        imagePoint = new Point(mapImage.getWidth(), mapImage.getHeight());
        pointList.add(new GroundOverlay.Tiepoint(geoPoint, imagePoint));

        geo = map.getSouthWestCornerLocation();
        geoPoint = toGeoPoint(geo[1], geo[0]);
        imagePoint = new Point(0, mapImage.getHeight());
        pointList.add(new GroundOverlay.Tiepoint(geoPoint, imagePoint));
      } else {
        // Map has only image location and rotation - find image to geo mapping
        float[] imageCorners = new float[] { //
          0, 0, //
          mapImage.getWidth(), 0, //
          mapImage.getWidth(), mapImage.getHeight(), //
        };
        float[] geoCorners = new float[] { //
          map.getWest(), map.getNorth(), //
          map.getEast(), map.getNorth(), //
          map.getEast(), map.getSouth(), //
        };
        Matrix imageToGeo = new Matrix();
        imageToGeo.setPolyToPoly(imageCorners, 0, geoCorners, 0, 3);
        // Find rotated image corners (store in geoCorners in image coordinates)
        Matrix imageRotate = new Matrix();
        imageRotate.setRotate(-map.getRotateAngle(), mapImage.getWidth() / 2f,
            mapImage.getHeight() / 2f);
        imageRotate.mapPoints(geoCorners, imageCorners);
        // Convert rotated image corners to geo coordinates
        imageToGeo.mapPoints(geoCorners);
        // Use just two opposite corners
        GeoPoint geoPoint = toGeoPoint(geoCorners[1], geoCorners[0]);
        pointList.add(new GroundOverlay.Tiepoint(geoPoint, new Point(0, 0)));
        geoPoint = toGeoPoint(geoCorners[5], geoCorners[4]);
        int x = mapImage.getWidth();
        int y = mapImage.getHeight();
        pointList.add(new GroundOverlay.Tiepoint(geoPoint, new Point(x, y)));
      }
      mapPoints = pointList;
    }

    int orientation = ImageHelper.readOrientation(FileUtil.TMP_IMAGE);

    for (GroundOverlay.Tiepoint oldPoint : mapPoints) {
      Point imagePoint = oldPoint.getImagePoint();
      byte[] snippet = ImageHelper.createPngSample(
          mapImage, imagePoint, SNIPPET_SIZE, orientation);
      Point snippetPoint = new Point();
      snippetPoint.x = Math.min(imagePoint.x, SNIPPET_SIZE / 2);
      snippetPoint.y = Math.min(imagePoint.y, SNIPPET_SIZE / 2);
      TiePoint newPoint = new TiePoint(oldPoint.getImagePoint(), snippet, snippetPoint);
      newPoint.setGeoPoint(oldPoint.getGeoPoint());
      tiepointAdapter.add(newPoint);
    }
    mapImage.recycle();
  }

  private void unpackImage(GroundOverlay map, String destinationPath) {
    InputStream in = null;
    OutputStream out = null;
    try {
      in = new BufferedInputStream(map.getKmlInfo().getImageStream(map.getImage()));
      out = new BufferedOutputStream(new FileOutputStream(destinationPath));
      copyContents(in, out);
      out.flush();
    } catch (IOException ex) {
      Log.w(CustomMaps.LOG_TAG, "Failed to unpack image from KMZ", ex);
    } finally {
      FileUtil.tryToClose(in);
      FileUtil.tryToClose(out);
    }
  }

  private void copyContents(InputStream in, OutputStream out) throws IOException {
    byte[] buf = new byte[1024];
    int n;
    while ((n = in.read(buf)) >= 0) {
      out.write(buf, 0, n);
    }
  }

  private static GeoPoint toGeoPoint(float latitude, float longitude) {
    int latitudeE6 = Math.round(1E6f * latitude);
    int longitudeE6 = Math.round(1E6f * longitude);
    return new GeoPoint(latitudeE6, longitudeE6);
  }

  // --------------------------------------------------------------------------
  // Sub-activity management

  private void processConvertPdfActivity(Uri imageFileUri) {
    bitmapFilename = imageFileUri.getEncodedPath();

    String defaultName = new File(bitmapFilename).getName();
    defaultName = defaultName.substring(0, defaultName.indexOf('.')).replace('_', ' ');
    nameField.setText(defaultName);
  }

  private void launchSelectImageFileActivity() {
    Intent selectImageFile = new Intent(this, SelectImageFileActivity.class);
    startActivityForResult(selectImageFile, SELECT_IMAGE_FILE);
  }

  private void processSelectedImageFile(Bundle filenameData) {
    bitmapFilename = filenameData.getString(SelectImageFileActivity.BITMAP_FILE);
    helpDialogManager.clearFirstTime(true);

    String defaultName = new File(bitmapFilename).getName();
    defaultName = defaultName.substring(0, defaultName.indexOf('.')).replace('_', ' ');
    nameField.setText(defaultName);
  }

  /**
   * Launches the activity to select another image point to be tied to geo
   * coordinates.
   */
  private void launchSelectPointActivity() {
    Intent selectImagePoint = new Intent(this, BitmapPointActivity.class);
    selectImagePoint.putExtra(BitmapPointActivity.BITMAP_FILE, bitmapFilename);
    if (!tiepointAdapter.isEmpty()) {
      int[] pointArray = new int[2 * tiepointAdapter.getCount()];
      for (int i = 0; i < tiepointAdapter.getCount(); i++) {
        TiePoint tiepoint = tiepointAdapter.getItem(i);
        Point p = tiepoint.getImagePoint();
        pointArray[2 * i] = p.x;
        pointArray[2 * i + 1] = p.y;
      }
      selectImagePoint.putExtra(BitmapPointActivity.TIEPOINTS, pointArray);
    }
    startActivityForResult(selectImagePoint, SELECT_IMAGE_POINT);
  }

  /**
   * Creates a new tiepoint (w/o geo location) from the returned data and adds
   * it to the end of list of tiepoints.
   *
   * @param imagePointData extras returned from select image point activity
   * @return newly created TiePoint
   */
  private TiePoint processSelectedImagePoint(Bundle imagePointData) {
    // Get the selected point and image around it
    int[] selectedPoint = imagePointData.getIntArray(BitmapPointActivity.SELECTED_POINT);
    Point imagePoint = new Point(selectedPoint[0], selectedPoint[1]);
    Point offset = new Point(selectedPoint[2], selectedPoint[3]);
    byte[] imageSnippet = imagePointData.getByteArray(BitmapPointActivity.BITMAP_DATA);
    TiePoint tiepoint = new TiePoint(imagePoint, imageSnippet, offset);
    tiepointAdapter.add(tiepoint);
    return tiepoint;
  }

  /**
   * Launches tie point activity to associate geo coordinates for a selected
   * image point.
   *
   * @param tiepoint image point to be associated with geo coordinates
   */
  private void launchTiePointActivity(TiePoint tiepoint) {
    Intent assignGeoPoint = new Intent(this, TiePointActivity.class);
    assignGeoPoint.putExtra(TiePointActivity.BITMAP_DATA, tiepoint.getPngData());
    Point p = tiepoint.getOffset();
    int[] selectedOffset = new int[] {p.x, p.y};
    assignGeoPoint.putExtra(TiePointActivity.IMAGE_POINT, selectedOffset);
    GeoPoint geoLocation = tiepoint.getGeoPoint();
    if (geoLocation != null) {
      int[] geopointE6 = new int[] {geoLocation.getLatitudeE6(), geoLocation.getLongitudeE6()};
      assignGeoPoint.putExtra(TiePointActivity.GEO_POINT_E6, geopointE6);
    }
    assignGeoPoint.putExtra(TiePointActivity.RESTORE_SETTINGS, !firstTiepoint);
    int index = tiepointAdapter.getPosition(tiepoint);
    if (index < 0) {
      Log.e(CustomMaps.LOG_TAG, "Given tiepoint was not found in tiepoint adapter!!!");
    }
    // store (index + 1), since value '0' means "not stored"
    assignGeoPoint.putExtra(TIEPOINT_INDEX, index);
    startActivityForResult(assignGeoPoint, SELECT_GEO_LOCATION);
  }

  private void processSelectedTiePoint(Bundle tiePointData) {
    int[] selectedGeoPoint = tiePointData.getIntArray(TiePointActivity.GEO_POINT_E6);
    if (selectedGeoPoint == null) {
      throw new IllegalArgumentException("No geo coordinates found");
    }
    // stored value is (index + 1)
    int index = tiePointData.getInt(TIEPOINT_INDEX, -1);
    if (index >= 0) {
      GeoPoint geoLocation = new GeoPoint(selectedGeoPoint[0], selectedGeoPoint[1]);
      TiePoint tiepoint = tiepointAdapter.getItem(index);
      tiepoint.setGeoPoint(geoLocation);
      tiepointAdapter.notifyDataSetChanged();
    } else {
      Log.e(CustomMaps.LOG_TAG, "TiePoint defined, but tiepoint index is missing!!!");
    }
    firstTiepoint = false;
  }

  /**
   * No geo coordinates were selected for image point. If the newest tiepoint
   * doesn't have a geo location associated with it, remove it.
   */
  private void cancelLastImagePoint() {
    if (!tiepointAdapter.isEmpty()) {
      TiePoint tiepoint = tiepointAdapter.getItem(tiepointAdapter.getCount() - 1);
      if (tiepoint.getGeoPoint() == null) {
        tiepointAdapter.remove(tiepoint);
        tiepoint.releaseBitmap();
      }
    }
  }

  /**
   * Displays current image overlaid on Google maps
   */
  private void launchPreviewActivity() {
    if (tiepointAdapter.getCount() < 2) {
      Toast.makeText(this, R.string.editor_need_two_points, Toast.LENGTH_LONG).show();
      return;
    }

    Intent preview = new Intent(this, PreviewMapActivity.class);
    preview.putExtra(PreviewMapActivity.BITMAP_FILE, bitmapFilename);
    if (!tiepointAdapter.isEmpty()) {
      int[] imagePointArray = new int[2 * tiepointAdapter.getCount()];
      int[] geoPointArray = new int[2 * tiepointAdapter.getCount()];
      for (int i = 0; i < tiepointAdapter.getCount(); i++) {
        TiePoint tiepoint = tiepointAdapter.getItem(i);
        Point p = tiepoint.getImagePoint();
        imagePointArray[2 * i] = p.x;
        imagePointArray[2 * i + 1] = p.y;
        GeoPoint g = tiepoint.getGeoPoint();
        geoPointArray[2 * i] = g.getLatitudeE6();
        geoPointArray[2 * i + 1] = g.getLongitudeE6();
      }
      preview.putExtra(PreviewMapActivity.IMAGE_POINTS, imagePointArray);
      preview.putExtra(PreviewMapActivity.TIEPOINTS, geoPointArray);
    }
    startActivityForResult(preview, PREVIEW);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Bundle results = (data != null ? data.getExtras() : null);
    switch (requestCode) {
      case CONVERT_PDF_FILE:
        if (resultCode != Activity.RESULT_OK) {
          launchSelectImageFileActivity();
        } else {
          processConvertPdfActivity(data.getData());
        }
        break;
      case SELECT_IMAGE_FILE:
        if (resultCode != Activity.RESULT_OK) {
          finish();
          break;
        }
        processSelectedImageFile(results);
        break;
      case SELECT_IMAGE_POINT:
        if (resultCode != Activity.RESULT_OK) {
          // Image point addition cancelled, show normal UI
          break;
        }
        // Save the image point and launch tie point activity to select geo
        // coordinates for it
        TiePoint tiepoint = processSelectedImagePoint(results);
        launchTiePointActivity(tiepoint);
        break;
      case SELECT_GEO_LOCATION:
        if (resultCode != Activity.RESULT_OK) {
          // Remove last tie point if it doesn't have geo location
          cancelLastImagePoint();
          break;
        }
        // Stores the selected geo coordinates to the tiepoint that was edited
        processSelectedTiePoint(results);
        break;
      case PREVIEW:
        if (resultCode != Activity.RESULT_OK) {
          // User backed out of preview, continue editing
          break;
        }
        // User returned by pressing "save" button, save kmz and exit
        saveMapAndExit(results);
        break;
    }
  }

  // --------------------------------------------------------------------------
  // Save and exit

  private void saveMapAndExit(Bundle imageCornerData) {
    // Restore GeoPoints from Bundle
    int[] cornerArray = imageCornerData.getIntArray(PreviewMapActivity.CORNER_GEO_POINTS);
    ArrayList<GeoPoint> corners = new ArrayList<GeoPoint>();
    for (int i = 0; i < 8; i += 2) {
      GeoPoint corner = new GeoPoint(cornerArray[i], cornerArray[i + 1]);
      corners.add(corner);
    }
    // Create the map kmz file
    try {
      saveAsKmz(corners);
    } catch (Exception ex) {
      Toast.makeText(this, R.string.editor_map_save_failed, Toast.LENGTH_LONG).show();
      Log.e(CustomMaps.LOG_TAG, "Failed to save map: " + nameField.getText(), ex);
      return;
    }

    // Release memory used by the image snippets
    while (tiepointAdapter.getCount() > 0) {
      TiePoint tiepoint = tiepointAdapter.getItem(0);
      tiepointAdapter.remove(tiepoint);
      tiepoint.releaseBitmap();
    }
    tiepointAdapter.notifyDataSetChanged();
    System.gc();

    // Return to calling activity
    Intent result = getIntent();
    setResult(RESULT_OK, result);
    finish();
  }

  /**
   * Converts a map name to a valid filename by keeping all letters and digits
   * and replacing all other characters with underscores ('_'). Collapses
   * underscores so that only single underscore separates letters and digit
   * sequences.
   *
   * @param mapName String to be converted
   * @return converted string that should be a valid file name
   */
  private String convertToFileName(CharSequence mapName) {
    StringBuilder fileName = new StringBuilder();
    boolean wasReplaced = true;
    for (int i = 0; i < mapName.length(); i++) {
      char ch = mapName.charAt(i);
      if (Character.isLetterOrDigit(ch)) {
        fileName.append(ch);
        wasReplaced = false;
      } else if (!wasReplaced) {
        fileName.append('_');
        wasReplaced = true;
      }
    }
    if (fileName.length() > 1 && wasReplaced) {
      fileName.setLength(fileName.length() - 1);
    }
    return fileName.toString();
  }

  private void saveAsKmz(ArrayList<GeoPoint> imageCorners) throws IOException {
    if (kmzFilename == null) {
      kmzFilename = convertToFileName(nameField.getText());
      File file = new File(FileUtil.DATA_DIR, kmzFilename + ".kmz");
      if (file.exists()) {
        // File with same name already exists, find unused name
        file = FileUtil.newFileInDataDirectory(kmzFilename + "_%d.kmz");
      }
      kmzFilename = file.getAbsolutePath();
    }

    FileOutputStream out = new FileOutputStream(kmzFilename);
    ZipOutputStream zipOut = new ZipOutputStream(out);
    try {
      zipOut.setMethod(ZipOutputStream.STORED);

      ZipEntry entry = new ZipEntry("doc.kml");
      byte[] data;
      if (tiepointAdapter.getCount() > 2) {
        data = generateLatLonQuadKml(imageCorners).getBytes();
      } else {
        data = generateLatLonBoxKml(imageCorners).getBytes();
      }
      entry.setTime(System.currentTimeMillis());
      writeToZip(data, entry, zipOut);

      entry = new ZipEntry(generateKmzImagePath());
      entry.setCrc(computeFileCRC(bitmapFilename));
      File imageFile = new File(bitmapFilename);
      entry.setSize(imageFile.length());
      entry.setTime(imageFile.lastModified());
      zipOut.putNextEntry(entry);
      copyFileToStream(bitmapFilename, zipOut);
      zipOut.closeEntry();

      // Only store image orientation info if image is not right way up
      int imageOrientation = ImageHelper.readOrientation(bitmapFilename);
      if (imageOrientation != 0) {
        entry = new ZipEntry(KmzFile.MAP_ORIENTATION_PROPERTIES);
        entry.setTime(System.currentTimeMillis());
        data = generateOrientationProperties(generateKmzImagePath(), imageOrientation);
        if (data != null) {
          writeToZip(data, entry, zipOut);
        }
      }

      zipOut.finish();
    } catch (IOException ex) {
      Log.e(CustomMaps.LOG_TAG, "Zip creation failed", ex);
      throw ex;
    } catch (RuntimeException ex) {
      Log.e(CustomMaps.LOG_TAG, "Zip creation failed", ex);
      throw ex;
    } finally {
      if (zipOut != null) {
        zipOut.close();
      }
    }
  }

  private long computeFileCRC(String fullPath) throws IOException {
    CRC32 crc = new CRC32();
    FileInputStream in = new FileInputStream(fullPath);
    try {
      int n = 0;
      byte[] chunk = new byte[2048];
      while ((n = in.read(chunk)) >= 0) {
        crc.update(chunk, 0, n);
      }
      return crc.getValue();
    } finally {
      in.close();
    }
  }

  private void copyFileToStream(String fullPath, OutputStream dest) throws IOException {
    FileInputStream in = new FileInputStream(fullPath);
    try {
      int n = 0;
      byte[] chunk = new byte[2048];
      while ((n = in.read(chunk)) >= 0) {
        dest.write(chunk, 0, n);
      }
    } finally {
      in.close();
    }
  }

  private void writeToZip(byte[] data, ZipEntry entry, ZipOutputStream zip) throws IOException {
    entry.setSize(data.length);
    CRC32 crc = new CRC32();
    crc.update(data);
    entry.setCrc(crc.getValue());
    zip.putNextEntry(entry);
    zip.write(data);
    zip.closeEntry();
  }

  private String generateLatLonQuadKml(ArrayList<GeoPoint> cornerList) {
    String kml = LATLONQUAD_KML_TEMPLATE.replace("${NAME}", nameField.getText().toString().trim());
    kml = kml.replace("${DESCRIPTION}", descriptionField.getText().toString().trim());
    kml = kml.replace("${IMAGE_PATH}", generateKmzImagePath());

    StringBuilder tiepointsKml = new StringBuilder();
    for (int i = 0; i < tiepointAdapter.getCount(); i++) {
      TiePoint tiepoint = tiepointAdapter.getItem(i);
      tiepointsKml.append(generateTiepointMarkup(tiepoint.getImagePoint(), tiepoint.getGeoPoint()));
    }
    kml = kml.replace("${TIEPOINTS}", tiepointsKml);

    // Add corner geo coordinates to kml and return it
    GeoPoint[] corners = cornerList.toArray(new GeoPoint[4]);
    return String.format(Locale.US, kml,
        corners[0].getLongitudeE6() / 1E6f,
        corners[0].getLatitudeE6() / 1E6f,
        corners[1].getLongitudeE6() / 1E6f,
        corners[1].getLatitudeE6() / 1E6f,
        corners[2].getLongitudeE6() / 1E6f,
        corners[2].getLatitudeE6() / 1E6f,
        corners[3].getLongitudeE6() / 1E6f,
        corners[3].getLatitudeE6() / 1E6f);
  }

  private String generateLatLonBoxKml(ArrayList<GeoPoint> cornerList) {
    String kml = LATLONBOX_KML_TEMPLATE.replace("${NAME}", nameField.getText().toString().trim());
    kml = kml.replace("${DESCRIPTION}", descriptionField.getText().toString().trim());
    kml = kml.replace("${IMAGE_PATH}", generateKmzImagePath());

    StringBuilder tiepointsKml = new StringBuilder();
    for (int i = 0; i < tiepointAdapter.getCount(); i++) {
      TiePoint tiepoint = tiepointAdapter.getItem(i);
      tiepointsKml.append(generateTiepointMarkup(tiepoint.getImagePoint(), tiepoint.getGeoPoint()));
    }
    kml = kml.replace("${TIEPOINTS}", tiepointsKml);

    // Figure out matrix conversion from rotated image to geo coordinates
    float[] imageCorners = generateImageCornerPoints();
    GeoPoint[] geoPoints = cornerList.toArray(new GeoPoint[4]);
    float[] geoCorners = new float[] {
        geoPoints[0].getLongitudeE6() / 1E6f, geoPoints[0].getLatitudeE6() / 1E6f,
        geoPoints[1].getLongitudeE6() / 1E6f, geoPoints[1].getLatitudeE6() / 1E6f,
        geoPoints[2].getLongitudeE6() / 1E6f, geoPoints[2].getLatitudeE6() / 1E6f,
        geoPoints[3].getLongitudeE6() / 1E6f, geoPoints[3].getLatitudeE6() / 1E6f
    };
    Matrix imageToGeoMatrix = new Matrix();
    imageToGeoMatrix.setPolyToPoly(imageCorners, 0, geoCorners, 0, 3);
    // Compute map rotation
    float rotation = computeMapRotation(cornerList.get(0), cornerList.get(3));
    // Compute unrotated image corners
    Matrix rotateMatrix = new Matrix();
    rotateMatrix.setRotate(rotation,
        (imageCorners[0] + imageCorners[4]) / 2f, (imageCorners[1] + imageCorners[5]) / 2f);
    rotateMatrix.mapPoints(imageCorners);
    // Map to geo points and find north/east/south/west edges
    imageToGeoMatrix.mapPoints(imageCorners);
    float north = (Math.max(imageCorners[1], imageCorners[5]) +
        Math.max(imageCorners[3], imageCorners[7])) / 2;
    float south = (Math.min(imageCorners[1], imageCorners[5]) +
        Math.min(imageCorners[3], imageCorners[7])) / 2;
    float east = (Math.max(imageCorners[0], imageCorners[4]) +
        Math.max(imageCorners[2], imageCorners[6])) / 2;
    float west = (Math.min(imageCorners[0], imageCorners[4]) +
        Math.min(imageCorners[2], imageCorners[6])) / 2;

    return String.format(Locale.US, kml, north, south, east, west, rotation);
  }

  /**
   * The two parameter points must have the same image x-coordinate.
   *
   * @param lowerPoint point lower in the unrotated image along y-axis
   * @param upperPoint point higher in the unrotated image along y-axis
   * @return map rotation in degrees [-180, 180] for LatLonBox KML tag
   */
  private float computeMapRotation(GeoPoint lowerPoint, GeoPoint upperPoint) {
    Location location1 = new Location("tmp");
    location1.setLatitude(lowerPoint.getLatitudeE6() / 1E6);
    location1.setLongitude(lowerPoint.getLongitudeE6() / 1E6);
    Location location2 = new Location("tmp");
    location2.setLatitude(upperPoint.getLatitudeE6() / 1E6);
    location2.setLongitude(upperPoint.getLongitudeE6() / 1E6);
    float bearing = location1.bearingTo(location2);
    if (bearing < -180f) {
      bearing += 360f;
    } else if (bearing > 180f) {
      bearing -= 360f;
    }
    return -bearing;
  }

  /**
   * Generates float[] containing image corner points in image coordinates
   * counter-clockwise from lower left corner.
   *
   * @return float[] containing corners in x, y, x, y,... order
   */
  private float[] generateImageCornerPoints() {
    BitmapFactory.Options info;
    InputStream in = null;
    try {
      in = new FileInputStream(bitmapFilename);
      info = ImageHelper.decodeImageBounds(in);
      in.close();
    } catch (IOException ex) {
      // Should never happen, but log it and assume 1000x1000 image
      Log.e(CustomMaps.LOG_TAG, "Failed to open image file: " + bitmapFilename, ex);
      return new float[] { 0, 1000, 1000, 1000, 1000, 0, 0, 0 };
    }
    // return all corners in image coordinates (clockwise from lower left)
    return new float[] {
        0, info.outHeight, info.outWidth, info.outHeight, info.outWidth, 0, 0, 0
    };
  }

  private byte[] generateOrientationProperties(String kmzImagePath, int orientation) {
    Properties props = new Properties();
    props.setProperty(kmzImagePath, String.valueOf(orientation));
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      props.store(out, "rotation info for the map image");
      return out.toByteArray();
    } catch (IOException ex) {
      return null;
    }
  }

  private String generateKmzImagePath() {
    String imageName = new File(bitmapFilename).getName();
    return FileUtil.KMZ_IMAGE_DIR + imageName.replace(' ', '_');
  }

  private String generateTiepointMarkup(Point imagePoint, GeoPoint geoPoint) {
    return String.format(Locale.US, TIEPOINT_FORMAT, imagePoint.x, imagePoint.y,
        geoPoint.getLongitudeE6() / 1E6f, geoPoint.getLatitudeE6() / 1E6f);
  }

  private static final String LATLONBOX_KML_TEMPLATE = //
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + //
      "<kml xmlns=\"http://www.opengis.net/kml/2.2\"\n" + //
      "     xmlns:gx=\"http://www.google.com/kml/ext/2.2\">\n" + //
      "<GroundOverlay>\n" + //
      "  <name><![CDATA[${NAME}]]></name>\n" + //
      "  <description><![CDATA[${DESCRIPTION}]]></description>\n" + //
      "  <Icon>\n" + //
      "    <href>${IMAGE_PATH}</href>\n" + //
      "  </Icon>\n" + //
      "  <LatLonBox>\n" + //
      "    <north>%.6f</north>\n" + //
      "    <south>%.6f</south>\n" + //
      "    <east>%.6f</east>\n" + //
      "    <west>%.6f</west>\n" + //
      "    <rotation>%.2f</rotation>\n" + //
      "  </LatLonBox>\n" + //
      "  <ExtendedData xmlns:tie=\"urn:tiepoints\">\n" + //
      "${TIEPOINTS}" + //
      "  </ExtendedData>\n" + //
      "</GroundOverlay>\n" + //
      "</kml>";

  private static final String LATLONQUAD_KML_TEMPLATE = //
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + //
      "<kml xmlns=\"http://www.opengis.net/kml/2.2\"\n" + //
      "     xmlns:gx=\"http://www.google.com/kml/ext/2.2\">\n" + //
      "<GroundOverlay>\n" + //
      "  <name><![CDATA[${NAME}]]></name>\n" + //
      "  <description><![CDATA[${DESCRIPTION}]]></description>\n" + //
      "  <Icon>\n" + //
      "    <href>${IMAGE_PATH}</href>\n" + //
      "  </Icon>\n" + //
      "  <gx:LatLonQuad>\n" + //
      "    <coordinates>\n" + //
      "      %.6f,%.6f,0 %.6f,%.6f,0 %.6f,%.6f,0 %.6f,%.6f,0\n" + //
      "    </coordinates>\n" + //
      "  </gx:LatLonQuad>\n" + //
      "  <ExtendedData xmlns:tie=\"urn:tiepoints\">\n" + //
      "${TIEPOINTS}" + //
      "  </ExtendedData>\n" + //
      "</GroundOverlay>\n" + //
      "</kml>";

  private static final String TIEPOINT_FORMAT = //
      "    <tie:tiepoint>\n" + //
      "      <tie:image>%d,%d</tie:image>\n" + //
      "      <tie:geo>%.6f,%.6f</tie:geo>\n" + //
      "    </tie:tiepoint>\n";

  // --------------------------------------------------------------------------
  // Context menu actions for tie points

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    if (item.getMenuInfo() instanceof AdapterContextMenuInfo) {
      AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
      TiePoint tiepoint = tiepointAdapter.getItem(menuInfo.position);
      switch (item.getItemId()) {
        case MENU_ADJUST_TIEPOINT:
          launchTiePointActivity(tiepoint);
          break;
        case MENU_DELETE_TIEPOINT:
          tiepointAdapter.remove(tiepoint);
          tiepoint.releaseBitmap();
          break;
      }
      return true;
    }
    return super.onContextItemSelected(item);
  }

  // --------------------------------------------------------------------------
  // Activity UI setup

  private void prepareUI() {
    nameField = (EditText) findViewById(R.id.nameField);
    descriptionField = (EditText) findViewById(R.id.descriptionField);
    tiePointsList = (ListView) findViewById(R.id.tiepoints);
    addPointButton = (Button) findViewById(R.id.addPoint);
    previewButton = (Button) findViewById(R.id.preview);

    tiepointAdapter = new TiePointAdapter(this, R.layout.tiepointitem);
    tiepointAdapter.setNotifyOnChange(true);
    tiePointsList.setAdapter(tiepointAdapter);
    tiePointsList.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
      @Override
      public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        menu.add(Menu.NONE, MENU_ADJUST_TIEPOINT, Menu.NONE, R.string.adjust_tiepoint);
        menu.add(Menu.NONE, MENU_DELETE_TIEPOINT, Menu.NONE, R.string.delete_tiepoint);
      }
    });

    addPointButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        launchSelectPointActivity();
      }
    });

    previewButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        launchPreviewActivity();
      }
    });
  }
}
