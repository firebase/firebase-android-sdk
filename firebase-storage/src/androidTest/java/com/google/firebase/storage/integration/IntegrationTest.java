// Copyright 2018 Google LLC
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

package com.google.firebase.storage.integration;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StreamDownloadTask.TaskSnapshot;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class IntegrationTest {
  @Rule
  public GrantPermissionRule grantPermissionRule =
      GrantPermissionRule.grant(WRITE_EXTERNAL_STORAGE);

  // The file size in bytes of "1.1mb.dat"
  private static final int LARGE_FILE_SIZE = 1100000;

  private static FirebaseStorage storageClient;

  @Before
  public void before() {
    if (storageClient == null) {
      FirebaseApp app =
          FirebaseApp.initializeApp(
              InstrumentationRegistry.getContext(),
              new FirebaseOptions.Builder()
                  .setApplicationId("1:196403931065:android:60949756fbe381ea")
                  .setApiKey("AIzaSyDMAScliyLx7F0NPDEJi1QmyCgHIAODrlU")
                  .setStorageBucket("project-5516366556574091405.appspot.com")
                  .build());
      storageClient = FirebaseStorage.getInstance(app);
    }
  }

  @Test
  public void downloadFile() throws ExecutionException, InterruptedException, IOException {
    File tempFile = new File(Environment.getExternalStorageDirectory(), "1.1mb.dat");

    FileDownloadTask.TaskSnapshot fileTask =
        Tasks.await(storageClient.getReference("1.1mb.dat").getFile(tempFile));

    assertThat(tempFile.exists()).isTrue();
    assertThat(tempFile.length()).isEqualTo(LARGE_FILE_SIZE);
    assertThat(fileTask.getBytesTransferred()).isEqualTo(LARGE_FILE_SIZE);
  }

  @Test
  public void streamFile() throws ExecutionException, InterruptedException, IOException {
    TaskSnapshot streamTask = Tasks.await(storageClient.getReference("1.1mb.dat").getStream());

    byte[] data = new byte[255];

    InputStream stream = streamTask.getStream();
    long totalBytesRead = 0;
    long currentBytesRead;

    while ((currentBytesRead = stream.read(data)) != -1) {
      totalBytesRead += currentBytesRead;
    }

    assertThat(totalBytesRead).isEqualTo(LARGE_FILE_SIZE);
  }

  @Test
  public void uploadBytesThenGetDownloadUrl() throws ExecutionException, InterruptedException {
    byte[] data = new byte[] {1, 2, 3};
    StorageReference reference = storageClient.getReference("bytes.dat");

    Uri downloadUrl =
        Tasks.await(
            reference
                .putBytes(data)
                .onSuccessTask(
                    taskSnapshot -> {
                      assertThat(taskSnapshot.getBytesTransferred()).isEqualTo(3);
                      assertThat(taskSnapshot.getTotalByteCount()).isEqualTo(3);

                      return reference.getDownloadUrl();
                    }));

    assertThat(downloadUrl.toString()).startsWith("https://firebasestorage.googleapis.com/");
  }

  @Test
  public void updateMetadata() throws ExecutionException, InterruptedException {
    StorageMetadata randomMetadata =
        new StorageMetadata.Builder()
            .setCustomMetadata("rand", Double.toString(Math.random()))
            .build();

    Task<StorageMetadata> storageMetadataTask =
        storageClient.getReference("metadata.dat").updateMetadata(randomMetadata);
    StorageMetadata metadata = Tasks.await(storageMetadataTask);

    assertThat(metadata.getCustomMetadata("rand"))
        .isEqualTo(randomMetadata.getCustomMetadata("rand"));

    metadata = Tasks.await(storageClient.getReference("metadata.dat").getMetadata());

    assertThat(metadata.getCustomMetadata("rand"))
        .isEqualTo(randomMetadata.getCustomMetadata("rand"));
  }
}
