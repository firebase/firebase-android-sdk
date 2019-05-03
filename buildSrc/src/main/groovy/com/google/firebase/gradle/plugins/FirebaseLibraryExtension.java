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

package com.google.firebase.gradle.plugins;

import com.google.firebase.gradle.plugins.ci.device.FirebaseTestLabExtension;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public class FirebaseLibraryExtension {
  /** True by default */
  public final Property<Boolean> publishJavadoc;
  public final Property<Boolean> publishSources;
  public final FirebaseTestLabExtension testLab;

  @Inject
  public FirebaseLibraryExtension(ObjectFactory objectFactory) {
    this.publishJavadoc = objectFactory.property(Boolean.class);
    this.publishSources = objectFactory.property(Boolean.class);
    this.testLab = new FirebaseTestLabExtension(objectFactory);
    this.publishJavadoc.set(true);
  }

  public void setPublishJavadoc(boolean value) {
    publishJavadoc.set(value);
  }

  void testLab(Action<FirebaseTestLabExtension> action) {
    action.execute(testLab);
  }
}
