/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googletest.firebase.remoteconfig.bandwagoner;

import static com.googletest.firebase.remoteconfig.bandwagoner.Constants.TAG;
import static com.googletest.firebase.remoteconfig.bandwagoner.TimeFormatHelper.getCurrentTimeString;

import android.util.Log;
import androidx.fragment.app.Fragment;
import com.google.android.gms.tasks.Task;

/**
 * Helper methods for dealing with the interactions between Tasks and Views.
 *
 * @author Miraziz Yusupov
 */
public class TaskHelper {

  static void addDebugOnCompleteListener(
      Task<?> task, BandwagonerFragment fragment, String taskName) {

    task.addOnCompleteListener(
        (unusedVoid) -> {
          if (isFragmentDestroyed(fragment)) {
            Log.w(TAG, "Fragment was destroyed before " + taskName + " was completed.");
            IdlingResourceManager.getInstance().decrement();
            return;
          }

          String currentTimeString = getCurrentTimeString();
          if (task.isSuccessful()) {
            fragment.callResultsText.setText(
                String.format(
                    "%s - %s task was successful with return value: %s!",
                    currentTimeString, taskName, task.getResult()));
            Log.i(TAG, taskName + " task was successful! Return value: " + task.getResult());
          } else {
            fragment.callResultsText.setText(
                String.format(
                    "%s - %s task failed with exception: %s",
                    currentTimeString, taskName, task.getException()));
            Log.e(TAG, taskName + " task failed!", task.getException());
          }

          fragment.callProgressText.setText("");
          IdlingResourceManager.getInstance().decrement();
        });
  }

  static boolean isFragmentDestroyed(Fragment fragment) {
    return fragment.isRemoving()
        || fragment.getActivity() == null
        || fragment.isDetached()
        || !fragment.isAdded()
        || fragment.getView() == null;
  }
}
