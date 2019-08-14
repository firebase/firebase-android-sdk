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
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import java.io.File;

import java.util.Arrays;


public abstract class GenerateApiTxtFileTask extends DefaultTask {

    @Input
    abstract String getMetalavaBinaryPath();

    @OutputFile
    abstract File getApiTxt();

    @Input
    abstract String getSourcePath();


    @OutputFile
    abstract File getBaselineFile();

    @Input
    abstract boolean getUpdateBaseline();


    public abstract void setSourcePath(String value);

    public abstract void setBaselineFile(File value);

    public abstract void setUpdateBaseline(boolean value);

    public abstract void setMetalavaBinaryPath(String value);

    public abstract void setApiTxt(File value);

    @TaskAction
    void execute() {
        List<String> cmd =  new ArrayList<String>(Arrays.asList(
            getMetalavaBinaryPath(),
            "--source-path", getSourcePath(),
            "--api", getApiTxt().getAbsolutePath(),
            "--format=v2",
            "--delete-empty-baselines"
        ));

        if(getUpdateBaseline()) {
            cmd.addAll(Arrays.asList("--update-baseline", getBaselineFile().getAbsolutePath()));
        } else if(getBaselineFile().exists()) {
            cmd.addAll(Arrays.asList("--baseline", getBaselineFile().getAbsolutePath()));
        }

        getProject().exec(spec -> {
            spec.setCommandLine(cmd);
            spec.setIgnoreExitValue(true);
        });

    }
}
