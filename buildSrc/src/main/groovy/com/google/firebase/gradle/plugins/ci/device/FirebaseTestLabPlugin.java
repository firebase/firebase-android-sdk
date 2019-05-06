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

package com.google.firebase.gradle.plugins.ci.device;

import com.android.build.gradle.BaseExtension;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Firebase Test Lab Integration plugin.
 *
 * @deprecated For library projects use 'firebase-library' plugin instead. App projects should still
 *     use this plugin.
 */
@Deprecated
public class FirebaseTestLabPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    FirebaseTestLabExtension extension =
        project
            .getExtensions()
            .create("firebaseTestLab", FirebaseTestLabExtension.class, project.getObjects());
    extension.setEnabled(true);
    BaseExtension android = (BaseExtension) project.getExtensions().getByName("android");
    android.testServer(new FirebaseTestServer(project, extension));
  }
}
