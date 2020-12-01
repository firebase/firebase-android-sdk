// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.remoteconfig.testutil.Assert.assertThrows;

import android.content.Context;
import com.google.android.gms.common.internal.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Unit tests for the {@link ConfigStorageClient}.
 *
 * @author Miraziz Yusupov
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ConfigStorageClientTest {
  private static final String FILE_NAME = "FILE_NAME";

  private Context context;
  private ConfigStorageClient storageClient;

  private ConfigContainer configContainer;

  @Before
  public void setUp() throws Exception {
    context = RuntimeEnvironment.application.getApplicationContext();

    ConfigStorageClient.clearInstancesForTest();
    storageClient = ConfigStorageClient.getInstance(context, FILE_NAME);

    configContainer =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("long_param", "1L", "string_param", "string_value"))
            .withFetchTime(new Date(1000L))
            .build();
  }

  @Test
  public void write_validContainer_writesContainerToFile() throws Exception {
    storageClient.write(configContainer);
    assertThat(getFileAsString()).isEqualTo(configContainer.toString());
  }

  @Test
  public void write_nullContainer_throwsNullPointerException() throws Exception {
    assertThrows(NullPointerException.class, () -> storageClient.write(null));
    assertThat(getFileAsString()).isEmpty();
  }

  @Test
  public void read_validContainer_returnsContainer() throws Exception {
    storageClient.write(configContainer);
    Preconditions.checkArgument(getFileAsString().equals(configContainer.toString()));

    ConfigContainer container = storageClient.read();
    assertThat(container).isEqualTo(configContainer);
  }

  @Test
  public void read_validContainerWithPersonalization_returnsContainer() throws Exception {
    ConfigContainer configWithPersonalization =
        ConfigContainer.newBuilder(configContainer)
            .withPersonalizationMetadata(
                new JSONObject(ImmutableMap.of(Personalization.ARM_KEY, "arm_value")))
            .build();
    storageClient.write(configWithPersonalization);
    Preconditions.checkArgument(getFileAsString().equals(configWithPersonalization.toString()));

    ConfigContainer container = storageClient.read();
    assertThat(container).isEqualTo(configWithPersonalization);
  }

  @Test
  public void read_validContainerWithoutPersonalization_returnsContainer() throws Exception {
    // Configs written by SDK versions <20.0.1 do not contain personalization metadata.
    // Since the serialized configContainer contains personalization metadata, we manually remove it
    // and write the config to disk directly to test.
    JSONObject configJSON = new JSONObject(configContainer.toString());
    configJSON.remove(ConfigContainer.PERSONALIZATION_METADATA_KEY);

    try (FileOutputStream outputStream = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE)) {
      outputStream.write(configJSON.toString().getBytes(StandardCharsets.UTF_8));
    }

    ConfigContainer container = storageClient.read();
    assertThat(container).isEqualTo(configContainer);
  }

  @Test
  public void read_emptyFile_returnsNull() throws Exception {
    ConfigContainer container = storageClient.read();

    assertThat(container).isNull();
  }

  @Test
  public void clear_fileExists_deletesFile() throws Exception {
    storageClient.write(configContainer);
    Preconditions.checkArgument(getFileAsString().equals(configContainer.toString()));

    storageClient.clear();

    assertThat(getFileAsString()).isEmpty();
  }

  @Test
  public void clear_emptyFile_doesNothing() throws Exception {
    storageClient.clear();

    assertThat(getFileAsString()).isEmpty();
  }

  private String getFileAsString() throws Exception {
    try (FileInputStream inputStream = context.openFileInput(FILE_NAME)) {
      BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
      String nextString = bufferedReader.readLine();

      StringBuilder stringBuilder = new StringBuilder();
      while (nextString != null) {
        stringBuilder.append(nextString);
        nextString = bufferedReader.readLine();
      }

      return stringBuilder.toString();
    } catch (FileNotFoundException e) {
      return "";
    }
  }
}
