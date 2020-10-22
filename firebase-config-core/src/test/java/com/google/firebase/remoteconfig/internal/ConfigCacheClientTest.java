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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.shadows.common.internal.ShadowPreconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for the {@link ConfigCacheClient}.
 *
 * @author Miraziz Yusupov
 */
@RunWith(RobolectricTestRunner.class)
@Config(
    manifest = Config.NONE,
    shadows = {ShadowPreconditions.class})
public class ConfigCacheClientTest {
  private static final IOException IO_EXCEPTION = new IOException("File I/O failed.");

  @Mock private ConfigStorageClient mockStorageClient;

  private ExecutorService testingThreadPool;
  private ExecutorService cacheThreadPool;
  private ConfigCacheClient cacheClient;
  private ConfigContainer configContainer;
  private ConfigContainer configContainer2;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    cacheThreadPool = Executors.newFixedThreadPool(/*nThreads=*/ 2);
    testingThreadPool = Executors.newFixedThreadPool(/*nThreads=*/ 3);

    ConfigCacheClient.clearInstancesForTest();
    when(mockStorageClient.getFileName()).thenReturn("FILE_NAME");
    cacheClient = ConfigCacheClient.getInstance(cacheThreadPool, mockStorageClient);

    configContainer =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("long_param", "1L", "string_param", "string_value"))
            .withFetchTime(new Date(1000L))
            .build();

    configContainer2 =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(
                ImmutableMap.of("string_param", "string_value", "double_param", "0.1"))
            .withFetchTime(new Date(2000L))
            .build();
  }

  @Test
  public void put_validContainer_writesToFileAndSetsCache() throws Exception {
    ConfigContainer putContainer = Tasks.await(cacheClient.put(configContainer));

    verifyFileWrites(configContainer);

    assertThat(putContainer).isEqualTo(configContainer);
    assertThat(cacheClient.getCachedContainerTask().getResult()).isEqualTo(configContainer);
  }

  @Test
  public void put_fileWriteFails_keepsCacheNull() throws Exception {
    when(mockStorageClient.write(configContainer)).thenThrow(IO_EXCEPTION);

    Task<ConfigContainer> putTask = cacheClient.put(configContainer);
    assertThrows(ExecutionException.class, () -> Tasks.await(putTask));

    assertThat(putTask.getException()).isInstanceOf(IOException.class);
    assertThat(cacheClient.getCachedContainerTask()).isNull();
  }

  @Test
  public void put_secondFileWriteFails_keepsFirstContainerInCache() throws Exception {
    when(mockStorageClient.write(configContainer2)).thenThrow(IO_EXCEPTION);

    Tasks.await(cacheClient.put(configContainer));
    Preconditions.checkArgument(
        cacheClient.getCachedContainerTask().getResult().equals(configContainer));

    Task<ConfigContainer> failedPutTask = cacheClient.put(configContainer2);
    assertThrows(ExecutionException.class, () -> Tasks.await(failedPutTask));

    verifyFileWrites(configContainer, configContainer2);

    assertThat(failedPutTask.getException()).isInstanceOf(IOException.class);
    assertThat(cacheClient.getCachedContainerTask().getResult()).isEqualTo(configContainer);
  }

  @Test
  public void get_hasCachedValue_returnsCache() throws Exception {
    Tasks.await(cacheClient.put(configContainer));
    Preconditions.checkArgument(
        cacheClient.getCachedContainerTask().getResult().equals(configContainer));

    ConfigContainer getContainer = Tasks.await(cacheClient.get());

    verify(mockStorageClient, never()).read();
    assertThat(getContainer).isEqualTo(configContainer);
  }

  @Test
  public void get_hasNoCachedValue_readsFileAndSetsCache() throws Exception {
    when(mockStorageClient.read()).thenReturn(configContainer);

    ConfigContainer getContainer = Tasks.await(cacheClient.get());
    assertThat(getContainer).isEqualTo(configContainer);

    assertThat(cacheClient.getCachedContainerTask().getResult()).isEqualTo(configContainer);
  }

  @Test
  public void get_hasNoCachedValueAndFileReadFails_throwsIOException() throws Exception {
    when(mockStorageClient.read()).thenThrow(IO_EXCEPTION);

    Task<ConfigContainer> getTask = cacheClient.get();
    assertThrows(ExecutionException.class, () -> Tasks.await(getTask));

    assertThat(getTask.getException()).isInstanceOf(IOException.class);
  }

  @Test
  public void get_hasFailedCacheValue_readsFileAndSetsCache() throws Exception {
    when(mockStorageClient.read()).thenThrow(IO_EXCEPTION);
    Task<ConfigContainer> getTask = cacheClient.get();
    assertThrows(ExecutionException.class, () -> Tasks.await(getTask));
    Preconditions.checkArgument(getTask.getException() instanceof IOException);

    doReturn(configContainer).when(mockStorageClient).read();

    ConfigContainer getContainer = Tasks.await(cacheClient.get());

    assertThat(getContainer).isEqualTo(configContainer);
  }

  @Test
  public void get_hasMultipleGetCallsInDifferentThreads_readsFileAndSetsCacheOnce()
      throws Exception {
    when(mockStorageClient.read()).thenReturn(configContainer);

    List<Task<ConfigContainer>> getTasks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      getTasks.add(Tasks.call(testingThreadPool, () -> Tasks.await(cacheClient.get())));
    }

    for (Task<ConfigContainer> getTask : getTasks) {
      assertThat(Tasks.await(getTask)).isEqualTo(configContainer);
    }
    verify(mockStorageClient, times(1)).read();
  }

  @Test
  public void get_firstTwoFileReadsFail_readsFileAndSetsCacheThreeTimes() throws Exception {
    doThrow(IO_EXCEPTION).when(mockStorageClient).read();
    assertThrows(ExecutionException.class, () -> Tasks.await(cacheClient.get()));
    assertThrows(ExecutionException.class, () -> Tasks.await(cacheClient.get()));

    doReturn(configContainer).when(mockStorageClient).read();
    for (int getCallIndex = 0; getCallIndex < 5; getCallIndex++) {
      assertThat(Tasks.await(cacheClient.get())).isEqualTo(configContainer);
    }

    // Three file reads: 2 failures and 1 success.
    verify(mockStorageClient, times(3)).read();
  }

  @Test
  public void getBlocking_hasCachedValue_returnsCache() throws Exception {
    Tasks.await(cacheClient.put(configContainer));
    Preconditions.checkArgument(
        cacheClient.getCachedContainerTask().getResult().equals(configContainer));

    ConfigContainer readConfig = cacheClient.getBlocking();

    assertThat(readConfig).isEqualTo(configContainer);

    verify(mockStorageClient, never()).read();
  }

  @Test
  public void getBlocking_hasNoCachedValueAndFileReadTimesOut_returnsNull() throws Exception {
    when(mockStorageClient.read()).thenReturn(configContainer);

    ConfigContainer container = cacheClient.getBlocking(/*diskReadTimeoutInSeconds=*/ 0L);

    assertThat(container).isNull();
  }

  @Test
  public void getBlocking_hasNoCachedValueAndFileReadFails_returnsNull() throws Exception {
    when(mockStorageClient.read()).thenThrow(IO_EXCEPTION);

    ConfigContainer container = cacheClient.getBlocking();

    assertThat(container).isNull();
  }

  @Test
  public void getBlocking_hasFailedCacheValue_blocksOnFileReadAndReturnsFileContainer()
      throws Exception {
    when(mockStorageClient.read()).thenThrow(IO_EXCEPTION);
    Task<ConfigContainer> getTask = cacheClient.get();
    assertThrows(ExecutionException.class, () -> Tasks.await(getTask));
    Preconditions.checkArgument(getTask.getException() instanceof IOException);

    doReturn(configContainer).when(mockStorageClient).read();

    ConfigContainer container = cacheClient.getBlocking();

    assertThat(container).isEqualTo(configContainer);
  }

  @Test
  public void getBlocking_hasNoCachedValue_blocksOnFileReadAndReturnsFileContainer()
      throws Exception {
    when(mockStorageClient.read()).thenReturn(configContainer);

    ConfigContainer container = cacheClient.getBlocking();

    assertThat(container).isEqualTo(configContainer);
  }

  @Test
  public void getBlocking_firstTwoFileReadsFail_readsFileAndSetsCacheThreeTimes() throws Exception {
    doThrow(IO_EXCEPTION).when(mockStorageClient).read();
    assertThat(cacheClient.getBlocking()).isNull();
    assertThat(cacheClient.getBlocking()).isNull();

    doReturn(configContainer).when(mockStorageClient).read();
    for (int getCallIndex = 0; getCallIndex < 5; getCallIndex++) {
      assertThat(cacheClient.getBlocking()).isEqualTo(configContainer);
    }

    verify(mockStorageClient, times(3)).read();
  }

  @Test
  public void clear_hasNoCachedValue_setsCacheContainerToNull() {
    Preconditions.checkArgument(cacheClient.getCachedContainerTask() == null);

    cacheClient.clear();

    verify(mockStorageClient).clear();

    assertThat(cacheClient.getCachedContainerTask().getResult()).isNull();
  }

  @Test
  public void clear_hasCachedValue_setsCacheContainerToNull() throws Exception {
    Tasks.await(cacheClient.put(configContainer));
    Preconditions.checkArgument(
        cacheClient.getCachedContainerTask().getResult().equals(configContainer));

    cacheClient.clear();

    verify(mockStorageClient).clear();

    assertThat(cacheClient.getCachedContainerTask().getResult()).isNull();
    assertThat(Tasks.await(cacheClient.get())).isNull();
  }

  @Test
  public void clear_hasOngoingGetCall_setsCacheContainerToNull() throws Exception {
    when(mockStorageClient.read()).thenReturn(configContainer);
    Tasks.call(testingThreadPool, () -> Tasks.await(cacheClient.get()));

    cacheClient.clear();

    verify(mockStorageClient).clear();

    assertThat(cacheClient.getCachedContainerTask().getResult()).isNull();
    assertThat(Tasks.await(cacheClient.get())).isNull();
  }

  private void verifyFileWrites(ConfigContainer... containers) throws Exception {
    int numContainers = containers.length;
    ArgumentCaptor<ConfigContainer> captor = ArgumentCaptor.forClass(ConfigContainer.class);
    verify(mockStorageClient, times(numContainers)).write(captor.capture());

    List<ConfigContainer> capturedContainers = captor.getAllValues();
    for (int i = 0; i < numContainers; i++) {
      assertThat(capturedContainers.get(i)).isEqualTo(containers[i]);
    }
  }

  @After
  public void cleanUp() {
    cacheThreadPool.shutdownNow();
    testingThreadPool.shutdownNow();
  }
}
