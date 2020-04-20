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
package com.custommapsapp.android;

import java.util.Locale;

import android.content.Context;
import android.widget.TextView;

import com.custommapsapp.android.storage.PreferenceStore;

/**
 * UnitsManager provides methods to format and display distances in user's preferred distance units.
 */
public class UnitsManager {
  public enum DistanceUnits {
    /** Metric units: km, m */
    KM,
    /** English units: mi, yds, ft */
    MI,
    /** Nautical/aviation units: nmi, ft */
    NMI
  }

  /**
   * Updates the given TextView by converting the given metric distance to user's preferred distance
   * units, and formatting it for display.
   *
   * @param scaleTextView TextView into which the distance should be rendered
   * @param distanceM distance in meters that should be displayed in user's preferred units
   */
  public static void updateScaleText(TextView scaleTextView, double distanceM) {
    DistanceUnits units = PreferenceStore.instance(scaleTextView.getContext()).getDistanceUnits();

    // 'distance' contains the distance in user's preferred distance units
    double distance;
    String unitsText;
    if (units == DistanceUnits.MI) {
      distance = metersToMiles(distanceM);
      unitsText = "mi";
    } else if (units == DistanceUnits.NMI) {
      distance = metersToNauticalMiles(distanceM);
      unitsText = "nmi";
    } else {
      // units == PreferenceStore.DistanceUnits.KM or unknown
      distance = distanceM / 1000.0;
      unitsText = "km";
    }
    // Use two significant digits for scale text
    // -> no decimals if displayed value is 10 or larger
    // -> one decimal if displayed value is [1.0, 9.9]
    // -> two decimals if displayed value is 0.99 or smaller
    String distanceFormat;
    if (distance >= 1000) {
      distance = Math.round(distance / 100) * 100;
      distanceFormat = "%.0f %s";
    } else if (distance >= 100) {
      distance = Math.round(distance / 10) * 10;
      distanceFormat = "%.0f %s";
    } else if (distance >= 9.95) {
      // Include values [9.95, 10] in this range to avoid displaying "10.0" (3 significant digits)
      distanceFormat = "%.0f %s";
    } else if (distance >= 0.995) {
      // Use one decimal for values [0.995, 1.0] to avoid displaying "1.00" (3 significant digits)
      distanceFormat = "%.1f %s";
    } else {
      // Value is between [0, 0.995], use two decimals
      distanceFormat = "%.2f %s";
    }
    String scaleText = String.format(Locale.getDefault(), distanceFormat, distance, unitsText);
    scaleTextView.setText(scaleText);
  }

  public static void updateAltitudeLabel(TextView altitudeLabel, double altitudeM) {
    DistanceUnits units = PreferenceStore.instance(altitudeLabel.getContext()).getDistanceUnits();
    if (units == DistanceUnits.MI || units == DistanceUnits.NMI) {
      // Both English (mi, yds, ft) and Nautical (nmi, ft) use feet for altitude
      double altitudeFt = metersToFeet(altitudeM);
      altitudeLabel.setText(String.format(Locale.getDefault(), "%.0f ft", altitudeFt));
    } else {
      // units == DistanceUnits.KM (metric) or unknown
      altitudeLabel.setText(String.format(Locale.getDefault(), "%.0f m", altitudeM));
    }
  }

  public static void updateSpeedLabel(TextView speedLabel, double speedMetersPerSecond) {
    DistanceUnits units = PreferenceStore.instance(speedLabel.getContext()).getDistanceUnits();
    double speed; // speed in user's preferred units
    String unitsText; // abbreviation of the speed units used in 'speed'
    if (units == DistanceUnits.MI) {
      // Convert speed to mph
      speed = metersPerSecondToMilesPerHour(speedMetersPerSecond);
      unitsText = "mph";
    } else if (units == DistanceUnits.NMI) {
      // Convert speed to knots
      speed = metersPerSecondToKnots(speedMetersPerSecond);
      unitsText = "kts";
    } else {
      // units == KM (metric) or unknown, convert speed to km/h
      speed = metersPerSecondToKmPerHour(speedMetersPerSecond);
      unitsText = "km/h";
    }
    String format;
    if (speed < 9.95) {
      format = "%.1f %s";
    } else {
      format = "%.0f %s";
    }
    String speedText = String.format(Locale.getDefault(), format, speed, unitsText);
    speedLabel.setText(speedText);
  }

  public static void updateAccuracyLabel(TextView accuracyLabel, double accuracyM) {
    DistanceUnits units = PreferenceStore.instance(accuracyLabel.getContext()).getDistanceUnits();
    String accuracyText;
    if (units == DistanceUnits.MI || units == DistanceUnits.NMI) {
      double accuracyFt = metersToFeet(accuracyM);
      if (accuracyFt < 995) {
        // Display short distances as feet
        if (accuracyFt < 99.5) {
          // Range 0 - 99, round to a whole number
          accuracyText = String.format(Locale.getDefault(), "%.0f ft", accuracyFt);
        } else {
          // Range 100 - 994, round to closest 10 ft
          accuracyFt = Math.round(accuracyFt / 10) * 10;
          accuracyText = String.format(Locale.getDefault(), "%.0f ft", accuracyFt);
        }
      } else {
        // Display accuracy in miles or nautical miles
        if (units == DistanceUnits.MI) {
          double accuracyMi = metersToMiles(accuracyM);
          accuracyText = String.format(Locale.getDefault(), "%.3f mi", accuracyMi);
        } else {
          double accuracyNmi = metersToNauticalMiles(accuracyM);
          accuracyText = String.format(Locale.getDefault(), "%.3f nmi", accuracyNmi);
        }
      }
    } else {
      // units == DistanceUnits.KM (metric) or unknown
      if (accuracyM < 995) {
        accuracyText = String.format(Locale.getDefault(), "%.0f m", accuracyM);
      } else {
        accuracyText = String.format(Locale.getDefault(), "%.2f km", accuracyM / 1000.0);
      }
    }
    accuracyLabel.setText(accuracyText);
  }

  public static String getDistanceToCenter(Context context, double distanceM, double accuracyM) {
    // Don't show accuracy if it is less than 1/10 of distance
    if (accuracyM < distanceM / 10.0) {
      accuracyM = 0;
    }
    // Return distance string in user's preferred units
    DistanceUnits units = PreferenceStore.instance(context).getDistanceUnits();
    switch (units) {
      case MI:
        return centerDistanceMiles(distanceM, accuracyM);
      case NMI:
        return centerDistanceNauticalMiles(distanceM, accuracyM);
      case KM:
      default:
        return centerDistanceMetric(distanceM, accuracyM);
    }
  }

  private static String centerDistanceMetric(double distanceM, double accuracyM) {
    // Distances rounding to 1 km and over are formatted as kilometers
    if (distanceM > 999.5) {
      double distanceKm = distanceM / 1000.0;
      double accuracyKm = accuracyM / 1000.0;
      return formatThreeDigits(distanceKm, accuracyKm, "km");
    }
    // Shorter distances are formatted as meters
    return formatNoDecimals(distanceM, accuracyM, "m");
  }

  private static String centerDistanceMiles(double distanceM, double accuracyM) {
    // Distances over 1/2 mile are displayed in miles
    double distanceMi = metersToMiles(distanceM);
    if (distanceMi > 0.5) {
      double accuracyMi = metersToMiles(accuracyM);
      return formatThreeDigits(distanceMi, accuracyMi, "mi");
    }
    // Distances below 100ft are displayed in feet
    double distanceFt = metersToFeet(distanceM);
    if (distanceFt < 99.5) {
      double accuracyFt = metersToFeet(accuracyM);
      return formatNoDecimals(distanceFt, accuracyFt, "ft");
    }
    // Other distances (100ft - 0.5 mi) are displayed in yards
    double distanceYds = metersToYards(distanceM);
    double accuracyYds = metersToYards(accuracyM);
    return formatNoDecimals(distanceYds, accuracyYds, "yds");
  }

  private static String centerDistanceNauticalMiles(double distanceM, double accuracyM) {
    double distanceNmi = metersToNauticalMiles(distanceM);
    // Use feet for distances less than quarter nautical mile
    if (distanceNmi < 0.245) {
      double distanceFt = metersToFeet(distanceM);
      double accuracyFt = metersToFeet(accuracyM);
      return formatNoDecimals(distanceFt, accuracyFt, "ft");
    }
    // Use nautical miles for all longer distances
    double accuracyNmi = metersToNauticalMiles(accuracyM);
    return formatThreeDigits(distanceNmi, accuracyNmi, "nmi");
  }

  private static String formatThreeDigits(double distance, double accuracy, String unitStr) {
    String format;
    if (distance < 9.995) {
      // Accuracy is ignored on longer distances, so return here if it needs to be shown
      if (accuracy > 0) {
        // \u00B1 is plus-minus sign (±)
        return String.format(Locale.getDefault(),
            "%.2f \u00B1 %.2f %s", distance, accuracy, unitStr);
      }
      // Use two decimals (1.00 - 9.99)
      format = "%.2f %s";
    } else if (distance < 99.95) {
      // Use one decimal (10.0 - 99.9)
      format = "%.1f %s";
    } else {
      // Use no decimals (100 - )
      format = "%.0f %s";
    }
    return String.format(Locale.getDefault(), format, distance, unitStr);
  }

  private static String formatNoDecimals(double distance, double accuracy, String unitStr) {
    if (accuracy == 0) {
      return String.format(Locale.getDefault(), "%.0f %s", distance, unitStr);
    }
    // \u00B1 is plus-minus sign (±)
    return String.format(Locale.getDefault(), "%.0f \u00B1 %.0f %s", distance, accuracy, unitStr);
  }

  private static double metersToMiles(double distanceM) {
    return distanceM / 1609.34;
  }

  private static double metersToYards(double distanceM) {
    return distanceM / 0.9144;
  }

  private static double metersToFeet(double distanceM) {
    return distanceM / 0.3048;
  }

  private static double metersToNauticalMiles(double distanceM) {
    return distanceM / 1852.0;
  }

  private static double metersPerSecondToKmPerHour(double metersPerSecond) {
    return 3600.0 * metersPerSecond / 1000.0;
  }

  private static double metersPerSecondToMilesPerHour(double metersPerSecond) {
    return 3600.0 * metersToMiles(metersPerSecond);
  }

  private static double metersPerSecondToKnots(double metersPerSecond) {
    return 3600.0 * metersToNauticalMiles(metersPerSecond);
  }
}
