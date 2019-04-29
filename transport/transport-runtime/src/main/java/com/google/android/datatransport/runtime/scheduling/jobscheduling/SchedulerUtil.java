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

package com.google.android.datatransport.runtime.scheduling.jobscheduling;

/** Used by the schedulers for some basic constants and utility methods. */
public final class SchedulerUtil {

  static final String ATTEMPT_NUMBER = "attemptNumber";

  static final String BACKEND_NAME = "backendName";

  static final String APPLICATION_BUNDLE_ID = "appBundleId";

  public static final long THIRTY_SECONDS = 30 * 1000;

  public static final long TWENTY_FOUR_HOURS = 24 * 60 * 60 * 1000;

  private SchedulerUtil() {}
}
