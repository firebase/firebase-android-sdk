package com.google.firebase.remoteconfig.internal;

import static com.google.firebase.remoteconfig.RemoteConfigConstants.REALTIME_REGEX_URL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.remoteconfig.ConfigUpdate;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.LEGACY)
public class ConfigRealtimeHttpClientTest {
  private final String NAMESPACE = "firebase";
  private final String PROJECT_NUMBER = "14368190084";
  private static final String APP_ID = "1:14368190084:android:09cb977358c6f241";
  private static final String API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String PROJECT_ID = "fake-frc-test-id";

  private Set<ConfigUpdateListener> configUpdateListeners;

  @Mock private ScheduledExecutorService scheduledExecutorService;
  @Mock private ConfigFetchHandler configFetchHandler;
  @Mock private FirebaseInstallationsApi firebaseInstallations;
  @Mock private ConfigRealtimeHttpStream configRealtimeHttpStream;
  @Mock private ScheduledFuture<?> t;
  @Mock private ConfigCacheClient activatedCacheCLient;

  private FakeHttpURLConnection fakeHttpURLConnection;
  private ConfigRealtimeHttpClient configRealtimeHttpClient;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    Context context = ApplicationProvider.getApplicationContext();
    FirebaseApp firebaseApp = initializeFirebaseApp(context);

    configUpdateListeners = new HashSet<>();
    configUpdateListeners.add(
        new ConfigUpdateListener() {
          @Override
          public void onUpdate(@NonNull ConfigUpdate configUpdate) {}

          @Override
          public void onError(@NonNull FirebaseRemoteConfigException error) {}
        });

    fakeHttpURLConnection =
        new FakeHttpURLConnection(
            new URL(String.format(REALTIME_REGEX_URL, PROJECT_NUMBER, NAMESPACE)));

    configRealtimeHttpClient =
        new ConfigRealtimeHttpClient(
            activatedCacheCLient,
            firebaseApp,
            firebaseInstallations,
            configFetchHandler,
            context,
            NAMESPACE,
            configUpdateListeners,
            scheduledExecutorService);
  }

  @Test
  public void createRealtimeHttpStreamFutureTask_andRun() throws Exception {
    when(configRealtimeHttpStream.getBackoffState()).thenReturn(false);
    when(configRealtimeHttpStream.getLastAttemptState()).thenReturn(false);
    when(configRealtimeHttpStream.getRetryState()).thenReturn(false);
    when(configRealtimeHttpStream.createRealtimeConnection()).thenReturn(fakeHttpURLConnection);
    ConfigRealtimeHttpClient.RealtimeHttpStreamFutureTask futureTask =
        configRealtimeHttpClient.createRealtimeHttpStreamFutureTask(configRealtimeHttpStream);

    futureTask.run();

    verify(configRealtimeHttpStream).beginRealtimeHttpStream(fakeHttpURLConnection);
    verify(configRealtimeHttpStream).getLastAttemptState();
  }

  @Test
  public void createRealtimeHttpStreamFutureTask_andRunThenRetry() throws Exception {
    when(configRealtimeHttpStream.getBackoffState()).thenReturn(false);
    when(configRealtimeHttpStream.getLastAttemptState()).thenReturn(false);
    when(configRealtimeHttpStream.getRetryState()).thenReturn(true);
    when(configRealtimeHttpStream.createRealtimeConnection()).thenReturn(fakeHttpURLConnection);
    ConfigRealtimeHttpClient.RealtimeHttpStreamFutureTask futureTask =
        configRealtimeHttpClient.createRealtimeHttpStreamFutureTask(configRealtimeHttpStream);

    futureTask.run();

    verify(scheduledExecutorService).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
  }

  @Test
  public void createRealtimeHttpStreamFutureTask_andRunThenNoRetry() throws Exception {
    when(configRealtimeHttpStream.getBackoffState()).thenReturn(true);
    when(configRealtimeHttpStream.getLastAttemptState()).thenReturn(false);
    when(configRealtimeHttpStream.getRetryState()).thenReturn(false);
    when(configRealtimeHttpStream.createRealtimeConnection()).thenReturn(fakeHttpURLConnection);
    ConfigRealtimeHttpClient.RealtimeHttpStreamFutureTask futureTask =
        configRealtimeHttpClient.createRealtimeHttpStreamFutureTask(configRealtimeHttpStream);

    futureTask.run();

    verify(scheduledExecutorService, never())
        .schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
  }

  @Test
  public void callStartRealtimeHttpStream_andSubmitToExecutorService() {
    configRealtimeHttpClient.startRealtimeHttpStream();

    verify(scheduledExecutorService)
        .submit(any(ConfigRealtimeHttpClient.RealtimeHttpStreamFutureTask.class));
  }

  @Test
  public void callStartRealtimeHttpStream_andBackoffEnabled() throws Exception {
    when(configRealtimeHttpStream.getBackoffState()).thenReturn(true);
    when(configRealtimeHttpStream.getLastAttemptState()).thenReturn(false);
    when(configRealtimeHttpStream.getRetryState()).thenReturn(false);
    when(configRealtimeHttpStream.createRealtimeConnection()).thenReturn(fakeHttpURLConnection);
    ConfigRealtimeHttpClient.RealtimeHttpStreamFutureTask futureTask =
        configRealtimeHttpClient.createRealtimeHttpStreamFutureTask(configRealtimeHttpStream);
    futureTask.run();

    configRealtimeHttpClient.startRealtimeHttpStream();

    verify(scheduledExecutorService, never()).submit(any(Runnable.class));
  }

  @Test
  public void callEndRealtimeHttpStream_andLetNewThreadToBeSubmitted() {
    doReturn(t).when(scheduledExecutorService).submit(any(Runnable.class));

    configRealtimeHttpClient.startRealtimeHttpStream();
    configRealtimeHttpClient.startRealtimeHttpStream();

    verify(scheduledExecutorService, times(1))
        .submit(any(ConfigRealtimeHttpClient.RealtimeHttpStreamFutureTask.class));

    configRealtimeHttpClient.endRealtimeHttpStream();
    configRealtimeHttpClient.startRealtimeHttpStream();

    verify(scheduledExecutorService, times(2))
        .submit(any(ConfigRealtimeHttpClient.RealtimeHttpStreamFutureTask.class));
  }

  private static FirebaseApp initializeFirebaseApp(Context context) {
    FirebaseApp.clearInstancesForTest();

    return FirebaseApp.initializeApp(
        context,
        new FirebaseOptions.Builder()
            .setApiKey(API_KEY)
            .setApplicationId(APP_ID)
            .setProjectId(PROJECT_ID)
            .build());
  }
}
