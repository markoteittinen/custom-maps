package com.custommapsapp.android;

import java.util.Locale;

import android.annotation.SuppressLint;
import android.location.Location;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class ScaleDisplay {
  private static final double METERS_PER_MILE = 1609.34;
  /** Index for X coordinate in arrays. */
  private static final int X = 0;
  /** Index of Y coordinate in arrays. */
  private static final int Y = 1;

  private static final int ICON_SPAN_SHORT = R.drawable.ic_span_short_24dp;
  private static final int ICON_SPAN_LONG = R.drawable.ic_span_long_24dp;

  private DisplayState displayState;
  private ImageView scaleIcon;
  private TextView scaleText;
  private boolean isHorizontal = false;
  private boolean isMetric = false;
  private int magnitude;
  private int topPaddingPx = 0;

  /** screenPoint is reused whenever a screen point is converted to Location. */
  private float[] screenPoint = new float[2];
  // These location object are re-used each time scale is computed to avoid memory allocation. */
  private Location upperLeft = new Location("tmp");
  private Location upperRight = new Location("tmp");
  private Location lowerLeft = new Location("tmp");
  private Location lowerRight = new Location("tmp");

  public ScaleDisplay(ImageView icon, TextView label, DisplayState displayState) {
    scaleIcon = icon;
    scaleText = label;
    this.displayState = displayState;
    scaleIcon.setImageResource(isHorizontal ? ICON_SPAN_SHORT : ICON_SPAN_LONG);
    scaleIcon.setOnClickListener(v -> {
      setHorizontal(!isHorizontal);
      update();
    });
  }

  public void setHorizontal(boolean horizontal) {
    if (isHorizontal != horizontal) {
      isHorizontal = horizontal;
      scaleIcon.setImageResource(isHorizontal ? ICON_SPAN_SHORT : ICON_SPAN_LONG);
    }
  }

  public boolean isHorizontal() {
    return isHorizontal;
  }

  /**
   * Sets the number of pixels to be ignored on top of the map view due to translucent ActionBar
   * covering it. This area will be ignored in vertical scale distance calculation.
   */
  public void setTopPaddingPx(int topPaddingPx) {
    this.topPaddingPx = topPaddingPx;
  }

  public void setMetric(boolean metric) {
    if (isMetric != metric) {
      isMetric = metric;
    }
  }

  public boolean isMetric() {
    return isMetric;
  }

  @SuppressLint("SetTextI18n")
  public void update() {
    Location upperLeftGeo = getUpperLeftLocation();
    Location upperRightGeo = getUpperRightLocation();
    Location lowerLeftGeo = getLowerLeftLocation();
    Location lowerRightGeo = getLowerRightLocation();

    // Span distance in meters
    double distanceM;
    if (isHorizontal) {
      // Use average distance of (top-left to top-right) and (bottom-left to bottom-right)
      double distanceTopM = upperLeftGeo.distanceTo(upperRightGeo);
      double distanceBottomM = lowerLeftGeo.distanceTo(lowerRightGeo);
      distanceM = (distanceTopM + distanceBottomM) / 2.0;
    } else {
      // Use average distance of (top-left to bottom-left) and (top-right to bottom-right)
      double distanceLeftM = upperLeftGeo.distanceTo(lowerLeftGeo);
      double distanceRightM = upperRightGeo.distanceTo(lowerRightGeo);
      distanceM = (distanceLeftM + distanceRightM) / 2.0;
    }
    // Span distance in kilometers or miles
    double distance;
    if (isMetric()) {
      distance = distanceM / 1000.0;
    } else {
      distance = metersToMiles(distanceM);
    }

    // Use two significant digits for label:
    // -> No decimals if displayed value is 10 or larger
    // -> One decimal if displayed value is [1.0, 9.9]
    // -> Two decimals if displayed value is 0.99 or smaller
    String distanceNumberText;
    if (distance >= 1000) {
      distance = Math.round(distance / 100) * 100;
      distanceNumberText = String.format(Locale.getDefault(), "%.0f", distance);
      updateMagnitude(4);
    } else if (distance >= 100) {
      distance = Math.round(distance / 10) * 10;
      distanceNumberText = String.format(Locale.getDefault(), "%.0f", distance);
      updateMagnitude(3);
    } else if (distance >= 9.95) {
      // Using no decimals for values [9.95, 10] prevents displaying "10.0" (3 significant digits)
      distanceNumberText = String.format(Locale.getDefault(), "%.0f", distance);
      updateMagnitude(2);
    } else if (distance >= 0.995) {
      // Using 1 decimal for values [0.995, 1.0] prevents displaying "1.00" (3 significant digits)
      distanceNumberText = String.format(Locale.getDefault(), "%.1f", distance);
      updateMagnitude(1);
    } else {
      distanceNumberText = String.format(Locale.getDefault(), "%.2f", distance);
      updateMagnitude(0);
    }

    scaleText.setText(distanceNumberText + (isMetric() ? " km" : " mi"));
  }

  private void updateMagnitude(int newMagnitude) {
    if (newMagnitude != magnitude) {
      magnitude = newMagnitude;
      // Magnitude of the scale value changed, trigger re-layout of the ScaleDisplay ViewGroup
      ((View) scaleText.getParent()).invalidate();
    }
  }

  /** Returns the geographic location of the upper left corner of map display. */
  private Location getUpperLeftLocation() {
    screenPoint[X] = 0f;
    screenPoint[Y] = topPaddingPx;
    return updateScreenPointLocation(screenPoint, upperLeft);
  }

  /** Returns the geographic location of the upper right corner of map display. */
  private Location getUpperRightLocation() {
    screenPoint[X] = displayState.getViewWidth();
    screenPoint[Y] = topPaddingPx;
    return updateScreenPointLocation(screenPoint, upperRight);
  }

  /** Returns the geographic location of the lower left corner of map display. */
  private Location getLowerLeftLocation() {
    screenPoint[X] = 0f;
    screenPoint[Y] = displayState.getViewHeight();
    return updateScreenPointLocation(screenPoint, lowerLeft);
  }

  /** Returns the geographic location of the lower right corner of map display. */
  private Location getLowerRightLocation() {
    screenPoint[X] = displayState.getViewWidth();
    screenPoint[Y] = displayState.getViewHeight();
    return updateScreenPointLocation(screenPoint, lowerRight);
  }

  /** Updates a given Location object with given screen point (x,y). */
  private Location updateScreenPointLocation(float[] xy, Location location) {
    float[] geoPoint = displayState.convertScreenToGeoCoordinates(xy);
    location.setLongitude(geoPoint[X]);
    location.setLatitude(geoPoint[Y]);
    return location;
  }

  private double metersToMiles(double meters) {
    return meters / METERS_PER_MILE;
  }
}
