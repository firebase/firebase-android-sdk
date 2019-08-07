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

package com.google.firebase.gradle.plugins.apiinfo;

import groovy.transform.CompileStatic;
import jdk.nashorn.internal.objects.annotations.Property;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.IssueService;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

/**
    Task generates the api diff of the current source code against the api.txt file stored
    alongside the project's src directory.
 */
@CompileStatic
public class ApiInformationTask extends DefaultTask {

    @Property
    String metalavaBinaryPath;

    @Property
    String apiTxt;

    @Property
    String sourcePath;

    @Property
    String baselinePath;

    @Property
    boolean updateBaseline;

    @Property
    String outputPath;


    private String getCmdOutput (String cmdToRun) {
        ProcessBuilder processBuilder = new ProcessBuilder(cmdToRun.split(" "));
        try {
            Process p = processBuilder.start();
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line = null;
            while ( (line = reader.readLine()) != null) {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
            }
            return builder.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    void writeToFile(String output) {
        try(BufferedWriter out = new BufferedWriter(new FileWriter(outputPath))) {
            out.write(output);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    @TaskAction
    void execute() {
        String cmdTemplate = "%s --source-path %s --check-compatibility:api:current %s --format=v2 --baseline %s";
        if(updateBaseline) {
            cmdTemplate = "%s --source-path %s --check-compatibility:api:current %s --format=v2 --update-baseline %s";
        }
        String cmdToRun = String.format(cmdTemplate, metalavaBinaryPath, sourcePath, apiTxt, baselinePath);
        String cmdOutput = getCmdOutput(cmdToRun);
        if(cmdOutput == null ){
            System.out.println("Unable to run the command " + cmdToRun);
        }
        else {
            writeToFile(cmdOutput);
        }
    }

}
