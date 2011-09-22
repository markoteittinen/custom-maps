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

import com.custommapsapp.android.FileUtil;
import com.custommapsapp.android.MemoryUtil;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * PdfToJpgActivity converts web hosted PDFs to local JPG files that can be used
 * for creating offline maps.
 *
 * @author Marko Teittinen
 */
public class PdfToJpgActivity extends Activity {
  public static final int RESULT_FAILED = RESULT_FIRST_USER;

  private static final String LOG_TAG = "Custom Maps";
  private static final String DOC_VIEWER_PATTERN =
      "http://docs.google.com/viewer?url=%s&a=bi&pagenumber=%d&w=%d";

  private List<Bitmap> thumbnails;
  private Uri pdfUri;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    pdfUri = getIntent().getData();
    // Make sure IMAGE_DIR exists
    FileUtil.verifyImageDir();
  }

  @Override
  protected void onResume() {
    super.onResume();
    String pdfUrlString = pdfUri.toString();
    thumbnails = readThumbnails(pdfUrlString);
    // Find first page that was successfully rendered
    int firstPage = 1;
    while (!thumbnails.isEmpty() && thumbnails.get(0) == null) {
      firstPage++;
      thumbnails.remove(0);
    }
    if (thumbnails.isEmpty()) {
      // Display error message and return
      Toast.makeText(this, "Failed to convert PDF to image", Toast.LENGTH_LONG).show();
      Log.w(LOG_TAG, "Failed to load any page thumbnails from " + pdfUrlString);
      setResult(RESULT_FAILED);
    } else if (thumbnails.size() >= 1) {
      // Find thumbnail size, and then release its resources
      Bitmap thumbnail = thumbnails.get(0);
      int w = thumbnail.getWidth();
      int h = thumbnail.getHeight();
      clearThumbnails();
      // Figure out maximum size of the image (total # of pixels)
      int maxPixels = MemoryUtil.getMaxImagePixelCount(getApplication());
      Log.i(LOG_TAG, "Maximum image size in pixels: " + maxPixels);
      double scale = Math.sqrt(maxPixels / (w * h));
      w = (int) Math.floor(scale * w);
      try {
        Bitmap image = readBitmap(pdfUrlString, firstPage, w);
        String imageFileName = createJpegName(pdfUrlString);
        File imageFile = writeJpeg(image, imageFileName);
        getIntent().setData(Uri.fromFile(imageFile));
        setResult(RESULT_OK, getIntent());
      } catch (Exception e) {
        Toast.makeText(this, "Failed to convert PDF to image", Toast.LENGTH_LONG).show();
        Log.w(LOG_TAG, "Failed to save full size page to jpeg file from " + pdfUrlString, e);
        setResult(RESULT_FAILED);
      }
    } else {
      // More than one page, user must select
      Toast.makeText(this, "More than single page in PDF", Toast.LENGTH_LONG).show();
      Log.w(LOG_TAG, "More than single page in PDF: " + pdfUrlString);
      setResult(RESULT_FAILED);
      // TODO: create a selector instead and allow user to select page
      // to be used
    }
    finish();
  }

  // --------------------------------------------------------------------------
  // Data processing methods

  /**
   * Releases all resources used by bitmaps in thumbnails list.
   */
  private void clearThumbnails() {
    for (Bitmap image : thumbnails) {
      if (image != null) {
        image.recycle();
      }
    }
    thumbnails.clear();
    System.gc();
  }

  private URL createPngUrl(String pdfUrl, int page, int width) throws MalformedURLException {
    return new URL(String.format(DOC_VIEWER_PATTERN, Uri.encode(pdfUrl), page, width));
  }

  /**
   * Finds an unused jpg file name matching the given PDF URL.
   *
   * @param pdfUrl to be converted to jpg name
   * @return name part (w/o path) of the jpg to be created
   */
  private String createJpegName(String pdfUrl) {
    String name = null;
    // Verify that filename matches expectations
    if (pdfUrl.endsWith(".pdf") && pdfUrl.lastIndexOf('/') >= 0) {
      name = pdfUrl.substring(pdfUrl.lastIndexOf('/') + 1, pdfUrl.length() - 4);
    } else {
      // Unexpected filename, use default
      name = "mapimage";
    }
    int counter = 0;
    File f = composeImageFile(name, counter);
    while (f.exists()) {
      f = composeImageFile(name, ++counter);
    }
    return f.getName();
  }

  /**
   * Creates a File with jpg extension from the name and extension
   *
   * @param name base part of the name (base.ext)
   * @param counter for distinguishing from others with same name (base-n.ext)
   * @return File object created from name and counter
   */
  private File composeImageFile(String name, int counter) {
    return new File(FileUtil.IMAGE_DIR, name + (counter > 0 ? "-" + counter : "") + ".jpg");
  }

  /**
   * Creates thumbnail image for each page in a web hosted PDF. The returned
   * list will contain a 'null' for each page that failed conversion.
   *
   * @param pdfUrl String (URL) of the PDF (http or https)
   * @return List of Bitmaps, one per page, contains nulls for pages that could
   *         not be converted to images (to keep page numbering)
   */
  private List<Bitmap> readThumbnails(String pdfUrl) {
    List<Bitmap> thumbnails = new ArrayList<Bitmap>();
    int width = 200;
    for (int page = 1; page <= 10; page++) {
      try {
        Bitmap image = readBitmap(pdfUrl, page, width);
        if (image == null) {
          break;
        }
        thumbnails.add(image);
      } catch (IOException e) {
        thumbnails.add(null);
        Log.w(LOG_TAG, String.format("Failed to read thumbnail #%d from %s", page, pdfUrl), e);
      }
    }
    return thumbnails;
  }

  /**
   * Converts a single page of a web hosted PDF to a Bitmap.
   *
   * @param pdfUrl String (URL) of the PDF (http or https)
   * @param page number of page to convert (first page is 1)
   * @param width pixel width of the produced page
   * @return Bitmap of the page
   */
  private Bitmap readBitmap(String pdfUrl, int page, int width) throws IOException {
    HttpURLConnection conn = null;
    try {
      URL pngUrl = createPngUrl(pdfUrl, page, width);
      conn = (HttpURLConnection) pngUrl.openConnection();
      if (conn.getResponseCode() / 100 != 2) {
        Log.w(LOG_TAG, String.format(
            "Bitmap read failed (%d): %s", conn.getResponseCode(), pngUrl.toString()));
        return null;
      }
      InputStream in = conn.getInputStream();
      Bitmap image = BitmapFactory.decodeStream(new BufferedInputStream(in));
      in.close();
      return image;
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
  }

  /**
   * Writes a bitmap into a JPEG file.
   *
   * @param image to be written as JPEG into the file
   * @param filename name only of the file to write image into
   * @return File that was created
   * @throws IOException in case of failures
   */
  private File writeJpeg(Bitmap image, String filename) throws IOException {
    FileOutputStream fOut = null;
    try {
      File f = new File(FileUtil.IMAGE_DIR, filename);
      fOut = new FileOutputStream(f);
      BufferedOutputStream out = new BufferedOutputStream(fOut);
      image.compress(Bitmap.CompressFormat.JPEG, 85, out);
      out.flush();
      out.close();
      return f;
    } finally {
      if (fOut != null) {
        fOut.close();
      }
    }
  }
}
