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
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.IncrementalTaskInputs

import javax.inject.Inject

@CompileStatic
class GenerateLicensesTask extends DefaultTask {
    private static final int NEW_LINE_LENGTH = "\n".getBytes().length



    @Input
    List<ProjectLicense> additionalLicenses

    @OutputDirectory
    File outputDir



    GenerateLicensesTask() {
        // it's impossible to tell if the configuration we depend on changed without resolving it, but
        // the configuration is most likely not resolvable.
        outputs.upToDateWhen { false }
    }


    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        Set<URI> licenseUris = additionalLicenses.collectMany { it.licenseUris } as Set

        //Fetch local licenses into memory
        Map<URI, String> licenseCache = licenseUris.collectEntries {
            File file
            file = new File(it)


            if (!file.exists()) {
                throw new GradleException("License file not found at ${it.toString()}",
                        new FileNotFoundException())
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

            jsonBuilder = new JsonBuilder(additionalLicenses.collectEntries { projectLicense ->
                String name = projectLicense.name
                List<URI> uris = projectLicense.licenseUris

                // Write project name
                String line1 = "$name:"
                writer.print line1
                writeOffset += line1.length()

                long licenseByteLength = 0
                writer.println ""
                licenseByteLength += NEW_LINE_LENGTH

                // Write all licenses for the project seperated by newlines
                uris.each { uri ->
                    String cachedLicense = licenseCache.get(uri)
                    writer.println cachedLicense
                    writer.println ""

                    licenseByteLength += cachedLicense.length() + (2 * NEW_LINE_LENGTH)
                }

                writeOffset += licenseByteLength

                [(projectLicense.name): [length: licenseByteLength,
                          start : writeOffset - licenseByteLength]]
            })
        }

        jsonFile.withPrintWriter { writer -> writer.println(jsonBuilder.toString()) }
    }
}
