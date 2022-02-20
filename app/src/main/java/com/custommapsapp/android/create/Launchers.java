package com.custommapsapp.android.create;

import java.io.File;
import java.util.List;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.util.Log;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.maps.model.LatLng;
import com.custommapsapp.android.CustomMaps;
import org.jetbrains.annotations.Contract;

/** Wrapper class for launching "helper" activities for MapEditor. */
public class Launchers {
  /**
   * Takes a content Uri pointing at a PDF file, and allows the user to select a page from it.
   * Returns a file Uri for a jpg image file of the page user selected, or null if the user
   * cancelled the action.
   */
  public static class SelectPdfPage extends ActivityResultContract<Uri, Uri> {
    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, Uri pdfContentUri) {
      return new Intent(context, SelectPdfPageActivity.class)
          .putExtra(SelectPdfPageActivity.PDF_CONTENTURI, pdfContentUri);
    }

    /**
     * Returns a Uri for a (jpg) image file containing the page user selected, or null if user
     * cancelled the action.
     */
    @Override
    public Uri parseResult(int resultCode, @Nullable Intent intent) {
      return wasCancelled(resultCode, intent) ? null : intent.getData();
    }
  }

  //---------------------------------------------------------------------------

  /**
   * Allows user to select an additional point on a map image to be linked with a map point. Takes
   * as input a File reference to the map image and a list of currently existing tiepoints. Returns
   * a new TiePoint instance (w/o geolocation), or null if the user cancelled image point selection.
   */
  public static class SelectImagePoint
      extends ActivityResultContract<SelectImagePointInput, TiePoint> {
    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, SelectImagePointInput input) {
      Intent selectImagePoint = new Intent(context, BitmapPointActivity.class)
          .putExtra(BitmapPointActivity.BITMAP_FILE, input.mapImageFile.getAbsolutePath());
      if (input.tiepoints != null && !input.tiepoints.isEmpty()) {
        int[] pointArray = new int[2 * input.tiepoints.size()];
        int pos = 0;
        for (TiePoint point : input.tiepoints) {
          pointArray[pos++] = point.getImagePoint().x;
          pointArray[pos++] = point.getImagePoint().y;
        }
        selectImagePoint.putExtra(BitmapPointActivity.TIEPOINTS, pointArray);
      }
      return selectImagePoint;
    }

    @Override
    public TiePoint parseResult(int resultCode, @Nullable Intent intent) {
      if (wasCancelled(resultCode, intent)) {
        return null;
      }
      int[] selectedPoint = intent.getIntArrayExtra(BitmapPointActivity.SELECTED_POINT);
      Point imagePoint = new Point(selectedPoint[0], selectedPoint[1]);
      Point offsetInSnippet = new Point(selectedPoint[2], selectedPoint[3]);
      byte[] imageSnippet = intent.getByteArrayExtra(BitmapPointActivity.BITMAP_DATA);
      return new TiePoint(imagePoint, imageSnippet, offsetInSnippet);
    }
  }

  /** Input parameters for image point selection activity. */
  public static class SelectImagePointInput {
    File mapImageFile;
    List<TiePoint> tiepoints;
  }

  //---------------------------------------------------------------------------

  /**
   * Allows user to select or modify the geographic location match a selected map image point. Takes
   * as input the TiePoint for which geolocation is adjusted and its index (to be returned with
   * result), and whether this is the first tiepoint. First tiepoint will use default values for
   * UI (translucency, scale, and rotation of the map image snippet), but adjusting other points
   * will restore the last used values.
   *
   * Returns the selected geolocation (LatLng) and tiepoint index (which was passed in as input), or
   * null if the action was cancelled.
   */
  public static class SelectMapPoint
      extends ActivityResultContract<SelectMapPointInput, SelectMapPointOutput> {
    private static final String TIEPOINT_INDEX = "SelectMapPoint.TiepointIndex";

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, SelectMapPointInput input) {
      Intent selectMapPoint = new Intent(context, TiePointActivity.class);
      Point p = input.tiepoint.getOffset();
      selectMapPoint.putExtra(TiePointActivity.IMAGE_POINT, new int[] { p.x, p.y });
      LatLng geoLocation = input.tiepoint.getGeoPoint();
      if (geoLocation != null) {
        selectMapPoint.putExtra(TiePointActivity.GEO_POINT, geoLocation);
      }
      return selectMapPoint
          .putExtra(TiePointActivity.BITMAP_DATA, input.tiepoint.getJpgData())
          .putExtra(TiePointActivity.RESTORE_SETTINGS, !input.isFirstTiePoint)
          .putExtra(TIEPOINT_INDEX, input.tiepointIndex);
    }

    @Override
    public SelectMapPointOutput parseResult(int resultCode, @Nullable Intent intent) {
      if (wasCancelled(resultCode, intent)) {
        return null;
      }
      LatLng selectedMapPoint = intent.getParcelableExtra(TiePointActivity.GEO_POINT);
      if (selectedMapPoint == null) {
        Log.e(CustomMaps.LOG_TAG, "No geo coordinates found from TiePointActivity");
        return null;
      }
      SelectMapPointOutput result = new SelectMapPointOutput();
      result.mapPoint = selectedMapPoint;
      result.tiepointIndex = intent.getIntExtra(TIEPOINT_INDEX, -1);
      return result;
    }
  }

  public static class SelectMapPointInput {
    TiePoint tiepoint;
    int tiepointIndex;
    boolean isFirstTiePoint;
  }

  public static class SelectMapPointOutput {
    LatLng mapPoint;
    int tiepointIndex;
  }

  //---------------------------------------------------------------------------

  public static class PreviewMap extends ActivityResultContract<PreviewMapInput, List<LatLng>> {
    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, PreviewMapInput input) {
      if (input.tiepoints == null || input.tiepoints.size() < 2) {
        throw new IllegalArgumentException("Must have at least two tiepoints for previewing");
      }
      int[] imagePointArray = new int[2 * input.tiepoints.size()];
      LatLng[] geoPointArray = new LatLng[input.tiepoints.size()];
      int i = 0;
      for (TiePoint tiepoint : input.tiepoints) {
        imagePointArray[2 * i] = tiepoint.getImagePoint().x;
        imagePointArray[2 * i + 1] = tiepoint.getImagePoint().y;
        geoPointArray[i++] = tiepoint.getGeoPoint();
      }
      return new Intent(context, PreviewMapActivity.class)
          .putExtra(PreviewMapActivity.BITMAP_FILE, input.mapImageFile.getAbsolutePath())
          .putExtra(PreviewMapActivity.IMAGE_POINTS, imagePointArray)
          .putExtra(PreviewMapActivity.TIEPOINTS, geoPointArray);
    }

    @Override
    public List<LatLng> parseResult(int resultCode, @Nullable Intent intent) {
      if (wasCancelled(resultCode, intent)) {
        return null;
      }
      return intent.getParcelableArrayListExtra(PreviewMapActivity.CORNER_GEO_POINTS);
    }
  }

  public static class PreviewMapInput {
    File mapImageFile;
    List<TiePoint> tiepoints;
  }

  //---------------------------------------------------------------------------
  // Helper method

  @Contract("_, null -> true")
  private static boolean wasCancelled(int resultCode, @Nullable Intent intent) {
    return resultCode != Activity.RESULT_OK || intent == null;
  }
}
