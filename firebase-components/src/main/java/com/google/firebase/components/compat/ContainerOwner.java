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

package com.google.firebase.components.compat;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.collection.ArrayMap;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentDiscovery;
import com.google.firebase.components.ComponentRuntime;
import com.google.firebase.components.ContainerInfo;
import com.google.firebase.components.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/** */
public final class ContainerOwner {

  private static final @NonNull String DEFAULT_CONTAINER_NAME = "[DEFAULT]";

  /** A map of (name, ComponentRuntime) instances. */
  private static final Map<String, ComponentRuntime> CONTAINERS = new ArrayMap<>();

  @GuardedBy("CONTAINERS")
  private static final Executor UI_EXECUTOR = new UiExecutor();

  private ContainerOwner() {}

  public static ComponentRuntime getInstance(String name) {
    synchronized (CONTAINERS) {
      return CONTAINERS.get(name);
    }
  }

  public static List<ComponentRuntime> getAll() {
    synchronized (CONTAINERS) {
      return new ArrayList<>(CONTAINERS.values());
    }
  }

  public static ComponentRuntime initialize(
      Context applicationContext, String name, Component<?>... additionalComponents) {
    if (applicationContext.getApplicationContext() != null) {
      // In shared processes' content providers getApplicationContext() can return null.
      applicationContext = applicationContext.getApplicationContext();
    }

    Preconditions.checkNotNull(applicationContext, "Application context cannot be null.");
    String normalizedName = name.trim();
    ComponentRuntime componentRuntime;
    synchronized (CONTAINERS) {
      Preconditions.checkState(
          !CONTAINERS.containsKey(normalizedName),
          "ComponentRuntime name " + normalizedName + " already exists!");

      List<Component<?>> components = new ArrayList<>(Arrays.asList(additionalComponents));
      components.add(Component.of(new ContainerInfo(normalizedName), ContainerInfo.class));
      components.add(Component.of(applicationContext, Context.class));

      componentRuntime =
          new ComponentRuntime(
              UI_EXECUTOR,
              ComponentDiscovery.forContext(applicationContext).discover(),
              components.toArray(new Component<?>[] {}));

      CONTAINERS.put(name, componentRuntime);
    }
    initializeAllApis(componentRuntime, applicationContext);
    return componentRuntime;
  }

  public static void delete(String name) {
    synchronized (CONTAINERS) {
      CONTAINERS.remove(name);
    }
  }

  @VisibleForTesting
  public static void clearInstancesForTest() {
    synchronized (CONTAINERS) {
      CONTAINERS.clear();
    }
  }

  /** Initializes all appropriate APIs for this instance. */
  private static void initializeAllApis(
      ComponentRuntime componentRuntime, Context applicationContext) {
    boolean inDirectBoot = !isUserUnlocked(applicationContext);
    if (inDirectBoot) {
      // Ensure that all APIs are initialized once the user unlocks the phone.
      UserUnlockReceiver.ensureReceiverRegistered(applicationContext);
    } else {
      componentRuntime.initializeEagerComponents(isDefault(componentRuntime));
    }
  }

  private static boolean isDefault(ComponentRuntime componentRuntime) {
    return DEFAULT_CONTAINER_NAME.equals(componentRuntime.get(ContainerInfo.class).getName());
  }

  private static boolean isUserUnlocked(Context context) {
    if (Build.VERSION.SDK_INT >= 24) {
      return context.getSystemService(UserManager.class).isUserUnlocked();
    } else {
      return true;
    }
  }

  /**
   * Utility class that initializes eager components when the user unlocks the device, if the app is
   * first started in direct boot mode.
   */
  @TargetApi(Build.VERSION_CODES.N)
  private static class UserUnlockReceiver extends BroadcastReceiver {

    private static AtomicReference<UserUnlockReceiver> INSTANCE = new AtomicReference<>();
    private final Context applicationContext;

    public UserUnlockReceiver(Context applicationContext) {
      this.applicationContext = applicationContext;
    }

    private static void ensureReceiverRegistered(Context applicationContext) {
      if (INSTANCE.get() == null) {
        UserUnlockReceiver receiver = new UserUnlockReceiver(applicationContext);
        if (INSTANCE.compareAndSet(null /* expected */, receiver)) {
          IntentFilter intentFilter = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
          applicationContext.registerReceiver(receiver, intentFilter);
        }
      }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      // API initialization is idempotent.
      synchronized (CONTAINERS) {
        for (ComponentRuntime runtime : CONTAINERS.values()) {
          initializeAllApis(runtime, context);
        }
      }
      unregister();
    }

    public void unregister() {
      applicationContext.unregisterReceiver(this);
    }
  }

  private static class UiExecutor implements Executor {
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    @Override
    public void execute(@NonNull Runnable command) {
      HANDLER.post(command);
    }
  }
}
