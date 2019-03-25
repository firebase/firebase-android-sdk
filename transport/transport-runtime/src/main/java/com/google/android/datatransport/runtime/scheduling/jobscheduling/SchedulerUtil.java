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


public class SchedulerUtil {

    public final static String NUMBER_OF_ATTEMPTS_CONSTANT = "numberOfAttempts";

    public final static String BACKEND_NAME_CONSTANT = "backendName";

    public final static String APPLICATION_BUNDLE_ID = "appBundleId";

    static long getScheduleDelay(long backendTimeDiff, int delta, int numberOfAttempts) {
        if(numberOfAttempts > 3) {
            return 1000000000;
        }
        return Math.max((long)(Math.pow(delta/1000, numberOfAttempts))*1000, backendTimeDiff);
    }
}