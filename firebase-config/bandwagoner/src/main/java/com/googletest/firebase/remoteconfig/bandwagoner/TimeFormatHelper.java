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

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Utility class for time-related helper methods.
 *
 * @author Miraziz Yusupov
 */
final class TimeFormatHelper {

  private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

  private TimeFormatHelper() {}

  /** Returns the current time in a human readable format. */
  static String getCurrentTimeString() {
    return timeFormat.format(Calendar.getInstance().getTime());
  }
}
