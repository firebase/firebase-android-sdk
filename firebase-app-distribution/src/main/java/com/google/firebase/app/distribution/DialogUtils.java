package com.google.firebase.app.distribution;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import com.google.firebase.FirebaseApp;

class DialogUtils {

  private DialogUtils() {}

  static AlertDialog getSignInDialog(
      Activity currentActivity,
      FirebaseApp firebaseApp,
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
}
