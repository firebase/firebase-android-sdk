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

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

class GenerateLicensesTask extends DefaultTask {
    def NEW_LINE_LENGTH = "\n".getBytes().length;

    @Input
    List<ProjectLicense> additionalLicenses

    @InputFile
    File licenseReportFile

    @InputDirectory
    File licenseDownloadDir

    @OutputDirectory
    File outputDir

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        List<ProjectLicense> licenses = extractLicensesFromReport() + additionalLicenses

        Set<URI> licenseUris = licenses.collect { it.licenseUris }.flatten().toSet()

        //Fetch local licenses into memory
        Map<URI, String> licenseCache = licenseUris.collectEntries {

            final File file
            if (it.getScheme() == "file") {
                file = new File(it)
                if (!file.exists()) {
                    throw new GradleException("License file not found at ${it.toString()}",
                            new FileNotFoundException())
                }
            } else {
                file = new File(licenseDownloadDir, "${it.toString().hashCode()}")
                if (!file.exists()) {
                    throw new GradleException(
                            "License file was not downloaded from ${it.toString()}. " +
                                    "Did you forget to add a custom RemoteLicenseFetcher?",
                            new FileNotFoundException())
                }
            }

            [(it): file.text]
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        File textFile = new File(outputDir, "third_party_licenses.txt")
        File jsonFile = new File(outputDir, "third_party_licenses.json")

        JsonBuilder jsonBuilder

        textFile.withPrintWriter { writer ->
            long writeOffset = 0

            jsonBuilder = new JsonBuilder(licenses.collectEntries { projectLicense ->
                String name = projectLicense.name
                List<URI> uris = projectLicense.licenseUris

                // Write project name
                String line1 = "$name:"
                writer.print line1
                writeOffset += line1.length()

                long licenseByteLength = 0
                writer.println ""
                licenseByteLength += NEW_LINE_LENGTH
                println name

                // Write all licenses for the project seperated by newlines
                uris.each { uri ->
                    String cachedLicense = licenseCache.get(uri)
                    writer.println cachedLicense
                    writer.println ""

                    licenseByteLength += cachedLicense.length() + (2 * NEW_LINE_LENGTH)
                }

                writeOffset += licenseByteLength

                String key = projectLicense.isExplicit ? projectLicense.name
                        : projectLicense.name.toLowerCase()
                ["$key": [length: licenseByteLength,
                          start : writeOffset - licenseByteLength]]
            })
        }

        jsonFile.withPrintWriter { writer -> writer.println(jsonBuilder.toString()) }
    }

    private List<ProjectLicense> extractLicensesFromReport() {
        new JsonSlurper().parseText(licenseReportFile.text).
                collect { report ->
                    new ProjectLicense(name: report.project,
                            licenseUris: report.licenses.collect { URI.create(it.license_url) },
                            isExplicit: false)
                }
    }
}
