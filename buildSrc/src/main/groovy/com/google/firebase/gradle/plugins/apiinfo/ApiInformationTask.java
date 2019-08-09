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

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

/**
    Task generates the api diff of the current source code against the api.txt file stored
    alongside the project's src directory.
 */
public class ApiInformationTask extends DefaultTask {

    @Input
    String metalavaBinaryPath;

    @InputFile
    File apiTxt;

    @Input
    String sourcePath;

    @InputFile
    File baselineFile;

    @Input
    boolean updateBaseline;

    @OutputFile
    File outputFile;


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
                builder.append("\n");
            }
            return builder.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    private void writeToFile(String output) {
        try(BufferedWriter out = new BufferedWriter(new FileWriter(outputFile))) {
            out.write(output);
        } catch (IOException e) {
            getLogger().error("Error: " + e.getMessage());
        }
    }

    @TaskAction
    void execute() {
        String cmdTemplate = "%s --source-path %s --check-compatibility:api:current %s --format=v2 --baseline %s --no-color";
        if(updateBaseline) {
            cmdTemplate = "%s --source-path %s --check-compatibility:api:current %s --format=v2 --update-baseline %s --no-color";
        }
        String cmdToRun = String.format(cmdTemplate, metalavaBinaryPath, sourcePath, apiTxt.getAbsolutePath(), baselineFile.getAbsolutePath());
        String cmdOutput = getCmdOutput(cmdToRun);
        if(cmdOutput == null ){
            getLogger().error("Unable to run the command " + cmdToRun);
        }
        else {
            writeToFile(cmdOutput);
        }
    }

}
