/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googletest.firebase.appdistribution.testapp

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import com.google.android.gms.tasks.Task
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.appdistribution.AppDistributionRelease
import com.google.firebase.appdistribution.InterruptionLevel
import com.google.firebase.appdistribution.UpdateProgress
import com.google.firebase.appdistribution.ktx.appDistribution
import com.google.firebase.ktx.Firebase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

  var firebaseAppDistribution = Firebase.appDistribution
  var updateTask: Task<Void>? = null
  val executorService: ExecutorService = Executors.newFixedThreadPool(1)

  lateinit var signInButton: AppCompatButton
  lateinit var signOutButton: AppCompatButton
  lateinit var checkForUpdateButton: AppCompatButton
  lateinit var updateAppButton: AppCompatButton
  lateinit var updateIfNewReleaseAvailableButton: AppCompatButton
  lateinit var updateIfNewReleaseAvailableButtonBackground: AppCompatButton
  lateinit var signInButtonBackground: AppCompatButton
  lateinit var signOutButtonBackground: AppCompatButton
  lateinit var checkForUpdateButtonBackground: AppCompatButton
  lateinit var updateAppButtonBackground: AppCompatButton
  lateinit var secondActivityButton: AppCompatButton
  lateinit var signInStatus: TextView
  lateinit var progressPercent: TextView
  lateinit var progressBar: ProgressBar
  lateinit var feedbackTriggerMenu: TextInputLayout
  lateinit var permissionRequestLauncher: ActivityResultLauncher<String>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    signInButton = findViewById(R.id.sign_in_button)
    signOutButton = findViewById(R.id.sign_out)
    checkForUpdateButton = findViewById(R.id.check_for_update)
    updateAppButton = findViewById(R.id.update_app)
    updateIfNewReleaseAvailableButton = findViewById(R.id.basic_config)
    updateIfNewReleaseAvailableButtonBackground = findViewById(R.id.basic_config2)
    signInButtonBackground = findViewById(R.id.sign_in_button2)
    signOutButtonBackground = findViewById(R.id.sign_out2)
    checkForUpdateButtonBackground = findViewById(R.id.check_for_update2)
    updateAppButtonBackground = findViewById(R.id.update_app2)
    secondActivityButton = findViewById(R.id.secondActivityButton)
    progressPercent = findViewById(R.id.progress_percentage)
    signInStatus = findViewById(R.id.sign_in_status)
    progressBar = findViewById(R.id.progress_bar)

    // Set up feedback trigger menu
    feedbackTriggerMenu = findViewById(R.id.feedbackTriggerMenu)
    val items =
      listOf(
        FeedbackTrigger.NONE.label,
        FeedbackTrigger.CUSTOM_NOTIFICATION.label,
        FeedbackTrigger.SHAKE.label,
        FeedbackTrigger.SCREENSHOT.label,
      )
    val adapter = ArrayAdapter(this, R.layout.list_item, items)
    val autoCompleteTextView = feedbackTriggerMenu.editText!! as AutoCompleteTextView
    autoCompleteTextView.setAdapter(adapter)
    // TODO: set it to the actual currently enabled trigger
    autoCompleteTextView.setText(FeedbackTrigger.NONE.label, false)
    autoCompleteTextView.doOnTextChanged { text, _, _, _ ->
      when (text.toString()) {
        FeedbackTrigger.NONE.label -> {
          disableAllFeedbackTriggers()
        }
        FeedbackTrigger.CUSTOM_NOTIFICATION.label -> {
          disableAllFeedbackTriggers()
          startActivity(Intent(this, CustomNotificationActivity::class.java))
        }
        FeedbackTrigger.SHAKE.label -> {
          disableAllFeedbackTriggers()
          Log.i(TAG, "Enabling shake for feedback trigger")
          ShakeDetectionFeedbackTrigger.enable(application, this)
        }
        FeedbackTrigger.SCREENSHOT.label -> {
          disableAllFeedbackTriggers()
          startActivity(Intent(this, ScreenshotDetectionActivity::class.java))
        }
      }
    }

    setupFeedbackNotification()
  }

  private fun setupFeedbackNotification() {
    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog
    permissionRequestLauncher =
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
          showFeedbackNotification()
        } else {
          // Typically, here we should show the user a message explaining what behavior is
          // unavailable because they have not enabled notifications. However in this case we are
          // calling this method every time the activity is created, and we don't want to keep
          // showing them such a message over and over if they've explicitly disabled notifications
          // for the app.
        }
      }

    // Check the permissions and behave accordingly
    when {
      ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED -> {
        showFeedbackNotification()
      }
      shouldShowRequestPermissionRationale(POST_NOTIFICATIONS) -> {
        Log.i(TAG, "Showing customer rationale for requesting permission.")
        AlertDialog.Builder(this)
          .setMessage(
            "Using a notification to initiate feedback to the developer. " +
              "To enable this feature, allow the app to post notifications."
          )
          .setPositiveButton("OK") { _, _ ->
            Log.i(TAG, "Launching request for permission.")
            permissionRequestLauncher.launch(POST_NOTIFICATIONS)
          }
          .setNegativeButton("No thanks") { _, _ -> Log.i(TAG, "User denied permission request.") }
          .show()
      }
      else -> {
        permissionRequestLauncher.launch(POST_NOTIFICATIONS)
      }
    }
  }

  private fun showFeedbackNotification() {
    firebaseAppDistribution.showFeedbackNotification(
      R.string.feedbackAdditionalFormText,
      InterruptionLevel.HIGH
    )
  }

  private fun disableAllFeedbackTriggers() {
    Log.i(TAG, "Disabling all feedback triggers")
    ShakeDetectionFeedbackTrigger.disable(application)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    val inflater: MenuInflater = menuInflater
    inflater.inflate(R.menu.action_menu, menu)
    if (BuildConfig.FLAVOR == "beta") {
      menu.findItem(R.id.startFeedbackMenuItem).isVisible = true
    }
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.startFeedbackMenuItem -> {
        Firebase.appDistribution.startFeedback(R.string.feedbackAdditionalFormText)
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  override fun onResume() {
    super.onResume()

    findViewById<TextView>(R.id.app_name).text =
      "Sample App v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    setupUI(isSignedIn = firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)

    /** Button Listeners */
    updateIfNewReleaseAvailableButton.setOnClickListener {
      if (updateTask == null || updateTask?.isComplete == true) {
        updateTask =
          firebaseAppDistribution.updateIfNewReleaseAvailable().addOnFailureListener() {
            failureListener(it)
          }
      }
    }

    updateIfNewReleaseAvailableButtonBackground.setOnClickListener {
      executorService.execute {
        if (updateTask == null || updateTask?.isComplete == true) {
          updateTask =
            firebaseAppDistribution.updateIfNewReleaseAvailable().addOnFailureListener {
              failureListener(it)
            }
        }
      }
    }

    signInButton.setOnClickListener {
      firebaseAppDistribution
        .signInTester()
        .addOnSuccessListener {
          setupUI(isSignedIn = firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)
        }
        .addOnFailureListener { failureListener(it) }
    }

    signInButtonBackground.setOnClickListener {
      executorService.execute {
        firebaseAppDistribution
          .signInTester()
          .addOnSuccessListener {}
          .addOnFailureListener { failureListener(it) }
      }
      setupUI(isSignedIn = firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)
    }

    checkForUpdateButton.setOnClickListener {
      firebaseAppDistribution
        .checkForNewRelease()
        .addOnSuccessListener { release ->
          setupUI(
            isSignedIn = firebaseAppDistribution.isTesterSignedIn,
            isUpdateAvailable = release != null,
            release = release
          )
        }
        .addOnFailureListener { failureListener(it) }
    }

    checkForUpdateButtonBackground.setOnClickListener {
      executorService.execute {
        firebaseAppDistribution
          .checkForNewRelease()
          .addOnSuccessListener { release ->
            setupUI(
              isSignedIn = firebaseAppDistribution.isTesterSignedIn,
              isUpdateAvailable = release != null,
              release = release
            )
          }
          .addOnFailureListener { failureListener(it) }
      }
    }

    signOutButton.setOnClickListener {
      firebaseAppDistribution.signOutTester()
      setupUI(isSignedIn = firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)
    }

    signOutButtonBackground.setOnClickListener {
      executorService.execute { firebaseAppDistribution.signOutTester() }

      setupUI(isSignedIn = firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)
    }

    updateAppButton.setOnClickListener {
      setProgressBar()
      firebaseAppDistribution
        .updateApp()
        .addOnProgressListener { progressListener(it) }
        .addOnCanceledListener {
          setupUI(isSignedIn = firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)
        }
    }

    updateAppButtonBackground.setOnClickListener {
      setProgressBar()
      executorService.execute {
        firebaseAppDistribution.updateApp().addOnProgressListener { progressListener(it) }
      }
    }

    secondActivityButton.setOnClickListener { startSecondActivity() }
  }

  private fun startSecondActivity() {
    val intent = Intent(this, SecondActivity::class.java)
    startActivity(intent)
  }

  private fun setProgressBar() {
    progressBar.visibility = View.VISIBLE
    progressPercent.visibility = View.VISIBLE
    progressBar.isIndeterminate = false
  }

  private fun failureListener(exception: Exception) {
    val ex = exception as com.google.firebase.appdistribution.FirebaseAppDistributionException
    Log.d("MainActivity", "Task failed with an error (${ex.errorCode}): ${ex.message}")
    AlertDialog.Builder(this)
      .setTitle("Task failed")
      .setMessage("${ex.message}\n\nCode: ${ex.errorCode}")
      .setNeutralButton("Okay") { dialog, _ -> dialog.dismiss() }
      .show()
  }

  private fun progressListener(updateProgress: UpdateProgress) {
    val percentage =
      ((updateProgress.apkBytesDownloaded * 100) / updateProgress.apkFileTotalBytes).toInt()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      progressBar.setProgress(percentage, true)
    }
    progressPercent.text = "$percentage %"
  }

  private fun setupUI(
    isSignedIn: Boolean,
    isUpdateAvailable: Boolean,
    release: AppDistributionRelease? = null
  ) {
    progressBar.visibility = View.GONE
    progressPercent.visibility = View.GONE
    if (isUpdateAvailable) {
      signInStatus.text = "Release available - ${release?.displayVersion} (${release?.versionCode})"
      signInButton.visibility = View.GONE
      signInButtonBackground.visibility = View.GONE
      checkForUpdateButton.visibility = View.GONE
      checkForUpdateButtonBackground.visibility = View.GONE
      updateAppButton.visibility = View.VISIBLE
      updateAppButtonBackground.visibility = View.VISIBLE
      signOutButton.visibility = View.VISIBLE
      progressBar.visibility = View.GONE
      signOutButtonBackground.visibility = View.VISIBLE
      return
    }

    signInStatus.text = if (isSignedIn) "Tester signed In!" else "Not Signed In"
    signInButton.visibility = if (isSignedIn) View.GONE else View.VISIBLE
    signInButtonBackground.visibility = if (isSignedIn) View.GONE else View.VISIBLE
    signOutButton.visibility = if (isSignedIn) View.VISIBLE else View.GONE
    signOutButtonBackground.visibility = if (isSignedIn) View.VISIBLE else View.GONE
    checkForUpdateButton.visibility = if (isSignedIn) View.VISIBLE else View.GONE
    checkForUpdateButtonBackground.visibility = if (isSignedIn) View.VISIBLE else View.GONE
    updateAppButton.visibility = if (isSignedIn) View.VISIBLE else View.GONE
    updateAppButtonBackground.visibility = if (isSignedIn) View.VISIBLE else View.GONE
    updateAppButton.visibility = View.GONE
    updateAppButtonBackground.visibility = View.GONE
  }

  companion object {
    const val TAG = "MainActivity"

    enum class FeedbackTrigger(val label: String) {
      NONE("None"),
      SHAKE("Shake the device"),
      SCREENSHOT("Take a screenshot"),
      CUSTOM_NOTIFICATION("Tap a notification (custom)")
    }
  }
}
