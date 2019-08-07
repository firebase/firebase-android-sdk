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
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

/**
    Task generates the api txt file for the current source code.
 */
@CompileStatic
class GenerateApiTask extends DefaultTask {

    @Input
    private String metalavaBinaryPath;

    @Input
    private String apiTxt;

    @Input
    private String sourcePath;


    @TaskAction
    void execute() {
        String cmdTemplate = "%s --source-path %s --api %s --format=v2";
        String cmdToRun = String.format(cmdTemplate, metalavaBinaryPath, sourcePath, apiTxt);
        try {
            Runtime.getRuntime().exec(cmdToRun);
        } catch (IOException e) {
            System.out.println("Failed to run command " + cmdToRun);
            System.out.println(e.toString());
        }
    }

}
