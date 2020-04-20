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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;

import com.custommapsapp.android.FileUtil;

/**
 * A {@link Fragment} that renders PDF pages into bitmaps so that they can be used in Custom Maps
 * app. By being a Fragment, it can continue preparing the PDF pages even if the device is rotated
 * triggering restart of the app.
 *
 * <p>To keep this Fragment around, call fragment.setRetainInstance(true), and when this Fragment
 * is no longer needed, call fragment.setRetainInstance(false);
 *
 * @author Marko Teittinen
 */
// PDF rendering requires API 21 (Android 5.0, Lollipop), don't use this fragment in older devices
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class PdfRendererFragment extends Fragment {

  /** Interface used to notify about completion of page rendering requests. */
  public interface PageReceiver {
    /**
     * Called when a requested page is not cached and needs to be rendered. It may be desirable to
     * display a "wait" animation (spinner or hourglass) to user. Always called in UI thread.
     *
     * @param pageNum page number for which rendering has started
     */
    void pageRendering(int pageNum);

    /**
     * Called when a requested page has been rendered and can be displayed. Note that the callback
     * may arrive after the activity has been stopped (if user rotates the phone, for example) in
     * which case the callback should be ignored.
     *
     * @param pageNum page number of the page that was rendered
     * @param pageImage image of the page (null if rendering failed)
     */
    void pageRendered(int pageNum, Bitmap pageImage);
  }

  /** Simple data structure storing the page image and its DPI information. */
  private class PageData {
    Bitmap image;
    int dpi;

    PageData(Bitmap image, int dpi) {
      this.image = image;
      this.dpi = dpi;
    }
  }

  /** Simple data structure storing information of a single page image. */
  private class PageImage {
    private File file;
    private Bitmap image;
    private ExifInterface exif;

    PageImage setFile(File file) {
      this.file = file;
      return this;
    }

    PageImage setImage(Bitmap image) {
      this.image = image;
      return this;
    }

    PageImage setExif(ExifInterface exif) {
      this.exif = exif;
      return this;
    }
  }

  public enum Rotate {
    CW,
    CCW
  }

  private static final int PREVIEW_DPI = 100;
  private static final int OUTPUT_DPI = 300;

  /** Single threaded background executor to avoid threading issues with cache. */
  private ExecutorService bgExecutor;
  private Handler mainThreadHandler;

  private PageCache pageCache;
  private Uri pdfUri;
  private int currentPageNum = -1;
  private PageImage currentPageImage;

  private PdfRenderer pdfRenderer;

  public PdfRendererFragment() {
    bgExecutor = Executors.newSingleThreadExecutor();
    mainThreadHandler = new Handler(Looper.getMainLooper());
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    pageCache = new PageCache(context);
    // Initialize page cache and pdfUri
    bgExecutor.submit(() -> pdfUri = pageCache.init());
  }

  @Override
  public void onDetach() {
    super.onDetach();
  }

  /**
   * Sets the PDF contents processed by this renderer. If the pdfUri does not match current pdfUri,
   * the page cache is cleared.
   */
  public void setPdfFile(Uri pdfUri) {
    setPdfFile(pdfUri, null);
  }

  public void setPdfFile(Uri pdfUri, Runnable readyCallback) {
    bgExecutor.submit(() -> setPdfFileBg(pdfUri, readyCallback));
  }

  public void requestPreviewPage(int pageNum, PageReceiver callback) {
    bgExecutor.submit(() -> renderPage(pageNum, null, PREVIEW_DPI, callback));
  }

  public void requestFinalPage(int pageNum, String fileName, PageReceiver callback) {
    bgExecutor.submit(() -> renderPage(pageNum, fileName, OUTPUT_DPI, callback));
  }

  public File getPreviewPageFile(int pageNum) {
    File pageFile = pageCache.getCacheFile(pageNum, PREVIEW_DPI);
    return pageFile.exists() ? pageFile : null;
  }

  public File getFinalPageFile(String fileName) {
    File pageFile = pageCache.getCacheFile(fileName);
    return pageFile.exists() ? pageFile : null;
  }

  public int getPageCount() {
    return pdfRenderer == null ? 0 : pdfRenderer.getPageCount();
  }

  public int getCurrentPageNum() {
    return currentPageNum;
  }

  public Bitmap getCurrentPage() {
    return currentPageImage == null ? null : currentPageImage.image;
  }

  /** Returns current page rotation in degrees to clockwise direction. */
  public int getCurrentPageRotation() {
    return currentPageImage == null ? 0 : currentPageImage.exif.getRotationDegrees();
  }

  public void rotateImage(Rotate direction) {
    if (currentPageImage == null) return;
    int degrees = direction == Rotate.CW ? 90 : -90;
    if (getCurrentPageRotation() == 0) {
      currentPageImage.exif.setAttribute(
          ExifInterface.TAG_ORIENTATION, "" + ExifInterface.ORIENTATION_NORMAL);
    }
    currentPageImage.exif.rotate(degrees);
    // Update EXIF attributes of the image file
    try {
      currentPageImage.exif.saveAttributes();
      currentPageImage.setExif(new ExifInterface(currentPageImage.file));
    } catch (IOException e) {
      Log.e(LOG_TAG, "Failed to rotate PDF page image");
    }
  }

  private void setPdfFileBg(Uri pdfUri, Runnable readyCallback) {
    if (pdfRenderer != null && pdfUri != null && pdfUri.equals(this.pdfUri)) {
      // PDF file did not change
      if (readyCallback != null) {
        mainThreadHandler.post(readyCallback);
      }
      return;
    }
    // Content changed, release memory references
    closePdfRenderer();
    currentPageImage = null;
    currentPageNum = -1;
    System.gc();

    this.pdfUri = pdfUri;
    pageCache.setPdfUri(pdfUri);
    if (pdfUri != null) {
      openPdfRenderer();
    }
    if (readyCallback != null) {
      mainThreadHandler.post(readyCallback);
    }
  }

  /**
   * Opens a PdfRenderer for current {@link #pdfUri}.
   *
   * @return true if the PDF file was opened successfully.
   */
  private boolean openPdfRenderer() {
    if (pdfRenderer != null || pdfUri == null) {
      String reason = pdfRenderer != null ? "PDF renderer is not null" : "PDF URI is null";
      Log.w(LOG_TAG, "Cannot open PDF. " + reason);
      return false;
    }
    try {
      if ("file".equals(pdfUri.getScheme())) {
        // Open PDF from file URI
        File file = new File(pdfUri.getPath());
        ParcelFileDescriptor fd =
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        pdfRenderer = new PdfRenderer(fd);
      } else if ("content".equals(pdfUri.getScheme()) && getContext() != null) {
        ParcelFileDescriptor fd = getContext()
            .getContentResolver()
            .openAssetFileDescriptor(pdfUri, "r")
            .getParcelFileDescriptor();
        pdfRenderer = new PdfRenderer(fd);
      } else {
        // Unsupported URI scheme, reset state
        Log.e(LOG_TAG, "Unsupported PDF Uri scheme: " + pdfUri.getScheme());
        setPdfFile(null);
        return false;
      }
      Log.i(LOG_TAG, "PdfRenderer opened successfully");
      return true;
    } catch (IOException ex) {
      Log.e(LOG_TAG, "Failed to open PDF: " + pdfUri, ex);
    }
    // Failed to open PDF file, reset state
    setPdfFile(null);
    return false;
  }

  /** Closes pdfRenderer if it is open. */
  private void closePdfRenderer() {
    if (pdfRenderer == null) {
      return;
    }
    pdfRenderer.close();
    pdfRenderer = null;
  }

  private void renderPage(int pageNum, String fileName, int dpi, PageReceiver callback) {
    if (pageNum < 0 || pdfRenderer.getPageCount() <= pageNum) {
      Log.w(LOG_TAG, "Invalid page number: " + pageNum);
      mainThreadHandler.post(() -> callback.pageRendered(pageNum, null));
      return;
    }
    // If the page is in cache, return it from there
    if (fileName == null && pageCache.isPageCached(pageNum, dpi)) {
      PageImage pageImage = pageCache.getCachedPage(pageNum, dpi);
      if (pageImage != null) {
        currentPageImage = pageImage;
        // Page image was successfully loaded from cache, return it
        mainThreadHandler.post(() -> callback.pageRendered(pageNum, pageImage.image));
        return;
      }
    }

    // Notify listener that page needs to be rendered, and may take some time
    mainThreadHandler.post(() -> callback.pageRendering(pageNum));

    try {
      PdfRenderer.Page pdfPage = pdfRenderer.openPage(pageNum);
      PageData pageData = getPageBitmapByDpi(pdfPage, dpi);
      if (pageData == null) {
        Log.e(LOG_TAG, "Page too large");
        mainThreadHandler.post(() -> callback.pageRendered(pageNum, null));
        return;
      }
      pdfPage.render(pageData.image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
      pdfPage.close();

      if (fileName == null) {
        // Cache page generating a name for the cached page image
        currentPageImage = pageCache.addPage(pageNum, pageData.dpi, pageData.image);
      } else {
        // Caller expects to find file with given name, cache page before returning
        pageCache.addPage(fileName, pageData.image, currentPageImage.exif.getRotationDegrees());
      }
      currentPageNum = pageNum;
      mainThreadHandler.post(() -> callback.pageRendered(pageNum, pageData.image));
    } catch (Exception ex) {
      Log.e(LOG_TAG, "Failed to render page", ex);
    }
  }

  /**
   * Returns a large bitmap for a page and the DPI to be used for rendering.
   *
   * @param pdfPage PDF page the bitmap will be allocated for.
   * @param maxDpi Maximum preferred DPI to be used for the page.
   * @return Blank white Bitmap for rendering the page on, and DPI of the page.
   */
  private PageData getPageBitmapByDpi(PdfRenderer.Page pdfPage, int maxDpi) {
    Bitmap bitmap;
    int[] dpiOptions = {300, 250, 200, 150, 100, 72};
    for (int dpi : dpiOptions) {
      if (dpi > maxDpi) {
        continue;
      }
      System.gc();
      float pageH = dpi / 72f * pdfPage.getHeight();
      float pageW = dpi / 72f * pdfPage.getWidth();
      try {
        bitmap = Bitmap.createBitmap(Math.round(pageW), Math.round(pageH),
            Bitmap.Config.ARGB_8888);
        Log.i(LOG_TAG, String.format("Created page bitmap at %d dpi (%.0f x %.0f)",
            dpi, pageW, pageH));
        bitmap.eraseColor(0xffffffff);  // or 0xfff2f2f2 for "paper white"
        return new PageData(bitmap, dpi);
      } catch (OutOfMemoryError err) {
        Log.w(LOG_TAG, String.format("Page bitmap failed at %d dpi (%.0f x %.0f, %.1f Mp)",
            dpi, pageW, pageH, pageW * pageH / 1e6));
        System.gc();
        // Fall-through to retry (or give up)
      }
    }
    return null;
  }

  // --------------------------------------------------------------------------

  /**
   * PageCache manages a cache directory where rendered PDF pages are stored as images.
   *
   * <p>PageCache contains URI_FILE containing the Uri of the PDF being cached, and one image file
   * for each cached page. The absence of URI_FILE indicates no pages are being cached.
   */
  private class PageCache {
    /** Name of the file containing Uri of currently cached PDF. */
    private static final String URI_FILE = "uri.txt";

    private File cacheDir;
    private Uri pdfUri;

    private PageCache(Context context) {
      cacheDir = new File(context.getCacheDir(), "/pdfpages");
      if (!cacheDir.exists()) {
        cacheDir.mkdirs();
      }
    }

    /** Initializes the cache and returns the current PDF Uri. */
    private Uri init() {
      pdfUri = readPdfUriFile();
      if (pdfUri == null) {
        // No PDF Uri stored, clear cache
        clear();
      }
      // Return the current PDF Uri (there might be cached pages for that PDF in cache)
      return pdfUri;
    }

    private void setPdfUri(Uri pdfUri) {
      if (pdfUri != null && pdfUri.equals(this.pdfUri)) {
        // PDF Uri did not change
        return;
      }
      // PDF Uri changed, clear old cache
      clear();

      this.pdfUri = pdfUri;
      if (pdfUri != null) {
        writePdfUriFile(pdfUri.toString());
      }
    }

    private void clear() {
      for (File f : cacheDir.listFiles()) {
        f.delete();
      }
      pdfUri = null;
    }

    private void writePdfUriFile(String pdfUri) {
      if (pdfUri == null) {
        clear();
        return;
      }

      File uriFile = new File(cacheDir, URI_FILE);
      try (BufferedWriter out = new BufferedWriter(new FileWriter(uriFile))) {
        out.write(pdfUri);
      } catch (IOException ex) {
        Log.e(LOG_TAG, "Failed to write PDF Uri file", ex);
      }
    }

    private Uri readPdfUriFile() {
      File uriFile = new File(cacheDir, URI_FILE);
      if (!uriFile.exists()) {
        return null;
      }
      // Read the stored PDF Uri from the file
      try (BufferedReader in = new BufferedReader(new FileReader(uriFile))) {
        pdfUri = Uri.parse(FileUtil.readTextFully(in));
        return pdfUri;
      } catch (IOException ex) {
        Log.e(LOG_TAG, "Failed to read PDF Uri file", ex);
      }
      return null;
    }

    private boolean isPageCached(int pageNum, int dpi) {
      return getCacheFile(pageNum, dpi).exists();
    }

    private PageImage getCachedPage(int pageNum, int dpi) {
      File pageFile = getCacheFile(pageNum, dpi);
      try (InputStream in = new FileInputStream(pageFile)) {
        Bitmap pageImage = BitmapFactory.decodeStream(in);
        ExifInterface exif = new ExifInterface(pageFile);
        return new PageImage()
            .setFile(pageFile)
            .setImage(pageImage)
            .setExif(exif);
      } catch (IOException ex) {
        Log.e(LOG_TAG, "Failed to read cached PDF page " + pageNum, ex);
      }
      return null;
    }

    private PageImage addPage(int pageNum, int dpi, Bitmap image) {
      File pageFile = getCacheFile(pageNum, dpi);
      return addPage(pageFile, image, 0);
    }

    private PageImage addPage(String fileName, Bitmap image, int rotationDegrees) {
      File pageFile = getCacheFile(fileName);
      return addPage(pageFile, image, rotationDegrees);
    }

    private PageImage addPage(File pageFile, Bitmap image, int rotationDegrees) {
      try (OutputStream out = new FileOutputStream(pageFile)) {
        image.compress(Bitmap.CompressFormat.JPEG, 90, out);
        out.flush();
        out.close();

        ExifInterface exif = new ExifInterface(pageFile);
        if (rotationDegrees > 0) {
          exif.setAttribute(ExifInterface.TAG_ORIENTATION, "" + ExifInterface.ORIENTATION_NORMAL);
          exif.rotate(rotationDegrees);
          exif.saveAttributes();
        }
        return new PageImage()
            .setFile(pageFile)
            .setImage(image)
            .setExif(exif);
      } catch (IOException ex) {
        Log.e(LOG_TAG, "Failed to cache PDF page " + pageFile, ex);
        return null;
      }
    }

    private File getCacheFile(int pageNum, int dpi) {
      return getCacheFile(String.format(Locale.US, "page_%d_%d.jpg", pageNum, dpi));
    }

    private File getCacheFile(String fileName) {
      return new File(cacheDir, fileName);
    }
  }
}
