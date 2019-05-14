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


package com.google.firebase.gradle.plugins.measurement

import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Takes the size information created by {@link GenerateMeasurementTask} and uploads it to the
 * database using the uploader tool.
 *
 * <p>The uploader tool is fetched from the Internet using a URL. This URL, and the path to the
 * report to upload, must be given as properties to this task. This task also requires a project
 * property, {@code database_config} for connecting to the database. The format of this file is
 * dictated by the uploader tool.
 */
public class UploadMeasurementsTask extends DefaultTask {

    /**
     * The URL of the uploader tool.
     *
     * <p>This must be a valid URL as a {@link String}.
     */
    @Input
    String uploader

    /**
     * The file to upload.
     *
     * <p>This file must exist prior to executing this task, but it may be created by other tasks
     * provided they run first.
     */
    @InputFile
    File reportFile

    @TaskAction
    def upload() {
        if (!project.hasProperty("database_config")) {
          throw new IllegalStateException("Cannot upload measurements without database config")
        }

        def configuration = project.file(project.properties["database_config"])

        withTempJar { jar ->
            getUploaderUrl().withInputStream {
                Files.copy(it, jar, StandardCopyOption.REPLACE_EXISTING)
            }

            project.logger.info("Running uploader with flags: --config_path=${configuration} --json_path=${reportFile}")

            project.exec {
                executable("java")

                args(
                    "-jar",
                    jar,
                    "--config_path=${configuration}",
                    "--json_path=${reportFile}",
                )
            }.rethrowFailure()
        }
    }

    def getUploaderUrl() {
        return new URL(uploader)
    }

    private def withTempJar(Closure action) {
        def path = Files.createTempFile("uploader", ".jar")
        try {
            action.call(path)
        } finally {
            Files.delete(path);
        }
    }
}
