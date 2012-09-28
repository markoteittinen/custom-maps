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

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * KmzDownloader downloads Custom Maps from URLs and displays the progress to
 * the user with option to cancel download. KmzDownloader intent should always
 * be launched with 'URL' extra to indicate file to be downloaded. Upon
 * completion KmzDownloader returns the full local path to the downloaded file
 * in 'LOCAL_FILE' extra.
 *
 * @author Marko Teittinen
 */
public class KmzDownloader extends Activity {
  private static final String PREFIX = "com.custommapsapp.android";
  public static final String URL = PREFIX + ".URL";
  public static final String LOCAL_FILE = PREFIX + ".LocalFile";

  private static final String DEFAULT_NAME = "custommap.kmz";

  // Note: UNKNOWN_SIZE matches "undefined content length" value returned by
  // URLConnection.getContentLength()
  private static final int UNKNOWN_SIZE = -1;

  private TextView urlLabel;
  private ViewSwitcher progressDisplay;
  private ProgressBar progressBar;
  private TextView progressDetails;

  private DownloadFile downloadTask;

  // TODO: Consider adding a map from URL to local file name to avoid
  // downloading the same map multiple times (store map in SharedPreferences)

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    prepareUI();

    String urlString = getIntent().getStringExtra(URL);
    URL mapUrl = null;
    try {
      if (urlString != null) {
        mapUrl = new URL(urlString);
      }
    } catch (MalformedURLException ex) {
      mapUrl = null;
    }
    if (mapUrl == null) {
      Log.e(CustomMaps.LOG_TAG, "KmzDownloader invoked with an invalid URL: " + urlString);
      cancelActivity(null);
      return;
    }

    urlLabel.setText(urlString);
    String localName = createLocalPath(mapUrl);
    if (localName == null) {
      return;
    }

    progressBar.setProgress(0);
    progressBar.setMax(100);
    progressDetails.setText(getString(R.string.progress_format, 0));
    if (progressDisplay.getCurrentView() != progressBar) {
      progressDisplay.showNext();
    }

    downloadTask = new DownloadFile(mapUrl, localName);
    downloadTask.execute();
  }

  private void updateProgress(int bytesDone, int totalSize) {
    displayProgressBar(totalSize != UNKNOWN_SIZE);
    if (totalSize != UNKNOWN_SIZE) {
      progressBar.setMax(totalSize);
      progressBar.setProgress(bytesDone);
    } else {
      progressDetails.setText(getString(R.string.progress_format, bytesDone / 1024));
    }
    progressDisplay.invalidate();
  }

  /**
   * Switch the UI to display either a progress bar or a spinner with details
   * about download progress.
   *
   * @param displayBar if {@code true} will display progress bar, otherwise will
   * display spinner and text view with downloaded byte count
   */
  private void displayProgressBar(boolean displayBar) {
    boolean needsSwitching =
        (displayBar ? progressDisplay.getCurrentView() != progressBar :
                      progressDisplay.getCurrentView() == progressBar);
    if (needsSwitching) {
      progressDisplay.showNext();
    }
  }

  private void cancelDownload() {
    if (downloadTask != null) {
      downloadTask.cancel(true);
    }
    cancelActivity(getString(R.string.download_canceled));
  }

  private void cancelActivity(String message) {
    if (message != null && message.length() > 0) {
      Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    setResult(RESULT_CANCELED);
    finish();
  }

  private void returnActivityResult(File localFile) {
    Toast.makeText(this, R.string.download_successful, Toast.LENGTH_SHORT).show();
    Intent resultIntent = getIntent();
    resultIntent.putExtra(LOCAL_FILE, localFile.getAbsolutePath());
    setResult(RESULT_OK, resultIntent);
    finish();
  }

  /**
   * Creates a new non-existing local path name where the map from a URL can be
   * stored. Never returns a filename matching an existing file.
   *
   * @param mapUrl URL where the map will be downloaded
   * @return Non-existing filename to which the map file should be saved
   */
  private String createLocalPath(URL mapUrl) {
    // Check that app data directory exists or can be created
    File localDir = FileUtil.getDataDirectory();
    if (!localDir.exists()) {
      cancelActivity(getString(R.string.download_no_datadir));
      return null;
    }
    // Find all existing filenames (use lower case to ignore case)
    Set<String> localNames = new HashSet<String>();
    for (String name : localDir.list()) {
      localNames.add(name.toLowerCase());
    }
    // Create filename suggestion
    // NOTE: can't use mapUrl.getFile() as it has a bug, will contain path+query
    String fileName = mapUrl.getPath();
    fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
    if (!fileName.toLowerCase().endsWith(".kmz")) {
      if (fileName.length() == 0) {
        fileName = DEFAULT_NAME;
      } else {
        fileName += ".kmz";
      }
    }
    // Find non-existing filename similar to suggestion
    String baseName = fileName.substring(0, fileName.length() - 4);
    int n = 2;
    while (localNames.contains(fileName.toLowerCase())) {
      fileName = String.format("%s-%d.kmz", baseName, n++);
    }
    File file = new File(localDir, fileName);
    return file.getAbsolutePath();
  }

  private void prepareUI() {
    setContentView(R.layout.kmzdownloader);
    urlLabel = (TextView) findViewById(R.id.urlLabel);
    progressDisplay = (ViewSwitcher) findViewById(R.id.progressView);
    progressBar = (ProgressBar) findViewById(R.id.progressBar);
    progressDetails = (TextView) findViewById(R.id.progressDetails);

    Button cancelButton = (Button) findViewById(R.id.cancelButton);
    cancelButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        cancelDownload();
      }
    });
  }

  // --------------------------------------------------------------------------
  // Inner class for downloading the file asynchronously in the background

  // if the download size can be determined, display progress bar w/o numbers
  // if the download size cannot be determined, display download numbers

  private class DownloadFile extends AsyncTask<Void, Integer, File> {
    private URL mapUrl;
    private String fileName;

    public DownloadFile(URL mapUrl, String fileName) {
      if (mapUrl == null || fileName == null) {
        throw new IllegalArgumentException("Null parameter values are not allowed");
      }
      this.mapUrl = mapUrl;
      this.fileName = fileName;
    }

    @Override
    protected File doInBackground(Void... params) {
      int bytesDownloaded = 0;
      int totalBytes = UNKNOWN_SIZE;

      HttpURLConnection conn = null;
      OutputStream out = null;
      BufferedInputStream in = null;
      File resultFile = new File(fileName);
      try {
        // Open connection to map file
        conn = openWithManualRedirect(mapUrl);
        int responseCode = conn.getResponseCode();
        String contentType = conn.getContentType();
        if (responseCode / 100 != 2 || contentType.startsWith("text/")) {
          throw new IOException(String.format("Invalid response from server: %d. Content type: %s",
            responseCode, contentType));
        }
        // Find size of the map file (or -1 if not known)
        totalBytes = conn.getContentLength();
        // Open the output file
        out = new BufferedOutputStream(new FileOutputStream(resultFile));

        // Download the file (in 1kB blocks)
        in = new BufferedInputStream(conn.getInputStream());
        int n;
        byte[] buffer = new byte[1024];
        while ((n = in.read(buffer)) > 0) {
          bytesDownloaded += n;
          out.write(buffer, 0, n);
          // Update progress in UI thread
          publishProgress(bytesDownloaded, totalBytes);
          // Interrupt download if cancellation is requested
          if (isCancelled()) {
            return null;
          }
        }
        out.flush();
      } catch (Exception ex) {
        // Error occurred during download, log it and return 'null' for error
        Log.e(CustomMaps.LOG_TAG,
              String.format("Map download failed from %s to %s", mapUrl, fileName), ex);
        return null;
      } finally {
        // All done, close open streams
        if (conn != null) {
          FileUtil.tryToClose(in);
          conn.disconnect();
        }
        FileUtil.tryToClose(out);
      }
      // Successful completion, return File object pointing to new map
      return resultFile;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
      int bytesDownloaded = values[0];
      int totalBytes = values[1];
      updateProgress(bytesDownloaded, totalBytes);
    }

    @Override
    protected void onPostExecute(File mapFile) {
      if (mapFile != null) {
        // Display download as completed
        updateProgress(100, 100);
        returnActivityResult(mapFile);
      } else {
        // An error occurred during the download, notify user
        cancelActivity(getString(R.string.download_failed, mapUrl));
      }
    }

    private HttpURLConnection openWithManualRedirect(URL url) throws IOException {
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(15000);
      conn.setReadTimeout(15000);
      int responseCode = conn.getResponseCode();

      if (responseCode / 100 == 3) {
        List<String> uris = conn.getHeaderFields().get("Location");
        if (uris == null || uris.isEmpty()) {
          throw new IOException("Redirect request did not provide new URL");
        } else {
          // Found redirected location(s) in header, use first
          url = new URL(uris.get(0));
        }
        conn.disconnect();
        // Return redirected connection, redirect again if necessary
        return openWithManualRedirect(url);
      } else {
        // Non-redirect response code found, return to caller
        return conn;
      }
    }
  }
}
