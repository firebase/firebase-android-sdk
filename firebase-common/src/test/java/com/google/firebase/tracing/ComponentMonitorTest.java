// Copyright 2022 Google LLC
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

package com.google.firebase.tracing;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ComponentMonitorTest {

  @Test
  public void testComponentMonitorTransformation() {
    List<Component<?>> list = new ArrayList<Component<?>>();
    list.add(
        Component.builder(ComponentHolder.class)
            .factory(ComponentHolder::new)
            .name("test")
            .build());
    list.add(
        Component.builder(ComponentHolder.class).factory(ComponentHolder::new).name("foo").build());
    list.add(
        Component.builder(ComponentHolder.class).factory(ComponentHolder::new).name("bar").build());
    ComponentMonitor monitor = new ComponentMonitor();
    List<Component<?>> transformed = monitor.processRegistrar(() -> list);
    assertThat(list.size()).isEqualTo(transformed.size());
    for (int i = 0; i < list.size(); i++) {
      assertThat(list.get(i).getName()).isEqualTo(transformed.get(i).getName());
      assertThat(transformed.get(i).getFactory().create(null)).isNotNull();
    }
  }

  private static final class ComponentHolder {
    private final ComponentContainer container;

    public ComponentHolder(ComponentContainer container) {
      this.container = container;
    }
  }
}
