/*
 * Copyright 2020 Google Inc. All Rights Reserved.
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
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

/**
 * PermissionFragment is used to query and acquire permissions the app needs. To request permission,
 * create a PermissionFragment and call method setRequestedPermission() before attaching the
 * fragment to an Activity. When the fragment is attached to an Activity, it immediately starts the
 * process of requesting permission, and delivers the permission result to the callback method once
 * result is available.
 */
public class PermissionFragment extends Fragment {
  /** Interface that delivers the permission result. */
  public interface PermissionResultCallback {
    void onPermissionResult(String permission, boolean granted);
  }

  /**
   * Checks if the given context already has the given permission. Simply calls
   * ContextCompat.checkSelfPermission() with the same parameters, but this method is easier to
   * remember.
   */
  public static boolean hasPermission(@NonNull Context context, @NonNull String permission) {
    return ContextCompat.checkSelfPermission(context, permission)
        == PackageManager.PERMISSION_GRANTED;
  }

  /**
   * Starts requesting permission in a FragmentActivity that implements {@link
   * PermissionResultCallback} interface. The result will be delivered to onPermissionResult()
   * method in the activity.
   *
   * @param activity FragmentActivity that implements PermissionResultCallback, so that it can
   *     receive the result of the permission request through its onPermissionResult() method.
   * @param permission Permission (one of Manifest.permission... strings) to be requested
   */
  public static void requestPermission(
      @NonNull FragmentActivity activity, @NonNull String permission) {
    if (!(activity instanceof PermissionResultCallback)) {
      throw new IllegalArgumentException("Activity must implement PermissionResultCallback");
    }
    Log.i(LOG_TAG, "Requesting permission " + permission);
    // Find existing permission fragment, or create a new one
    FragmentManager fragmentManager = activity.getSupportFragmentManager();
    PermissionFragment permissionFragment = (PermissionFragment)
        fragmentManager.findFragmentByTag(FRAGMENT_TAG);
    if (permissionFragment == null) {
      permissionFragment = new PermissionFragment();
    }
    // Initialize fragment if it hasn't been added yet (= it is not active)
    if (!permissionFragment.isAdded()) {
      permissionFragment.setRequestedPermission(permission);
      // Start requesting permission by adding the fragment to the activity
      fragmentManager.beginTransaction().add(permissionFragment, FRAGMENT_TAG).commit();
      // Keep the fragment around across device rotations
      permissionFragment.setRetainInstance(true);
    }
  }

  private static final String LOG_TAG = PermissionFragment.class.getSimpleName();
  private static final String FRAGMENT_TAG = "com.custommapsapp.android.PermissionFragment";
  private static final int PERMISSION_REQ = 9999;

  private String requestedPermission;
  private PermissionResultCallback callback;
  private boolean isWaitingForResult = false;

  public void setRequestedPermission(@NonNull String permission) {
    requestedPermission = permission;
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    try {
      callback = (PermissionResultCallback) context;
    } catch (Exception ex) {
      throw new IllegalStateException("Context must implement PermissionResultCallback");
    }
  }

  @Override
  public void onDetach() {
    callback = null;
    super.onDetach();
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    Log.i(LOG_TAG, "Activity created, starting to verify permission: " + requestedPermission);
    if (!isWaitingForResult) {
      verifyPermission();
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
  }

  /**
   * Checks if the parent activity has the requested permission. If the permission has been
   * granted,
   */
  private void verifyPermission() {
    Activity activity = getActivity();
    if (ContextCompat.checkSelfPermission(activity, requestedPermission)
        == PackageManager.PERMISSION_GRANTED) {
      // Permission is already granted, deliver result to callback immediately
      deliverResult(true);
      return;
    }
    // Permission has not been granted, start requesting the permission
    if (shouldShowRequestPermissionRationale(requestedPermission)) {
      // TODO: Display a dialog with a reason why permission is needed, and return
      // The reason string should be provided by the activity
      // When the dialog is closed, trigger this verifyPermission() method again
    }
    // Request permission, result is delivered to onRequestPermissionResult() method
    isWaitingForResult = true;
    requestPermissions(new String[]{requestedPermission}, PERMISSION_REQ);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
      @NonNull int[] grantResults) {
    // Filter our requests that were not triggered by PermissionFragment (should not happen?)
    if (requestCode != PERMISSION_REQ) {
      return;
    }

    if (permissions.length == 0 && grantResults.length == 0) {
      // Permission request was cancelled, interpret as permission denial
      deliverResult(false);
    } else if (permissions[0].equals(requestedPermission)) {
      // Response to the current permission request, deliver result to callback
      deliverResult(grantResults[0] == PackageManager.PERMISSION_GRANTED);
    }
  }

  private void deliverResult(boolean result) {
    isWaitingForResult = false;
    // Remove self from parent activity or fragment, and deliver result
    getFragmentManager().beginTransaction().remove(this).commit();
    if (callback != null) {
      callback.onPermissionResult(requestedPermission, result);
    }
  }
}
