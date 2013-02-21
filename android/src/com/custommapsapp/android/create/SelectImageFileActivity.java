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

import com.custommapsapp.android.CustomMaps;
import com.custommapsapp.android.FileUtil;
import com.custommapsapp.android.HelpDialogManager;
import com.custommapsapp.android.ImageHelper;
import com.custommapsapp.android.MemoryUtil;
import com.custommapsapp.android.PtSizeFixer;
import com.custommapsapp.android.R;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * SelectImageFileActivity allows the user to select an image on the SD card to
 * create a map from.
 *
 * @author Marko Teittinen
 */
public class SelectImageFileActivity extends Activity {
  private static final String EXTRA_PREFIX = "com.custommapsapp.android";
  public static final String BITMAP_FILE = EXTRA_PREFIX + ".BitmapFile";
  private static final String SAVED_CURRENTDIR = EXTRA_PREFIX + ".CurrentDir";

  private static final int MENU_PHOTOS = 1;
  private static final int MENU_DOWNLOADS = 2;
  private static final int MENU_SD_ROOT = 3;

  private ProgressBar progress;
  private ListView fileListView;
  private ArrayAdapter<ImageFile> imageFileList;
  private File currentDir;
  private HelpDialogManager helpDialogManager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    boolean ptSizeFixNeeded = PtSizeFixer.isFixNeeded(this);
    setContentView(R.layout.selectfile);

    currentDir = FileUtil.getSdRoot();

    prepareUI();
    if (ptSizeFixNeeded) {
      PtSizeFixer.fixView(fileListView.getRootView());
    }

    helpDialogManager = new HelpDialogManager(this, HelpDialogManager.HELP_SELECT_IMAGE_FILE,
                                              getString(R.string.select_image_help));
  }

  @Override
  protected void onResume() {
    super.onResume();
    updateImageFileList();
    helpDialogManager.onResume();
  }

  @Override
  protected void onPause() {
    helpDialogManager.onPause();
    clearImageFileList();
    super.onPause();
  }

  // --------------------------------------------------------------------------
  // Instance state management

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    helpDialogManager.onSaveInstanceState(outState);
    if (currentDir != null) {
      outState.putString(SAVED_CURRENTDIR, currentDir.getAbsolutePath());
    }
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    helpDialogManager.onRestoreInstanceState(savedInstanceState);
    String savedPath = savedInstanceState.getString(SAVED_CURRENTDIR);
    if (savedPath != null) {
      currentDir = new File(savedPath);
    }
  }

  // --------------------------------------------------------------------------
  // Options menu

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    File dir = FileUtil.getPhotosDirectory();
    if (dir.exists() && dir.isDirectory()) {
      menu.add(Menu.NONE, MENU_PHOTOS, Menu.NONE, R.string.photos)
          .setIcon(android.R.drawable.ic_menu_gallery);
    }
    dir = FileUtil.getDownloadsDirectory();
    if (dir.exists() && dir.isDirectory()) {
      menu.add(Menu.NONE, MENU_DOWNLOADS, Menu.NONE, R.string.downloads)
          .setIcon(R.drawable.ic_menu_my_downloads);
    }
    menu.add(Menu.NONE, MENU_SD_ROOT, Menu.NONE, R.string.sdcard)
        .setIcon(android.R.drawable.ic_menu_save);
    helpDialogManager.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    File jumpTo = null;
    switch (item.getItemId()) {
      case MENU_PHOTOS:
        jumpTo = FileUtil.getPhotosDirectory();
        break;
      case MENU_DOWNLOADS:
        jumpTo = FileUtil.getDownloadsDirectory();
        break;
      case MENU_SD_ROOT:
        jumpTo = FileUtil.getSdRoot();
        break;
      default:
        helpDialogManager.onOptionsItemSelected(item);
        break;
    }
    if (jumpTo != null) {
      clearImageFileList();
      currentDir = jumpTo;
      updateImageFileList();
    }
    return true;
  }

  // --------------------------------------------------------------------------
  // Dialog management methods

  @Override
  protected Dialog onCreateDialog(int id) {
    return helpDialogManager.onCreateDialog(id);
  }

  @Override
  protected void onPrepareDialog(int id, Dialog dialog) {
    helpDialogManager.onPrepareDialog(id, dialog);
  }

  // --------------------------------------------------------------------------
  // ProgressBar helper methods

  /**
   * Shows or hides the progress bar on top of screen.
   */
  private void showProgress(boolean visible) {
    progress.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
  }

  /**
   * Updates the value on the progress bar on top of the screen.
   * @param percent value between [0, 100]
   */
  private void setProgressValue(int percent) {
    percent = Math.min(100, Math.max(0, percent));
    progress.setProgress(percent);
    if (percent == 100) {
      progress.postDelayed(hideProgressBar, 1000);
    }
  }

  // --------------------------------------------------------------------------
  // Methods related to completion of activity

  /**
   * Overrides back button behavior on Android 2.0 and later. Ignored in 1.6.
   */
  public void onBackPressed() {
    if (currentDir == null || isSdRootDir(currentDir)) {
      setResult(RESULT_CANCELED);
      finish();
    } else {
      clearImageFileList();
      File parent = currentDir.getParentFile();
      if (parent == null) {
        try {
          currentDir = currentDir.getCanonicalFile();
        } catch (IOException ex) {
          // ignore, just keep currentDir as is
        }
        parent = currentDir.getParentFile();
        if (parent == null) {
          setResult(RESULT_CANCELED);
          finish();
        }
      }
      currentDir = parent;
      updateImageFileList();
    }
  }

  /**
   * Returns absolute path to a file as an activity result.
   *
   * @param f (non-null) File to be returned from activity
   */
  private void returnImageFile(File f) {
    Intent result = getIntent();
    result.putExtra(BITMAP_FILE, f.getAbsolutePath());
    setResult(RESULT_OK, result);
    finish();
  }

  // --------------------------------------------------------------------------
  // Image file list management

  /**
   * Clears image file list
   */
  private void clearImageFileList() {
    for (int pos = 0; pos < imageFileList.getCount(); pos++) {
      ImageFile imgFile = imageFileList.getItem(pos);
      if (!imgFile.isDirectory()) {
        imgFile.recycleBitmap();
      }
    }
    imageFileList.clear();
    fileListView.scrollTo(0, 0);
    System.gc();
  }

  /**
   * Updates all text entries in image file list synchronously and kicks off
   * an AsyncTask to update thumbnails for the image files.
   */
  private void updateImageFileList() {
    if (currentDir == null) {
      return;
    }
    File[] fileArray = currentDir.listFiles();
    // Prevent app from force closing if currentDir somehow is non-directory
    while (fileArray == null) {
      currentDir = currentDir.getParentFile();
      if (currentDir == null) {
        currentDir = FileUtil.getSdRoot();
      }
      fileArray = currentDir.listFiles();
    }
    List<ImageFile> dirListing = new ArrayList<ImageFile>();
    if (!isSdRootDir(currentDir)) {
      dirListing.add(new ImageFile(new File(currentDir, "..")));
    }
    for (File file : fileArray) {
      String name = file.getName();
      if (name.startsWith(".")) {
        continue;
      } else if (file.isDirectory() || isImageFilename(name)) {
        dirListing.add(new ImageFile(file));
      } else {
        // Skip others
        continue;
      }
    }
    // Sort image files by name except in photos dir list most recent first
    Comparator<ImageFile> sorter = imageFileNameSorter;
    if (currentDir.getAbsolutePath().equals(FileUtil.getPhotosDirectory().getAbsolutePath())) {
      sorter = imageFileDateSorter;
    }
    Collections.sort(dirListing, sorter);
    for (ImageFile imgFile : dirListing) {
      imageFileList.add(imgFile);
    }
    // Scroll to top of the list
    fileListView.setSelectionAfterHeaderView();

    new LoadThumbnailsTask().execute(dirListing.toArray(new ImageFile[dirListing.size()]));
  }

  /**
   * Repaints all items in the list.
   */
  private void refreshImageFileList() {
    imageFileList.notifyDataSetChanged();
  }

  // --------------------------------------------------------------------------
  // Helper methods for files and images

  /**
   * Checks if a file points to root of SD card
   */
  private boolean isSdRootDir(File f) {
    String filePath = FileUtil.getBestPath(f);
    String sdRootPath = FileUtil.getSdRoot().getAbsolutePath();
    return filePath.equals(sdRootPath);
  }

  /**
   * Checks if a filename is likely an image.
   */
  private boolean isImageFilename(String name) {
    name = name.toLowerCase();
    return (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
        || name.endsWith(".gif"));
  }

  /**
   * Decodes the image boundaries of the given image file
   *
   * @param file Image file to be checked
   * @return BitmapFactory.Options containing the decoded image size
   */
  private BitmapFactory.Options decodeImageBounds(File file) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inScaled = false;
    options.inTargetDensity = 0;
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    return options;
  }

  /**
   * Limits the given file to fit in the memory of this device. If the image is
   * larger, resizes it and stores it into a temporary file on SD card.
   *
   * @param origImage Image file to be checked
   * @return the original file, if it was within limits, otherwise the new file
   */
  private File limitImageSize(File origImage) {
    BitmapFactory.Options options = decodeImageBounds(origImage);
    int pixelCount = options.outWidth * options.outHeight;
    // If image is small enough, return original file reference
    int maxPixels = MemoryUtil.getMaxImagePixelCount(getApplication());
    if (pixelCount <= maxPixels) {
      // Selected image is small enough, not resizing
      return origImage;
    }
    // Need to resize image, but first verify target directory exists
    if (!FileUtil.verifyImageDir()) {
      Log.e(CustomMaps.LOG_TAG, "Failed to create directory for resized image file " +
            FileUtil.getTmpImagePath());
      // Attempt to use full size image
      return origImage;
    }

    // Prepare factory options to scale image into suitable size
    options = new BitmapFactory.Options();
    options.inPreferredConfig = Bitmap.Config.RGB_565;
    // Compute necessary scaling factor
    DisplayMetrics metrics = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(metrics);
    options.inTargetDensity = metrics.densityDpi;
    options.inDensity =
        (int) FloatMath.ceil(FloatMath.sqrt(pixelCount / (float) maxPixels) * metrics.densityDpi);
    options.inScaled = true;
    // Garbage collect to maximize available memory for resizing
    System.gc();
    Bitmap image = null;
    try {
      // Load resized image (may throw OutOfMemoryError)
      image = BitmapFactory.decodeFile(origImage.getAbsolutePath(), options);
      if (image == null) {
        Log.e(CustomMaps.LOG_TAG, String.format("Failed to read image: %s, size: %.2f MP",
            origImage.getAbsolutePath(), pixelCount / 1e6f));
        return origImage;
      }
      // Successfully resized, save into temporary file
      FileOutputStream out = new FileOutputStream(FileUtil.getTmpImageFile());
      image.compress(Bitmap.CompressFormat.JPEG, 85, out);
      out.close();
      // Release memory used by the image
      image.recycle();
      image = null;
      // Copy orientation information, and return resized image
      File resizedImage = FileUtil.getTmpImageFile();
      copyImageOrientation(origImage, resizedImage);
      return resizedImage;
    } catch (OutOfMemoryError err) {
      Log.e(CustomMaps.LOG_TAG, "Failed to resize large image", err);
    } catch (IOException ex) {
      Log.e(CustomMaps.LOG_TAG, "Failed to save resized image", ex);
    } finally {
      if (image != null && !image.isRecycled()) {
        image.recycle();
      }
    }
    // Resize or save image failed, return full size image
    return origImage;
  }

  /**
   * Copies EXIF image orientation info from source to target.
   *
   * @param source
   * @param target
   */
  private void copyImageOrientation(File source, File target) {
    int orientation = ImageHelper.readOrientation(source.getAbsolutePath());
    ImageHelper.writeOrientation(target.getAbsolutePath(), orientation);
  }

  // --------------------------------------------------------------------------
  // UI management

  /**
   * Displays a short error message on screen as Toast.
   */
  private void displayError(String message) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  private void prepareUI() {
    progress = (ProgressBar) findViewById(R.id.progressBar);
    fileListView = (ListView) findViewById(R.id.filelist);
    imageFileList = new ImageFileAdapter(this);
    fileListView.setAdapter(imageFileList);
    fileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapterView, View itemView, int position, long id) {
        ImageFile selected = imageFileList.getItem(position);
        if (selected.getFile().isDirectory()) {
          clearImageFileList();
          if (selected.getFile().getName().equals("..")) {
            currentDir = currentDir.getParentFile();
          } else {
            currentDir = selected.getFile();
          }
          updateImageFileList();
        } else if (selected.getImage(true) != null) {
          File f = limitImageSize(selected.getFile());
          clearImageFileList();
          returnImageFile(f);
        } else {
          displayError(getString(R.string.select_image_invalid, selected.getFile().getName()));
        }
      }
    });
  }

  // --------------------------------------------------------------------------
  // ImageFile class and helpers

  // imageFileNameSorter sorts all entries by name, directories first
  private Comparator<ImageFile> imageFileNameSorter = new Comparator<ImageFile>() {
    @Override
    public int compare(ImageFile img1, ImageFile img2) {
      File f1 = img1.getFile();
      File f2 = img2.getFile();
      if (f1.isDirectory() == f2.isDirectory()) {
        // Both are directories or both are files, sort alphabetically
        return String.CASE_INSENSITIVE_ORDER.compare(f1.getName(), f2.getName());
      }
      // Only one is directory, sort it first
      return (f1.isDirectory() ? -1 : 1);
    }
  };

  // imageFileDateSorter sorts directories by name, files (images) by date
  private Comparator<ImageFile> imageFileDateSorter = new Comparator<ImageFile>() {
    @Override
    public int compare(ImageFile img1, ImageFile img2) {
      File f1 = img1.getFile();
      File f2 = img2.getFile();
      if (f1.isDirectory() != f2.isDirectory()) {
        // Only one is directory, sort it first
        return (f1.isDirectory() ? -1 : 1);
      } else if (f1.isDirectory()) {
        // Both are directories, sort alphabetically
        return String.CASE_INSENSITIVE_ORDER.compare(f1.getName(), f2.getName());
      }
      // Both are files, sort by date (newest first)
      long diff = f1.lastModified() - f2.lastModified();
      return -(diff < 0 ? -1 : (0 < diff ? 1 : 0));
    }
  };

  private class ImageFile {
    private File file;
    private Bitmap image;
    private boolean isImageSet;

    ImageFile(File file) {
      this.file = file;
      isImageSet = false;
    }

    public File getFile() {
      return file;
    }

    public boolean isDirectory() {
      return file.isDirectory();
    }

    public void setImage(Bitmap image) {
      this.image = image;
      isImageSet = true;
    }

    public Bitmap getImage(boolean validate) {
      if (!isImageSet && validate) {
        setImage(loadThumbnail());
      }
      return image;
    }

    public synchronized Bitmap loadThumbnail() {
      BitmapFactory.Options options = decodeImageBounds(file);
      int w = options.outWidth;
      int h = options.outHeight;
      if (w < 0) {
        return null;
      }
      // Target max dimensions: 150 x 100 (w x h)
      float divider = Math.max(w / 150f, h / 100f);
      options.inJustDecodeBounds = false;
      // Use inSampleSize to get close to target dimension
      if (divider >= 16) {
        options.inSampleSize = 16;
      } else if (divider >= 8) {
        options.inSampleSize = 8;
      } else if (divider >= 4) {
        options.inSampleSize = 4;
      } else if (divider >= 2) {
        options.inSampleSize = 2;
      }
      // Match the target dimension by scaling
      divider = divider / options.inSampleSize;
      options.inScaled = true;
      options.inTargetDensity = getResources().getDisplayMetrics().densityDpi;
      options.inDensity = (int) FloatMath.ceil(options.inTargetDensity * divider);
      return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    public void recycleBitmap() {
      if (image != null) {
        Bitmap tmp = image;
        image = null;
        if (!tmp.isRecycled()) {
          tmp.recycle();
        }
      }
    }
  }

  /**
   * ListAdapter for ImageFile objects
   */
  private class ImageFileAdapter extends ArrayAdapter<ImageFile> {
    public ImageFileAdapter(Context context) {
      super(context, 0);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        convertView = LayoutInflater.from(getContext()).inflate(R.layout.imagefilelist, null);
        if (PtSizeFixer.isFixNeeded((Activity) null)) {
          PtSizeFixer.fixView(convertView);
        }
      }
      ImageView imageView = (ImageView) convertView.findViewById(R.id.thumbnail);
      TextView textView = (TextView) convertView.findViewById(R.id.nameField);
      ImageFile imageFile = getItem(position);
      if (imageFile.getImage(false) != null) {
        imageView.setImageBitmap(imageFile.getImage(false));
        imageView.setVisibility(View.VISIBLE);
      } else {
        // No image available (directory?), remove unused image area from view
        imageView.setVisibility(View.GONE);
        imageView.setImageBitmap(null);
      }
      textView.setText(imageFile.getFile().getName() + (imageFile.isDirectory() ? "/" : ""));
      return convertView;
    }
  }

  // --------------------------------------------------------------------------
  // AsyncTask to load thumbnails in the background

  private class LoadThumbnailsTask extends AsyncTask<ImageFile, Integer, Void> {
    long startTime = -1;
    boolean progressShown = false;

    @Override
    protected void onPreExecute() {
      setProgressValue(0);
      showProgress(false);
      startTime = SystemClock.elapsedRealtime();
      progressShown = false;
    }

    @Override
    protected Void doInBackground(ImageFile... imageFileParams) {
      // Copy images to a list
      List<ImageFile> imageFiles = new ArrayList<ImageFile>();
      for (ImageFile imageFile : imageFileParams) {
        imageFiles.add(imageFile);
      }

      // 'i' tracks progress, 'imageLoaded' tracks if list needs to be refreshed
      int i = 0;
      boolean imageLoaded = false;
      for (ImageFile imageFile : imageFiles) {
        i++;
        if (!imageFile.isDirectory()) {
          Bitmap thumbnail = imageFile.loadThumbnail();
          imageLoaded = (thumbnail != null);
          imageFile.setImage(thumbnail);
        }
        // First param is progress value, second boolean flag (as int)
        publishProgress(Math.round(100f * i / imageFiles.size()), imageLoaded ? 1 : 0);
        // reset image loading flag
        imageLoaded = false;
      }
      return null;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
      setProgressValue(values[0]);
      boolean showProgress = (timeElapsed() > 1000);
      if (values[1] > 0) {
        refreshImageFileList();
        showProgress = true;
      }
      if (!progressShown && showProgress) {
        showProgress(true);
        progressShown = true;
      }
    }

    @Override
    protected void onPostExecute(Void result) {
      if (progressShown) {
        progress.postDelayed(hideProgressBar, timeElapsed() > 2000 ? 1000 : 0);
        progressShown = false;
      }
    }

    private long timeElapsed() {
      return SystemClock.elapsedRealtime() - startTime;
    }
  }

  // Simple runnable to hide progress bar
  private Runnable hideProgressBar = new Runnable() {
    @Override
    public void run() {
      showProgress(false);
    }
  };
}
