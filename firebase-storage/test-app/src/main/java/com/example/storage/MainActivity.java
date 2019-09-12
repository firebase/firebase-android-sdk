// Copyright 2019 Google LLC
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

package com.example.storage;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.TestCommandHelper;
import com.google.firebase.storage.TestDownloadHelper;
import com.google.firebase.storage.TestDownloadHelper.StreamDownloadResponse;
import com.google.firebase.storage.TestUploadHelper;
import com.google.firebase.storage.network.ConnectionInjector;
import com.google.firebase.storage.network.RecordingHttpURLConnection;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;

/** test app for Firebase Storage */
public class MainActivity extends AppCompatActivity {
  private static final String TAG = "MainActivity";

  // Storage Permissions
  private static final int REQUEST_EXTERNAL_STORAGE = 1;
  private static final String[] PERMISSIONS_STORAGE = {
    Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
  };
  static Runnable sR;
  boolean t1;
  boolean mStopped = false;

  /**
   * Checks if the app has permission to write to device storage
   *
   * <p>If the app does not has permission then the user will be prompted to grant permissions
   *
   * @param activity activity
   */
  public static void verifyStoragePermissions(Activity activity, Runnable r) {
    // Check if we have write permission
    int permission =
        ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

    if (permission != PackageManager.PERMISSION_GRANTED) {
      sR = r;
      // We don't have permission so prompt the user
      ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
    } else {
      r.run();
    }
  }

  private static void ensureFolder() {
    StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskWrites();
    try {
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).mkdirs();
    } finally {
      StrictMode.setThreadPolicy(oldPolicy);
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    FirebaseApp.initializeApp(getApplicationContext());
    FirebaseAuth.getInstance().signInAnonymously();
    FirebaseStorage storage = FirebaseStorage.getInstance();

    Button clickButton = findViewById(R.id.streamDownload);
    clickButton.setOnClickListener(
        v -> {
          final EditText editText = findViewById(R.id.editText);
          editText.setText(null, TextView.BufferType.EDITABLE);

          if (t1) {
            ImageView img = findViewById(R.id.imageView1);
            img.setImageBitmap(null);
            t1 = false;
            return;
          }
          t1 = true;
          runTaskTest(
              "streamDownload",
              () ->
                  TestDownloadHelper.streamDownload(
                      bitmap -> {
                        ImageView img = findViewById(R.id.imageView1);
                        img.setImageBitmap(bitmap);
                        if (bitmap == null) {
                          editText.setText("error!", TextView.BufferType.EDITABLE);
                        }
                      },
                      null,
                      "image.jpg",
                      -1));
        });

    clickButton = findViewById(R.id.smallTextUpload);
    clickButton.setOnClickListener(
        v ->
            runTaskTest(
                "smallTextUpload",
                () -> {
                  try {
                    return TestUploadHelper.smallTextUpload();
                  } catch (Exception e) {
                    e.printStackTrace();
                  }
                  return null;
                }));

    clickButton = findViewById(R.id.uploadWithSpace);
    clickButton.setOnClickListener(
        v ->
            runTaskTest(
                "uploadWithSpace",
                () -> {
                  try {
                    return TestUploadHelper.byteUpload(storage.getReference("hello world.txt"));
                  } catch (Exception e) {
                    e.printStackTrace();
                  }
                  return null;
                }));

    clickButton = findViewById(R.id.smallTextUpload2);
    clickButton.setOnClickListener(
        v ->
            runTaskTest(
                "smallTextUpload2",
                () -> {
                  try {
                    return TestUploadHelper.smallTextUpload2();
                  } catch (Exception e) {
                    e.printStackTrace();
                  }
                  return null;
                }));

    clickButton = findViewById(R.id.updateMetadata);
    clickButton.setOnClickListener(
        v -> runTaskTest("updateMetadata", TestCommandHelper::testUpdateMetadata));

    clickButton = findViewById(R.id.unicodeMetadata);
    clickButton.setOnClickListener(
        v -> runTaskTest("unicodeMetadata", TestCommandHelper::testUnicodeMetadata));

    clickButton = findViewById(R.id.clearMetadata);
    clickButton.setOnClickListener(
        v -> runTaskTest("clearMetadata", TestCommandHelper::testClearMetadata));

    clickButton = findViewById(R.id.deleteBlob);
    clickButton.setOnClickListener(v -> runTaskTest("deleteBlob", TestCommandHelper::deleteBlob));

    clickButton = findViewById(R.id.fileDownload);
    clickButton.setOnClickListener(
        v ->
            verifyStoragePermissions(MainActivity.this, () -> imageDownload("fileDownload", null)));

    clickButton = findViewById(R.id.fileUpload);
    clickButton.setOnClickListener(
        v ->
            verifyStoragePermissions(
                MainActivity.this, () -> fileUpload("fileUpload", "image.jpg")));

    clickButton = findViewById(R.id.fileUploadWithPauseCancel);
    clickButton.setOnClickListener(
        v -> verifyStoragePermissions(MainActivity.this, this::fileUploadWithPauseCancel));

    clickButton = findViewById(R.id.fileUploadWithPauseResume);
    clickButton.setOnClickListener(
        v -> verifyStoragePermissions(MainActivity.this, this::fileUploadWithPauseResume));

    clickButton = findViewById(R.id.fileDownloadWithResume);
    clickButton.setOnClickListener(
        v ->
            verifyStoragePermissions(
                MainActivity.this,
                () -> {
                  SortedSet<Integer> exceptionPoints = new TreeSet<>();
                  exceptionPoints.add(1076408 / 4);
                  exceptionPoints.add(1076408 / 4 * 2);
                  exceptionPoints.add(1076408 / 4 * 3);
                  imageDownload("fileDownloadResume", exceptionPoints);
                }));

    clickButton = findViewById(R.id.fileUploadWithPauseActivity);
    clickButton.setOnClickListener(
        v -> verifyStoragePermissions(MainActivity.this, this::fileUploadWithPauseActivitySub));

    clickButton = findViewById(R.id.emptyUpload);
    clickButton.setOnClickListener(
        v ->
            verifyStoragePermissions(
                MainActivity.this, () -> fileUpload("emptyUpload", "empty.dat")));

    clickButton = findViewById(R.id.emptyDownload);
    clickButton.setOnClickListener(
        v ->
            verifyStoragePermissions(
                MainActivity.this, () -> fileDownload("emptyDownload", "empty.dat")));

    clickButton = findViewById(R.id.streamUploadWithInterruptions);
    clickButton.setOnClickListener(
        v ->
            verifyStoragePermissions(
                MainActivity.this,
                () ->
                    runTaskTest(
                        "streamUploadWithInterruptions",
                        TestUploadHelper::streamUploadWithInterruptions)));

    clickButton = findViewById(R.id.streamDownloadWithResume);
    clickButton.setOnClickListener(
        v -> {
          final EditText editText = findViewById(R.id.editText);
          editText.setText(null, TextView.BufferType.EDITABLE);

          if (t1) {
            ImageView img = findViewById(R.id.imageView1);
            img.setImageBitmap(null);
            t1 = false;
            return;
          }
          t1 = true;

          SortedSet<Integer> exceptionPoints = new TreeSet<>();
          exceptionPoints.add(1076408 / 4);
          exceptionPoints.add(1076408 / 4 * 2);
          exceptionPoints.add(1076408 / 4 * 3);
          runTaskTest(
              "streamDownloadWithResume",
              () ->
                  TestDownloadHelper.streamDownload(
                      bitmap -> {
                        if (bitmap == null) {
                          editText.setText("error!", TextView.BufferType.EDITABLE);
                        } else {
                          ImageView img = findViewById(R.id.imageView1);
                          img.setImageBitmap(bitmap);
                        }
                      },
                      null,
                      "image.jpg",
                      -1),
              exceptionPoints,
              null);
        });

    clickButton = findViewById(R.id.emptyStreamDownload);
    clickButton.setOnClickListener(
        v -> {
          final EditText editText = findViewById(R.id.editText);
          editText.setText(null, TextView.BufferType.EDITABLE);

          runTaskTest(
              "emptyStreamDownload",
              () ->
                  TestDownloadHelper.streamDownload(
                      null,
                      bytes -> {
                        Preconditions.checkNotNull(bytes);
                        Preconditions.checkState(0 == bytes.length);
                      },
                      "empty.dat",
                      -1));
        });

    clickButton = findViewById(R.id.streamDownloadWithCancel);
    clickButton.setOnClickListener(
        v -> {
          final EditText editText = findViewById(R.id.editText);
          editText.setText(null, TextView.BufferType.EDITABLE);

          SortedSet<Integer> exceptionPoints = new TreeSet<>();
          exceptionPoints.add(1076408 / 4);
          exceptionPoints.add(1076408 / 4 * 2);
          exceptionPoints.add(1076408 / 4 * 3);

          runTaskTest(
              "streamDownloadWithResumeAndCancel",
              () ->
                  TestDownloadHelper.streamDownload(
                      null,
                      bytes -> {
                        Preconditions.checkNotNull(bytes);
                        Preconditions.checkState(0 == bytes.length);
                      },
                      "image.jpg",
                      26000),
              exceptionPoints,
              null);
        });

    clickButton = findViewById(R.id.adaptiveChunking);
    clickButton.setOnClickListener(
        v -> {
          final EditText editText = findViewById(R.id.editText);
          editText.setText(null, TextView.BufferType.EDITABLE);

          SortedSet<Integer> exceptionPoints = new TreeSet<>();
          exceptionPoints.add(1024 * 1024 + 200);

          runTaskTest(
              "adaptiveChunking",
              () -> {
                try {
                  return TestUploadHelper.adaptiveChunking();
                } catch (Exception e) {
                  System.err.println(e.toString());
                }
                return null;
              },
              null,
              exceptionPoints);
        });

    clickButton = findViewById(R.id.download12kb);
    clickButton.setOnClickListener(
        v ->
            runTaskTest(
                "12kbdownload",
                () ->
                    TestDownloadHelper.streamDownload(
                        null,
                        bytes -> {
                          Preconditions.checkNotNull(bytes);
                          Log.i(TAG, "Downloaded " + bytes.length + " bytes.");
                        },
                        "12kb.dat",
                        -1),
                null,
                null));

    clickButton = findViewById(R.id.upload1100kb);
    clickButton.setOnClickListener(v -> fileUpload("1.1MB upload", "1.1mb.dat"));

    clickButton = findViewById(R.id.multiBucket);
    clickButton.setOnClickListener(v -> runMultiBucketSequence());

    clickButton = findViewById(R.id.listSinglePage);
    clickButton.setOnClickListener(
        v -> runTaskTest("listSinglePage", () -> TestCommandHelper.listFiles(10, 1)));

    clickButton = findViewById(R.id.listMultplePages);
    clickButton.setOnClickListener(
        v -> runTaskTest("listMultplePages", () -> TestCommandHelper.listFiles(10, 10)));

    clickButton = findViewById(R.id.listAll);
    clickButton.setOnClickListener(v -> runTaskTest("listAll", TestCommandHelper::listAllFiles));
  }

  private void runTaskTest(final String testName, TaskProvider runner) {
    runTaskTest(testName, runner, null, null);
  }

  private void runTaskTest(final String testName, StreamProvider runner) {
    runTaskTest(testName, runner, null, null);
  }

  private void runTaskTest(
      final String testName,
      TaskProvider runner,
      final @Nullable SortedSet<Integer> inputStreamInjections,
      final @Nullable SortedSet<Integer> outputStreamInjections) {
    final Task<StringBuilder> task = runner.getTask();

    StreamProvider streamProvider =
        () -> {
          TaskCompletionSource<StreamDownloadResponse> result = new TaskCompletionSource<>();
          task.continueWith(
              AsyncTask.THREAD_POOL_EXECUTOR,
              (Continuation<StringBuilder, Object>)
                  testResult -> {
                    StreamDownloadResponse response = new StreamDownloadResponse();
                    response.mainTask = testResult.getResult();
                    result.setResult(response);
                    return response;
                  });
          return result.getTask();
        };

    runTaskTest(testName, streamProvider, inputStreamInjections, outputStreamInjections);
  }

  private void runTaskTest(
      final String testName,
      StreamProvider runner,
      final @Nullable SortedSet<Integer> inputStreamInjections,
      final @Nullable SortedSet<Integer> outputStreamInjections) {
    final StringBuilder networkBuilder = new StringBuilder();

    final ConnectionInjector injector =
        new ConnectionInjector() {
          public void injectInputStream(int start, int end) throws IOException {
            if (inputStreamInjections != null) {
              SortedSet<Integer> subset = inputStreamInjections.subSet(start, end);
              if (!subset.isEmpty()) {
                // We have to remove the exceptions here so that the second attempt can succeed.
                inputStreamInjections.remove(subset.first());
                throw new IOException("Injected Exception");
              }
            }
          }

          public void injectOutputStream(int start, int end) throws IOException {
            if (outputStreamInjections != null) {
              SortedSet<Integer> subset = outputStreamInjections.subSet(start, end);
              if (!subset.isEmpty()) {
                // We have to remove the exceptions here so that the second attempt can succeed.
                outputStreamInjections.remove(subset.first());
                throw new IOException("Injected Exception");
              }
            }
          }
        };

    RecordingHttpURLConnection.install(networkBuilder, injector);

    final Task<StreamDownloadResponse> task = runner.getTask();
    final EditText editText = findViewById(R.id.editText);
    editText.setText(null, TextView.BufferType.EDITABLE);

    task.continueWith(
        AsyncTask.THREAD_POOL_EXECUTOR,
        testOutput -> {
          StreamDownloadResponse taskOutput = testOutput.getResult();
          try {
            if (taskOutput.mainTask.length() > 0) {
              taskOutput.mainTask.append(("\ndone.\n"));
              FileOutputStream taskStream =
                  MainActivity.this.openFileOutput(testName + "_task.txt", 0);
              taskStream.write(taskOutput.mainTask.toString().getBytes());
              taskStream.close();
            }
            if (taskOutput.backgroundTask.length() > 0) {
              taskOutput.backgroundTask.append(("\ndone.\n"));
              FileOutputStream backgroundStream =
                  MainActivity.this.openFileOutput(testName + "_background.txt", 0);
              backgroundStream.write(taskOutput.backgroundTask.toString().getBytes());
              backgroundStream.close();
            }
            if (networkBuilder.length() > 0) {
              FileOutputStream networkStream =
                  MainActivity.this.openFileOutput(testName + "_network.txt", 0);
              networkBuilder.append("\n");
              networkStream.write(networkBuilder.toString().getBytes());
              networkStream.close();
            }
          } catch (IOException e) {
            e.printStackTrace();
          }

          if (!mStopped) {
            MainActivity.this.runOnUiThread(
                () ->
                    editText.setText(
                        MainActivity.this.getFilesDir().toString()
                            + " "
                            + testName
                            + " task.txt/network.txt",
                        TextView.BufferType.EDITABLE));
          }
          return null;
        });
  }

  private void runMultiBucketSequence() {
    AsyncTask.execute(
        () -> {
          final EditText editText = findViewById(R.id.editText);

          Log.d(TAG, "Signing in");
          Log.d(TAG, "Signed in");
          final FirebaseStorage defaultInstance =
              FirebaseStorage.getInstance("gs://fooey.appspot.com");
          final FirebaseStorage stagingInstance =
              FirebaseStorage.getInstance("gs://fooey-secondary");

          Log.d(TAG, "Uploading first file");
          defaultInstance
              .getReference("multibucket1.test")
              .putBytes(new byte[] {1, 2, 3})
              .addOnFailureListener(
                  e -> {
                    Log.e(TAG, "Upload of first file failed", e);
                    editText.setText("Upload of first file failed", TextView.BufferType.EDITABLE);
                  })
              .addOnSuccessListener(
                  taskSnapshot -> {
                    Log.d(TAG, "Uploading second file");
                    stagingInstance
                        .getReference("multibucket2.test")
                        .putBytes(new byte[] {3, 4, 5})
                        .addOnFailureListener(
                            e -> {
                              Log.e(TAG, "Upload of second file failed", e);
                              editText.setText(
                                  "Upload of second file failed", TextView.BufferType.EDITABLE);
                            })
                        .addOnSuccessListener(
                            taskSnapshot1 -> {
                              Log.d(TAG, "Upload of second file succeeded");
                              editText.setText("Success", TextView.BufferType.EDITABLE);
                            });
                  });
        });
  }

  @Override
  public void onStop() {
    super.onStop();
    mStopped = true;
    TestUploadHelper.cancelInProgressTask();
    new CountDownTimer(5000, 1000) {
      @Override
      public void onTick(long millisUntilFinished) {}

      @Override
      public void onFinish() {}
    }.start();
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void fileUpload(String testName, final String filename) {
    EditText editText = findViewById(R.id.editText);
    editText.setText(null, TextView.BufferType.EDITABLE);

    ensureFolder();

    final File file =
        new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            filename);
    Log.i(TAG, "Reading file from " + file.getAbsolutePath());

    if (!file.exists()) {
      editText.setText("upload to " + file.getAbsolutePath(), TextView.BufferType.EDITABLE);
      return;
    }

    final Uri sourceUri = Uri.fromFile(file);
    runTaskTest(testName, () -> TestUploadHelper.fileUpload(sourceUri, filename));
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void fileUploadWithPauseCancel() {
    EditText editText = findViewById(R.id.editText);
    editText.setText(null, TextView.BufferType.EDITABLE);

    ensureFolder();

    final File file =
        new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "image.jpg");
    if (!file.exists()) {
      editText.setText("upload to " + file.getAbsolutePath(), TextView.BufferType.EDITABLE);
      return;
    }

    final Uri sourceUri = Uri.fromFile(file);
    runTaskTest(
        "fileUploadWithPauseCancel",
        () -> TestUploadHelper.fileUploadWithPauseCancel(null, sourceUri));
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void fileUploadWithPauseResume() {
    EditText editText = findViewById(R.id.editText);
    editText.setText(null, TextView.BufferType.EDITABLE);

    ensureFolder();

    final File file =
        new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "image.jpg");
    if (!file.exists()) {
      editText.setText("upload to " + file.getAbsolutePath(), TextView.BufferType.EDITABLE);
      return;
    }

    final Uri sourceUri = Uri.fromFile(file);
    runTaskTest(
        "fileUploadWithPauseResume",
        () -> TestUploadHelper.fileUploadWithPauseResume(null, sourceUri));
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void fileUploadWithPauseActivitySub() {
    EditText editText = findViewById(R.id.editText);
    editText.setText(null, TextView.BufferType.EDITABLE);

    ensureFolder();

    final File file =
        new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "image.jpg");
    if (!file.exists()) {
      editText.setText("upload to " + file.getAbsolutePath(), TextView.BufferType.EDITABLE);
      return;
    }

    final Uri sourceUri = Uri.fromFile(file);
    runTaskTest(
        "fileUploadWithPauseAct",
        () -> TestUploadHelper.fileUploadWithPauseActSub(MainActivity.this, sourceUri));
  }

  private void fileDownload(String testname, String filename) {
    EditText editText = findViewById(R.id.editText);
    editText.setText(null, TextView.BufferType.EDITABLE);

    final File file =
        new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            filename);

    final Uri destinationUri = Uri.fromFile(file);
    runTaskTest(testname, () -> TestDownloadHelper.fileDownload(destinationUri, null, -1));
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void imageDownload(String testName, @Nullable SortedSet<Integer> injectExecptionsAt) {
    EditText editText = findViewById(R.id.editText);
    editText.setText(null, TextView.BufferType.EDITABLE);

    if (t1) {
      ImageView img = findViewById(R.id.imageView1);
      img.setImageBitmap(null);
      t1 = false;
      return;
    }
    t1 = true;
    ensureFolder();

    final File file =
        new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "image.jpg");
    Log.i(TAG, "Saving file to " + file.getAbsolutePath());

    final Uri destinationUri = Uri.fromFile(file);
    runTaskTest(
        testName,
        () ->
            TestDownloadHelper.fileDownload(
                destinationUri,
                () -> {
                  AsyncTask<Void, Void, Void> task =
                      new AsyncTask<Void, Void, Void>() {
                        Bitmap bitmap = null;

                        @Override
                        protected Void doInBackground(Void... params) {
                          try {
                            FileInputStream fileInputStream = new FileInputStream(file);
                            bitmap =
                                BitmapFactory.decodeStream(
                                    new BufferedInputStream(fileInputStream));
                            fileInputStream.close();
                          } catch (IOException e) {
                            e.printStackTrace();
                          }
                          MainActivity.this.runOnUiThread(
                              () -> {
                                ImageView img = findViewById(R.id.imageView1);
                                img.setImageBitmap(bitmap);
                              });
                          return null;
                        }
                      };
                  task.execute();
                },
                -1),
        injectExecptionsAt,
        null);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      sR.run();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    // noinspection SimplifiableIfStatement
    if (id == R.id.action_settings) {
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  private interface TaskProvider {
    Task<StringBuilder> getTask();
  }

  private interface StreamProvider {
    Task<TestDownloadHelper.StreamDownloadResponse> getTask();
  }
}
