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
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/** Activity for tester to compose and submit feedback. */
public class FeedbackActivity extends AppCompatActivity {

  private static final String TAG = "FeedbackActivity";

  public static final String RELEASE_NAME_EXTRA_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.RELEASE_NAME";
  public static final String SCREENSHOT_FILENAME_EXTRA_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.SCREENSHOT_FILE_NAME";

  private FeedbackSender feedbackSender;
  private String releaseName;
  @Nullable private Bitmap screenshot;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    releaseName = getIntent().getStringExtra(RELEASE_NAME_EXTRA_KEY);
    screenshot = readScreenshot(getIntent().getStringExtra(SCREENSHOT_FILENAME_EXTRA_KEY));
    feedbackSender = FeedbackSender.getInstance();
    setupView();
  }

  private void setupView() {
    setContentView(R.layout.activity_feedback);
    if (screenshot != null) {
      ImageView screenshotImageView = (ImageView) this.findViewById(R.id.screenshot);
      screenshotImageView.setImageBitmap(screenshot);
    } else {
      View screenshotErrorLabel = this.findViewById(R.id.screenshotErrorLabel);
      screenshotErrorLabel.setVisibility(View.VISIBLE);
    }
  }

  @Nullable
  private Bitmap readScreenshot(String filename) {
    try {
      return BitmapFactory.decodeFile(getFileStreamPath(filename).getAbsolutePath());
    } catch (Exception | OutOfMemoryError e) {
      LogWrapper.getInstance()
          .e("Failed to read screenshot from storage, preparing feedback without screenshot", e);
      return null;
    }
  }

  public void submitFeedback(View view) {
    setSubmittingStateEnabled(true);
    EditText feedbackText = (EditText) findViewById(R.id.feedbackText);
    feedbackSender
        .sendFeedback(releaseName, feedbackText.getText().toString(), screenshot)
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
