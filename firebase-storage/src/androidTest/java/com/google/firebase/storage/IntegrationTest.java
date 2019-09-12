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

package com.google.firebase.storage;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.StreamDownloadTask.TaskSnapshot;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Integration tests for {@link FirebaseStorage}. */
@RunWith(AndroidJUnit4.class)
public class IntegrationTest {
  @Rule
  public GrantPermissionRule grantPermissionRule =
      GrantPermissionRule.grant(WRITE_EXTERNAL_STORAGE);

  // The file size in bytes of "1.1mb.dat"
  private static final int LARGE_FILE_SIZE_BYTES = 10 * 1024;

  private FirebaseStorage storageClient;

  private final String randomPrefix = UUID.randomUUID().toString();

  private final String unicodePrefix = "prefix/\\%:😊 ";

  @Before
  public void before() throws ExecutionException, InterruptedException {
    if (storageClient == null) {
      FirebaseApp app = FirebaseApp.initializeApp(InstrumentationRegistry.getContext());
      storageClient = FirebaseStorage.getInstance(app);

      Tasks.await(getReference("metadata.dat").putBytes(new byte[0]));
      Tasks.await(getReference("download.dat").putBytes(new byte[LARGE_FILE_SIZE_BYTES]));
      Tasks.await(getReference("prefix/empty.dat").putBytes(new byte[0]));
      Tasks.await(getReference(unicodePrefix + "/empty.dat").putBytes(new byte[0]));
    }
  }

  @Test
  public void downloadFile() throws ExecutionException, InterruptedException, IOException {
    File tempFile = new File(Environment.getExternalStorageDirectory(), "download.dat");

    FileDownloadTask.TaskSnapshot fileTask =
        Tasks.await(getReference("download.dat").getFile(tempFile));

    assertThat(tempFile.exists()).isTrue();
    assertThat(tempFile.length()).isEqualTo(LARGE_FILE_SIZE_BYTES);
    assertThat(fileTask.getBytesTransferred()).isEqualTo(LARGE_FILE_SIZE_BYTES);
  }

  @Test
  public void downloadUnicodeFile() throws ExecutionException, InterruptedException, IOException {
    File tempFile = new File(Environment.getExternalStorageDirectory(), "empty.dat");

    Tasks.await(getReference(unicodePrefix + "/empty.dat").getFile(tempFile));

    assertThat(tempFile.exists()).isTrue();
  }

  @Test
  public void streamFile() throws ExecutionException, InterruptedException, IOException {
    TaskSnapshot streamTask = Tasks.await(getReference("download.dat").getStream());

    byte[] data = new byte[255];

    InputStream stream = streamTask.getStream();
    long totalBytesRead = 0;
    long currentBytesRead;

    while ((currentBytesRead = stream.read(data)) != -1) {
      totalBytesRead += currentBytesRead;
    }

    assertThat(totalBytesRead).isEqualTo(LARGE_FILE_SIZE_BYTES);
  }

  @Test
  public void uploadBytesThenGetDownloadUrl() throws ExecutionException, InterruptedException {
    byte[] data = new byte[] {1, 2, 3};
    StorageReference reference = getReference("upload.dat");

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
        getReference("metadata.dat").updateMetadata(randomMetadata);
    StorageMetadata metadata = Tasks.await(storageMetadataTask);

    assertThat(metadata.getCustomMetadata("rand"))
        .isEqualTo(randomMetadata.getCustomMetadata("rand"));

    metadata = Tasks.await(getReference("metadata.dat").getMetadata());

    assertThat(metadata.getCustomMetadata("rand"))
        .isEqualTo(randomMetadata.getCustomMetadata("rand"));
  }

  @Test
  public void pagedListFiles() throws ExecutionException, InterruptedException {
    Task<ListResult> listTask = getReference().list(2);
    ListResult listResult = Tasks.await(listTask);

    assertThat(listResult.getItems())
        .containsExactly(getReference("download.dat"), getReference("metadata.dat"));
    assertThat(listResult.getPrefixes()).isEmpty();
    assertThat(listResult.getPageToken()).isNotEmpty();

    listTask = getReference().list(2, listResult.getPageToken());
    listResult = Tasks.await(listTask);

    assertThat(listResult.getItems()).isEmpty();
    assertThat(listResult.getPrefixes()).containsExactly(getReference("prefix"));
    assertThat(listResult.getPageToken()).isNull();
  }

  @Test
  public void listAllFiles() throws ExecutionException, InterruptedException {
    Task<ListResult> listTask = getReference().listAll();
    ListResult listResult = Tasks.await(listTask);

    assertThat(listResult.getPrefixes()).containsExactly(getReference("prefix"));
    assertThat(listResult.getItems())
        .containsExactly(getReference("metadata.dat"), getReference("download.dat"));
    assertThat(listResult.getPageToken()).isNull();
  }

  @Test
  public void listUnicodeFiles() throws ExecutionException, InterruptedException {
    Task<ListResult> listTask = getReference("prefix").listAll();
    ListResult listResult = Tasks.await(listTask);

    assertThat(listResult.getPrefixes()).containsExactly(getReference(unicodePrefix));

    listTask = getReference(unicodePrefix).listAll();
    listResult = Tasks.await(listTask);

    assertThat(listResult.getItems()).containsExactly(getReference(unicodePrefix + "/empty.dat"));
  }

  @NonNull
  private StorageReference getReference() {
    return storageClient.getReference(randomPrefix);
  }

  @NonNull
  private StorageReference getReference(String filename) {
    return storageClient.getReference(randomPrefix + "/" + filename);
  }
}
