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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.RuntimeExecutionException;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.UploadTask.TaskSnapshot;
import com.google.firebase.storage.internal.MockClockHelper;
import com.google.firebase.storage.internal.RobolectricThreadFix;
import com.google.firebase.storage.network.MockConnectionFactory;
import com.google.firebase.storage.network.NetworkLayerMock;
import com.google.firebase.storage.network.ResumableUploadCancelRequest;
import com.google.firebase.testing.FirebaseAppRule;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNetworkInfo;

/** Tests for {@link FirebaseStorage}. */
@SuppressWarnings("ConstantConditions")
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.LOLLIPOP_MR1)
public class UploadTest {

  private static final String TEST_ASSET_ROOT = "assets/";

  @Rule public RetryRule retryRule = new RetryRule(3);
  @Rule public final FirebaseAppRule firebaseAppRule = new FirebaseAppRule();

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
  public void smallTextUpload() throws Exception {
    System.out.println("Starting test smallTextUpload.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("smallTextUpload", true);
    Task<StringBuilder> task = TestUploadHelper.smallTextUpload();

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("smallTextUpload", task.getResult().toString());
  }

  @Test
  public void cantUploadToRoot() throws Exception {
    System.out.println("Starting test cantUploadToRoot.");

    StorageReference storage =
        FirebaseStorage.getInstance().getReferenceFromUrl("gs://fooey.appspot.com");

    AtomicReference<Exception> taskException = new AtomicReference<>();

    final UploadTask task = storage.putBytes(new byte[] {});

    try {
      task.getResult();
      Assert.fail();
    } catch (IllegalStateException ignore) {
      // Task is not yet done.
    }

    Assert.assertNull(task.getException());

    task.addOnFailureListener(
        (exception) -> {
          Assert.assertEquals(
              "Cannot upload to getRoot. You should upload to a storage location such as "
                  + ".getReference('image.png').putFile...",
              exception.getCause().getMessage());
          taskException.set(exception);
        });

    // TODO(mrschmidt): Lower the timeout
    TestUtil.await(task, 300, TimeUnit.SECONDS);

    try {
      task.getResult();
      Assert.fail();
    } catch (RuntimeExecutionException e) {
      Assert.assertEquals(taskException.get().getCause(), e.getCause().getCause());
    }

    try {
      task.getResult(StorageException.class);
      Assert.fail();
    } catch (StorageException e) {
      Assert.assertEquals(taskException.get().getCause(), e.getCause());
    }

    Assert.assertEquals(taskException.get().getCause(), task.getException().getCause());
  }

  @Test
  public void addAndRemoveListeners() throws Exception {
    System.out.println("Starting test addAndRemoveListeners.");

    StorageReference storage =
        FirebaseStorage.getInstance().getReferenceFromUrl("gs://fooey.appspot.com/listeners.txt");

    ActivityController<Activity> activityController =
        Robolectric.buildActivity(Activity.class).create();
    Activity activity = activityController.get();
    Executor executor = Executors.newSingleThreadExecutor();
    Uri sourceFile = Uri.parse("file://dev/random");
    StorageMetadata metadata = new StorageMetadata.Builder().build();

    List<UploadTask> pendingTasks = new ArrayList<>();

    UploadTask task = storage.putBytes(new byte[] {});
    pendingTasks.add(task);
    task.cancel();

    OnPausedListener<TaskSnapshot> pausedListener = snapshot -> {};

    task.addOnPausedListener(pausedListener);
    task.removeOnPausedListener(pausedListener);
    task.addOnPausedListener(executor, pausedListener);
    task.removeOnPausedListener(pausedListener);
    task.addOnPausedListener(activity, pausedListener);
    task.removeOnPausedListener(pausedListener);

    OnProgressListener<TaskSnapshot> progessListener = snapshot -> {};

    task = storage.putBytes(new byte[] {}, metadata);
    pendingTasks.add(task);
    task.cancel();

    task.addOnProgressListener(progessListener);
    task.removeOnProgressListener(progessListener);
    task.addOnProgressListener(executor, progessListener);
    task.removeOnProgressListener(progessListener);
    task.addOnProgressListener(activity, progessListener);
    task.removeOnProgressListener(progessListener);

    task = storage.putFile(sourceFile);
    pendingTasks.add(task);
    task.cancel();

    OnSuccessListener<TaskSnapshot> successListener = snapshot -> {};

    task.addOnSuccessListener(successListener);
    task.removeOnSuccessListener(successListener);
    task.addOnSuccessListener(executor, successListener);
    task.removeOnSuccessListener(successListener);
    task.addOnSuccessListener(activity, successListener);
    task.removeOnSuccessListener(successListener);

    task = storage.putFile(sourceFile, metadata);
    pendingTasks.add(task);
    task.cancel();

    OnCanceledListener cancelListener = () -> {};

    task = storage.putFile(sourceFile, metadata, null);
    pendingTasks.add(task);
    task.cancel();

    task.addOnCanceledListener(cancelListener);
    task.removeOnCanceledListener(cancelListener);
    task.addOnCanceledListener(executor, cancelListener);
    task.removeOnCanceledListener(cancelListener);
    task.addOnCanceledListener(activity, cancelListener);
    task.removeOnCanceledListener(cancelListener);

    OnCompleteListener<TaskSnapshot> completeListener = snapshot -> {};

    task = storage.putFile(sourceFile, metadata, null);
    pendingTasks.add(task);
    task.cancel();

    task.addOnCompleteListener(completeListener);
    task.removeOnCompleteListener(completeListener);
    task.addOnCompleteListener(executor, completeListener);
    task.removeOnCompleteListener(completeListener);
    task.addOnCompleteListener(activity, completeListener);
    task.removeOnCompleteListener(completeListener);

    OnFailureListener failureListener = exception -> {};

    task = storage.putFile(sourceFile, metadata, null);
    pendingTasks.add(task);
    task.cancel();

    task.addOnFailureListener(failureListener);
    task.removeOnFailureListener(failureListener);
    task.addOnFailureListener(executor, failureListener);
    task.removeOnFailureListener(failureListener);
    task.addOnFailureListener(activity, failureListener);
    task.removeOnFailureListener(failureListener);

    activityController.stop();

    TestUtil.await(Tasks.whenAll(pendingTasks), 5, TimeUnit.SECONDS);

    Assert.assertTrue(
        StorageTaskManager.getInstance().getUploadTasksUnder(storage.getParent()).isEmpty());
  }

  @Test
  public void cancelledUpload() throws Exception {
    System.out.println("Starting test cancelledUpload.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("cancelledUpload", true);
    Task<StringBuilder> task = TestUploadHelper.byteUploadCancel();

    // TODO(mrschmidt): Lower the timeout
    TestUtil.await(task, 500, TimeUnit.SECONDS);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("cancelledUpload", task.getResult().toString());
  }

  @Test
  public void uploadWithSpace() throws Exception {
    System.out.println("Starting test uploadWithSpace.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("uploadWithSpace", true);
    StorageReference storage =
        FirebaseStorage.getInstance().getReference().child("hello world.txt");
    Task<StringBuilder> task = TestUploadHelper.byteUpload(storage);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("uploadWithSpace", task.getResult().toString());
  }

  @Test
  public void smallTextUpload2() throws Exception {
    System.out.println("Starting test smallTextUpload2.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("smallTextUpload2", true);

    Task<StringBuilder> task = TestUploadHelper.smallTextUpload2();

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("smallTextUpload2", task.getResult().toString());
  }

  @Test
  public void fileUpload() throws Exception {
    System.out.println("Starting test fileUpload.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("fileUpload", true);

    String filename = TEST_ASSET_ROOT + "image.jpg";
    ClassLoader classLoader = UploadTest.class.getClassLoader();
    InputStream imageStream = classLoader.getResourceAsStream(filename);
    Uri sourceFile = Uri.parse("file://" + filename);

    ContentResolver resolver =
        RuntimeEnvironment.application.getApplicationContext().getContentResolver();
    Shadows.shadowOf(resolver).registerInputStream(sourceFile, imageStream);

    Task<StringBuilder> task = TestUploadHelper.fileUpload(sourceFile, "image.jpg");

    TestUtil.await(task, 5, TimeUnit.SECONDS);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("fileUpload", task.getResult().toString());
  }

  @Test
  public void emptyUpload() throws Exception {
    System.out.println("Starting test emptyUpload.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("emptyUpload", true);

    String filename = TEST_ASSET_ROOT + "empty.dat";
    ClassLoader classLoader = UploadTest.class.getClassLoader();
    InputStream imageStream = classLoader.getResourceAsStream(filename);
    Uri sourceFile = Uri.parse("file://" + filename);

    ContentResolver resolver =
        RuntimeEnvironment.application.getApplicationContext().getContentResolver();
    Shadows.shadowOf(resolver).registerInputStream(sourceFile, imageStream);

    Task<StringBuilder> task = TestUploadHelper.fileUpload(sourceFile, "empty.dat");

    TestUtil.await(task, 5, TimeUnit.SECONDS);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("emptyUpload", task.getResult().toString());
  }

  @Test
  public void unicodeUpload() throws Exception {
    System.out.println("Starting test unicodeUpload.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("uploadWithUnicode", true);
    StorageReference storage = FirebaseStorage.getInstance().getReference().child("\\%:😊");
    Task<StringBuilder> task = TestUploadHelper.byteUpload(storage);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("uploadWithUnicode", task.getResult().toString());
  }

  @Test
  public void fileUploadWithPauseCancel() throws Exception {
    System.out.println("Starting test fileUploadWithPauseCancel.");

    ResumableUploadCancelRequest.cancelCalled = false;

    MockConnectionFactory factory =
        NetworkLayerMock.ensureNetworkMock("fileUploadWithPauseCancel", true);

    factory.setPauseRecord(4);

    String filename = TEST_ASSET_ROOT + "image.jpg";
    ClassLoader classLoader = UploadTest.class.getClassLoader();
    InputStream imageStream = classLoader.getResourceAsStream(filename);
    Uri sourceFile = Uri.parse("file://" + filename);

    ContentResolver resolver =
        RuntimeEnvironment.application.getApplicationContext().getContentResolver();
    Shadows.shadowOf(resolver).registerInputStream(sourceFile, imageStream);

    Task<StringBuilder> task =
        TestUploadHelper.fileUploadWithPauseCancel(factory.getSemaphore(), sourceFile);

    // This is 20 seconds due to a fairness bug where resumed tasks can be put at the end.
    TestUtil.await(task, 20, TimeUnit.SECONDS);

    TestUtil.verifyTaskStateChanges("fileUploadWithPauseCancel", task.getResult().toString());
    Assert.assertTrue(ResumableUploadCancelRequest.cancelCalled);
  }

  @Test
  public void fileUploadWithPauseResume() throws Exception {
    System.out.println("Starting test fileUploadWithPauseResume.");

    MockConnectionFactory factory =
        NetworkLayerMock.ensureNetworkMock("fileUploadWithPauseResume", true);

    factory.setPauseRecord(4);

    String filename = TEST_ASSET_ROOT + "image.jpg";
    ClassLoader classLoader = UploadTest.class.getClassLoader();
    InputStream imageStream = classLoader.getResourceAsStream(filename);
    Uri sourceFile = Uri.parse("file://" + filename);

    ContentResolver resolver =
        RuntimeEnvironment.application.getApplicationContext().getContentResolver();
    Shadows.shadowOf(resolver).registerInputStream(sourceFile, imageStream);

    Task<StringBuilder> task =
        TestUploadHelper.fileUploadWithPauseResume(factory.getSemaphore(), sourceFile);

    // This is 20 seconds due to a fairness bug where resumed tasks can be put at the end.
    TestUtil.await(task, 20, TimeUnit.SECONDS);

    TestUtil.verifyTaskStateChanges("fileUploadWithPauseResume", task.getResult().toString());
  }

  @Test
  public void fileUploadWithQueueCancel() throws Exception {
    System.out.println("Starting test fileUploadWithQueueCancel.");

    ResumableUploadCancelRequest.cancelCalled = false;

    final StringBuilder taskOutput = new StringBuilder();
    NetworkLayerMock.ensureNetworkMock("fileUploadWithPauseCancel", true);

    String filename = TEST_ASSET_ROOT + "image.jpg";
    ClassLoader classLoader = UploadTest.class.getClassLoader();
    InputStream imageStream = classLoader.getResourceAsStream(filename);
    Uri sourceFile = Uri.parse("file://" + filename);

    ContentResolver resolver =
        RuntimeEnvironment.application.getApplicationContext().getContentResolver();
    Shadows.shadowOf(resolver).registerInputStream(sourceFile, imageStream);

    Task<Void> task = TestUploadHelper.fileUploadQueuedCancel(taskOutput, sourceFile);

    TestUtil.await(task, 2, TimeUnit.SECONDS);

    TestUtil.verifyTaskStateChanges("fileUploadWithQueueCancel", taskOutput.toString());
    Assert.assertFalse(ResumableUploadCancelRequest.cancelCalled);
  }

  @Test
  public void adaptiveChunking() throws Exception {
    System.out.println("Starting test adaptiveChunking.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("adaptiveChunking", false);

    Task<StringBuilder> task = TestUploadHelper.adaptiveChunking();

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("adaptiveChunking", task.getResult().toString());
  }

  @Test
  public void fileUploadRecovery() throws Exception {
    System.out.println("Starting test fileUploadRecovery.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("fileUploadRecovery", false);

    String filename = TEST_ASSET_ROOT + "flubbertest.jpg";
    ClassLoader classLoader = UploadTest.class.getClassLoader();
    InputStream imageStream = classLoader.getResourceAsStream(filename);
    Uri sourceFile = Uri.parse("file://" + filename);

    ContentResolver resolver =
        RuntimeEnvironment.application.getApplicationContext().getContentResolver();
    Shadows.shadowOf(resolver).registerInputStream(sourceFile, imageStream);

    Task<StringBuilder> task = TestUploadHelper.fileUpload(sourceFile, "flubbertest.jpg");

    TestUtil.await(task, 5, TimeUnit.SECONDS);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("fileUploadRecovery", task.getResult().toString());
  }

  @Test
  public void fileUploadNoRecovery() throws Exception {
    System.out.println("Starting test fileUploadNoRecovery.");

    MockConnectionFactory factory =
        NetworkLayerMock.ensureNetworkMock("fileUploadNoRecovery", false);

    String filename = TEST_ASSET_ROOT + "flubbertest.jpg";
    ClassLoader classLoader = UploadTest.class.getClassLoader();
    InputStream imageStream = classLoader.getResourceAsStream(filename);
    Uri sourceFile = Uri.parse("file://" + filename);

    ContentResolver resolver =
        RuntimeEnvironment.application.getApplicationContext().getContentResolver();
    Shadows.shadowOf(resolver).registerInputStream(sourceFile, imageStream);

    Task<StringBuilder> task = TestUploadHelper.fileUpload(sourceFile, "flubbertest.jpg");

    TestUtil.await(task, 5, TimeUnit.SECONDS);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("fileUploadNoRecovery", task.getResult().toString());
  }

  @Test
  public void streamUploadWithInterruptions() throws InterruptedException {
    System.out.println("Starting test streamUploadWithInterruptions.");

    MockConnectionFactory factory =
        NetworkLayerMock.ensureNetworkMock("streamUploadWithInterruptions", false);

    Task<StringBuilder> task = TestUploadHelper.streamUploadWithInterruptions();

    TestUtil.await(task, 5, TimeUnit.SECONDS);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("streamUploadWithInterruptions", task.getResult().toString());
  }

  @Test
  public void removeListeners() throws InterruptedException {
    System.out.println("Starting test removeListeners.");

    NetworkLayerMock.ensureNetworkMock("streamDownload", true);

    StorageReference storage =
        FirebaseStorage.getInstance().getReferenceFromUrl("gs://fooey.appspot.com/image.jpg");
    final Semaphore semaphore = new Semaphore(0);

    StreamDownloadTask task =
        storage.getStream(
            (state, stream) -> {
              try {
                semaphore.tryAcquire(3, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                e.printStackTrace();
                Assert.fail();
              }
            });

    OnCompleteListener<StreamDownloadTask.TaskSnapshot> completeListener = ignored -> {};
    OnSuccessListener<StreamDownloadTask.TaskSnapshot> successListener = ignored -> {};
    OnProgressListener<StreamDownloadTask.TaskSnapshot> progressListener = ignored -> {};
    OnPausedListener<StreamDownloadTask.TaskSnapshot> pausedListener = ignored -> {};
    OnFailureListener failureListener = ignored -> {};
    OnCanceledListener canceledListener = () -> {};

    task.addOnCompleteListener(completeListener)
        .addOnSuccessListener(successListener)
        .addOnProgressListener(progressListener)
        .addOnFailureListener(failureListener)
        .addOnPausedListener(pausedListener)
        .addOnCanceledListener(canceledListener);

    Assert.assertEquals(1, task.completeListener.getListenerCount());
    Assert.assertEquals(1, task.successManager.getListenerCount());
    Assert.assertEquals(1, task.failureManager.getListenerCount());
    Assert.assertEquals(1, task.progressManager.getListenerCount());
    Assert.assertEquals(1, task.cancelManager.getListenerCount());
    Assert.assertEquals(1, task.pausedManager.getListenerCount());

    task.removeOnCompleteListener(completeListener)
        .removeOnSuccessListener(successListener)
        .removeOnProgressListener(progressListener)
        .removeOnFailureListener(failureListener)
        .removeOnPausedListener(pausedListener)
        .removeOnCanceledListener(canceledListener);

    Assert.assertEquals(0, task.completeListener.getListenerCount());
    Assert.assertEquals(0, task.successManager.getListenerCount());
    Assert.assertEquals(0, task.failureManager.getListenerCount());
    Assert.assertEquals(0, task.progressManager.getListenerCount());
    Assert.assertEquals(0, task.cancelManager.getListenerCount());
    Assert.assertEquals(0, task.pausedManager.getListenerCount());
    semaphore.release();
  }

  @Test
  public void badConnectivitySmallUpload() throws Exception {
    System.out.println("Starting test badConnectivitySmallUpload.");

    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("smallTextUpload", true);

    ConnectivityManager connectivityManager =
        (ConnectivityManager)
            RuntimeEnvironment.application.getSystemService(Context.CONNECTIVITY_SERVICE);
    final NetworkInfo originalNetwork = connectivityManager.getActiveNetworkInfo();

    try {
      Shadows.shadowOf(connectivityManager)
          .setActiveNetworkInfo(
              ShadowNetworkInfo.newInstance(
                  NetworkInfo.DetailedState.DISCONNECTED,
                  ConnectivityManager.TYPE_MOBILE,
                  0,
                  false,
                  NetworkInfo.State.DISCONNECTED));
      // after 10 seconds of simulated time, turn the network back on.
      MockClockHelper.install(
          new MockClockHelper() {
            @Override
            public void advance(int millis) {
              super.advance(millis);
              if (this.currentTimeMillis() > 10000) {
                Shadows.shadowOf(connectivityManager).setActiveNetworkInfo(originalNetwork);
              }
            }
          });

      Task<StringBuilder> task = TestUploadHelper.smallTextUpload();

      // TODO(mrschmidt): Lower the timeout
      TestUtil.await(task, 300, TimeUnit.SECONDS);

      factory.verifyOldMock();
      TestUtil.verifyTaskStateChanges("smallTextUpload", task.getResult().toString());
    } finally {
      MockClockHelper.install(new MockClockHelper());
      Shadows.shadowOf(connectivityManager).setActiveNetworkInfo(originalNetwork);
    }
  }
}
