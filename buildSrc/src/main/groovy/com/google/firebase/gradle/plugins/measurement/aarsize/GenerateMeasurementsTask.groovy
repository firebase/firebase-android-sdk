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


package com.google.firebase.gradle.plugins.measurement.aarsize

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Generates size measurements after building the release aar's.
 *
 * <p>This task can run in two modes. The first mode, enabled when running the task with the
 * {@code pull_request} flag set, is a dependency of {@link UploadMeasurementsTask} and generates
 * a JSON file with measurement information. The second mode, enabled when running the task without
 * flags, outputs a table to standard out with more human-readable information. See the README for
 * more details.
 *
 * <p>This task has two properties, a required ignore SDK map file, as input, and the optional
 * report file, as output. The map is used to ignore SDKs that shouldn't be included in the report.
 * The report path is where the output should be stored. These properties are not used when the task
 * is run in the second, human-friendly mode. However, they are still required to be specified.
 */
public class GenerateMeasurementsTask extends DefaultTask {

    /**
     * The file for storing the report.
     *
     * <p>This may be any type recognized by Gradle as a file. The contents, if any, will be
     * overwritten by the new report.
     */
    @OutputFile
    @Optional
    File reportFile

    @Override
    Task configure(Closure closure) {
        outputs.upToDateWhen { false }
        dependsOn "assemble"
        return super.configure(closure)
    }

    @TaskAction
    def generate() {
        def subprojects = [:]
         project.rootProject.subprojects.collect { Project it ->
            def aars = it.fileTree('build') {
                include '**/*release.aar'
            }
            if (aars.size() > 1) {
                def msg = "${it.name} produced more than one AAR"
                throw new IllegalStateException(msg)
            }
            def name = it.name
            if (it.parent != project.rootProject) {
                name = "${it.parent.name}:${it.name}"
            }
            aars.each { File f ->
                subprojects[name] = f.length()
            }
        }
        generateJson(subprojects)
    }

    private def generateJson(subprojects) {
        def builder = new AarSizeJsonBuilder()
        subprojects.each { name, aarSize ->
            builder.addAarSize(name, aarSize)
        }

        reportFile.withWriter {
            it.write(builder.toJsonString())
        }
    }

    private def generateTable(subprojects) {
        def builder = new AarSizeTableBuilder()
        subprojects.each { name, aarSize ->
            builder.addAarSize(name, aarSize)
        }

        project.logger.quiet(builder.toTableString())
    }
}
