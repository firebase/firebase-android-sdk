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

import com.google.firebase.appdistribution.gradle.AppDistributionEnvironment
import com.google.firebase.appdistribution.gradle.AppDistributionException
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.MISSING_PROJECT_NUMBER
import com.google.firebase.appdistribution.gradle.TesterManagementOptions
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

// Uses Gradle's "Incubating" API to support configuration cache
@DisableCachingByDefault(because = "Remote server operation")
abstract class TesterManagementTask : DefaultTask() {
  @Input val projectNumber: Property<Long> = project.objects.property(Long::class.java)

  @Input @Optional val emails: Property<String> = project.objects.property(String::class.java)

  @Input @Optional val file: Property<String> = project.objects.property(String::class.java)

  @Input
  @Optional
  val serviceCredentialsFile: Property<String> = project.objects.property(String::class.java)

  @Option(option = "projectNumber", description = "Firebase project number")
  fun setProjectNumber(projectNumber: String) {
    try {
      this.projectNumber.set(projectNumber.toLong())
    } catch (e: NumberFormatException) {
      throw GradleException(String.format("Invalid project number %s", projectNumber))
    }
  }

  @Option(option = "emails", description = "Comma separated list of tester emails")
  fun setEmails(emails: String) = this.emails.set(emails)

  @Option(
    option = "file",
    description = "Path to a file containing a comma or newline separated list of tester emails"
  )
  fun setFile(filePath: String) = this.file.set(filePath)

  @Option(
    option = "serviceCredentialsFile",
    description =
      "Path to your service account private key JSON file. " +
        "Required only if you use service account authentication."
  )
  fun setServiceCredentialsFile(serviceCredentialsFilePath: String) =
    this.serviceCredentialsFile.set(serviceCredentialsFilePath)

  @get:Internal
  val options: TesterManagementOptions
    get() {
      val projectNumber =
        projectNumber.orNull ?: throw AppDistributionException(MISSING_PROJECT_NUMBER)

      return TesterManagementOptions(
        projectNumber = projectNumber,
        emailsValue = emails.orNull,
        emailsFile = file.orNull,
        serviceCredentialsFile = serviceCredentialsFile.orNull
      )
    }

  protected fun processException(e: AppDistributionException) {
    when (e.reason) {
      AppDistributionException.Reason.MISSING_PROJECT_NUMBER ->
        throw GradleException(
          "Could not find your project number. " +
            "Please verify that you set the command line flag " +
            "`projectNumber` and try again."
        )
      AppDistributionException.Reason.MISSING_TESTER_EMAILS ->
        throw GradleException(
          "Could not find tester emails. " +
            "Please verify that you set either the command line flag " +
            "`emails` or `file` with valid emails, then try again."
        )
      AppDistributionException.Reason.MISSING_CREDENTIALS ->
        throw GradleException(
          "Could not find credentials. To authenticate, you " +
            "have a few options: \n" +
            "1. Set the `serviceCredentialsFile` command line flag\n" +
            "2. Set a refresh token with the " +
            AppDistributionEnvironment.ENV_FIREBASE_TOKEN +
            " environment variable\n" +
            "3. Log in with the Firebase CLI\n" +
            "4. Set service credentials with the " +
            AppDistributionEnvironment.ENV_GOOGLE_APPLICATION_CREDENTIALS +
            " environment variable"
        )
      else -> {
        val message = e.message ?: "Unknown exception occurred"
        throw GradleException(message, e)
      }
    }
  }
}
