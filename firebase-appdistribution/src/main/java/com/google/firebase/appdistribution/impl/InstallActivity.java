// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.appdistribution.impl;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import java.io.File;

/**
 * Activity opened during installation in {@link FirebaseAppDistribution} after APK download is
 * finished.
 */
public class InstallActivity extends AppCompatActivity {
  private static final String TAG = "InstallActivity";

  private boolean installInProgress = false;
  private AlertDialog enableUnknownSourcesDialog;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    enableUnknownSourcesDialog = new AlertDialog.Builder(this).create();
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Since we kick-off installation with FLAG_ACTIVITY_NEW_TASK (in a new task), we won't be able
    // to figure out if installation failed or was canceled.
    // If we re-enter InstallActivity after install is already kicked off, we can assume that either
    // installation failure or tester canceled the install.
    if (installInProgress) {
      LogWrapper.e(
          TAG,
          "Activity resumed when installation was already in progress. Installation was either canceled or failed");
      finish();
      return;
    }

    if (!isUnknownSourcesEnabled()) {
      // See comment about install progress above. Same applies to unknown sources UI.
      if (enableUnknownSourcesDialog.isShowing()) {
        LogWrapper.e(
            TAG,
            "Unknown sources enablement was already in progress. It was either canceled or failed");
        enableUnknownSourcesDialog.dismiss();
        finish();
        return;
      }
      showUnknownSourcesUi();
      return;
    }

    startAndroidPackageInstallerIntent();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (enableUnknownSourcesDialog.isShowing()) {
      enableUnknownSourcesDialog.dismiss();
      LogWrapper.e(TAG, "Unknown sources enablement canceled. Activity was destroyed");
    }
  }

  private boolean isUnknownSourcesEnabled() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      return this.getPackageManager().canRequestPackageInstalls();
    } else {
      try {
        return Settings.Secure.getInt(getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS)
            == 1;
      } catch (Settings.SettingNotFoundException e) {
        LogWrapper.e(
            TAG, "Unable to determine if unknown sources is enabled. Assuming it's enabled.", e);
        return true;
      }
    }
  }

  private void showUnknownSourcesUi() {
    enableUnknownSourcesDialog.setTitle(getString(R.string.unknown_sources_dialog_title));
    enableUnknownSourcesDialog.setMessage(getString(R.string.unknown_sources_dialog_description));
    enableUnknownSourcesDialog.setButton(
        AlertDialog.BUTTON_POSITIVE,
        getString(R.string.unknown_sources_yes_button),
        (dialogInterface, i) -> startActivity(getUnknownSourcesIntent()));
    enableUnknownSourcesDialog.setButton(
        AlertDialog.BUTTON_NEGATIVE,
        getString(R.string.update_no_button),
        (dialogInterface, i) -> dismissUnknownSourcesDialogCallback());
    enableUnknownSourcesDialog.setOnCancelListener(
        dialogInterface -> dismissUnknownSourcesDialogCallback());

    if (!enableUnknownSourcesDialog.isShowing()) {
      enableUnknownSourcesDialog.show();
    }
  }

  private void dismissUnknownSourcesDialogCallback() {
    LogWrapper.v(TAG, "Unknown sources dialog canceled");
    finish();
  }

  private Intent getUnknownSourcesIntent() {
    Intent intent;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
      intent.setData(Uri.parse("package:" + getPackageName()));
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      LogWrapper.v(TAG, "Starting unknown sources in new task");
    } else {
      intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    return intent;
  }

  private void startAndroidPackageInstallerIntent() {
    installInProgress = true;
    Intent originalIntent = getIntent();
    String path = originalIntent.getStringExtra("INSTALL_PATH");
    Intent intent = new Intent(Intent.ACTION_VIEW);
    File apkFile = new File(path);
    String APK_MIME_TYPE = "application/vnd.android.package-archive";

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
      Uri apkUri =
          FileProvider.getUriForFile(
              getApplicationContext(),
              getApplicationContext().getPackageName() + ".FirebaseAppDistributionFileProvider",
              apkFile);
      intent.setDataAndType(apkUri, APK_MIME_TYPE);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    } else {
      LogWrapper.d(TAG, "Requesting a vanilla URI");
      intent.setDataAndType(Uri.fromFile(apkFile), APK_MIME_TYPE);
    }

    // These flags open the installation activity in a new task and to prevent earlier installation
    // tasks from causing future ones to fail we use the clear task flag
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    LogWrapper.v(TAG, "Kicking off install as new activity");
    startActivity(intent);
  }
}
