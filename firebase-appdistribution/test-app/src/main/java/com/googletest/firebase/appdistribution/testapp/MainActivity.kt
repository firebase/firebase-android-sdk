package com.googletest.firebase.appdistribution.testapp

import android.app.AlertDialog
import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.widget.doOnTextChanged
import com.google.android.gms.tasks.Task
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.appdistribution.AppDistributionRelease
import com.google.firebase.appdistribution.UpdateProgress
import com.google.firebase.appdistribution.ktx.appDistribution
import com.google.firebase.ktx.Firebase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    val firebaseAppDistribution = Firebase.appDistribution
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

    var updateTask: Task<Void>? = null
    var notificationPermissionLauncher: ActivityResultLauncher<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        signInButton = findViewById(R.id.sign_in_button)
        signOutButton = findViewById(R.id.sign_out)
        checkForUpdateButton = findViewById(R.id.check_for_update)
        updateAppButton = findViewById(R.id.update_app)
        updateIfNewReleaseAvailableButton = findViewById(R.id.basic_config)
        updateIfNewReleaseAvailableButtonBackground =
            findViewById(R.id.basic_config2)
        signInButtonBackground = findViewById(R.id.sign_in_button2)
        signOutButtonBackground = findViewById(R.id.sign_out2)
        checkForUpdateButtonBackground = findViewById(R.id.check_for_update2)
        updateAppButtonBackground = findViewById(R.id.update_app2)
        secondActivityButton = findViewById(R.id.secondActivityButton)
        progressPercent = findViewById(R.id.progress_percentage)
        signInStatus = findViewById(R.id.sign_in_status)
        progressBar = findViewById(R.id.progress_bar)
        notificationPermissionLauncher = NotificationFeedbackTrigger.registerPermissionLauncher(this)

        // Set up feedback trigger menu
        feedbackTriggerMenu = findViewById(R.id.feedbackTriggerMenu)
        val items = listOf(
            FeedbackTrigger.NONE.label,
            FeedbackTrigger.SHAKE.label,
            FeedbackTrigger.SCREENSHOT.label,
            FeedbackTrigger.NOTIFICATION.label,
        )
        val adapter = ArrayAdapter(this, R.layout.list_item, items)
        val autoCompleteTextView = feedbackTriggerMenu.editText!! as AutoCompleteTextView
        autoCompleteTextView.setAdapter(adapter)
        // TODO: set it to the actual currently enabled trigger
        autoCompleteTextView.setText(FeedbackTrigger.NONE.label, false)
        autoCompleteTextView.doOnTextChanged { text, _, _, _ ->
            when(text.toString()) {
                FeedbackTrigger.NONE.label -> {
                    disableAllFeedbackTriggers()
                }
                FeedbackTrigger.SHAKE.label -> {
                    disableAllFeedbackTriggers()
                    Log.i(TAG, "Enabling shake for feedback trigger")
                    ShakeForFeedback.enable(application, this)
                }
                FeedbackTrigger.SCREENSHOT.label -> {
                    disableAllFeedbackTriggers()
                    Log.i(TAG, "Enabling screenshot detection trigger")
                    ScreenshotDetectionFeedbackTrigger.enable()
                }
                FeedbackTrigger.NOTIFICATION.label -> {
                    disableAllFeedbackTriggers()
                    Log.i(TAG, "Enabling notification trigger")
                    NotificationFeedbackTrigger.enable(this, notificationPermissionLauncher)
                }
            }
        }
    }

    private fun disableAllFeedbackTriggers() {
        Log.i(TAG, "Disabling all feedback triggers")
        ShakeForFeedback.disable(application)
        ScreenshotDetectionFeedbackTrigger.disable()
        NotificationFeedbackTrigger.disable();
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.action_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.startFeedbackMenuItem -> {
                Firebase.appDistribution.startFeedback(R.string.terms_and_conditions)
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
                        setupUI(
                            isSignedIn = firebaseAppDistribution.isTesterSignedIn,
                            isUpdateAvailable = it != null,
                            release = it)
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
        Log.d("FirebaseAppDistribution", "MAINACTIVITY:ERROR ERROR. CODE: " + exception.errorCode)
        AlertDialog.Builder(this)
            .setTitle("Error updating to new release")
            .setMessage("${ex.message}: ${ex.errorCode}")
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

    companion object {
        const val TAG = "MainActivity"

        enum class FeedbackTrigger(val label: String) {
            NONE("None"),
            SHAKE("Shake the device"),
            SCREENSHOT("Take a screenshot"),
            NOTIFICATION("Click the notification")
        }
    }
}
