package com.googletest.firebase.appdistribution.testapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.appdistribution.AppDistributionRelease
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.google.firebase.appdistribution.FirebaseAppDistributionException

class MainActivity : AppCompatActivity() {
    var firebaseAppDistribution: FirebaseAppDistribution = FirebaseAppDistribution.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.app_name).text = "Sample App v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        setupUI(isSignedIn = firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)

        /** Basic Configuration */
        findViewById<AppCompatButton>(R.id.sign_in_button).visibility = View.GONE
        findViewById<AppCompatButton>(R.id.check_for_update).visibility = View.GONE
        findViewById<AppCompatButton>(R.id.sign_out).visibility = View.GONE

        firebaseAppDistribution.updateIfNewReleaseAvailable()
            .addOnSuccessListener {
                firebaseAppDistribution.updateIfNewReleaseAvailable().addOnFailureListener {
                    val ex = it as FirebaseAppDistributionException
                    Log.d("FirebaseAppDistribution", "MAINACTIVITY:ERROR ERROR. CODE: " + it.errorCode)
                }
            }

        /** Advanced Configuration */
//        val signInButton = findViewById<AppCompatButton>(R.id.sign_in_button)
//        val checkForUpdateButton = findViewById<AppCompatButton>(R.id.check_for_update)
//        val updateAppButton = findViewById<AppCompatButton>(R.id.update_app)
//        val signOutButton = findViewById<AppCompatButton>(R.id.sign_out)
//
//        signOutButton.setOnClickListener {
//            firebaseAppDistribution.signOutTester()
//            setupUI(isSignedIn = firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)
//        }
//
//        signInButton.setOnClickListener {
//            firebaseAppDistribution.signInTester().addOnSuccessListener {
//                setupUI(isSignedIn = true, isUpdateAvailable = false)
//            }
//        }
//
//        checkForUpdateButton.setOnClickListener {
//            firebaseAppDistribution.checkForNewRelease().addOnSuccessListener {
//                setupUI(isSignedIn = true, isUpdateAvailable = it != null, release = it)
//            }
//        }
//
//        updateAppButton.setOnClickListener {
//            val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
//            val progressPercent = findViewById<TextView>(R.id.progress_percentage)
//            progressBar.visibility = View.VISIBLE
//            progressPercent.visibility = View.VISIBLE
//            progressBar.isIndeterminate = false
//            firebaseAppDistribution.updateApp().addOnProgressListener {
//                progressBar.isIndeterminate = false
//                val percentage = ((it.apkBytesDownloaded * 100)/ it.apkFileTotalBytes).toInt()
//                progressBar.setProgress(percentage, true)
//                progressPercent.text = "$percentage %"
//            }
//        }
    }

    private fun setupUI(isSignedIn: Boolean, isUpdateAvailable: Boolean, release: AppDistributionRelease? = null) {
        val signInStatus = findViewById<TextView>(R.id.sign_in_status)
        val signInButton = findViewById<AppCompatButton>(R.id.sign_in_button)
        val checkForUpdateButton = findViewById<AppCompatButton>(R.id.check_for_update)
        val updateAppButton = findViewById<AppCompatButton>(R.id.update_app)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        val progressPercentage = findViewById<TextView>(R.id.progress_percentage)

        if (!isSignedIn) {
            signInStatus.text = "Not Signed In"
            signInButton.visibility = View.VISIBLE
            checkForUpdateButton.visibility = View.GONE
            updateAppButton.visibility = View.GONE
            progressBar.visibility = View.GONE
            progressPercentage.visibility = View.GONE
            return
        }

        if (isSignedIn && !isUpdateAvailable) {
            signInStatus.text = "Tester signed In!"
            signInButton.visibility = View.GONE
            checkForUpdateButton.visibility = View.VISIBLE
            updateAppButton.visibility = View.GONE
            progressBar.visibility = View.GONE
            progressPercentage.visibility = View.GONE
            return
        }

        signInStatus.text = "Release available - ${release?.displayVersion} (${release?.versionCode})"
        signInButton.visibility = View.GONE
        checkForUpdateButton.visibility = View.GONE
        updateAppButton.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        progressPercentage.visibility = View.GONE
    }
}
