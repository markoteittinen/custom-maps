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

import com.custommapsapp.android.HelpDialogManager;
import com.custommapsapp.android.ImageHelper;
import com.custommapsapp.android.R;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * BitmapPointActivity allows user to select a point on a bitmap to be matched
 * to a geo coordinates on a map. This Activity needs as parameters both a full
 * path to a bitmap file (jpg or png), and a list of already existing tie
 * points.
 * <p>
 *
 * BitmapPointActivity returns a PNG compressed {@code byte[]} in {@code
 * BITMAP_DATA} containing a small image surrounding the selected point. The
 * selected point itself is returned in an {@code int[]} in {@code
 * SELECTED_POINT} which contains the point both in bitmap space, and then the
 * offset of the selected point in the returned small image (x0, y0, x1, y1).
 *
 * @author Marko Teittinen
 */
public class BitmapPointActivity extends Activity {
  private static final String EXTRA_PREFIX = "com.custommapsapp.android";
  public static final String BITMAP_FILE = EXTRA_PREFIX + ".BitmapFile";
  public static final String TIEPOINTS = EXTRA_PREFIX + ".Tiepoints";
  public static final String SELECTED_POINT = EXTRA_PREFIX + ".SelectedPoint";
  public static final String BITMAP_DATA = EXTRA_PREFIX + ".BitmapData";

  private static final String CENTER_POINT = EXTRA_PREFIX + ".CenterPoint";
  private static final String SCALE = EXTRA_PREFIX + ".Scale";

  private static final String LOG_TAG = "Custom Maps";

  private ImageDisplay imageDisplay;
  private Bitmap image = null;
  private Button selectPoint;
  private int orientation = 0;
  private HelpDialogManager helpDialogManager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.bitmappoint);

    imageDisplay = (ImageDisplay) findViewById(R.id.imageDisplay);
    AnnotationLayer dataLayer = (AnnotationLayer) findViewById(R.id.bitmapOverlay);
    imageDisplay.setAnnotationLayer(dataLayer);

    selectPoint = (Button) findViewById(R.id.selectPoint);
    selectPoint.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        returnSelectedPoint(imageDisplay.getCenterPoint());
      }
    });

    String fileName = getIntent().getStringExtra(BITMAP_FILE);
    if (fileName == null) {
      Toast.makeText(this, "No map image specified, can't select point", Toast.LENGTH_LONG).show();
      setResult(RESULT_CANCELED);
      finish();
    }

    // Load the actual bitmap (if load fails, should display an error message)
    image = ImageHelper.loadImage(fileName);
    if (image != null) {
      imageDisplay.setBitmap(image);
      orientation = ImageHelper.readOrientation(fileName);
      imageDisplay.setOrientation(orientation);
      imageDisplay.setCenterPoint(new PointF(image.getWidth() / 2f, image.getHeight() / 2f));
    } else {
      Toast.makeText(this, "Selected map image is too large", Toast.LENGTH_LONG).show();
      setResult(RESULT_CANCELED);
      finish();
    }

    // Pass existing tiepoints to AnnotationLayer
    int[] pointArray = getIntent().getIntArrayExtra(TIEPOINTS);
    if (pointArray != null) {
      List<PointF> tiePoints = new ArrayList<PointF>();
      for (int i = 0; i + 1 < pointArray.length; i += 2) {
        PointF p = new PointF(pointArray[i], pointArray[i + 1]);
        tiePoints.add(p);
      }
      dataLayer.addTiePoints(tiePoints);
    }

    ImageButton zoomIn = (ImageButton) findViewById(R.id.zoomIn);
    ImageButton zoomOut = (ImageButton) findViewById(R.id.zoomOut);
    zoomIn.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        imageDisplay.zoomBy(2.0f);
      }
    });
    zoomOut.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        imageDisplay.zoomBy(0.5f);
      }
    });

    helpDialogManager = new HelpDialogManager(this, HelpDialogManager.HELP_BITMAP_POINT,
        "Select a point to be located on a Google map.\n\n" + //
        "Select points far apart for best results.\n\n" + //
        "Avoid selecting points between previous points.");
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    float scale = imageDisplay.getScale();
    outState.putFloat(SCALE, scale);
    PointF p = imageDisplay.getCenterPoint();
    outState.putFloatArray(CENTER_POINT, new float[] {p.x, p.y});
    helpDialogManager.onSaveInstanceState(outState);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    float scale = savedInstanceState.getFloat(SCALE, 1f);
    imageDisplay.setScale(scale);
    float[] point = savedInstanceState.getFloatArray(CENTER_POINT);
    if (point != null) {
      imageDisplay.setCenterPoint(new PointF(point[0], point[1]));
    }
    helpDialogManager.onRestoreInstanceState(savedInstanceState);
  }

  @Override
  protected void onResume() {
    super.onResume();
    helpDialogManager.onResume();
  }

  @Override
  protected void onPause() {
    if (isFinishing()) {
      if (image != null && !image.isRecycled()) {
        image.recycle();
        image = null;
      }
    } else {
      helpDialogManager.onPause();
    }
    super.onPause();
  }

  // --------------------------------------------------------------------------
  // Options menu

  private static final int MENU_SELECT_POINT = 1;

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    menu.add(Menu.NONE, MENU_SELECT_POINT, Menu.NONE, "Select point");
    helpDialogManager.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case MENU_SELECT_POINT:
        returnSelectedPoint(imageDisplay.getCenterPoint());
        return true;
      default:
        helpDialogManager.onOptionsItemSelected(item);
        return true;
    }
  }

  private void returnSelectedPoint(PointF bitmapPoint) {
    Point intPoint = new Point(Math.round(bitmapPoint.x), Math.round(bitmapPoint.y));
    int snippetSize = MapEditor.SNIPPET_SIZE;
    byte[] mapSnippet = ImageHelper.createPngSample(image, intPoint, snippetSize, orientation);
    Point snippetPoint = new Point();
    snippetPoint.x = Math.min(intPoint.x, snippetSize / 2);
    snippetPoint.y = Math.min(intPoint.y, snippetSize / 2);

    // Release memory used by the loaded large bitmap
    imageDisplay.setBitmap(null);
    if (image != null && !image.isRecycled()) {
      image.recycle();
    }
    image = null;
    System.gc();

    // Return the result to calling activity in the original Intent
    Intent result = getIntent();
    int[] points = new int[] { intPoint.x, intPoint.y, snippetPoint.x, snippetPoint.y};
    result.putExtra(SELECTED_POINT, points);
    result.putExtra(BITMAP_DATA, mapSnippet);
    setResult(RESULT_OK, result);
    finish();
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
}
