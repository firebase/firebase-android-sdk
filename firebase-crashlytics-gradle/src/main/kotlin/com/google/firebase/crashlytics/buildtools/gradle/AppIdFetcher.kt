/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.crashlytics.buildtools.gradle

import com.android.build.api.variant.Variant
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin.Companion.UPGRADE_MSG
import com.google.firebase.crashlytics.buildtools.gradle.extensions.capitalized
import com.google.gms.googleservices.GoogleServicesTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.named

internal object AppIdFetcher {
  /** Fetch the google app id file from the Google-Services plugin. */
  fun getGoogleServicesAppId(project: Project, variant: Variant): Provider<RegularFile> =
    try {
      project.tasks
        .named<GoogleServicesTask>("process${variant.name.capitalized()}GoogleServices")
        .flatMap { googleServicesTask ->
          try {
            googleServicesTask.gmpAppId
          } catch (ex: NoSuchMethodError) {
            // The gmpAppId field was added in version 4.4.1, so previous versions will throw.
            throw GradleException(
              "The Crashlytics Gradle plugin 3 requires Google-Services 4.4.1 and above. $UPGRADE_MSG"
            )
          }
        }
    } catch (ex: NoClassDefFoundError) {
      throw GradleException("Google-Services plugin not found.")
    } catch (ex: UnknownTaskException) {
      throw GradleException("Google-Services plugin not configured properly.")
    }
}
