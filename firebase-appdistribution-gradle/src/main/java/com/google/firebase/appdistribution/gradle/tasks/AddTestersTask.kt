/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.appdistribution.gradle.tasks

import com.google.firebase.appdistribution.gradle.AppDistributionException
import com.google.firebase.appdistribution.gradle.FirebaseAppDistribution.addTesters
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Task for adding Firebase App Distribution testers, in bulk. */
@DisableCachingByDefault(because = "Remote server operation")
abstract class AddTestersTask : TesterManagementTask() {
  @TaskAction
  fun addTesters() {
    try {
      addTesters(options)
    } catch (e: AppDistributionException) {
      // Catch errors and re-throw as Gradle exceptions with more details
      processException(e)
    }
  }
}
