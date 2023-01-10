// Copyright 2021 Google LLC
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

package com.google.firebase.heartbeatinfo;

import android.content.Context;
import android.util.Base64;
import android.util.Base64OutputStream;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.os.UserManagerCompat;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import com.google.firebase.inject.Provider;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.zip.GZIPOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Provides a function to store heartbeats and another function to retrieve stored heartbeats. */
public class DefaultHeartBeatController implements HeartBeatController, HeartBeatInfo {

  private final Provider<HeartBeatInfoStorage> storageProvider;

  private final Context applicationContext;

  private final Provider<UserAgentPublisher> userAgentProvider;

  private final Set<HeartBeatConsumer> consumers;

  private final Executor backgroundExecutor;

  public Task<Void> registerHeartBeat() {
    if (consumers.size() <= 0) {
      return Tasks.forResult(null);
    }
    boolean inDirectBoot = !UserManagerCompat.isUserUnlocked(applicationContext);
    if (inDirectBoot) {
      return Tasks.forResult(null);
    }

    return Tasks.call(
        backgroundExecutor,
        () -> {
          synchronized (DefaultHeartBeatController.this) {
            this.storageProvider
                .get()
                .storeHeartBeat(
                    System.currentTimeMillis(), this.userAgentProvider.get().getUserAgent());
          }

          return null;
        });
  }

  @Override
  public Task<String> getHeartBeatsHeader() {
    boolean inDirectBoot = !UserManagerCompat.isUserUnlocked(applicationContext);
    if (inDirectBoot) {
      return Tasks.forResult("");
    }
    return Tasks.call(
        backgroundExecutor,
        () -> {
          synchronized (DefaultHeartBeatController.this) {
            HeartBeatInfoStorage storage = this.storageProvider.get();
            List<HeartBeatResult> allHeartBeats = storage.getAllHeartBeats();
            storage.deleteAllHeartBeats();
            JSONArray array = new JSONArray();
            for (int i = 0; i < allHeartBeats.size(); i++) {
              HeartBeatResult result = allHeartBeats.get(i);
              JSONObject obj = new JSONObject();
              obj.put("agent", result.getUserAgent());
              obj.put("dates", new JSONArray(result.getUsedDates()));
              array.put(obj);
            }
            JSONObject output = new JSONObject();
            output.put("heartbeats", array);
            output.put("version", "2");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (Base64OutputStream b64os =
                    new Base64OutputStream(
                        out, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
                GZIPOutputStream gzip = new GZIPOutputStream(b64os); ) {
              gzip.write(output.toString().getBytes("UTF-8"));
            }
            return out.toString("UTF-8");
          }
        });
  }

  private DefaultHeartBeatController(
      Context context,
      String persistenceKey,
      Set<HeartBeatConsumer> consumers,
      Provider<UserAgentPublisher> userAgentProvider,
      Executor backgroundExecutor) {
    this(
        () -> new HeartBeatInfoStorage(context, persistenceKey),
        consumers,
        backgroundExecutor,
        userAgentProvider,
        context);
  }

  @VisibleForTesting
  DefaultHeartBeatController(
      Provider<HeartBeatInfoStorage> testStorage,
      Set<HeartBeatConsumer> consumers,
      Executor executor,
      Provider<UserAgentPublisher> userAgentProvider,
      Context context) {
    storageProvider = testStorage;
    this.consumers = consumers;
    this.backgroundExecutor = executor;
    this.userAgentProvider = userAgentProvider;
    this.applicationContext = context;
  }

  public static @NonNull Component<DefaultHeartBeatController> component() {
    Qualified<Executor> backgroundExecutor = Qualified.qualified(Background.class, Executor.class);
    return Component.builder(
            DefaultHeartBeatController.class, HeartBeatController.class, HeartBeatInfo.class)
        .add(Dependency.required(Context.class))
        .add(Dependency.required(FirebaseApp.class))
        .add(Dependency.setOf(HeartBeatConsumer.class))
        .add(Dependency.requiredProvider(UserAgentPublisher.class))
        .add(Dependency.required(backgroundExecutor))
        .factory(
            c ->
                new DefaultHeartBeatController(
                    c.get(Context.class),
                    c.get(FirebaseApp.class).getPersistenceKey(),
                    c.setOf(HeartBeatConsumer.class),
                    c.getProvider(UserAgentPublisher.class),
                    c.get(backgroundExecutor)))
        .build();
  }

  @Override
  @NonNull
  public synchronized HeartBeat getHeartBeatCode(@NonNull String heartBeatTag) {
    long presentTime = System.currentTimeMillis();
    HeartBeatInfoStorage storage = storageProvider.get();
    boolean shouldSendGlobalHB = storage.shouldSendGlobalHeartBeat(presentTime);
    if (shouldSendGlobalHB) {
      storage.postHeartBeatCleanUp();
      return HeartBeat.GLOBAL;
    } else {
      return HeartBeat.NONE;
    }
  }
}
