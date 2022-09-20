package com.googletest.firebase.appdistribution.testapp;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.app.AlertDialog;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.firebase.appdistribution.FirebaseAppDistribution;

import java.util.HashSet;
import java.util.Set;

class FeedbackTriggers extends ContentObserver {

    private static final String TAG = "FeedbackTriggers";
    private static final String[] PROJECTION =
            new String[] {
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media._ID
            };
    private final Set<String> seenImages = new HashSet<>();

    private final Context context;
    private final CharSequence infoText;

    private final ActivityResultLauncher<String> requestPermissionLauncher;

    private Uri currentUri;

    /**
     * Creates a FeedbackTriggers instance for an activity.
     *
     * This must be called during activity initialization.
     *
     * @param activity The host activity
     * @param handler The handler to run {@link #onChange} on, or null if none.
     */
    public FeedbackTriggers(ComponentActivity activity, CharSequence infoText, Handler handler) {
        super(handler);
        this.context = activity;
        this.infoText = infoText;

        // Register the permissions launcher
        requestPermissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                maybeStartFeedbackForScreenshot(currentUri);
            } else {
                new AlertDialog.Builder(context)
                    .setMessage("Without storage access, screenshots taken while in the app will not be able to automatically start the feedback flow. To enable this feature, grant the app storage access in Settings.")
                    .setPositiveButton("OK", (a, b) -> { /* do nothing */ })
                    .show();
            }
        });
    }

    void registerScreenshotObserver() {
        context.getContentResolver().registerContentObserver(
                Media.EXTERNAL_CONTENT_URI, true /*notifyForDescendants*/, this);
    }

    void unRegisterScreenshotObserver() {
        context.getContentResolver().unregisterContentObserver(this);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        if (!uri.toString().matches(String.format("%s/[0-9]+", Media.EXTERNAL_CONTENT_URI)) || !seenImages.add(uri.toString())) {
            return;
        }

        if (ContextCompat.checkSelfPermission(context, WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
            maybeStartFeedbackForScreenshot(uri);
        } else {
            // Ideally we'd give the user some context here, but we have no way of knowing if they
            // have previously "Deny & don't ask again" in which case we wouldn't want to show them
            // anything here.
            currentUri = uri;
            requestPermissionLauncher.launch(WRITE_EXTERNAL_STORAGE);
        }
    }

    private void maybeStartFeedbackForScreenshot(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, PROJECTION, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                Long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                Log.i(TAG, "Path: " + path);
                if (path.toLowerCase().contains("screenshot")) {
                    // TODO: since we have a file path here, should we just use File or path everywhere instead of content URI?
                    FirebaseAppDistribution.getInstance().startFeedback(infoText, uri);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not determine if media change was due to taking a screenshot", e);
        } finally {
            if (cursor != null) cursor.close();
        }
    }
}
