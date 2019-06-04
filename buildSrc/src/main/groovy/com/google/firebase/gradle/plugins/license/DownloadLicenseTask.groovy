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

package com.google.firebase.gradle.plugins.license

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

class DownloadLicenseTask extends DefaultTask {
  @Input
  List<RemoteLicenseFetcher> parsers

  @OutputDirectory
  File outputDir

  @TaskAction
  void execute(IncrementalTaskInputs inputs) {
    parsers.each { parser ->
      println "Downloading license from ${parser.getRemoteUrl()} ..."

      String licenseContent = parser.getText()

      if (!outputDir.exists()) {
        downloadsDir.mkdirs()
      }

      //We use the URI string's hashcode as the filename to store the download
      //This is safe since java String 's hashcode generation algorithm is part of the api spec
      File licenseFile = new File(outputDir, "${parser.getRemoteUrl().toString().hashCode()}")
      licenseFile.write licenseContent
    }
  }
}
