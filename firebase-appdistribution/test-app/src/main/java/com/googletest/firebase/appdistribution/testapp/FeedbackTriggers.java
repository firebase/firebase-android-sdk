package com.googletest.firebase.appdistribution.testapp;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.util.Log;

import com.google.firebase.appdistribution.FirebaseAppDistribution;

import java.io.IOException;
import java.io.InputStream;
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

    /**
     * Creates a FeedbackTriggers instance.
     *
     * @param handler The handler to run {@link #onChange} on, or null if none.
     */
    public FeedbackTriggers(Context context, CharSequence infoText, Handler handler) {
        super(handler);
        this.context = context;
        this.infoText = infoText;
    }

    void registerScreenshotObserver() {
        context.getContentResolver()
                .registerContentObserver(
                        Media.EXTERNAL_CONTENT_URI, true /*notifyForDescendants*/, this);
        Log.i(TAG, "Screenshot observer successfully registered.");
    }

    void unRegisterScreenshotObserver() {
        context.getContentResolver()
                .unregisterContentObserver(this);
        Log.i(TAG, "Screenshot observer successfully unregistered.");
    }

    @Override
    public void onChange(boolean selfChange) {
        Log.i(TAG, "Detected content written to external media but no URI present");
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        Log.i(TAG, "Detected content written to external media: " + uri);
        if (!uri.toString().matches(String.format("%s/[0-9]+", Media.EXTERNAL_CONTENT_URI))) {
            Log.i(TAG, "Detected non-screenshot content written to external media");
            return;
        }

        if (!seenImages.add(uri.toString())) {
            Log.i(TAG, "Ignoring redundant URI");
            return;
        }

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, PROJECTION, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                Long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                Log.i(TAG, "Path: " + path);
                if (!path.toLowerCase().contains("screenshot")) {
                    return;
                }
                Log.i(TAG, "Detected screenshot.");
                // TODO: since we have a file path here, should we just use File or path everywhere instead of content URI?
                try {
                    waitUntilImageIsAvailable(uri);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for screenshot to be readable.", e);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read screenshot.", e);
                }
                FirebaseAppDistribution.getInstance().startFeedback(infoText, uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not determine if media change was due to taking a screenshot", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    // TODO: wait until image is available in the FeedbackActivity instead of here
    private void waitUntilImageIsAvailable(Uri uri) throws InterruptedException, IOException {
        for (int i = 0; i < 10; i++) {
            try {
                InputStream is = context.getContentResolver().openInputStream(uri);
                Log.i(TAG, "Screenshot is readable. Starting feedback flow.");
                is.close();
                return;
            } catch (IllegalStateException e) {
                Log.e(TAG, "Screenshot in URI is still pending. Sleeping.", e);
                Thread.sleep(300);
            }
        }
    }
}
