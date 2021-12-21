package com.google.firebase.app.distribution;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;

class DialogUtils {

  private DialogUtils() {}

  static AlertDialog getSignInDialog(
      @NonNull Activity currentActivity,
      @NonNull FirebaseApp firebaseApp,
      OnClickListener onConfirmListener,
      OnClickListener onDismissListener,
      OnCancelListener onCanceledListener) {
    AlertDialog signInDialog = new AlertDialog.Builder(currentActivity).create();
    Context context = firebaseApp.getApplicationContext();
    signInDialog.setTitle(context.getString(R.string.signin_dialog_title));
    signInDialog.setMessage(context.getString(R.string.singin_dialog_message));

    signInDialog.setButton(
        AlertDialog.BUTTON_POSITIVE,
        context.getString(R.string.singin_yes_button),
        onConfirmListener);

    signInDialog.setButton(
        AlertDialog.BUTTON_NEGATIVE,
        context.getString(R.string.singin_no_button),
        onDismissListener);

    signInDialog.setOnCancelListener(onCanceledListener);

    return signInDialog;
  }

  static AlertDialog getUpdateDialog(
      @NonNull Activity currentActivity,
      @NonNull FirebaseApp firebaseApp,
      AppDistributionRelease newRelease,
      OnClickListener onConfirmListener,
      OnClickListener onDismissListener,
      OnCancelListener onCanceledListener) {
    Context context = firebaseApp.getApplicationContext();

    AlertDialog updateDialog = new AlertDialog.Builder(currentActivity).create();
    updateDialog.setTitle(context.getString(R.string.update_dialog_title));

    StringBuilder message =
        new StringBuilder(
            String.format(
                "Version %s (%s) is available.",
                newRelease.getDisplayVersion(), newRelease.getVersionCode()));

    if (newRelease.getReleaseNotes() != null && !newRelease.getReleaseNotes().isEmpty()) {
      message.append(String.format("\n\nRelease notes: %s", newRelease.getReleaseNotes()));
    }
    updateDialog.setMessage(message);

    updateDialog.setButton(
        AlertDialog.BUTTON_POSITIVE,
        context.getString(R.string.update_yes_button),
        onConfirmListener);

    updateDialog.setButton(
        AlertDialog.BUTTON_NEGATIVE,
        context.getString(R.string.update_no_button),
        onDismissListener);

    updateDialog.setOnCancelListener(onCanceledListener);

    return updateDialog;
  }
}
