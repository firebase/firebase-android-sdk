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
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

import groovy.transform.CompileStatic;
import jdk.nashorn.internal.objects.annotations.Property;

@CompileStatic
public class GenerateApiTxtFileTask extends DefaultTask {

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



    @TaskAction
    void execute() {
        String cmdTemplate = "%s --source-path %s --api %s --format=v2 --baseline %s";
        if(updateBaseline) {
            cmdTemplate = "%s --source-path %s --api %s --format=v2 --update-baseline %s";
        }
        String cmdToRun = String.format(cmdTemplate, metalavaBinaryPath, sourcePath, apiTxt, baselinePath);
        try {
            Process p = Runtime.getRuntime().exec(cmdToRun);
            System.out.println("Generated api txt file at " + apiTxt);
        } catch (IOException e) {
            System.out.println("Failed to run command " + cmdToRun);
            System.out.println(e.toString());
        }
    }

}
