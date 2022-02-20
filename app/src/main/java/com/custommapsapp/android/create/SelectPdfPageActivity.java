/*
 * Copyright 2019 Google Inc. All Rights Reserved.
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

import static com.custommapsapp.android.CustomMaps.LOG_TAG;

import java.io.File;
import java.util.Locale;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.custommapsapp.android.CustomMaps;
import com.custommapsapp.android.CustomMapsApp;
import com.custommapsapp.android.FileUtil;
import com.custommapsapp.android.ImageHelper;
import com.custommapsapp.android.R;
import com.custommapsapp.android.language.Linguist;

/**
 * SelectPdfPageActivity allows the user to select a page containing a map in a PDF document that
 * can contain multiple pages. Also, the page can be rotated in 90 degree increments to avoid a
 * situation where the text and legend of the map are sideways, as sometimes PDF documents meant for
 * printing have different orientation for a page displaying a map.
 *
 * @author Marko Teittinen
 */
// PDF rendering requires API 21 (Android 5.0, Lollipop), don't use this Activity in older devices
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class SelectPdfPageActivity extends AppCompatActivity {
  private static final String EXTRA_PREFIX = "com.custommapsapp.android";
  private static final String CURRENT_PAGE = EXTRA_PREFIX + ".CurrentPage";
  public static final String PDF_FILENAME = EXTRA_PREFIX + ".PdfFile";
  public static final String PDF_CONTENTURI = EXTRA_PREFIX + ".PdfUri";

  private ImageView pageDisplay;
  private TextView pageNumber;

  private Linguist linguist;

  private String pdfFilename;
  private Uri pdfContentUri;
  private int currentPageNum = 0;
  private int lastPage = 9;

  private PdfRendererFragment pdfRendererFragment;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // PDF rendering is only supported on SDK 21 (Lollipop) and later
    if (Build.VERSION.SDK_INT < 21) {
      // No PDF support available, return immediately from activity
      finish();
      return;
    }
    setContentView(R.layout.selectpdfpage);
    linguist = ((CustomMapsApp) getApplication()).getLinguist();
    linguist.translateView(findViewById(R.id.root_view));
    ImageHelper.initializePreferredBitmapConfig(this);
    prepareUI();

    setSupportActionBar(findViewById(R.id.toolbar));

    if (savedInstanceState != null) {
      getSupportActionBar().setTitle(linguist.getString(R.string.select_map_page));
      pdfRendererFragment = (PdfRendererFragment) getSupportFragmentManager()
          .findFragmentByTag(CustomMaps.PDF_RENDERER_FRAGMENT_TAG);
      pdfFilename = savedInstanceState.getString(PDF_FILENAME);
      pdfContentUri = savedInstanceState.getParcelable(PDF_CONTENTURI);
      currentPageNum = savedInstanceState.getInt(CURRENT_PAGE);
    } else {
      getSupportActionBar().setTitle(linguist.getString(R.string.preparing_pdf_file));
      displayScrim(true);
      pdfFilename = getIntent().getStringExtra(PDF_FILENAME);
      pdfContentUri = getIntent().getParcelableExtra(PDF_CONTENTURI);
      currentPageNum = 0;
    }
    if (pdfRendererFragment == null) {
      pdfRendererFragment = new PdfRendererFragment();
      pdfRendererFragment.setRetainInstance(true);
      getSupportFragmentManager().beginTransaction()
          .add(pdfRendererFragment, CustomMaps.PDF_RENDERER_FRAGMENT_TAG)
          .commit();
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putInt(CURRENT_PAGE, currentPageNum);
    outState.putString(PDF_FILENAME, pdfFilename);
  }

  @Override
  protected void onResume() {
    super.onResume();
    Uri pdfUri = pdfContentUri != null ? pdfContentUri : Uri.fromFile(new File(pdfFilename));
    pdfRendererFragment.setPdfFile(pdfUri, this::pdfRendererReady);
  }

  private void pdfRendererReady() {
    lastPage = pdfRendererFragment.getPageCount() - 1;
    if (lastPage < 0) {
      Log.e(LOG_TAG, "SelectPdfPageActivity - Failed to open PDF: " + pdfFilename);
      finish();
      return;
    } else {
      Log.i(LOG_TAG, "SelectPdfPageActivity - PDF opened, page count: " + (lastPage + 1));
    }
    // Document has been opened, update activity title and render the first page
    getSupportActionBar().setTitle(linguist.getString(R.string.select_map_page));
    findViewById(R.id.blank).setVisibility(View.INVISIBLE);
    displayScrim(false);
    updateCurrentPage();
  }

  private void updateCurrentPage() {
    pageNumber.setText(String.format(Locale.US, "%d/%d", currentPageNum+1, lastPage+1));
    pdfRendererFragment.requestPreviewPage(currentPageNum, previewPageReceiver);
  }

  /** Moves to the previous page of the PDF document, if not already on the first page. */
  private void prevPage(View button) {
    if (currentPageNum <= 0) {
      currentPageNum = 0;
      return;
    }
    currentPageNum--;
    updateCurrentPage();
  }

  /** Moves to the next page of the PDF document, if not already on the last page */
  private void nextPage(View button) {
    if (currentPageNum >= lastPage) {
      currentPageNum = lastPage;
      return;
    }
    currentPageNum++;
    updateCurrentPage();
  }

  /** Rotate page image 90 degrees in clockwise direction. */
  private void rotateCw(View button) {
    pdfRendererFragment.rotateImage(PdfRendererFragment.Rotate.CW);
    updateCurrentPage();
  }

  /** Rotate page image 90 degrees in counter-clockwise direction. */
  private void rotateCcw(View button) {
    pdfRendererFragment.rotateImage(PdfRendererFragment.Rotate.CCW);
    updateCurrentPage();
  }

  private String generatePdfImageName() {
    String filename;
    if (pdfContentUri != null) {
      filename = FileUtil.resolveContentFileName(pdfContentUri);
      if (filename == null) {
        filename = pdfContentUri.getLastPathSegment();
      }
    } else {
      filename = new File(pdfFilename).getName();
    }
    // Replace spaces in the PDF filename with underscores
    filename = filename.replace(' ', '_');
    // Drop suffix, and replace it with ".jpg"
    if (filename.lastIndexOf('.') > 0) {
      filename = filename.substring(0, filename.lastIndexOf('.'));
    }
    return filename + ".jpg";
  }

  /**
   * Selects current PDF page to be used for a map. Executed when "Select"
   * menu item is clicked.
   */
  private boolean selectCurrentPage(MenuItem item) {
    // Disable the only menu item on screen
    Toolbar bottomBar = findViewById(R.id.bottombar);
    bottomBar.getMenu().getItem(0).setEnabled(false);
    // Start rendering full resolution version of the page
    String imageName = generatePdfImageName();
    pdfRendererFragment.requestFinalPage(currentPageNum, imageName, finalPageReceiver);
    return true;
  }

  /** Returns the high quality rendered selected page file to invoking activity. */
  private void returnSelectedPage() {
    String imageName = generatePdfImageName();
    File imageFile = pdfRendererFragment.getFinalPageFile(imageName);
    if (imageFile == null || !imageFile.exists()) {
      // Do not exit, since the selected page image file does not exist
      return;
    }
    Log.i(LOG_TAG, "Returning selected page image: " + imageFile);
    getSupportFragmentManager().beginTransaction().remove(pdfRendererFragment).commit();
    Intent result = getIntent().setData(Uri.fromFile(imageFile));
    setResult(RESULT_OK, result);
    finish();
  }

  @Override
  public void onBackPressed() {
    pdfRendererFragment.setPdfFile(null);
    getSupportFragmentManager().beginTransaction().remove(pdfRendererFragment).commit();
    setResult(RESULT_CANCELED);
    super.onBackPressed();
  }

  private void displayScrim(boolean makeVisible) {
    int mode = makeVisible ? View.VISIBLE : View.GONE;
    findViewById(R.id.scrim).setVisibility(mode);
  }

  private void prepareUI() {
    pageDisplay = findViewById(R.id.pageImage);
    pageNumber = findViewById(R.id.pageNumber);

    findViewById(R.id.prevPage).setOnClickListener(this::prevPage);
    findViewById(R.id.nextPage).setOnClickListener(this::nextPage);

    findViewById(R.id.rotateCw).setOnClickListener(this::rotateCw);
    findViewById(R.id.rotateCcw).setOnClickListener(this::rotateCcw);

    // Add "Select" action to bottom bar
    Toolbar bottomBar = findViewById(R.id.bottombar);
    bottomBar.getMenu()
        .add(1, 1, 1, linguist.getString(R.string.select_action))
        .setOnMenuItemClickListener(this::selectCurrentPage)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
  }

  private PdfRendererFragment.PageReceiver previewPageReceiver = new PdfRendererFragment.PageReceiver() {
    @Override
    public void pageRendering(int pageNum) {
      displayScrim(true);
    }

    @Override
    public void pageRendered(int pageNum, Bitmap pageImage) {
      displayScrim(false);
      if (pageNum == currentPageNum) {
        int pageRotation = pdfRendererFragment.getCurrentPageRotation();
        if (pageRotation == 0) {
          pageDisplay.setScaleType(ImageView.ScaleType.FIT_CENTER);
        } else {
          Matrix m = computePageDisplayMatrix(pageDisplay, pageImage, pageRotation);
          pageDisplay.setScaleType(ImageView.ScaleType.MATRIX);
          pageDisplay.setImageMatrix(m);
        }
        pageDisplay.setImageBitmap(pageImage);
        pageDisplay.invalidate();
      }
    }

    private Matrix computePageDisplayMatrix(ImageView display, Bitmap image, int rotation) {
      Matrix m = new Matrix();
      // Find out image width and height after rotation
      boolean isImageSideways = (rotation % 180) != 0;
      int imageW = isImageSideways ? image.getHeight() : image.getWidth();
      int imageH = isImageSideways ? image.getWidth() : image.getHeight();

      // Scale image to fully fit in display
      float xScale = display.getWidth() / (float) imageW;
      float yScale = display.getHeight() / (float) imageH;
      float scale = Math.min(xScale, yScale);
      m.postScale(scale, scale);

      // Center image in display area
      float dx = (display.getWidth() - scale * image.getWidth()) / 2f;
      float dy = (display.getHeight() - scale * image.getHeight()) / 2f;
      m.postTranslate(dx, dy);

      // Rotate image to requested orientation
      m.postRotate(rotation, display.getWidth() / 2f, display.getHeight() / 2f);
      return m;
    }
  };

  private PdfRendererFragment.PageReceiver finalPageReceiver = new PdfRendererFragment.PageReceiver() {
    @Override
    public void pageRendering(int pageNum) {
      displayScrim(true);
    }

    @Override
    public void pageRendered(int pageNum, Bitmap pageImage) {
      displayScrim(false);
      returnSelectedPage();
    }
  };
}
