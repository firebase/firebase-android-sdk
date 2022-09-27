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

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;

/** Activity for tester to compose and submit feedback. */
public class FeedbackActivity extends AppCompatActivity {

  private static final String TAG = "FeedbackActivity";
  private static final int THUMBNAIL_WIDTH = 200;
  private static final int THUMBNAIL_HEIGHT = 200;

  public static final String RELEASE_NAME_EXTRA_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.RELEASE_NAME";
  public static final String INFO_TEXT_EXTRA_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.INFO_TEXT";
  public static final String SCREENSHOT_URI_EXTRA_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.SCREENSHOT_URI";

  private FeedbackSender feedbackSender;
  private String releaseName;
  private CharSequence infoText;
  @Nullable private Uri screenshotUri;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    releaseName = getIntent().getStringExtra(RELEASE_NAME_EXTRA_KEY);
    infoText = getIntent().getCharSequenceExtra(INFO_TEXT_EXTRA_KEY);
    if (getIntent().hasExtra(SCREENSHOT_URI_EXTRA_KEY)) {
      screenshotUri = Uri.parse(getIntent().getStringExtra(SCREENSHOT_URI_EXTRA_KEY));
    }
    feedbackSender = FeedbackSender.getInstance();
    setupView();
  }

  private void setupView() {
    setContentView(R.layout.activity_feedback);

    TextView infoTextView = this.findViewById(R.id.infoText);
    infoTextView.setText(infoText);
    infoTextView.setMovementMethod(LinkMovementMethod.getInstance());
    Button submitButton = this.findViewById(R.id.submitButton);
    submitButton.setOnClickListener(this::submitFeedback);

    Bitmap thumbnail = screenshotUri == null ? null : readThumbnail();
    if (thumbnail != null) {
      ImageView screenshotImageView = this.findViewById(R.id.thumbnail);
      screenshotImageView.setImageBitmap(thumbnail);
    } else {
      LogWrapper.getInstance().e(TAG, "No screenshot available");
      View screenshotErrorLabel = this.findViewById(R.id.screenshotErrorLabel);
      screenshotErrorLabel.setVisibility(View.VISIBLE);
    }
  }

  @Nullable
  private Bitmap readThumbnail() {
    Bitmap thumbnail;
    try {
      thumbnail =
          ImageUtils.readScaledImage(
              getContentResolver(), screenshotUri, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
    } catch (IOException | SecurityException e) {
      LogWrapper.getInstance()
          .e(TAG, "Could not read screenshot image from URI: " + screenshotUri, e);
      return null;
    }
    if (thumbnail == null) {
      LogWrapper.getInstance().e(TAG, "Could not decode screenshot image: " + screenshotUri);
    }
    return thumbnail;
  }

  public void submitFeedback(View view) {
    setSubmittingStateEnabled(true);
    EditText feedbackText = findViewById(R.id.feedbackText);
    feedbackSender
        .sendFeedback(releaseName, feedbackText.getText().toString(), screenshotUri)
        .addOnSuccessListener(
            unused -> {
              LogWrapper.getInstance().i(TAG, "Feedback submitted");
              Toast.makeText(this, "Feedback submitted", Toast.LENGTH_LONG).show();
              finish();
            })
        .addOnFailureListener(
            e -> {
              LogWrapper.getInstance().e(TAG, "Failed to submit feedback", e);
              Toast.makeText(this, "Error submitting feedback", Toast.LENGTH_LONG).show();
              setSubmittingStateEnabled(false);
            });
  }

  public void setSubmittingStateEnabled(boolean loading) {
    findViewById(R.id.submitButton).setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
    findViewById(R.id.loadingLabel).setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
  }
}
