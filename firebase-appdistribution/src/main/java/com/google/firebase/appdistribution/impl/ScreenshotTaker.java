package com.google.firebase.appdistribution.impl;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

/** A class that takes screenshots of the host app. */
class ScreenshotTaker {

  private static final Bitmap TEMP_FIXED_BITMAP =
      Bitmap.createBitmap(400, 400, Config.RGB_565);

  /** Take a screenshot of the running host app. */
  Task<Bitmap> takeScreenshot() {
    // TODO(lkellogg): Actually take a screenshot
    return Tasks.forResult(TEMP_FIXED_BITMAP);
  }
}
