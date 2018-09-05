// Copyright 2018 Google LLC
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

package com.google.firebase.database.logging;

import android.util.Log;
import java.util.List;

public class AndroidLogger extends DefaultLogger {

  public AndroidLogger(Level level, List<String> enabledComponents) {
    super(level, enabledComponents);
  }

  @Override
  protected String buildLogMessage(Level level, String tag, String message, long msTimestamp) {
    // We'll log the level and tag separately on Android.
    return message;
  }

  @Override
  protected void error(String tag, String toLog) {
    Log.e(tag, toLog);
  }

  @Override
  protected void warn(String tag, String toLog) {
    Log.w(tag, toLog);
  }

  @Override
  protected void info(String tag, String toLog) {
    Log.i(tag, toLog);
  }

  @Override
  protected void debug(String tag, String toLog) {
    Log.d(tag, toLog);
  }
}
