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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.Uri;
import android.os.Build;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.TestDownloadHelper.StreamDownloadResponse;
import com.google.firebase.storage.internal.MockClockHelper;
import com.google.firebase.storage.internal.RobolectricThreadFix;
import com.google.firebase.storage.network.MockConnectionFactory;
import com.google.firebase.storage.network.NetworkLayerMock;
import com.google.firebase.testing.FirebaseAppRule;
import java.io.File;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link FirebaseStorage}. */
@SuppressWarnings("ConstantConditions")
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.LOLLIPOP_MR1)
public class DownloadTest {

  @Rule public RetryRule retryRule = new RetryRule(3);
  @Rule public FirebaseAppRule appRule = new FirebaseAppRule();

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private FirebaseApp app;

  @Before
  public void setUp() throws Exception {
    RobolectricThreadFix.install();
    MockClockHelper.install();
    app = TestUtil.createApp();
  }

  @After
  public void tearDown() {
    FirebaseStorageComponent component = app.get(FirebaseStorageComponent.class);
    component.clearInstancesForTesting();
  }

  @Test
  public void streamDownload() throws Exception {
    System.out.println("Starting test streamDownload.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("streamDownload", true);
    final boolean[] completeHandlerInvoked = new boolean[] {false};

    Task<StreamDownloadResponse> task =
        TestDownloadHelper.streamDownload(
            bitmap -> {
              assertNotNull(bitmap);
              assertEquals(2560, bitmap.getWidth());
              assertEquals(1710, bitmap.getHeight());
              completeHandlerInvoked[0] = true;
            },
            null,
            "image.jpg",
            -1);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("streamDownload", task.getResult());
    assertTrue(completeHandlerInvoked[0]);
  }

  @Test
  public void streamDownloadStateVerification() throws Exception {
    System.out.println("Starting test streamDownloadStateVerification.");

    NetworkLayerMock.ensureNetworkMock("streamDownload", true);
    final Semaphore semaphore = new Semaphore(0);

    StorageReference storage = FirebaseStorage.getInstance().getReference("image.jpg");

    final AtomicLong bytesDownloaded = new AtomicLong();
    final AtomicLong bytesTransferred = new AtomicLong();
    final int fileSize = 1076408;

    ControllableSchedulerHelper.getInstance().pause();

    final StreamDownloadTask task =
        storage.getStream(
            (state, stream) -> {
              Assert.assertEquals(0, state.getBytesTransferred());
              Assert.assertEquals(fileSize, state.getTotalByteCount());

              byte[] buffer = new byte[256];

              int length;
              while ((length = stream.read(buffer)) != -1) {
                bytesDownloaded.addAndGet(length);
                Assert.assertEquals(fileSize, state.getTotalByteCount());
              }

              Assert.assertEquals(bytesDownloaded.get(), state.getTotalByteCount());
              stream.close();
              semaphore.release(1);
            });

    task.addOnProgressListener(state -> bytesTransferred.set(state.getBytesTransferred()));

    task.addOnSuccessListener(taskSnapshot -> semaphore.release(1));

    ControllableSchedulerHelper.getInstance().resume();

    for (int i = 0; i < 3000; i++) {
      Robolectric.flushForegroundThreadScheduler();
      if (semaphore.tryAcquire(2, 1, TimeUnit.MILLISECONDS)) {
        Assert.assertEquals(bytesDownloaded.get(), bytesTransferred.get());
        return;
      }
    }
    fail();
  }

  @Test
  public void streamDownloadWithResume() throws Exception {
    System.out.println("Starting test streamDownloadWithResume.");

    MockConnectionFactory factory =
        NetworkLayerMock.ensureNetworkMock("streamDownloadWithResume", true);
    final boolean[] completeHandlerInvoked = new boolean[] {false};

    Task<StreamDownloadResponse> task =
        TestDownloadHelper.streamDownload(
            bitmap -> {
              assertNotNull(bitmap);
              assertEquals(2560, bitmap.getWidth());
              assertEquals(1710, bitmap.getHeight());
              completeHandlerInvoked[0] = true;
            },
            null,
            "image.jpg",
            -1);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("streamDownloadWithResume", task.getResult());
    assertTrue(completeHandlerInvoked[0]);
  }

  @Test
  public void streamDownloadWithResumeAndCancel() throws Exception {
    System.out.println("Starting test streamDownloadWithResumeAndCancel.");

    MockConnectionFactory factory =
        NetworkLayerMock.ensureNetworkMock("streamDownloadWithResumeAndCancel", true);

    Task<StreamDownloadResponse> task =
        TestDownloadHelper.streamDownload(
            bitmap -> fail("Should not get called since we cancelled."), null, "image.jpg", 260000);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("streamDownloadWithResumeAndCancel", task.getResult());
  }

  @Test
  public void streamDownloadCanceled() throws Exception {
    System.out.println("Starting test streamDownloadCanceled.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("streamDownload", true);

    Task<StreamDownloadResponse> task =
        TestDownloadHelper.streamDownload(
            bitmap -> fail("Should not get called since we cancelled."), null, "image.jpg", 0);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("streamDownloadCanceled", task.getResult());
  }

  @Test
  public void streamDownloadWithETagChange() throws Exception {
    System.out.println("Starting test streamDownloadWithETagChange.");

    MockConnectionFactory factory =
        NetworkLayerMock.ensureNetworkMock("streamDownloadWithETagChange", true);

    Task<StreamDownloadResponse> task =
        TestDownloadHelper.streamDownload(null, null, "image.jpg", -1);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("streamDownloadWithETagChange", task.getResult());
  }

  @Test
  public void emptyStreamDownload() throws Exception {
    System.out.println("Starting test emptyStreamDownload.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("emptyStreamDownload", true);
    final boolean[] completeHandlerInvoked = new boolean[] {false};

    Task<StreamDownloadResponse> task =
        TestDownloadHelper.streamDownload(
            null,
            bytes -> {
              assertEquals(0, bytes.length);
              completeHandlerInvoked[0] = true;
            },
            "empty.dat",
            -1);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("emptyStreamDownload", task.getResult());
    assertTrue(completeHandlerInvoked[0]);
  }

  @Test
  public void byteDownload() throws Exception {
    System.out.println("Starting test byteDownload.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("streamDownload", true);

    Semaphore semaphore =
        TestDownloadHelper.byteDownload(
            new StringBuilder(), bitmap -> assertEquals(1076408, bitmap.length));
    for (int i = 0; i < 3000; i++) {
      Robolectric.flushForegroundThreadScheduler();
      if (semaphore.tryAcquire(1, 1, TimeUnit.MILLISECONDS)) {
        // success!
        factory.verifyOldMock();
        return;
      }
    }
    fail();
  }

  @Test
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void fileDownload() throws Exception {
    System.out.println("Starting test fileDownload.");

    final File outputFile = new File(folder.getRoot(), "download.jpg");
    if (outputFile.exists()) {
      outputFile.delete();
    }
    Uri destinationUri = Uri.fromFile(outputFile);
    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("fileDownload", true);

    final boolean[] completeHandlerInvoked = new boolean[] {false};

    Task<StringBuilder> task =
        TestDownloadHelper.fileDownload(
            destinationUri,
            () -> {
              assertTrue(outputFile.exists());
              assertEquals(1076408, outputFile.length());
              completeHandlerInvoked[0] = true;
            },
            -1);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("fileDownload", task.getResult().toString());
    assertTrue(completeHandlerInvoked[0]);
  }

  @Test
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void fileDownloadResume() throws Exception {
    System.out.println("Starting test fileDownloadResume.");

    final File outputFile = new File(folder.getRoot(), "download.jpg");
    if (outputFile.exists()) {
      outputFile.delete();
    }
    Uri destinationUri = Uri.fromFile(outputFile);
    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("fileDownloadResume", true);

    final boolean[] completeHandlerInvoked = new boolean[] {false};

    Task<StringBuilder> task =
        TestDownloadHelper.fileDownload(
            destinationUri,
            () -> {
              assertTrue(outputFile.exists());
              assertEquals(1076408, outputFile.length());
              completeHandlerInvoked[0] = true;
            },
            -1);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("fileDownloadResume", task.getResult().toString());
    assertTrue(completeHandlerInvoked[0]);
  }

  @Test
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void emptyDownload() throws Exception {
    System.out.println("Starting test emptyDownload.");

    final File outputFile = new File(folder.getRoot(), "empty.dat");
    if (outputFile.exists()) {
      outputFile.delete();
    }
    Uri destinationUri = Uri.fromFile(outputFile);
    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("emptyDownload", true);
    final boolean[] completeHandlerInvoked = new boolean[] {false};

    Task<StringBuilder> task =
        TestDownloadHelper.fileDownload(
            destinationUri,
            () -> {
              assertTrue(outputFile.exists());
              assertEquals(0, outputFile.length());
              completeHandlerInvoked[0] = true;
            },
            -1);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("emptyDownload", task.getResult().toString());
    assertTrue(completeHandlerInvoked[0]);
  }

  @Test
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void fileDownloadCanceledImmediately() throws Exception {
    System.out.println("Starting test fileDownloadCanceled.");

    final File outputFile = new File(folder.getRoot(), "download.jpg");
    if (outputFile.exists()) {
      outputFile.delete();
    }
    Uri destinationUri = Uri.fromFile(outputFile);
    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("fileDownload", true);

    Task<StringBuilder> task =
        TestDownloadHelper.fileDownload(
            destinationUri, () -> fail("Should not run since we cancelled the task."), 0);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("fileDownloadCanceled", task.getResult().toString());
  }
}
