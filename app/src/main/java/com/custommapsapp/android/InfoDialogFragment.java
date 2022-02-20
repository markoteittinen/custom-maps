package com.custommapsapp.android;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

public class InfoDialogFragment extends DialogFragment {
  private String title;
  private String message;

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState != null) {
      title = savedInstanceState.getString("title");
      message = savedInstanceState.getString("message");
    }
    return new AlertDialog.Builder(requireContext())
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(getString(android.R.string.ok), this::onOkPressed)
        .create();
  }

  private void onOkPressed(DialogInterface dialog, int button) {
    getParentFragmentManager().setFragmentResult(TAG, Bundle.EMPTY);
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString("title", title);
    outState.putString("message", message);
  }

  public static void showDialog(FragmentManager fragmentManager, String title, String message) {
    InfoDialogFragment dialogFragment = new InfoDialogFragment();
    dialogFragment.setCancelable(false);
    dialogFragment.title = title;
    dialogFragment.message = message;
    dialogFragment.show(fragmentManager, TAG);
  }

  public static final String TAG = "InfoDialogFragment";
  public static final boolean CONTINUE = true;
  public static final boolean CANCEL = false;
}
