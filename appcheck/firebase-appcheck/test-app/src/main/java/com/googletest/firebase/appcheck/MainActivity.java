// Copyright 2020 Google LLC
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

package com.googletest.firebase.appcheck;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.FirebaseAppCheck.AppCheckListener;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.google.firebase.appcheck.safetynet.SafetyNetAppCheckProviderFactory;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;

public class MainActivity extends AppCompatActivity {

  private static final String TAG = "FirebaseAppCheckTest";

  private FirebaseAppCheck firebaseAppCheck;
  private FirebaseStorage firebaseStorage;
  private AppCheckListener appCheckListener;
  private Button installPlayIntegrityButton;
  private Button installSafetyNetButton;
  private Button installDebugButton;
  private Button getAppCheckTokenButton;
  private Button listStorageFilesButton;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    initFirebase();
    initViews();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    firebaseAppCheck.removeAppCheckListener(appCheckListener);
  }

  private void initFirebase() {
    FirebaseApp.initializeApp(this);
    firebaseAppCheck = FirebaseAppCheck.getInstance();
    firebaseStorage = FirebaseStorage.getInstance();

    appCheckListener =
        new AppCheckListener() {
          @Override
          public void onAppCheckTokenChanged(@NonNull AppCheckToken token) {
            Log.d(TAG, "onAppCheckTokenChanged");
          }
        };

    firebaseAppCheck.addAppCheckListener(appCheckListener);
  }

  private void initViews() {
    installPlayIntegrityButton = findViewById(R.id.install_play_integrity_app_check_button);
    installSafetyNetButton = findViewById(R.id.install_safety_net_app_check_button);
    installDebugButton = findViewById(R.id.install_debug_app_check_button);
    getAppCheckTokenButton = findViewById(R.id.exchange_app_check_button);
    listStorageFilesButton = findViewById(R.id.storage_list_files_button);

    setOnClickListeners();
  }

  private void setOnClickListeners() {
    installPlayIntegrityButton.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance());
            Log.d(TAG, "Installed PlayIntegrityAppCheckProvider");
            showToast("Installed PlayIntegrityAppCheckProvider.");
          }
        });

    installSafetyNetButton.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            firebaseAppCheck.installAppCheckProviderFactory(
                SafetyNetAppCheckProviderFactory.getInstance());
            Log.d(TAG, "Installed SafetyNetAppCheckProvider");
            showToast("Installed SafetyNetAppCheckProvider.");
          }
        });

    installDebugButton.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance());
            Log.d(TAG, "Installed DebugAppCheckProvider");
            showToast("Installed DebugAppCheckProvider.");
          }
        });

    getAppCheckTokenButton.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            Task<AppCheckToken> task = firebaseAppCheck.getAppCheckToken(/* forceRefresh= */ true);
            task.addOnSuccessListener(
                new OnSuccessListener<AppCheckToken>() {
                  @Override
                  public void onSuccess(AppCheckToken appCheckToken) {
                    Log.d(TAG, "Successfully retrieved AppCheck token.");
                    showToast("Successfully retrieved AppCheck token.");
                  }
                });
            task.addOnFailureListener(
                new OnFailureListener() {
                  @Override
                  public void onFailure(@NonNull Exception e) {
                    Log.d(TAG, "AppCheck token exchange failed with error: " + e.getMessage());
                    showToast("AppCheck token exchange failed.");
                  }
                });
          }
        });
    listStorageFilesButton.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            StorageReference listRef = firebaseStorage.getReference();

            listRef
                .listAll()
                .addOnSuccessListener(
                    new OnSuccessListener<ListResult>() {
                      @Override
                      public void onSuccess(ListResult listResult) {
                        for (StorageReference item : listResult.getItems()) {
                          Log.d(TAG, item.getName());
                        }
                        showToast("Successfully listed files.");
                      }
                    })
                .addOnFailureListener(
                    new OnFailureListener() {
                      @Override
                      public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "List files failed: " + e);
                        showToast("List files failed.");
                      }
                    });
          }
        });
  }

  private void showToast(String text) {
    Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
  }
}
