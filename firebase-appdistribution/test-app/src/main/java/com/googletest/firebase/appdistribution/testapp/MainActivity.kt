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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.firebase.appdistribution.FirebaseAppDistributionException as FirebaseAppDistributionException1

class MainActivity : AppCompatActivity() {
    var firebaseAppDistribution: FirebaseAppDistribution = FirebaseAppDistribution.getInstance()
    var updateTask: Task<Void>? = null
    var release: AppDistributionRelease? = null
    val executorService: ExecutorService = Executors.newFixedThreadPool(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.app_name).text =
            "Sample App v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        setupUI(isSignedIn = firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)

        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        val progressPercent = findViewById<TextView>(R.id.progress_percentage)
        val signInButton = findViewById<AppCompatButton>(R.id.sign_in_button)
        val signOutButton = findViewById<AppCompatButton>(R.id.sign_out)
        val checkForUpdateButton = findViewById<AppCompatButton>(R.id.check_for_update)
        val updateAppButton = findViewById<AppCompatButton>(R.id.update_app)
        val updateIfNewReleaseAvailableButton = findViewById<AppCompatButton>(R.id.basic_config)
        val updateIfNewReleaseAvailableButtonBackground = findViewById<AppCompatButton>(R.id.basic_config2)
        val signInButtonBackground = findViewById<AppCompatButton>(R.id.sign_in_button2)
        val signOutButtonBackground = findViewById<AppCompatButton>(R.id.sign_out2)
        val checkForUpdateButtonBackground = findViewById<AppCompatButton>(R.id.check_for_update2)
        val updateAppButtonBackground = findViewById<AppCompatButton>(R.id.update_app2)

        /** Button Listeners */
        updateIfNewReleaseAvailableButton.setOnClickListener {
                   if (updateTask == null || updateTask?.isComplete == true) {
           updateTask = firebaseAppDistribution.updateIfNewReleaseAvailable().addOnFailureListener {
               val ex = it as FirebaseAppDistributionException1
               Log.d("FirebaseAppDistribution", "MAINACTIVITY:ERROR ERROR. CODE: " + it.errorCode)
               AlertDialog.Builder(this)
                   .setTitle("Error updating to new release")
                   .setMessage("${ex.message}: ${ex.errorCode}")
                   .setNeutralButton("Okay") { dialog, _ ->
                       dialog.dismiss()
                   }.show()
           }
       }
        }

        updateIfNewReleaseAvailableButtonBackground.setOnClickListener {
            executorService.execute {
                Thread.sleep(10000)
                if (updateTask == null || updateTask?.isComplete == true) {
                    updateTask =
                        firebaseAppDistribution.updateIfNewReleaseAvailable().addOnFailureListener {
                            val ex = it as com.google.firebase.appdistribution.FirebaseAppDistributionException
                            Log.d(
                                "FirebaseAppDistribution",
                                "MAINACTIVITY:ERROR ERROR. CODE: " + it.errorCode
                            )
                            AlertDialog.Builder(this)
                                .setTitle("Error updating to new release")
                                .setMessage("${ex.message}: ${ex.errorCode}")
                                .setNeutralButton("Okay") { dialog, _ ->
                                    dialog.dismiss()
                                }.show()
                        }
                }
            }
        }

        signInButton.setOnClickListener {
            firebaseAppDistribution.signInTester().addOnSuccessListener {
                setupUI(isSignedIn =  firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)
            }
                .addOnFailureListener {
                    val ex = it as com.google.firebase.appdistribution.FirebaseAppDistributionException
                    Log.d("FirebaseAppDistribution", "MAINACTIVITY:ERROR ERROR. CODE: " + it.errorCode)
                    AlertDialog.Builder(this)
                        .setTitle("Error updating to new release")
                        .setMessage("${ex.message}: ${ex.errorCode}")
                        .setNeutralButton("Okay") { dialog, _ ->
                            dialog.dismiss()
                        }.show()
                }
        }

        signInButtonBackground.setOnClickListener {
            executorService.execute {
                firebaseAppDistribution.signInTester().addOnSuccessListener {

                }
            }
            setupUI(isSignedIn =  firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)
        }

        checkForUpdateButton.setOnClickListener {
            firebaseAppDistribution.checkForNewRelease().addOnSuccessListener {
                setupUI(isSignedIn =  firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = it != null, release = it)
            }
        }
        checkForUpdateButtonBackground.setOnClickListener {
            executorService.execute {
                firebaseAppDistribution.checkForNewRelease().addOnSuccessListener {
                    release = it
                }
            }
            setupUI(isSignedIn =  firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = release != null, release =  release)
        }

        signOutButton.setOnClickListener {
            firebaseAppDistribution.signOutTester()
            setupUI(isSignedIn =  firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)
        }

        signOutButtonBackground.setOnClickListener {
            executorService.execute {
                firebaseAppDistribution.signOutTester()

            }
            setupUI(isSignedIn =  firebaseAppDistribution.isTesterSignedIn, isUpdateAvailable = false)
        }

        updateAppButton.setOnClickListener {
            val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
            val progressPercent = findViewById<TextView>(R.id.progress_percentage)
            progressBar.visibility = View.VISIBLE
            progressPercent.visibility = View.VISIBLE
            progressBar.isIndeterminate = false
            firebaseAppDistribution.updateApp().addOnProgressListener {
                progressBar.isIndeterminate = false
                val percentage = ((it.apkBytesDownloaded * 100)/ it.apkFileTotalBytes).toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    progressBar.setProgress(percentage, true)
                }
                progressPercent.text = "$percentage %"
            }
        }

        updateAppButtonBackground.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            progressPercent.visibility = View.VISIBLE
            executorService.execute {
                progressBar.isIndeterminate = false
                firebaseAppDistribution.updateApp().addOnProgressListener {
                    progressBar.isIndeterminate = false
                    val percentage = ((it.apkBytesDownloaded * 100) / it.apkFileTotalBytes).toInt()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        progressBar.setProgress(percentage, true)
                    }
                    progressPercent.text = "$percentage %"
                }
            }
        }
    }

    fun startSecondActivity() {
        val intent = Intent(this, SecondActivity::class.java)
        startActivity(intent)
    }

    private fun setupUI(isSignedIn: Boolean, isUpdateAvailable: Boolean, release: AppDistributionRelease? = null) {
        val signInStatus = findViewById<TextView>(R.id.sign_in_status)
        val signInButton = findViewById<AppCompatButton>(R.id.sign_in_button)
        val signOutButton = findViewById<AppCompatButton>(R.id.sign_out)
        val checkForUpdateButton = findViewById<AppCompatButton>(R.id.check_for_update)
        val updateAppButton = findViewById<AppCompatButton>(R.id.update_app)
        val signInButtonBackground = findViewById<AppCompatButton>(R.id.sign_in_button2)
        val signOutButtonBackground = findViewById<AppCompatButton>(R.id.sign_out2)
        val checkForUpdateButtonBackground = findViewById<AppCompatButton>(R.id.check_for_update2)
        val updateAppButtonBackground = findViewById<AppCompatButton>(R.id.update_app2)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        val progressPercentage = findViewById<TextView>(R.id.progress_percentage)

        if (!isSignedIn) {
            signInStatus.text = "Not Signed In"
            signInButton.visibility = View.VISIBLE
            signInButtonBackground.visibility = View.VISIBLE
            checkForUpdateButton.visibility = View.GONE
            checkForUpdateButtonBackground.visibility = View.GONE
            updateAppButton.visibility = View.GONE
            updateAppButtonBackground.visibility = View.GONE
            progressBar.visibility = View.GONE
            signOutButton.visibility = View.GONE
            signOutButtonBackground.visibility = View.GONE
            progressPercentage.visibility = View.GONE
            return
        }

        if (isSignedIn && !isUpdateAvailable) {
            signInStatus.text = "Tester signed In!"
            signInButton.visibility = View.GONE
            signInButtonBackground.visibility = View.GONE
            signOutButton.visibility = View.VISIBLE
            signOutButtonBackground.visibility = View.VISIBLE
            checkForUpdateButton.visibility = View.VISIBLE
            checkForUpdateButtonBackground.visibility = View.VISIBLE
            updateAppButton.visibility = View.GONE
            updateAppButtonBackground.visibility = View.GONE
            progressBar.visibility = View.GONE
            progressPercentage.visibility = View.GONE
            return
        }

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
        progressPercentage.visibility = View.GONE
    }
}
