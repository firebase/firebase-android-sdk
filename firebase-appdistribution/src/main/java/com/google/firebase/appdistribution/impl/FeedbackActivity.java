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

import android.annotation.SuppressLint;
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;

/** Activity for tester to compose and submit feedback. */
public class FeedbackActivity extends AppCompatActivity {
  private static final String TAG = "FeedbackActivity";
  private static final int SCREENSHOT_TARGET_WIDTH_PX = 600;
  private static final int SCREENSHOT_TARGET_HEIGHT_PX = -1; // scale proportionally

  public static final String RELEASE_NAME_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.RELEASE_NAME";
  public static final String INFO_TEXT_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.INFO_TEXT";
  public static final String SCREENSHOT_URI_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.SCREENSHOT_URI";

  private FeedbackSender feedbackSender;
  @Nullable private String releaseName; // in development-mode the releaseName might be null
  private CharSequence infoText;
  @Nullable private Uri screenshotUri;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    feedbackSender = FeedbackSender.getInstance();
    if (savedInstanceState != null) {
      releaseName = savedInstanceState.getString(RELEASE_NAME_KEY);
      infoText = savedInstanceState.getCharSequence(INFO_TEXT_KEY);
      String screenshotUriString = savedInstanceState.getString(SCREENSHOT_URI_KEY);
      if (screenshotUriString != null) {
        screenshotUri = Uri.parse(screenshotUriString);
      }
    } else {
      releaseName = getIntent().getStringExtra(RELEASE_NAME_KEY);
      infoText = getIntent().getCharSequenceExtra(INFO_TEXT_KEY);
      if (getIntent().hasExtra(SCREENSHOT_URI_KEY)) {
        screenshotUri = Uri.parse(getIntent().getStringExtra(SCREENSHOT_URI_KEY));
      }
    }
    setupView();
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    outState.putString(RELEASE_NAME_KEY, releaseName);
    outState.putCharSequence(INFO_TEXT_KEY, infoText);
    outState.putString(SCREENSHOT_URI_KEY, screenshotUri.toString());
    super.onSaveInstanceState(outState);
  }

  private void setupView() {
    setTheme(R.style.FeedbackTheme);
    setContentView(R.layout.activity_feedback);

    TextView infoTextView = this.findViewById(R.id.infoText);
    infoTextView.setText(infoText);
    infoTextView.setMovementMethod(LinkMovementMethod.getInstance());

    findViewById(R.id.backButton).setOnClickListener(v -> finish());
    findViewById(R.id.sendButton).setOnClickListener(this::submitFeedback);

    setupScreenshot();
  }

  private void setupScreenshot() {
    feedbackSender
        .getBlockingExecutor()
        .execute(
            () -> {
              // do I/O on separate thread in order to not block the UI
              Bitmap screenshot = screenshotUri == null ? null : readScreenshot();
              if (screenshot != null) {
                runOnUiThread(
                    () -> {
                      ImageView imageView = findViewById(R.id.screenshotImageView);
                      imageView.setImageBitmap(screenshot);
                      CheckBox checkBox = findViewById(R.id.screenshotCheckBox);
                      checkBox.setChecked(true);
                      checkBox.setOnClickListener(
                          v -> imageView.setVisibility(checkBox.isChecked() ? VISIBLE : GONE));
                    });
              } else {
                LogWrapper.e(TAG, "No screenshot available");
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

  @Nullable
  private Bitmap readScreenshot() {
    Bitmap bitmap;
    try {
      bitmap =
          ImageUtils.readScaledImage(
              getContentResolver(),
              screenshotUri,
              SCREENSHOT_TARGET_WIDTH_PX,
              SCREENSHOT_TARGET_HEIGHT_PX);
    } catch (IOException | SecurityException e) {
      LogWrapper.e(TAG, "Could not read screenshot image from URI: " + screenshotUri, e);
      return null;
    }
    if (bitmap == null) {
      LogWrapper.e(TAG, "Could not decode screenshot image: " + screenshotUri);
    }
    return bitmap;
  }

  // TODO(b/261014422): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  public void submitFeedback(View view) {
    setSubmittingStateEnabled(true);
    if (releaseName == null) {
      // Don't actually send feedback in development-mode
      Toast.makeText(this, R.string.feedback_no_release, Toast.LENGTH_LONG).show();
      finish();
      return;
    }
    EditText feedbackText = findViewById(R.id.feedbackText);
    CheckBox screenshotCheckBox = findViewById(R.id.screenshotCheckBox);
    feedbackSender
        .sendFeedback(
            releaseName,
            feedbackText.getText().toString(),
            screenshotCheckBox.isChecked() ? screenshotUri : null)
        .addOnSuccessListener(
            unused -> {
              LogWrapper.i(TAG, "Feedback submitted");
              Toast.makeText(this, "Feedback submitted", Toast.LENGTH_LONG).show();
              finish();
            })
        .addOnFailureListener(
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
