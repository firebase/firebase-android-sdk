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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;

/**
 * Activity opened during installation in {@link UpdateAppClient} after APK download is finished.
 */
public class InstallActivity extends AppCompatActivity {

  @Override
  protected void onCreate(@NonNull Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ActivityResultLauncher<Intent> mStartForResult =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            activityResult -> {
              int resultCode = activityResult.getResultCode();
              getFirebaseAppDistributionInstance().setInstallationResult(resultCode);
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
    String APK_MIME_TYPE = "application/vnd.android.package-archive";
    intent.setDataAndType(apkUri, APK_MIME_TYPE);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    mStartForResult.launch(intent);
  }

  @VisibleForTesting
  FirebaseAppDistribution getFirebaseAppDistributionInstance() {
    return FirebaseAppDistribution.getInstance();
  }
}
