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

import com.google.gson.Gson;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

abstract class GenerateLicensesTask extends DefaultTask {
    private static final int NEW_LINE_LENGTH = "\n".getBytes().length;

    @Input
    public abstract List<ThirdPartyLicensesExtension.CustomLicense> getadditionalLicenses();

    public abstract void setAdditionalLicenses(List<ThirdPartyLicensesExtension.CustomLicense> additionalLicenses);


    @OutputDirectory
    public abstract void setOutputDir(File outputDir);

    public abstract File getOutputDir();


    @Inject
    public GenerateLicensesTask() {
        // it's impossible to tell if the configuration we depend on changed without resolving it, but
        // the configuration is most likely not resolvable.
        this.getOutputs().upToDateWhen(task -> false);
    }


    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        Set<URI> licenseUris = new HashSet<URI>();
        for (ThirdPartyLicensesExtension.CustomLicense license : getadditionalLicenses()) {
            licenseUris.addAll(license.licenseUris);
        }

        //Fetch local licenses into memory
        Map<URI, String> licenseCache = new HashMap<>();

        for (URI uri : licenseUris) {
            File file = new File(uri);

            if (!file.exists()) {
                throw new GradleException("License file not found at " + uri +
                        new FileNotFoundException());
            }

            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                licenseCache.put(uri, new String(bytes));
            } catch (IOException e) {
                throw new GradleException("Error reading license file",e);
            }
        }

        if (!getOutputDir().exists()) {
            getOutputDir().mkdirs();
        }

        File textFile = new File(getOutputDir(), "third_party_licenses.txt");
        File jsonFile = new File(getOutputDir(), "third_party_licenses.json");
        Map<String, Map<String, Long>> index = new HashMap<>();

        try(PrintWriter writer = new PrintWriter(textFile)) {
            long writeOffset = 0;

            for (ThirdPartyLicensesExtension.CustomLicense projectLicense : getadditionalLicenses()) {
                String name = projectLicense.name;
                List<URI> uris = projectLicense.licenseUris;

                // Write project name
                String line1 = name + ":";
                writer.print(line1);
                writeOffset += line1.length();

                long licenseByteLength = 0;
                writer.println("");
                licenseByteLength += NEW_LINE_LENGTH;

                // Write all licenses for the project seperated by newlines
                for (URI uri : uris) {
                    String cachedLicense = licenseCache.get(uri);
                    writer.println(cachedLicense);
                    writer.println("");

                    licenseByteLength += cachedLicense.length() + (2 * NEW_LINE_LENGTH);
                }

                writeOffset += licenseByteLength;

                Map<String, Long> a = new HashMap<>();
                a.put("length", licenseByteLength);
                a.put("start", writeOffset - licenseByteLength);
                index.put(projectLicense.name, a);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try(PrintWriter writer = new PrintWriter(jsonFile)) {
            writer.println(new Gson().toJson(index));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
