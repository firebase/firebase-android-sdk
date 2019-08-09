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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

/**
    Task generates the api diff of the current source code against the api.txt file stored
    alongside the project's src directory.
 */
public abstract class ApiInformationTask extends DefaultTask {

    @Input
    abstract String getMetalavaBinaryPath();

    @InputFile
    abstract File getApiTxt();

    @Input
    abstract String getSourcePath();

    @OutputFile
    abstract File getBaselineFile();

    @Input
    abstract boolean getUpdateBaseline();

    @OutputFile
    abstract File getOutputFile();

    abstract void setSourcePath(String value);

    abstract void setBaselineFile(File value);

    abstract void setUpdateBaseline(boolean value);

    abstract void setMetalavaBinaryPath(String value);

    abstract void setApiTxt(File value);

    abstract void setOutputFile(File value);


    @TaskAction
    void execute() {
        File outputFileDir = getOutputFile().getParentFile();
        if(!outputFileDir.exists()) {
            outputFileDir.mkdirs();
        }
        String cmdTemplate = getUpdateBaseline() ?
                "%s --source-path %s --check-compatibility:api:current %s --format=v2 --update-baseline %s --no-color"
                : "%s --source-path %s --check-compatibility:api:current %s --format=v2 --baseline %s --no-color";

        String cmdToRun = String.format(cmdTemplate, getMetalavaBinaryPath(), getSourcePath(), getApiTxt().getAbsolutePath(), getBaselineFile().getAbsolutePath());
        getProject().exec(spec-> {
            spec.setCommandLine(Arrays.asList(cmdToRun.split(" ")));
            spec.setIgnoreExitValue(true);
            try {
                spec.setStandardOutput(new FileOutputStream(getOutputFile()));
            } catch (FileNotFoundException e) {
                getLogger().error(e.toString());
            }
        });

    }

}
