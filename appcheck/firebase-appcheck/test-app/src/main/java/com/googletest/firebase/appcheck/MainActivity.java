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
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.safetynet.SafetyNetAppCheckProviderFactory;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;

public class MainActivity extends AppCompatActivity {

  private static final String TAG = "FirebaseAppCheckTest";

  private FirebaseAppCheck firebaseAppCheck;
  private FirebaseStorage firebaseStorage;
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

  private void initFirebase() {
    FirebaseApp.initializeApp(this);
    firebaseAppCheck = FirebaseAppCheck.getInstance();
    firebaseStorage = FirebaseStorage.getInstance();
  }

  private void initViews() {
    installSafetyNetButton = findViewById(R.id.install_safety_net_app_check_button);
    installDebugButton = findViewById(R.id.install_debug_app_check_button);
    getAppCheckTokenButton = findViewById(R.id.exchange_app_check_button);
    listStorageFilesButton = findViewById(R.id.storage_list_files_button);

    setOnClickListeners();
  }

  private void setOnClickListeners() {
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
            Task<AppCheckTokenResult> task = firebaseAppCheck.getToken(/* forceRefresh= */ false);
            task.addOnSuccessListener(
                new OnSuccessListener<AppCheckTokenResult>() {
                  @Override
                  public void onSuccess(AppCheckTokenResult appCheckTokenResult) {
                    if (appCheckTokenResult.getError() == null) {
                      Log.d(TAG, "Successfully retrieved AppCheck token.");
                      showToast("Successfully retrieved AppCheck token.");
                    } else {
                      Log.d(
                          TAG,
                          "AppCheck token exchange failed with error: "
                              + appCheckTokenResult.getError().getMessage());
                      showToast("AppCheck token exchange failed.");
                    }
                  }
                });
            task.addOnFailureListener(
                new OnFailureListener() {
                  @Override
                  public void onFailure(@NonNull Exception e) {
                    // This should not happen; the task should always return with a success.
                    Log.e(TAG, "Unexpected failure in getToken: " + e.toString());
                    showToast("Unexpected failure in getToken.");
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
