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

class FeedbackTriggers extends ContentObserver {

    private static final String TAG = "FeedbackTriggers";
    private static final String[] PROJECTION =
            new String[] {
                    MediaStore.Images.Media.DATA,
            };

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

        Cursor cursor = null;
        try {
//            cursor = context.getContentResolver().query(uri, PROJECTION, null, null, null);
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            Log.i(TAG, "Cursor: " + cursor);
            Log.i(TAG, "Cursor has count: " + cursor.getCount());
            if (cursor != null && cursor.moveToFirst()) {
                String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                Log.i(TAG, "Path: " + path);
                if (path.toLowerCase().contains("images") || path.toLowerCase().contains("screenshot")) {
                    Log.i(TAG, "Detected screenshot. Starting feedback flow.");
                    FirebaseAppDistribution.getInstance().startFeedback(infoText);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not determine if media change was due to taking a screenshot", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
