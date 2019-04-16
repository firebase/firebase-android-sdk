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

class ThirdPartyLicensesExtension {
    private final File baseDir
    private final List<ProjectLicense> additionalLicenses = new ArrayList<>()

    ThirdPartyLicensesExtension(File baseDir) {
        this.baseDir = baseDir
    }

    /** Add a library with its licenses in passed-in files(relative to rootDir). */
    void add(String name, String... licenseUris) throws IOException {
        additionalLicenses.add(
                new ProjectLicense(name: name, explicitLicenseUris: licenseUris.collect {
                    URI.create(it)
                }))
    }

    List<ProjectLicense> getLibraries() {
        return additionalLicenses
    }
}