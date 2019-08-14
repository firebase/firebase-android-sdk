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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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

    @OutputFile
    abstract File getOutputApiFile();

    @Input
    abstract boolean getUpdateBaseline();

    @OutputFile
    abstract File getOutputFile();

    public abstract void setSourcePath(String value);

    public abstract void setBaselineFile(File value);

    public abstract void setUpdateBaseline(boolean value);

    public abstract void setMetalavaBinaryPath(String value);

    public abstract void setApiTxt(File value);

    public abstract void setOutputApiFile(File value);

    public abstract void setOutputFile(File value);


    @TaskAction
    void execute() {
        File outputFileDir = getOutputFile().getParentFile();
        if(!outputFileDir.exists()) {
            outputFileDir.mkdirs();
        }

        // Generate api.txt file and store it in the  build directory.
        getProject().exec(spec-> {
            spec.setCommandLine(Arrays.asList(
                getMetalavaBinaryPath(),
                "--source-path", getSourcePath(),
                "--api", getOutputApiFile().getAbsolutePath(),
                "--format=v2"
            ));
            spec.setIgnoreExitValue(true);
        });
        getProject().exec(spec-> {
            List<String> cmd = new ArrayList<>(Arrays.asList(
                getMetalavaBinaryPath(),
                "--source-files", getOutputApiFile().getAbsolutePath(),
                "--check-compatibility:api:current", getApiTxt().getAbsolutePath(),
                "--format=v2",
                "--no-color",
                "--delete-empty-baselines"
            ));
            if(getUpdateBaseline()) {
                cmd.addAll(Arrays.asList("--update-baseline", getBaselineFile().getAbsolutePath()));
            } else if(getBaselineFile().exists()) {
                cmd.addAll(Arrays.asList("--baseline", getBaselineFile().getAbsolutePath()));
            }
            spec.setCommandLine(cmd);
            spec.setIgnoreExitValue(true);
            try {
                spec.setStandardOutput(new FileOutputStream(getOutputFile()));
            } catch (FileNotFoundException e) {
                throw new GradleException("Unable to run the command", e);
            }
        });

    }

}
