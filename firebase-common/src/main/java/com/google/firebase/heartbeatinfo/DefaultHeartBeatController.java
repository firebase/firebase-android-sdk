package com.google.firebase.heartbeatinfo;

import android.content.Context;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import com.google.firebase.inject.Provider;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DefaultHeartBeatController implements HeartBeatController {

  private final Provider<HeartBeatInfoStorage> storageProvider;

  private final Provider<UserAgentPublisher> userAgentProvider;

  private final Set<HeartBeatConsumer> consumers;

  private final Executor backgroundExecutor;

  private static final ThreadFactory THREAD_FACTORY =
      r -> new Thread(r, "heartbeat-information-executor");

  @Override
  public Task<Void> registerHeartBeat() {
    if (consumers.size() <= 0) {
      return Tasks.forResult(null);
    }
    return Tasks.call(
        backgroundExecutor,
        () -> {
          this.storageProvider
              .get()
              .storeHeartBeat(
                  System.currentTimeMillis(), this.userAgentProvider.get().getUserAgent());
          return null;
        });
  }

  @Override
  public Task<String> getHeartBeatsHeader() throws JSONException {
    return Tasks.call(
        backgroundExecutor,
        () -> {
          List<HeartBeatResult> allHeartBeats = this.storageProvider.get().getAllHeartBeats();
          this.storageProvider.get().deleteAllHeartBeats();
          JSONArray array = new JSONArray();
          for (int i = 0; i < allHeartBeats.size(); i++) {
            HeartBeatResult result = allHeartBeats.get(i);
            JSONObject obj = new JSONObject();
            obj.put("agent", result.getUserAgent());
            obj.put("date", result.getUsedDates());
            array.put(obj);
          }
          return Base64.encodeToString(array.toString().getBytes(), Base64.DEFAULT);
        });
  }

  private DefaultHeartBeatController(
      Context context,
      String persistenceKey,
      Set<HeartBeatConsumer> consumers,
      Provider<UserAgentPublisher> userAgentProvider) {
    // It is very important the executor is single threaded as otherwise it would lead to
    // race conditions.
    this(
        () -> new HeartBeatInfoStorage(context, persistenceKey),
        consumers,
        new ThreadPoolExecutor(
            0, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), THREAD_FACTORY),
        userAgentProvider);
  }

  @VisibleForTesting
  DefaultHeartBeatController(
      Provider<HeartBeatInfoStorage> testStorage,
      Set<HeartBeatConsumer> consumers,
      Executor executor,
      Provider<UserAgentPublisher> userAgentProvider) {
    storageProvider = testStorage;
    this.consumers = consumers;
    this.backgroundExecutor = executor;
    this.userAgentProvider = userAgentProvider;
  }

  public static @NonNull Component<HeartBeatController> component() {
    return Component.builder(HeartBeatController.class)
        .add(Dependency.required(Context.class))
        .add(Dependency.required(FirebaseApp.class))
        .add(Dependency.setOf(HeartBeatConsumer.class))
        .add(Dependency.requiredProvider(UserAgentPublisher.class))
        .factory(
            c ->
                new DefaultHeartBeatController(
                    c.get(Context.class),
                    c.get(FirebaseApp.class).getPersistenceKey(),
                    c.setOf(HeartBeatConsumer.class),
                    c.getProvider(UserAgentPublisher.class)))
        .build();
  }
}
