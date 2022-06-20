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

package com.google.firebase.appdistribution.impl;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

/** A class that takes screenshots of the host app. */
class ScreenshotTaker {

  private static final Bitmap TEMP_FIXED_BITMAP = Bitmap.createBitmap(400, 400, Config.RGB_565);

  /** Take a screenshot of the running host app. */
  Task<Bitmap> takeScreenshot() {
    // TODO(lkellogg): Actually take a screenshot
    return Tasks.forResult(TEMP_FIXED_BITMAP);
  }
}
