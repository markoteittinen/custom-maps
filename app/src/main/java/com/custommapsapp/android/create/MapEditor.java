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
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.maps.android.SphericalUtil;
import com.custommapsapp.android.CustomMaps;
import com.custommapsapp.android.CustomMapsApp;
import com.custommapsapp.android.DMatrix;
import com.custommapsapp.android.FileUtil;
import com.custommapsapp.android.HelpDialogManager;
import com.custommapsapp.android.ImageHelper;
import com.custommapsapp.android.InfoDialogFragment;
import com.custommapsapp.android.R;
import com.custommapsapp.android.kml.GroundOverlay;
import com.custommapsapp.android.kml.KmlFeature;
import com.custommapsapp.android.kml.KmlFolder;
import com.custommapsapp.android.kml.KmlInfo;
import com.custommapsapp.android.kml.KmzFile;
import com.custommapsapp.android.kml.Placemark;
import com.custommapsapp.android.language.Linguist;

/**
 * MapEditor manages editing of a map and its tiepoints.
 *
 * @author Marko Teittinen
 */
public class MapEditor extends AppCompatActivity {
  private static final String EXTRA_PREFIX = "com.custommapsapp.android";
  public static final String BITMAP_FILE = EXTRA_PREFIX + ".BitmapFile";
  public static final String KMZ_FILE = EXTRA_PREFIX + ".KmzFile";
  public static final String KML_FOLDER = EXTRA_PREFIX + ".KmlFolder";

  public static final int SNIPPET_SIZE = 240;

  private static final int MENU_ADJUST_TIEPOINT = 1;
  private static final int MENU_DELETE_TIEPOINT = 2;

  private String bitmapFilename;
  private String kmzFilename;
  private KmlFolder originalMap;
  private GroundOverlay mapImage;
  private List<Placemark> placemarks;

  private Linguist linguist;

  private EditText nameField;
  private EditText descriptionField;
  private TiePointAdapter tiepointAdapter;
  private HelpDialogManager helpDialogManager;
  // when 'false', tiepoint selection will restore thumbnail orientation
  private boolean firstTiepoint;

  private final ActivityResultLauncher<String[]> imageFileSelector =
      registerForActivityResult(
          new ActivityResultContracts.OpenDocument(), this::onImageFileSelected);
  private final ActivityResultLauncher<Uri> pdfPageSelector =
      registerForActivityResult(new Launchers.SelectPdfPage(), this::processMapImageUri);
  private final ActivityResultLauncher<Launchers.SelectImagePointInput> imagePointSelector =
      registerForActivityResult(new Launchers.SelectImagePoint(), this::onImagePointSelected);
  private final ActivityResultLauncher<Launchers.SelectMapPointInput> mapPointSelector =
      registerForActivityResult(new Launchers.SelectMapPoint(), this::onMapPointSelected);
  private final ActivityResultLauncher<Launchers.PreviewMapInput> mapPreviewer =
      registerForActivityResult(new Launchers.PreviewMap(), this::saveMapAndExit);

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.mapeditor);
    linguist = ((CustomMapsApp) getApplication()).getLinguist();
    ImageHelper.initializePreferredBitmapConfig(this);
    prepareUI();
    setSupportActionBar(findViewById(R.id.toolbar));

    // Update actionbar title to match current locale
    getSupportActionBar().setTitle(linguist.getString(R.string.create_map_name));

    helpDialogManager = new HelpDialogManager(this, HelpDialogManager.HELP_MAP_EDITOR,
        linguist.getString(R.string.editor_help));

    if (savedInstanceState != null) {
      onRestoreInstanceState(savedInstanceState);
    }

    Intent intent = getIntent();
    if (bitmapFilename == null && kmzFilename == null) {
      if (intent.hasExtra(BITMAP_FILE)) {
        bitmapFilename = intent.getStringExtra(BITMAP_FILE);
      } else if (intent.hasExtra(KMZ_FILE)) {
        kmzFilename = intent.getStringExtra(KMZ_FILE);
        initializeMapVariables((KmlFolder) intent.getSerializableExtra(KML_FOLDER));

        if (mapImage != null) {
          nameField.setText(mapImage.getName());
          descriptionField.setText(mapImage.getDescription());
          findTiePoints(mapImage);
        } else {
          // No map with image provided, cancel
          Toast.makeText(this,
              linguist.getString(R.string.editor_image_load_failed), Toast.LENGTH_LONG).show();
          setResult(RESULT_CANCELED);
          finish();
        }
        // If there was an error, this activity will quit
        if (this.isFinishing()) {
          return;
        }
      }
    }
    if (bitmapFilename == null && kmzFilename == null) {
      // Prevent from clearing "firstTime" flag for help dialog never shown
      helpDialogManager.clearFirstTime(false);
      // Display dialog letting the user know they have to select the map image first
      FragmentManager fragmentManager = getSupportFragmentManager();
      fragmentManager.setFragmentResultListener(InfoDialogFragment.TAG, this,
          (requestKey, result) -> launchSelectImageFileActivity());
      InfoDialogFragment infoDialog = (InfoDialogFragment)
          fragmentManager.findFragmentByTag(InfoDialogFragment.TAG);
      if (infoDialog == null) {
        InfoDialogFragment.showDialog(
            fragmentManager,
            getString(R.string.create_map_name),
            getString(R.string.editor_select_file_prompt));
      }
    }
    firstTiepoint = tiepointAdapter.isEmpty();
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
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(BITMAP_FILE, bitmapFilename);
    outState.putString(NAME, nameField.getText().toString());
    outState.putString(DESCRIPTION, descriptionField.getText().toString());
    outState.putParcelableArrayList(TIEPOINTS, tiepointAdapter.getAllTiePoints());
    outState.putString(KMZ_FILE, kmzFilename);
    if (originalMap != null) {
      outState.putSerializable(KML_FOLDER, originalMap);
    }
    helpDialogManager.onSaveInstanceState(outState);
  }

  @Override
  protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    bitmapFilename = savedInstanceState.getString(BITMAP_FILE);
    String name = savedInstanceState.getString(NAME);
    nameField.setText(name);
    String description = savedInstanceState.getString(DESCRIPTION);
    descriptionField.setText(description);

    kmzFilename = savedInstanceState.getString(KMZ_FILE);
    if (savedInstanceState.containsKey(KML_FOLDER)) {
      initializeMapVariables((KmlFolder) savedInstanceState.getSerializable(KML_FOLDER));
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

  /**
   * Initializes mapImage (first GroundOverlay in KmlFolder) and list of placemarks stored with
   * map.
   *
   * @param map KmlFolder containing the data
   */
  private void initializeMapVariables(KmlFolder map) {
    originalMap = map;
    placemarks = new ArrayList<>();
    mapImage = null;
    if (map != null) {
      mapImage = map.getFirstMap();
      for (KmlFeature feature : map.getFeatures()) {
        if (feature instanceof Placemark) {
          placemarks.add((Placemark) feature);
        }
      }
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    helpDialogManager.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    super.onOptionsItemSelected(item);
    helpDialogManager.onOptionsItemSelected(item);
    return true;
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

  private void findTiePoints(GroundOverlay map) {
    unpackImage(map, FileUtil.getTmpImagePath());
    bitmapFilename = FileUtil.getTmpImagePath();

    Bitmap mapImage = ImageHelper.loadImage(FileUtil.getTmpImagePath(), true);
    if (mapImage == null) {
      Toast.makeText(this, linguist.getString(R.string.editor_image_load_failed), Toast.LENGTH_LONG)
          .show();
      setResult(RESULT_CANCELED);
      finish();
      return;
    }

    Iterable<GroundOverlay.Tiepoint> mapPoints = map.getTiepoints();
    if (!mapPoints.iterator().hasNext()) {
      // No tiepoints defined, use map corners
      List<GroundOverlay.Tiepoint> pointList = new ArrayList<>();
      if (map.hasCornerTiePoints()) {
        // Map is defined with LatLonQuad, use those points
        float[] geo = map.getNorthWestCornerLocation();
        LatLng geoPoint = new LatLng(geo[1], geo[0]);
        Point imagePoint = new Point(0, 0);
        pointList.add(new GroundOverlay.Tiepoint(geoPoint, imagePoint));

        geo = map.getNorthEastCornerLocation();
        geoPoint = new LatLng(geo[1], geo[0]);
        imagePoint = new Point(mapImage.getWidth(), 0);
        pointList.add(new GroundOverlay.Tiepoint(geoPoint, imagePoint));

        geo = map.getSouthEastCornerLocation();
        geoPoint = new LatLng(geo[1], geo[0]);
        imagePoint = new Point(mapImage.getWidth(), mapImage.getHeight());
        pointList.add(new GroundOverlay.Tiepoint(geoPoint, imagePoint));

        geo = map.getSouthWestCornerLocation();
        geoPoint = new LatLng(geo[1], geo[0]);
        imagePoint = new Point(0, mapImage.getHeight());
        pointList.add(new GroundOverlay.Tiepoint(geoPoint, imagePoint));
      } else {
        // Map has only image location and rotation - find image to geo mapping
        float[] imageCorners = new float[]{
            0, 0,
            mapImage.getWidth(), 0,
            mapImage.getWidth(), mapImage.getHeight(),
        };
        float[] geoCorners = new float[]{
            map.getWest(), map.getNorth(),
            map.getEast(), map.getNorth(),
            map.getEast(), map.getSouth(),
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
        LatLng geoPoint = new LatLng(geoCorners[1], geoCorners[0]);
        pointList.add(new GroundOverlay.Tiepoint(geoPoint, new Point(0, 0)));
        geoPoint = new LatLng(geoCorners[5], geoCorners[4]);
        int x = mapImage.getWidth();
        int y = mapImage.getHeight();
        pointList.add(new GroundOverlay.Tiepoint(geoPoint, new Point(x, y)));
      }
      mapPoints = pointList;
    }

    int orientation = ImageHelper.readOrientation(FileUtil.getTmpImagePath());

    for (GroundOverlay.Tiepoint oldPoint : mapPoints) {
      Point imagePoint = oldPoint.getImagePoint();
      byte[] snippet = ImageHelper.createJpgSample(mapImage, imagePoint, SNIPPET_SIZE, orientation);
      Point snippetPoint = new Point();
      snippetPoint.x = snippetPoint.y = SNIPPET_SIZE / 2;
      TiePoint newPoint = new TiePoint(oldPoint.getImagePoint(), snippet, snippetPoint);
      newPoint.setGeoPoint(oldPoint.getGeoPoint());
      tiepointAdapter.add(newPoint);
    }
    mapImage.recycle();
  }

  private void unpackImage(GroundOverlay map, String destinationPath) {
    InputStream in = null;
    OutputStream out = null;
    File destFile;
    long imageDate = 0;
    try {
      KmlInfo srcInfo = map.getKmlInfo();
      String imageName = map.getImage();
      imageDate = srcInfo.getImageDate(imageName);
      in = new BufferedInputStream(srcInfo.getImageStream(map.getImage()));
      destFile = new File(destinationPath);
      out = new BufferedOutputStream(new FileOutputStream(destFile));
      FileUtil.copyContents(in, out);
      out.flush();
    } catch (IOException ex) {
      Log.w(CustomMaps.LOG_TAG, "Failed to unpack image from KMZ", ex);
      destFile = null;
    } finally {
      FileUtil.tryToClose(in);
      FileUtil.tryToClose(out);
    }
    // Keep image timestamp
    if (destFile != null && imageDate != 0) {
      destFile.setLastModified(imageDate);
    }
  }

  // --------------------------------------------------------------------------
  // Sub-activity management

  /** Launches image file selection activity allowing selection of pdf, jpg, png, and gif files. */
  private void launchSelectImageFileActivity() {
    Log.d("MapEditor", "*** Launching select image file...");
    MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
    ArrayList<String> mimeTypes = new ArrayList<>();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      mimeTypes.add(mimeTypeMap.getMimeTypeFromExtension("pdf"));
    }
    mimeTypes.add(mimeTypeMap.getMimeTypeFromExtension("jpg"));
    mimeTypes.add(mimeTypeMap.getMimeTypeFromExtension("png"));
    mimeTypes.add(mimeTypeMap.getMimeTypeFromExtension("gif"));
    imageFileSelector.launch(mimeTypes.toArray(new String[0]));
  }

  /**
   * Selects processing path for select map image file. Starts PDF page selection activity if a PDF
   * file was selected, otherwise passes image file for further processing.
   */
  private void onImageFileSelected(Uri contentUri) {
    if (contentUri == null) {
      // User cancelled map image selection, exit activity
      finish();
      return;
    }
    String filename = FileUtil.resolveContentFileName(contentUri);
    if (filename == null) {
      filename = contentUri.getLastPathSegment();
    }
    Log.i("MapEditor", "Map image file selected: " + filename);
    if (filename.toLowerCase().endsWith(".pdf")) {
      // PDF file selected, launch page selection activity
      pdfPageSelector.launch(contentUri);
    } else {
      // Image file selected, process it
      processMapImageUri(contentUri);
    }
  }

  /** Called when user has either selected a bitmap image file, or has selected a PDF page. */
  private void processMapImageUri(Uri mapImageUri) {
    if (mapImageUri == null) {
      // User cancelled PDF page selection, restart map image selection activity
      launchSelectImageFileActivity();
      return;
    }

    String filename = FileUtil.resolveContentFileName(mapImageUri);
    if (filename == null) {
      filename = mapImageUri.getLastPathSegment();
    }
    if (mapImageUri.getScheme().equals("content")) {
      // Copy content to internal cache directory
      File copiedImageFile = new File(FileUtil.getCacheDirectory("MapEditor"), filename);
      try {
        InputStream source = getContentResolver().openInputStream(mapImageUri);
        OutputStream dest = new FileOutputStream(copiedImageFile);
        FileUtil.copyContents(source, dest);
        dest.close();
        source.close();
      } catch (IOException ex) {
        // Finish activity
        // TODO: notify user with an error msg
        Log.e(CustomMaps.LOG_TAG, "Failed to copy selected map image to internal storage");
        finish();
        return;
      }
      mapImageUri = Uri.fromFile(copiedImageFile);
    }

    helpDialogManager.clearFirstTime(true);

    // Remove "file://" prefix from Uri (7 chars)
    bitmapFilename = mapImageUri.toString().substring(7);

    // Initialize the map name field based on the image filename
    String defaultName = filename.substring(0, filename.lastIndexOf('.'))
        .replace('_', ' ')
        .replace('+', ' ');
    nameField.setText(defaultName);
  }

  /**
   * Launches the activity to select another image point to be tied to geo coordinates.
   */
  private void launchSelectPointActivity() {
    Launchers.SelectImagePointInput params = new Launchers.SelectImagePointInput();
    params.mapImageFile = new File(bitmapFilename);
    params.tiepoints = tiepointAdapter.getAllTiePoints();
    imagePointSelector.launch(params);
  }

  /** Processes the TiePoint created when user selected a new point in the map image. */
  private void onImagePointSelected(TiePoint tiepoint) {
    if (tiepoint == null) {
      // User cancelled adding a tiepoint, no change on editor page
      return;
    }
    tiepointAdapter.add(tiepoint);
    launchTiePointActivity(tiepoint);
  }

  /**
   * Launches tie point activity to associate geo coordinates for a selected image point.
   *
   * @param tiepoint image point to be associated with geo coordinates
   */
  private void launchTiePointActivity(TiePoint tiepoint) {
    int index = tiepointAdapter.getPosition(tiepoint);
    if (tiepoint.getGeoPoint() == null) {
      // Make a copy of the tiepoint to leave the one in adapter untouched (w/o geolocation)
      tiepoint = tiepoint.clone();
      // Estimate the point's location based on previously created points (may return 'null')
      tiepoint.setGeoPoint(guessGeolocationOf(tiepoint.getImagePoint()));
    }
    Launchers.SelectMapPointInput params = new Launchers.SelectMapPointInput();
    params.tiepoint = tiepoint;
    params.tiepointIndex = index;
    params.isFirstTiePoint = firstTiepoint;
    mapPointSelector.launch(params);
  }

  /** Processes the selected geolocation for a TiePoint or cancellation of TiePoint creation. */
  private void onMapPointSelected(Launchers.SelectMapPointOutput result) {
    if (result == null || result.tiepointIndex < 0 || result.mapPoint == null) {
      // Remove the last tiepoint if it doesn't have geolocation
      cancelLastImagePoint();
      return;
    }
    // Update the tiepoint with the selected geolocation
    TiePoint tiepoint = tiepointAdapter.getItem(result.tiepointIndex);
    tiepoint.setGeoPoint(result.mapPoint);
    tiepointAdapter.notifyDataSetChanged();

    firstTiepoint = false;
  }

  /**
   * No geo coordinates were selected for image point. If the newest tiepoint doesn't have a geo
   * location associated with it, remove it.
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
      Toast.makeText(this, linguist.getString(R.string.editor_need_two_points), Toast.LENGTH_LONG)
          .show();
      return;
    }
    Launchers.PreviewMapInput params = new Launchers.PreviewMapInput();
    params.mapImageFile = new File(bitmapFilename);
    params.tiepoints = tiepointAdapter.getAllTiePoints();
    mapPreviewer.launch(params);
  }

  /**
   * Tries to guess the geolocation of an image point. Will return null if the location cannot be
   * guessed (at least two previous points have to be set before guessing can happen).
   */
  private LatLng guessGeolocationOf(Point imagePoint) {
    // Ignore the last point in tiepointAdapter, it is the one being added (it has no geo location)
    int n = tiepointAdapter.getCount() - 1;
    // If we don't already have at least 2 known points, return null
    if (n < 2) {
      return null;
    }
    if (n == 2) {
      return guessGeolocationByGeometry(imagePoint);
    }
    // Use only 3 points to avoid potential unsolvable matrix with 4 points
    n = 3;
    double[] imageCoords = new double[2 * n]; // points ordered in x1,y1, x2,y2, ...
    double[] geoCoords = new double[2 * n];   // points ordered lon1,lng1, lon2,lng2, ...
    for (int i = 0; i < n; i++) {
      TiePoint tiePoint = tiepointAdapter.getItem(i);
      imageCoords[2 * i] = tiePoint.getImagePoint().x;
      imageCoords[2 * i + 1] = tiePoint.getImagePoint().y;
      // When geoPoint is stored, invert sign of latitude so that it grows down like image y
      geoCoords[2 * i] = tiePoint.getGeoPoint().longitude;
      geoCoords[2 * i + 1] = -tiePoint.getGeoPoint().latitude;
    }
    // Solve linear matrix mapping image points to their geo coordinates
    DMatrix matrix = new DMatrix();
    matrix.setPolyToPoly(imageCoords, 0, geoCoords, 0, n);
    // Map given image point to its geo coordinates using that matrix
    double[] point = {imagePoint.x, imagePoint.y};
    matrix.mapPoints(point);
    // Invert sign of latitude to get correct value
    point[1] = -point[1];
    return new LatLng(point[1], point[0]);
  }

  /**
   * Guesses the geolocation of an image point based on two earlier points for which we already
   * have geo coordinates.
   */
  private LatLng guessGeolocationByGeometry(Point imagePoint) {
    Point imagePt1 = tiepointAdapter.getItem(0).getImagePoint();
    Point imagePt2 = tiepointAdapter.getItem(1).getImagePoint();
    double dx = imagePt2.x - imagePt1.x;
    double dy = imagePt1.y - imagePt2.y;  // sign inverted since image y grows downwards
    double imageDistancePx = Math.sqrt(dx * dx + dy * dy);
    double imageHeading = 90 - Math.toDegrees(Math.atan2(dy, dx));

    LatLng geoPt1 = tiepointAdapter.getItem(0).getGeoPoint();
    LatLng geoPt2 = tiepointAdapter.getItem(1).getGeoPoint();
    double geoDistanceM = SphericalUtil.computeDistanceBetween(geoPt1, geoPt2);
    double geoHeading = SphericalUtil.computeHeading(geoPt1, geoPt2);

    // Compute distance and heading from imagePt1 to given imagePoint
    dx = imagePoint.x - imagePt1.x;
    dy = imagePt1.y - imagePoint.y; // sign inverted since image y grows downwards
    double distancePx = Math.sqrt(dx * dx + dy * dy);
    double heading = 90 - Math.toDegrees(Math.atan2(dy, dx));

    // Convert the new distance and heading to geo values
    double distanceM = geoDistanceM * distancePx / imageDistancePx;
    double headingNew = heading + (geoHeading - imageHeading);
    return SphericalUtil.computeOffset(geoPt1, distanceM, headingNew);
  }

  // --------------------------------------------------------------------------
  // Save and exit

  private void saveMapAndExit(List<LatLng> imageCornerLocations) {
    if (imageCornerLocations == null) {
      // User cancelled saving and exiting in preview screen
      return;
    }
    try {
      // Create the map kmz file, and delete the map image file from cache
      saveAsKmz(imageCornerLocations);
      new File(bitmapFilename).delete();
    } catch (Exception ex) {
      Toast.makeText(this, linguist.getString(R.string.editor_map_save_failed), Toast.LENGTH_LONG)
          .show();
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
    result.putExtra(KMZ_FILE, kmzFilename);
    setResult(RESULT_OK, result);
    finish();
  }

  /**
   * Converts a map name to a valid filename by keeping all letters and digits and replacing all
   * other characters with underscores ('_'). Collapses underscores so that only single underscore
   * separates letters and digit sequences.
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

  private void saveAsKmz(List<LatLng> imageCorners) throws IOException {
    if (kmzFilename == null) {
      kmzFilename = convertToFileName(nameField.getText());
      File file = new File(FileUtil.getInternalMapDirectory(), kmzFilename + ".kmz");
      if (file.exists()) {
        // File with same name already exists, find unused name
        file = FileUtil.newFileInDataDirectory(kmzFilename + "_%d.kmz");
      }
      kmzFilename = file.getAbsolutePath();
    }

    FileOutputStream out = new FileOutputStream(kmzFilename);
    try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
      zipOut.setMethod(ZipOutputStream.STORED);

      ZipEntry entry = new ZipEntry("doc.kml");
      // TODO: Add support for saving maps with placemarks.
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
    } catch (IOException | RuntimeException ex) {
      Log.e(CustomMaps.LOG_TAG, "Zip creation failed", ex);
      throw ex;
    }
  }

  private long computeFileCRC(String fullPath) throws IOException {
    CRC32 crc = new CRC32();
    try (FileInputStream in = new FileInputStream(fullPath)) {
      int n;
      byte[] chunk = new byte[2048];
      while ((n = in.read(chunk)) >= 0) {
        crc.update(chunk, 0, n);
      }
      return crc.getValue();
    }
  }

  private void copyFileToStream(String fullPath, OutputStream dest) throws IOException {
    try (FileInputStream in = new FileInputStream(fullPath)) {
      int n;
      byte[] chunk = new byte[2048];
      while ((n = in.read(chunk)) >= 0) {
        dest.write(chunk, 0, n);
      }
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

  private String generateLatLonQuadKml(List<LatLng> cornerList) {
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
    LatLng[] corners = cornerList.toArray(new LatLng[4]);
    return String.format(Locale.US, kml,
        corners[0].longitude, corners[0].latitude,
        corners[1].longitude, corners[1].latitude,
        corners[2].longitude, corners[2].latitude,
        corners[3].longitude, corners[3].latitude);
  }

  private String generateLatLonBoxKml(List<LatLng> cornerList) {
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
    LatLng[] geoPoints = cornerList.toArray(new LatLng[4]);
    float[] geoCorners = new float[]{
        (float) geoPoints[0].longitude, (float) geoPoints[0].latitude,
        (float) geoPoints[1].longitude, (float) geoPoints[1].latitude,
        (float) geoPoints[2].longitude, (float) geoPoints[2].latitude,
        (float) geoPoints[3].longitude, (float) geoPoints[3].latitude
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
  private float computeMapRotation(LatLng lowerPoint, LatLng upperPoint) {
    Location location1 = new Location("tmp");
    location1.setLatitude(lowerPoint.latitude);
    location1.setLongitude(lowerPoint.longitude);
    Location location2 = new Location("tmp");
    location2.setLatitude(upperPoint.latitude);
    location2.setLongitude(upperPoint.longitude);
    float bearing = location1.bearingTo(location2);
    if (bearing < -180f) {
      bearing += 360f;
    } else if (bearing > 180f) {
      bearing -= 360f;
    }
    return -bearing;
  }

  /**
   * Generates float[] containing image corner points in image coordinates counter-clockwise from
   * lower left corner.
   *
   * @return float[] containing corners in x, y, x, y,... order
   */
  private float[] generateImageCornerPoints() {
    BitmapFactory.Options info;
    InputStream in;
    try {
      in = new FileInputStream(bitmapFilename);
      info = ImageHelper.decodeImageBounds(in);
      in.close();
    } catch (IOException ex) {
      // Should never happen, but log it and assume 1000x1000 image
      Log.e(CustomMaps.LOG_TAG, "Failed to open image file: " + bitmapFilename, ex);
      return new float[]{0, 1000, 1000, 1000, 1000, 0, 0, 0};
    }
    // return all corners in image coordinates (clockwise from lower left)
    return new float[]{
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

  private String generateTiepointMarkup(Point imagePoint, LatLng geoPoint) {
    return String.format(Locale.US, TIEPOINT_FORMAT, imagePoint.x, imagePoint.y,
        geoPoint.longitude, geoPoint.latitude);
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
    nameField = findViewById(R.id.nameField);
    descriptionField = findViewById(R.id.descriptionField);
    ListView tiePointsList = findViewById(R.id.tiepoints);

    tiepointAdapter = new TiePointAdapter(this, R.layout.tiepointitem);
    tiepointAdapter.setNotifyOnChange(true);
    tiePointsList.setAdapter(tiepointAdapter);
    tiePointsList.setOnCreateContextMenuListener((menu, v, menuInfo) -> {
      menu.add(
          Menu.NONE, MENU_ADJUST_TIEPOINT, Menu.NONE, linguist.getString(R.string.adjust_tiepoint));
      menu.add(
          Menu.NONE, MENU_DELETE_TIEPOINT, Menu.NONE, linguist.getString(R.string.delete_tiepoint));
    });

    FloatingActionButton addPointFab = findViewById(R.id.addPointFab);
    addPointFab.setOnClickListener(v -> launchSelectPointActivity());
    Toolbar bottomBar = findViewById(R.id.bottombar);
    bottomBar.getMenu()
        .add(1, 1, 1, linguist.getString(R.string.button_preview))
        .setOnMenuItemClickListener(item -> {
          launchPreviewActivity();
          return true;
        })
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
  }
}
