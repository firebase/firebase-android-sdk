package com.google.firebase.appdistribution.impl;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.FirebaseApp;

/** Activity for tester to compose and submit feedback. */
public class FeedbackActivity extends AppCompatActivity {

  private static final String TAG = "FeedbackActivity";

  public static final String RELEASE_NAME_EXTRA_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.RELEASE_NAME";
  public static final String SCREENSHOT_EXTRA_KEY =
      "com.google.firebase.appdistribution.FeedbackActivity.SCREENSHOT";

  private FirebaseAppDistributionTesterApiClient testerApiClient;
  private String releaseName;
  private Bitmap screenshot;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    releaseName = getIntent().getStringExtra(RELEASE_NAME_EXTRA_KEY);
    screenshot = getIntent().getParcelableExtra(SCREENSHOT_EXTRA_KEY);
    testerApiClient = FirebaseApp.getInstance().get(FirebaseAppDistributionTesterApiClient.class);
    setContentView(R.layout.activity_feedback);
  }

  public void submitFeedback(View view) {
    setSubmittingStateEnabled(true);
    EditText feedbackText = (EditText) findViewById(R.id.feedbackText);
    testerApiClient
        .createFeedback(releaseName, feedbackText.getText().toString())
        .onSuccessTask(feedbackName -> testerApiClient.attachScreenshot(feedbackName, screenshot))
        .onSuccessTask(testerApiClient::commitFeedback)
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
