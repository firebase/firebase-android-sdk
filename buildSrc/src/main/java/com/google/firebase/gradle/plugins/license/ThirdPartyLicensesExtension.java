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

package com.google.firebase.gradle.plugins.license;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class ThirdPartyLicensesExtension {
    private final ArrayList<CustomLicense> customLicenses = new ArrayList<>();

    /** Add a library with its licenses in passed-in files(relative to rootDir). Only file URIs are supported*/
    public void add(String name, String... licenseUris) {
        customLicenses.add(new CustomLicense(name, licenseUris));
    }

    static class CustomLicense implements Serializable {
        final String name;
        final List<URI> licenseUris = new ArrayList<>();

        CustomLicense(String name, String[] licenseUris) {
            this.name = name;
            for(String s: licenseUris){
                this.licenseUris.add(URI.create(s));
            }
        }
    }

    List<CustomLicense> getLibraries() {
        return customLicenses;
    }
}