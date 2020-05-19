// Copyright 2020 Google LLC
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

package com.google.firebase.remoteconfig.internal;

import android.util.Log;

public class ConfigLogger {
    public static final String TAG = "FirebaseRemoteConfig";

    private final String tag;
    private int logLevel;

    public ConfigLogger(String tag) {
        this.tag = tag;
        this.logLevel = Log.INFO;
    }

    public static ConfigLogger createLogger() {
        return new ConfigLogger(TAG);
    }

    private boolean canLog(int level) {
        return logLevel <= level || Log.isLoggable(tag, level);
    }

    public void v(String text, Throwable throwable) {
        if (canLog(Log.VERBOSE)) {
            Log.v(tag, text, throwable);
        }
    }

    public void d(String text, Throwable throwable) {
        if (canLog(Log.DEBUG)) {
            Log.d(tag, text, throwable);
        }
    }

    public void i(String text, Throwable throwable) {
        if (canLog(Log.INFO)) {
            Log.i(tag, text, throwable);
        }
    }

    public void w(String text, Throwable throwable) {
        if (canLog(Log.WARN)) {
            Log.w(tag, text, throwable);
        }
    }

    public void e(String text, Throwable throwable) {
        if (canLog(Log.WARN)) {
            Log.e(tag, text, throwable);
        }
    }

    public void v(String text) {
        v(text, null);
    }

    public void d(String text) {
        d(text, null);
    }

    public void i(String text) {
        i(text, null);
    }

    public void w(String text) {
        w(text, null);
    }

    public void e(String text) {
        e(text,null);
    }
}
