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
import java.io.InputStreamReader;
import java.util.Date;
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
