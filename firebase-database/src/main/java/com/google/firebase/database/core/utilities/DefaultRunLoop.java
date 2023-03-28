// Copyright 2018 Google LLC
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

package com.google.firebase.database.core.utilities;

import static com.google.firebase.components.Qualified.qualified;

import android.util.Log;

import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.components.Qualified;
import com.google.firebase.concurrent.FirebaseExecutors;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.core.RunLoop;
import com.google.firebase.database.core.ThreadInitializer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

// TODO(mtewani): Replace with blocking thread, and for every instance ,use sequential executor
public abstract class DefaultRunLoop implements RunLoop {



  protected ThreadFactory getThreadFactory() {
    return Executors.defaultThreadFactory();
  }

  protected ThreadInitializer getThreadInitializer() {
    return ThreadInitializer.defaultInstance;
  }

  public abstract void handleException(Throwable e);

  private ScheduledThreadPoolExecutor executor;

  public DefaultRunLoop() { }

  public DefaultRunLoop(ScheduledExecutorService delegate) {
    executor = (ScheduledThreadPoolExecutor) FirebaseExecutors.newSequentialExecutor(delegate);

    // Core threads don't time out, this only takes effect when we drop the number of required
    // core threads
    executor.setKeepAliveTime(3, TimeUnit.SECONDS);
  }

  public void setExecutor(ScheduledThreadPoolExecutor executor) {
    this.executor = executor;
  }

  public ScheduledExecutorService getExecutorService() {
    return this.executor;
  }

  private Runnable withAfter(Runnable r) {
      return () -> {
        try {
          r.run();
        } catch(Throwable t) {
          Throwable throwable = t;
          if (r instanceof Future<?>) {
            Future<?> future = (Future<?>) r;
            try {
              // Not all Futures will be done, e.g. when used with scheduledAtFixedRate
              if (future.isDone()) { // If we want to wait until the future is done, we should probably check the opposite.
                future.get();
              }
            } catch (CancellationException ce) {
              // Cancellation exceptions are okay, we expect them to happen sometimes
            } catch (ExecutionException ee) {
              throwable = ee.getCause();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
          if(throwable != null) {
            handleException(throwable);
          }
        }
      };
  }

  @Override
  public void scheduleNow(final Runnable r) {
    executor.execute(withAfter(r));
  }

  @Override
  public ScheduledFuture schedule(final Runnable runnable, long milliseconds) {
    return executor.schedule(runnable, milliseconds, TimeUnit.MILLISECONDS);
  }

  @Override
  public void shutdown() {
    executor.setCorePoolSize(0);
  }

  @Override
  public void restart() {
    executor.setCorePoolSize(1);
  }

  public static String messageForException(Throwable t) {
    if (t instanceof OutOfMemoryError) {
      return "Firebase Database encountered an OutOfMemoryError. You may need to reduce the"
          + " amount of data you are syncing to the client (e.g. by using queries or syncing"
          + " a deeper path). See "
          + "https://firebase.google.com/docs/database/ios/structure-data#best_practices_for_data_structure"
          + " and "
          + "https://firebase.google.com/docs/database/android/retrieve-data#filtering_data";
    } else if (t instanceof NoClassDefFoundError) {
      return "A symbol that the Firebase Database SDK depends on failed to load. This usually "
          + "indicates that your project includes an incompatible version of another Firebase "
          + "dependency. If updating your dependencies to the latest version does not resolve "
          + "this issue, please file a report at https://github.com/firebase/firebase-android-sdk";
    } else if (t instanceof DatabaseException) {
      // Exception should be self-explanatory and they shouldn't contact support.
      return "";
    } else {
      return "Uncaught exception in Firebase Database runloop ("
          + FirebaseDatabase.getSdkVersion()
          + "). If you are not already on the latest version of the Firebase SDKs, try updating "
          + "your dependencies. Should this problem persist, please file a report at "
          + "https://github.com/firebase/firebase-android-sdk";
    }
  }
}
