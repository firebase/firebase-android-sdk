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

import java.util.Collections;
import java.util.Set;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.SetProperty;

public class FirebaseTestLabExtension {
  private final SetProperty<String> devices;
  private String timeout;
  private boolean enabled;

  @Inject
  public FirebaseTestLabExtension(ObjectFactory objectFactory) {
    devices = objectFactory.setProperty(String.class);
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public void device(String device) {
    devices.add(device);
  }

  public void timeout(String timeout) {
    this.timeout = timeout;
  }

  Set<String> getDevices() {
    if (devices.get().isEmpty()) {
      return Collections.singleton("model=panther,version=33,locale=en,orientation=portrait");
    }
    return devices.get();
  }

  @Nullable
  public String getTimeout() {
    return timeout;
  }
}
