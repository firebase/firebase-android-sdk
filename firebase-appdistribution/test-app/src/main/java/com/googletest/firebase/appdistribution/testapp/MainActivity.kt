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

package com.googletest.firebase.appdistribution.testapp

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.android.gms.tasks.Task
import com.google.firebase.appdistribution.AppDistributionRelease
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.google.firebase.appdistribution.UpdateProgress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    var firebaseAppDistribution: FirebaseAppDistribution = FirebaseAppDistribution.getInstance()
    var updateTask: Task<Void>? = null
    var release: AppDistributionRelease? = null
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
    lateinit var progressPercentage: TextView
    lateinit var signInStatus: TextView
    lateinit var progressPercent: TextView
    lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        signInButton = findViewById<AppCompatButton>(R.id.sign_in_button)
        signOutButton = findViewById<AppCompatButton>(R.id.sign_out)
        checkForUpdateButton = findViewById<AppCompatButton>(R.id.check_for_update)
        updateAppButton = findViewById<AppCompatButton>(R.id.update_app)
        updateIfNewReleaseAvailableButton = findViewById<AppCompatButton>(R.id.basic_config)
        updateIfNewReleaseAvailableButtonBackground =
            findViewById<AppCompatButton>(R.id.basic_config2)
        signInButtonBackground = findViewById<AppCompatButton>(R.id.sign_in_button2)
        signOutButtonBackground = findViewById<AppCompatButton>(R.id.sign_out2)
        checkForUpdateButtonBackground = findViewById<AppCompatButton>(R.id.check_for_update2)
        updateAppButtonBackground = findViewById<AppCompatButton>(R.id.update_app2)
        progressPercentage = findViewById<TextView>(R.id.progress_percentage)
        signInStatus = findViewById<TextView>(R.id.sign_in_status)
        progressPercent = findViewById<TextView>(R.id.progress_percentage)
        progressBar = findViewById<ProgressBar>(R.id.progress_bar)
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
                    setupUI(
                        isSignedIn = firebaseAppDistribution.isTesterSignedIn,
                        isUpdateAvailable = false)
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
            setupUI(
                isSignedIn = firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)
        }

        checkForUpdateButton.setOnClickListener {
            firebaseAppDistribution
                .checkForNewRelease()
                .addOnSuccessListener {
                    setupUI(
                        isSignedIn = firebaseAppDistribution.isTesterSignedIn,
                        isUpdateAvailable = it != null,
                        release = it)
                }
                .addOnFailureListener { failureListener(it) }
        }

        checkForUpdateButtonBackground.setOnClickListener {
            executorService.execute {
                firebaseAppDistribution
                    .checkForNewRelease()
                    .addOnSuccessListener {
                        release = it
                        setupUI(
                            isSignedIn = firebaseAppDistribution.isTesterSignedIn,
                            isUpdateAvailable = release != null,
                            release = release)
                    }
                    .addOnFailureListener { failureListener(it) }
            }
        }

        signOutButton.setOnClickListener {
            firebaseAppDistribution.signOutTester()
            setupUI(
                isSignedIn = firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)
        }

        signOutButtonBackground.setOnClickListener {
            executorService.execute { firebaseAppDistribution.signOutTester() }

            setupUI(
                isSignedIn = firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)
        }

        updateAppButton.setOnClickListener {
            setProgressBar()
            firebaseAppDistribution
                .updateApp()
                .addOnProgressListener { progressListener(it) }
                .addOnCanceledListener {
                    setupUI(
                        isSignedIn = firebaseAppDistribution.isTesterSignedIn,
                        isUpdateAvailable = false)
                }
        }

        updateAppButtonBackground.setOnClickListener {
            setProgressBar()
            executorService.execute {
                firebaseAppDistribution.updateApp().addOnProgressListener { progressListener(it) }
            }
        }
    }

    fun startSecondActivity() {
        val intent = Intent(this, SecondActivity::class.java)
        startActivity(intent)
    }

    fun setProgressBar() {
        progressBar.visibility = View.VISIBLE
        progressPercent.visibility = View.VISIBLE
        progressBar.isIndeterminate = false
    }

    fun failureListener(exception: Exception) {
        val ex = exception as com.google.firebase.appdistribution.FirebaseAppDistributionException
        Log.d("FirebaseAppDistribution", "MAINACTIVITY:ERROR ERROR. CODE: " + exception.errorCode)
        AlertDialog.Builder(this)
            .setTitle("Error updating to new release")
            .setMessage("${ex.message}: ${ex.errorCode}")
            .setNeutralButton("Okay") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    fun progressListener(updateProgress: UpdateProgress) {
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
        progressPercentage.visibility = View.GONE
        if (isUpdateAvailable) {
            signInStatus.text =
                "Release available - ${release?.displayVersion} (${release?.versionCode})"
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
}
