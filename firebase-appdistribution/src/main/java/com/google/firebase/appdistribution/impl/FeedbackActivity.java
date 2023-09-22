// Copyright 2022 Google LLC
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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.UiThread;
import java.io.IOException;
import java.util.concurrent.Executor;
import javax.inject.Inject;

/** Activity for tester to compose and submit feedback. */
public class FeedbackActivity extends AppCompatActivity {
  private static final String TAG = "FeedbackActivity";
  private static final int SCREENSHOT_TARGET_WIDTH_PX = 600;
  private static final int SCREENSHOT_TARGET_HEIGHT_PX = -1; // scale proportionally

  public static final String RELEASE_NAME_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.RELEASE_NAME";
  public static final String ADDITIONAL_FORM_TEXT_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.ADDITIONAL_FORM_TEXT";
  public static final String SCREENSHOT_URI_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.SCREENSHOT_URI";
  public static final String FEEDBACK_TRIGGER_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.FEEDBACK_TRIGGER";

  private final ActivityResultLauncher<Intent> chooseScreenshotLauncher =
      registerForActivityResult(new StartActivityForResult(), this::handleChooseScreenshotResult);

  @Inject FeedbackSender feedbackSender;
  @Inject @Blocking Executor blockingExecutor;
  @Inject @UiThread Executor uiThreadExecutor;

  @Nullable private String releaseName; // in development-mode the releaseName might be null
  private CharSequence additionalFormText;
  @Nullable private Uri screenshotUri;
  private FeedbackTrigger feedbackTrigger = FeedbackTrigger.UNKNOWN;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // Inject members before calling super.onCreate to avoid issues with fragment restoration
    AppDistroComponent.getInstance().inject(this);

    super.onCreate(savedInstanceState);

    if (savedInstanceState != null) {
      releaseName = savedInstanceState.getString(RELEASE_NAME_KEY);
      additionalFormText = savedInstanceState.getCharSequence(ADDITIONAL_FORM_TEXT_KEY);
      String feedbackTriggerKey = savedInstanceState.getString(FEEDBACK_TRIGGER_KEY);
      if (feedbackTriggerKey != null) {
        feedbackTrigger = FeedbackTrigger.fromString(feedbackTriggerKey);
      }
      String screenshotUriString = savedInstanceState.getString(SCREENSHOT_URI_KEY);
      if (screenshotUriString != null) {
        screenshotUri = Uri.parse(screenshotUriString);
      }
    } else {
      releaseName = getIntent().getStringExtra(RELEASE_NAME_KEY);
      additionalFormText = getIntent().getCharSequenceExtra(ADDITIONAL_FORM_TEXT_KEY);
      if (getIntent().hasExtra(FEEDBACK_TRIGGER_KEY)) {
        feedbackTrigger =
            FeedbackTrigger.fromString(getIntent().getStringExtra(FEEDBACK_TRIGGER_KEY));
      }
      if (getIntent().hasExtra(SCREENSHOT_URI_KEY)) {
        screenshotUri = Uri.parse(getIntent().getStringExtra(SCREENSHOT_URI_KEY));
      }
    }

    setupView();
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    outState.putString(RELEASE_NAME_KEY, releaseName);
    outState.putCharSequence(ADDITIONAL_FORM_TEXT_KEY, additionalFormText);
    outState.putString(SCREENSHOT_URI_KEY, screenshotUri.toString());
    outState.putString(FEEDBACK_TRIGGER_KEY, feedbackTrigger.toString());
    super.onSaveInstanceState(outState);
  }

  private void setupView() {
    setTheme(R.style.FeedbackTheme);
    setContentView(R.layout.activity_feedback);

    TextView additionalFormTextView = this.findViewById(R.id.additionalFormText);
    additionalFormTextView.setText(additionalFormText);
    additionalFormTextView.setMovementMethod(LinkMovementMethod.getInstance());

    findViewById(R.id.backButton).setOnClickListener(v -> finish());
    findViewById(R.id.sendButton).setOnClickListener(this::submitFeedback);

    findViewById(R.id.chooseScreenshotButton)
        .setOnClickListener(
            v -> {
              Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
              intent.addCategory(Intent.CATEGORY_OPENABLE);
              intent.setType("*/*");
              intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/png", "image/jpeg"});
              chooseScreenshotLauncher.launch(intent);
            });

    setupScreenshot();
  }

  private void setupScreenshot() {
    blockingExecutor.execute(
        () -> {
          // Do I/O on separate thread in order to not block the UI
          Bitmap screenshot = readScreenshot(screenshotUri);
          if (screenshot != null) {
            runOnUiThread(
                () -> {
                  ImageView imageView = findViewById(R.id.screenshotImageView);
                  imageView.setImageBitmap(screenshot);
                  imageView.setVisibility(VISIBLE);
                  CheckBox checkBox = findViewById(R.id.screenshotCheckBox);
                  checkBox.setChecked(true);
                  checkBox.setOnClickListener(
                      v -> imageView.setVisibility(checkBox.isChecked() ? VISIBLE : GONE));
                });
          } else {
            runOnUiThread(
                () -> {
                  CheckBox checkBox = findViewById(R.id.screenshotCheckBox);
                  checkBox.setText(R.string.no_screenshot);
                  checkBox.setClickable(false);
                  checkBox.setChecked(false);
                });
          }
        });
  }

  private void handleChooseScreenshotResult(ActivityResult activityResult) {
    int resultCode = activityResult.getResultCode();
    Intent intent = activityResult.getData();
    if (resultCode == Activity.RESULT_OK && intent != null && intent.getData() != null) {
      Uri uri = intent.getData();
      LogWrapper.d(TAG, "Selected custom screenshot URI: " + uri);
      screenshotUri = uri;
      setupScreenshot();
    } else {
      LogWrapper.d(TAG, "No custom screenshot selected. Not changing screenshot URI.");
    }
  }

  @Nullable
  private Bitmap readScreenshot(@Nullable Uri uri) {
    if (uri == null) {
      return null;
    }
    Bitmap bitmap;
    try {
      bitmap =
          ImageUtils.readScaledImage(
              getContentResolver(), uri, SCREENSHOT_TARGET_WIDTH_PX, SCREENSHOT_TARGET_HEIGHT_PX);
    } catch (IOException | SecurityException e) {
      LogWrapper.e(TAG, "Could not read screenshot image from URI: " + uri, e);
      return null;
    }
    if (bitmap == null) {
      LogWrapper.e(TAG, "Could not decode screenshot image from URI: " + uri);
    }
    return bitmap;
  }

  public void submitFeedback(View view) {
    setSubmittingStateEnabled(true);
    if (releaseName == null) {
      Toast.makeText(this, R.string.feedback_no_release, Toast.LENGTH_LONG).show();
      LogWrapper.w(TAG, "Not submitting feedback because development mode is enabled.");
      finish();
      return;
    }
    EditText feedbackText = findViewById(R.id.feedbackText);
    CheckBox screenshotCheckBox = findViewById(R.id.screenshotCheckBox);
    feedbackSender
        .sendFeedback(
            releaseName,
            feedbackText.getText().toString(),
            screenshotCheckBox.isChecked() ? screenshotUri : null,
            feedbackTrigger)
        .addOnSuccessListener(
            uiThreadExecutor,
            unused -> {
              LogWrapper.i(TAG, "Feedback submitted");
              Toast.makeText(this, "Feedback submitted", Toast.LENGTH_LONG).show();
              finish();
            })
        .addOnFailureListener(
            uiThreadExecutor,
            e -> {
              LogWrapper.e(TAG, "Failed to submit feedback", e);
              Toast.makeText(this, "Error submitting feedback", Toast.LENGTH_LONG).show();
              setSubmittingStateEnabled(false);
            });
  }

  public void setSubmittingStateEnabled(boolean loading) {
    findViewById(R.id.sendButton).setVisibility(loading ? GONE : VISIBLE);
    findViewById(R.id.sendSpinner).setVisibility(loading ? VISIBLE : GONE);
    findViewById(R.id.feedbackText).setEnabled(!loading);
    findViewById(R.id.feedbackText).setFocusable(!loading);
  }
}
