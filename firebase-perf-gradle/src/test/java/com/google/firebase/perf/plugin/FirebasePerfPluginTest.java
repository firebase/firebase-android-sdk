/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.perf.plugin;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit and Functional tests for {@link FirebasePerfPlugin}. */
public class FirebasePerfPluginTest {

  // LINT.IfChange(plugin_version)
  private static final String PLUGIN_VERSION_PROPERTY_KEY = "pluginVersion";
  // LINT.ThenChange()

  // LINT.IfChange(plugin_version)
  private static final String CURRENT_PLUGIN_VERSION = "2.0.2";
  // LINT.ThenChange()

  // LINT.IfChange(plugin_version_default)
  private static final String PLUGIN_VERSION_DEFAULT_VALUE = "unknown";
  // LINT.ThenChange()

  @TempDir File tempFolder;

  @Mock ClassLoader classLoader;

  @BeforeEach
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void getPluginVersion_matchesCurrentPluginVersion() {
    assertThat(FirebasePerfPlugin.getPluginVersion()).isEqualTo(CURRENT_PLUGIN_VERSION);
  }

  @Test
  public void getPluginVersion_noVersionProperty_returnsDefaultValue() throws IOException {

    String resFileName = "random_project_props_res_file.txt";
    File res = new File(tempFolder, resFileName);

    // Pass 1: Verify that it returns correct plugin version when property exist
    FileUtils.writeStringToFile(res, String.format("%s=%s", PLUGIN_VERSION_PROPERTY_KEY, "1.2.3"));

    when(classLoader.getResourceAsStream(any())).thenReturn(FileUtils.openInputStream(res));

    assertThat(FirebasePerfPlugin.getPluginVersion(classLoader, resFileName))
        .isEqualTo(/* expected= */ "1.2.3");

    // Pass 2: Verify that it returns default value when property doesn't exist
    FileUtils.writeStringToFile(res, /* data= */ "");

    when(classLoader.getResourceAsStream(any())).thenReturn(FileUtils.openInputStream(res));

    assertThat(FirebasePerfPlugin.getPluginVersion(classLoader, resFileName))
        .isEqualTo(PLUGIN_VERSION_DEFAULT_VALUE);
  }

  @Test
  public void getPluginVersion_noProjectPropsResFile_returnsDefaultValue() {
    assertThat(
            FirebasePerfPlugin.getPluginVersion(
                classLoader, /* projectPropsResFile*/ "random_project_props_res_file.txt"))
        .isEqualTo(PLUGIN_VERSION_DEFAULT_VALUE);
  }
}
