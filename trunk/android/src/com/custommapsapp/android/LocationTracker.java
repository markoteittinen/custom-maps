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

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Display;

/**
 * LocationTracker keeps track of the last known location, including longitude,
 * latitude, altitude, location accuracy, bearing, speed and time last updated.
 * LocationTracker can use both GPS and network location information.
 *
 * @author Marko Teittinen
 */
public class LocationTracker implements LocationListener, SensorEventListener {
  // Time in ms after which GPS data is considered stale and can be replaced by NETWORK location
  protected static final long GPS_EXPIRATION = 60000L;

  protected Context context = null;
  protected Float compassDeclination = null;
  protected long declinationTime = 0;
  protected long lastGpsTime = 0;
  protected boolean hasHeading = false;
  protected float compassHeading = 0f;
  protected Location currentLocation = null;
  protected Display display = null;
  protected boolean isQuitting = false;

  public LocationTracker(Context context) {
    this.context = context;
  }

  public void setQuitting(boolean quitting) {
    isQuitting = quitting;
  }

  public void setContext(Context context) {
    this.context = context;
    if (context != null && !GeoidHeightEstimator.isInitialized()) {
      GeoidHeightEstimator.initialize(context.getAssets());
    }
  }

  public void setDisplay(Display display) {
    this.display = display;
  }

  /**
   * Returns the current location information in the given Location object (or
   * 'null' if current location is unknown). Passing in 'null' will result in a
   * new Location object being created.
   *
   * @param result object to be used for returning the result
   * @return Location object passed in as parameter containing up to date
   *         location information, or 'null' if location info is not available
   */
  public Location getCurrentLocation(Location result) {
    if (currentLocation == null) {
      return null;
    }
    if (result == null) {
      result = new Location(currentLocation);
    } else {
      result.set(currentLocation);
    }
    if (hasAltitude()) {
      result.setAltitude(getAltitude());
    } else {
      result.removeAltitude();
    }
    if (hasSpeed()) {
      result.setSpeed(getSpeed());
    } else {
      result.removeSpeed();
    }
    return result;
  }

  //-------------------------------------------------------------------------------------
  // Altitude and speed averaging to reduce noise in the values

  private static final int AVERAGING_BUFFER = 4;
  private int speedIndex = 0;
  private int altitudeIndex = 0;
  boolean speedFull = false;
  boolean altitudeFull = false;
  float[] speedBuffer = new float[AVERAGING_BUFFER];
  float[] altitudeBuffer = new float[AVERAGING_BUFFER];

  private void updateSpeedAndAltitude(Location location) {
    if (location.hasSpeed()) {
      speedBuffer[speedIndex++] = location.getSpeed();
      if (speedIndex == AVERAGING_BUFFER) {
        speedIndex = 0;
        speedFull = true;
      }
    }
    if (location.hasAltitude()) {
      altitudeBuffer[altitudeIndex++] = (float) location.getAltitude();
      if (altitudeIndex == AVERAGING_BUFFER) {
        altitudeIndex = 0;
        altitudeFull = true;
      }
    }
  }

  public boolean hasAltitude() {
    return (altitudeFull || altitudeIndex > 0);
  }

  public float getAltitude() {
    return average(altitudeBuffer, altitudeFull ? altitudeBuffer.length : altitudeIndex);
  }

  public boolean hasSpeed() {
    return (speedFull || speedIndex > 0);
  }

  public float getSpeed() {
    return average(speedBuffer, speedFull ? speedBuffer.length : speedIndex);
  }

  // Compute average of the values in buffer. Only consider 'samples' first values stored.
  private float average(float[] buffer, int samples) {
    if (samples == 0) {
      return 0.0f;
    }
    // Compute total of the values, and find min and max values
    float total = 0;
    float min = buffer[0];
    float max = buffer[0];
    for (int i = 0; i < samples; i++) {
      total += buffer[i];
      if (buffer[i] < min) {
        min = buffer[i];
      } else if (max < buffer[i]) {
        max = buffer[i];
      }
    }
    // If the full buffer was used, remove potential noise (min and max values)
    if (samples == AVERAGING_BUFFER) {
      total -= min + max;
      samples -= 2;
    }
    return total / samples;
  }

  //-------------------------------------------------------------------------------------
  // LocationListener implementation

  @Override
  public void onLocationChanged(Location location) {
    if (location == null || isQuitting) {
      return;
    }

    long now = System.currentTimeMillis();
    if (currentLocation != null) {
      // Never overwrite recent GPS location with a NETWORK location
      if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER) &&
          currentLocation.getProvider().equals(LocationManager.GPS_PROVIDER) &&
          now < lastGpsTime + GPS_EXPIRATION) {
        return;
      }
      // Ignore updates that come out of order from the same location provider
      if (location.getProvider().equals(currentLocation.getProvider()) &&
          location.getTime() < currentLocation.getTime()) {
        return;
      }
    }
    // Update magnetic declination once every minute
    // -- diff between "true north" and magnetic north in degrees
    if (declinationTime + 60000 < now) {
      declinationTime = now;
      GeomagneticField magneticField = new GeomagneticField(
          (float) location.getLatitude(), (float) location.getLongitude(),
          location.hasAltitude() ? (float) location.getAltitude() : 100f, // use 100m if not known
          declinationTime);
      compassDeclination = Float.valueOf(magneticField.getDeclination());
    }
    // If there is no bearing (lack of motion or network location), use latest compass heading
    if (!location.hasBearing() || !location.hasSpeed() || location.getSpeed() < 0.5f) {
      location.setBearing(compassHeading);
    }

    // Apply geoid height correction to altitude, if reported
    if (location.hasAltitude() && GeoidHeightEstimator.isInitialized()) {
      int geoidCm =
          GeoidHeightEstimator.computeGeoidHeight(location.getLatitude(), location.getLongitude());
      location.setAltitude(location.getAltitude() - Math.round(geoidCm / 100f));
    }

    // Update current location
    if (currentLocation != null) {
      currentLocation.set(location);
    } else {
      currentLocation = new Location(location);
    }

    if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
      lastGpsTime = now;
    }
    updateSpeedAndAltitude(location);
  }

  @Override
  public void onProviderDisabled(String provider) {
    // ignore
  }

  @Override
  public void onProviderEnabled(String provider) {
    // ignore
  }

  @Override
  public void onStatusChanged(String provider, int status, Bundle extras) {
    // ignore
  }

  //-------------------------------------------------------------------------------------
  // SensorEventListener implementation

  private float[] headingFilter = new float[20];
  private int headingFilterIndex = 0;
  private float[] accelerometerValues = new float[3];
  private float[] magneticFieldValues = new float[3];
  private float[] orientation = new float[3];
  private float[] rotationMatrix = new float[9];
  private boolean magneticFieldReady = false;

  public void resetCompass() {
    for (int i = 0; i < headingFilter.length; i++) {
      headingFilter[i] = Float.NaN;
    }
    headingFilterIndex = 0;
    magneticFieldReady = false;
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    // ignore for now
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (isQuitting) {
      return;
    }
    boolean isAccelerometer = false;
    int sensorType = event.sensor.getType();
    if (sensorType == Sensor.TYPE_ACCELEROMETER) {
      System.arraycopy(event.values, 0, accelerometerValues, 0, 3);
      isAccelerometer = true;
    } else if (sensorType == Sensor.TYPE_MAGNETIC_FIELD) {
      System.arraycopy(event.values,  0, magneticFieldValues, 0, 3);
      magneticFieldReady = true;
    } else {
      // Other sensor that has not impact on compass, ignore
      return;
    }
    // Only update compass direction on accelerometer changes
    if (isAccelerometer && magneticFieldReady) {
      if (SensorManager.getRotationMatrix(rotationMatrix, null,
          accelerometerValues, magneticFieldValues)) {
        // RotationMatrix computation successful, update compass heading
        SensorManager.getOrientation(rotationMatrix, orientation);
        float heading = (float) Math.toDegrees(orientation[0]);
        headingFilter[headingFilterIndex++] = heading;
        headingFilterIndex %= headingFilter.length;
        // Stabilize heading by averaging recent values
        // We cannot use mathematical average on raw headings, otherwise
        // 359 and 1 would average to 180, not 0.
        float diffSum = 0f;
        int count = 0;
        for (int i = 0; i < headingFilter.length; i++) {
          if (Float.isNaN(headingFilter[i])) {
            break;
          }
          float diff = headingFilter[i] - heading;
          if (diff > 180) {
            diff = diff - 360;
          } else if (diff < -180) {
            diff = 360 + diff;
          }
          diffSum += diff;
          count++;
        }
        compassHeading = (360 + heading + (diffSum / count)) % 360f;
        if (compassDeclination != null) {
          compassHeading = (compassHeading + compassDeclination.floatValue()) % 360f;
        }
        // Adjust to screen orientation
        int screenOrientation = (display != null ? display.getOrientation() : 0);
        if (screenOrientation != 0) {
          compassHeading = (compassHeading + (90f * screenOrientation)) % 360f;
        }
        hasHeading = true;
      }
    }
  }
}
