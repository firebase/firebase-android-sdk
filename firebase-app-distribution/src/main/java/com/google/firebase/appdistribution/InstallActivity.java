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

package com.google.firebase.appdistribution;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.google.android.gms.tasks.TaskCompletionSource;
import java.io.File;

/**
 * Activity opened during installation in {@link UpdateAppClient} after APK download is finished.
 */
public class InstallActivity extends AppCompatActivity {

  private static TaskCompletionSource<Void> installTaskCompletionSource;
  private ActivityResultLauncher<Intent> mStartForResult;
  private final String APK_MIME_TYPE = "application/vnd.android.package-archive";

  @Override
  protected void onCreate(@NonNull Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.mStartForResult =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            activityResult -> {
              if (activityResult.getResultCode() == Activity.RESULT_OK) {
                installTaskCompletionSource.setResult(null);
              } else {
                installTaskCompletionSource.setException(
                    new FirebaseAppDistributionException(
                        "Installation failed with result code: " + activityResult.getResultCode(),
                        FirebaseAppDistributionException.Status.INSTALLATION_FAILURE));
              }
              finish();
            });
    Intent originalIntent = getIntent();
    String path = originalIntent.getStringExtra("INSTALL_PATH");
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
    Uri apkUri =
        FileProvider.getUriForFile(
            getApplicationContext(),
            getApplicationContext().getPackageName() + ".provider",
            new File(path));
    intent.setDataAndType(apkUri, APK_MIME_TYPE);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    this.mStartForResult.launch(intent);
  }

  public static void registerOnCompletionListener(
      @NonNull TaskCompletionSource<Void> taskCompletionSource) {
    installTaskCompletionSource = taskCompletionSource;
  }
}
