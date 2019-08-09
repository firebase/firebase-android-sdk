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
import org.gradle.api.tasks.TaskAction;
import java.io.File;

import java.io.IOException;


public class GenerateApiTxtFileTask extends DefaultTask {

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



    @TaskAction
    void execute() {
        String cmdTemplate = "%s --source-path %s --api %s --format=v2 --baseline %s";
        if(updateBaseline) {
            cmdTemplate = "%s --source-path %s --api %s --format=v2 --update-baseline %s";
        }
        String cmdToRun = String.format(cmdTemplate, metalavaBinaryPath, sourcePath, apiTxt.getAbsolutePath(), baselineFile.getAbsolutePath());
        try {
            Process p = Runtime.getRuntime().exec(cmdToRun);
            System.out.println("Generated api txt file at " + apiTxt);
        } catch (IOException e) {
            getLogger().error("Failed to run command " + cmdToRun);
            getLogger().error(e.toString());
        }
    }

}
