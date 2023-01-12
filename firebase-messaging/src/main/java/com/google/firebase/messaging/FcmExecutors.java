// Copyright 2020 Google LLC
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
package com.google.firebase.messaging;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.SuppressLint;
import com.google.android.gms.common.util.concurrent.NamedThreadFactory;
import com.google.firebase.messaging.threads.PoolableExecutors;
import com.google.firebase.messaging.threads.ThreadPriority;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Shared executors for FirebaseMessaging.
 *
 * <p>Details for the FCM threading model seek to be documented here.
 */
class FcmExecutors {
  // TODO(b/117848373): TikTok applications need to comply with go/tiktok-tattletale. Before we
  // migrate to use TikTok thread pools, threads need to use the whitelisted prefix
  // "Firebase-Messaging".
  private static final String THREAD_NETWORK_IO = "Firebase-Messaging-Network-Io";
  private static final String THREAD_TASK = "Firebase-Messaging-Task";
  private static final String THREAD_FILE = "Firebase-Messaging-File";
  private static final String THREAD_INTENT_HANDLE = "Firebase-Messaging-Intent-Handle";
  private static final String THREAD_TOPICS_IO = "Firebase-Messaging-Topics-Io";
  private static final String THREAD_INIT = "Firebase-Messaging-Init";

  static final String THREAD_FILE_IO = "Firebase-Messaging-File-Io";
  static final String THREAD_RPC_TASK = "Firebase-Messaging-Rpc-Task";

  static Executor newRpcTasksExecutor() {
    return newCachedSingleThreadExecutor(THREAD_RPC_TASK);
  }

  static Executor newFileIOExecutor() {
    return newCachedSingleThreadExecutor(THREAD_FILE_IO);
  }

  @SuppressWarnings("ThreadChecker")
  // TODO(b/258424124): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  private static Executor newCachedSingleThreadExecutor(String threadName) {
    // Creates a single threaded executor that only keeps the thread alive for a short time when
    // idle to reduce resource use.
    return new ThreadPoolExecutor(
        /* corePoolSize= */ 0,
        /* maximumPoolSize= */ 1,
        /* keepAliveTime= */ 30,
        SECONDS,
        /* workQueue= */ new LinkedBlockingQueue<>(),
        /* threadFactory= */ new NamedThreadFactory(threadName));
  }

  /** Creates a single threaded ScheduledPoolExecutor. */
  @SuppressWarnings("ThreadChecker")
  // TODO(b/258424124): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  static ScheduledExecutorService newTopicsSyncExecutor() {
    return new ScheduledThreadPoolExecutor(
        /* corePoolSize= */ 1, new NamedThreadFactory(THREAD_TOPICS_IO));
  }

  @SuppressWarnings("ThreadChecker")
  // TODO(b/258424124): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  static ExecutorService newNetworkIOExecutor() {
    // TODO(b/148493968): consider use PoolableExecutors for all FCM threading
    return Executors.newSingleThreadExecutor(new NamedThreadFactory(THREAD_NETWORK_IO));
  }

  @SuppressWarnings("ThreadChecker")
  // TODO(b/258424124): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  static ExecutorService newTaskExecutor() {
    return Executors.newSingleThreadExecutor(new NamedThreadFactory(THREAD_TASK));
  }

  @SuppressWarnings("ThreadChecker")
  // TODO(b/258424124): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  static ExecutorService newFileExecutor() {
    return Executors.newSingleThreadExecutor(new NamedThreadFactory(THREAD_FILE));
  }

  // TODO(b/258424124): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  static ExecutorService newIntentHandleExecutor() {
    return PoolableExecutors.factory()
        .newSingleThreadExecutor(
            new NamedThreadFactory(THREAD_INTENT_HANDLE), ThreadPriority.HIGH_SPEED);
  }

  /** Creates a single threaded ScheduledPoolExecutor. */
  @SuppressWarnings("ThreadChecker")
  // TODO(b/258424124): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  static ScheduledExecutorService newInitExecutor() {
    return new ScheduledThreadPoolExecutor(
        /* corePoolSize= */ 1, new NamedThreadFactory(THREAD_INIT));
  }

  private FcmExecutors() {}
}
