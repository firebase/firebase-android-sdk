// Copyright 2019 Google LLC
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
package com.google.firebase.gradle.plugins

import com.android.build.gradle.LibraryExtension
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.Project

val Project.sdkDir: File
    get() {
        val properties = Properties()
        val localProperties = rootProject.file("local.properties")
        if (localProperties.exists()) {
            try {
                FileInputStream(localProperties).use { fis -> properties.load(fis) }
            } catch (ex: IOException) {
                throw GradleException("Could not load local.properties", ex)
            }
        }
        val sdkDir = properties.getProperty("sdk.dir")
        if (sdkDir != null) {
            return file(sdkDir)
        }
        val androidHome = System.getenv("ANDROID_HOME")
                ?: throw GradleException("No sdk.dir or ANDROID_HOME set.")
        return file(androidHome)
    }

val Project.androidJar: File?
    get() {
        val android = project.extensions.findByType(LibraryExtension::class.java)
                ?: return null
        return File(
                sdkDir, String.format("/platforms/%s/android.jar", android.compileSdkVersion))
    }
