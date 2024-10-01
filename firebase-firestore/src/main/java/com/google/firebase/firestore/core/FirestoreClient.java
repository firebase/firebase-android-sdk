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

package com.google.firebase.firestore.core;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.AggregateField;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.LoadBundleTask;
import com.google.firebase.firestore.TransactionOptions;
import com.google.firebase.firestore.auth.CredentialsProvider;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.bundle.BundleReader;
import com.google.firebase.firestore.bundle.BundleSerializer;
import com.google.firebase.firestore.bundle.NamedQuery;
import com.google.firebase.firestore.core.EventManager.ListenOptions;
import com.google.firebase.firestore.local.IndexBackfiller;
import com.google.firebase.firestore.local.LocalStore;
import com.google.firebase.firestore.local.Persistence;
import com.google.firebase.firestore.local.QueryResult;
import com.google.firebase.firestore.local.Scheduler;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.remote.GrpcMetadataProvider;
import com.google.firebase.firestore.remote.RemoteSerializer;
import com.google.firebase.firestore.remote.RemoteStore;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Function;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.remoteconfig.ConfigUpdate;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firestore.v1.Value;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

/**
 * FirestoreClient is a top-level class that constructs and owns all of the pieces of the client SDK
 * architecture.
 */
public final class FirestoreClient {

  private static final String LOG_TAG = "FirestoreClient";
  private static final int MAX_CONCURRENT_LIMBO_RESOLUTIONS = 100;

  private final DatabaseInfo databaseInfo;
  private final CredentialsProvider<User> authProvider;
  private final CredentialsProvider<String> appCheckProvider;
  private final AsyncQueue asyncQueue;
  private final BundleSerializer bundleSerializer;
  private Persistence persistence;
  private LocalStore localStore;
  private RemoteStore remoteStore;
  private SyncEngine syncEngine;
  private EventManager eventManager;

  // Telemetry-related
  private static final String FIRESTORE_LOGGING_ENABLED_PARAMETER_NAME = "__firestore_logging_enabled__";
  private static final String FIRESTORE_TRACING_ENABLED_PARAMETER_NAME = "__firestore_tracing_enabled__";
  // Unique identifier for this client.
  private String uuid;
  private OpenTelemetry openTelemetry;
  private FirebaseRemoteConfig remoteConfig;
  private boolean loggingEnabled;
  private boolean tracingEnabled;

  // LRU-related
  @Nullable private Scheduler indexBackfillScheduler;
  @Nullable private Scheduler gcScheduler;

  public FirestoreClient(
      final Context context,
      DatabaseInfo databaseInfo,
      CredentialsProvider<User> authProvider,
      CredentialsProvider<String> appCheckProvider,
      AsyncQueue asyncQueue,
      @Nullable GrpcMetadataProvider metadataProvider,
      ComponentProvider componentProvider) {
    this.databaseInfo = databaseInfo;
    this.authProvider = authProvider;
    this.appCheckProvider = appCheckProvider;
    this.asyncQueue = asyncQueue;
    this.bundleSerializer =
        new BundleSerializer(new RemoteSerializer(databaseInfo.getDatabaseId()));

    TaskCompletionSource<User> firstUser = new TaskCompletionSource<>();
    final AtomicBoolean initialized = new AtomicBoolean(false);

    // Defer initialization until we get the current user from the changeListener. This is
    // guaranteed to be synchronously dispatched onto our worker queue, so we will be initialized
    // before any subsequently queued work runs.
    asyncQueue.enqueueAndForget(
        () -> {
          try {
            // Block on initial user being available
            User initialUser = Tasks.await(firstUser.getTask());
            initialize(context, initialUser, componentProvider, metadataProvider);
          } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
          }
        });

    authProvider.setChangeListener(
        (User user) -> {
          if (initialized.compareAndSet(false, true)) {
            hardAssert(!firstUser.getTask().isComplete(), "Already fulfilled first user task");
            firstUser.setResult(user);
          } else {
            asyncQueue.enqueueAndForget(
                () -> {
                  hardAssert(syncEngine != null, "SyncEngine not yet initialized");
                  Logger.debug(LOG_TAG, "Credential changed. Current user: %s", user.getUid());
                  syncEngine.handleCredentialChange(user);
                });
          }
        });

    appCheckProvider.setChangeListener(
        (String appCheckToken) -> {
          // Register an empty credentials change listener to activate token
          // refresh.
        });
  }

  public boolean isLoggingEnabled() {
    return loggingEnabled;
  }

  public boolean isTracingEnabled() {
    return tracingEnabled;
  }

  public OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  public String getUuid() {
    return uuid;
  }

  public Task<Void> disableNetwork() {
    this.verifyNotTerminated();
    return asyncQueue.enqueue(() -> remoteStore.disableNetwork());
  }

  public Task<Void> enableNetwork() {
    this.verifyNotTerminated();
    return asyncQueue.enqueue(() -> remoteStore.enableNetwork());
  }

  /** Terminates this client, cancels all writes / listeners, and releases all resources. */
  public Task<Void> terminate() {
    authProvider.removeChangeListener();
    appCheckProvider.removeChangeListener();
    return asyncQueue.enqueueAndInitiateShutdown(
        () -> {
          remoteStore.shutdown();
          persistence.shutdown();
          if (gcScheduler != null) {
            gcScheduler.stop();
          }
          if (indexBackfillScheduler != null) {
            indexBackfillScheduler.stop();
          }
        });
  }

  /** Returns true if this client has been terminated. */
  public boolean isTerminated() {
    // Technically, the asyncQueue is still running, but only accepting tasks related to terminating
    // or supposed to be run after terminate(). It is effectively terminated to the eyes of users.
    return this.asyncQueue.isShuttingDown();
  }

  /** Starts listening to a query. */
  public QueryListener listen(
      Query query, ListenOptions options, EventListener<ViewSnapshot> listener) {
    this.verifyNotTerminated();
    QueryListener queryListener = new QueryListener(query, options, listener);
    asyncQueue.enqueueAndForget(() -> eventManager.addQueryListener(queryListener));
    return queryListener;
  }

  /** Stops listening to a query previously listened to. */
  public void stopListening(QueryListener listener) {
    // `enqueueAndForget` will no-op if client is already terminated.
    asyncQueue.enqueueAndForget(() -> eventManager.removeQueryListener(listener));
  }

  // TODO(b/261013682): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  public Task<Document> getDocumentFromLocalCache(DocumentKey docKey) {
    this.verifyNotTerminated();
    return asyncQueue
        .enqueue(() -> localStore.readDocument(docKey))
        .continueWith(
            (result) -> {
              Document document = result.getResult();
              if (document.isFoundDocument()) {
                return document;
              } else if (document.isNoDocument()) {
                return null;
              } else {
                throw new FirebaseFirestoreException(
                    "Failed to get document from cache. (However, this document may exist on the "
                        + "server. Run again without setting source to CACHE to attempt "
                        + "to retrieve the document from the server.)",
                    Code.UNAVAILABLE);
              }
            });
  }

  public Task<ViewSnapshot> getDocumentsFromLocalCache(Query query) {
    this.verifyNotTerminated();
    return asyncQueue.enqueue(
        () -> {
          QueryResult queryResult = localStore.executeQuery(query, /* usePreviousResults= */ true);
          View view = new View(query, queryResult.getRemoteKeys());
          View.DocumentChanges viewDocChanges = view.computeDocChanges(queryResult.getDocuments());
          return view.applyChanges(viewDocChanges).getSnapshot();
        });
  }

  /** Writes mutations. The returned task will be notified when it's written to the backend. */
  public Task<Void> write(final List<Mutation> mutations) {
    this.verifyNotTerminated();
    final TaskCompletionSource<Void> source = new TaskCompletionSource<>();
    asyncQueue.enqueueAndForget(() -> syncEngine.writeMutations(mutations, source));
    return source.getTask();
  }

  /** Tries to execute the transaction in updateFunction. */
  public <TResult> Task<TResult> transaction(
      TransactionOptions options, Function<Transaction, Task<TResult>> updateFunction) {
    this.verifyNotTerminated();
    return AsyncQueue.callTask(
        asyncQueue.getExecutor(),
        () -> syncEngine.transaction(asyncQueue, options, updateFunction));
  }

  // TODO(b/261013682): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  public Task<Map<String, Value>> runAggregateQuery(
      Query query, List<AggregateField> aggregateFields) {
    this.verifyNotTerminated();
    final TaskCompletionSource<Map<String, Value>> result = new TaskCompletionSource<>();
    asyncQueue.enqueueAndForget(
        () ->
            syncEngine
                .runAggregateQuery(query, aggregateFields)
                .addOnSuccessListener(data -> result.setResult(data))
                .addOnFailureListener(e -> result.setException(e)));
    return result.getTask();
  }

  /**
   * Returns a task resolves when all the pending writes at the time when this method is called
   * received server acknowledgement. An acknowledgement can be either acceptance or rejections.
   */
  public Task<Void> waitForPendingWrites() {
    this.verifyNotTerminated();

    final TaskCompletionSource<Void> source = new TaskCompletionSource<>();
    asyncQueue.enqueueAndForget(() -> syncEngine.registerPendingWritesTask(source));
    return source.getTask();
  }

  private void initialize(
      Context context,
      User user,
      ComponentProvider provider,
      GrpcMetadataProvider metadataProvider) {
    // Note: The initialization work must all be synchronous (we can't dispatch more work) since
    // external write/listen operations could get queued to run before that subsequent work
    // completes.
    Logger.debug(LOG_TAG, "Initializing. user=%s", user.getUid());

    ComponentProvider.Configuration configuration =
        new ComponentProvider.Configuration(
            context,
            asyncQueue,
            databaseInfo,
            user,
            MAX_CONCURRENT_LIMBO_RESOLUTIONS,
            authProvider,
            appCheckProvider,
            metadataProvider);
    provider.initialize(configuration);
    persistence = provider.getPersistence();
    gcScheduler = provider.getGarbageCollectionScheduler();
    localStore = provider.getLocalStore();
    remoteStore = provider.getRemoteStore();
    syncEngine = provider.getSyncEngine();
    eventManager = provider.getEventManager();
    IndexBackfiller indexBackfiller = provider.getIndexBackfiller();

    if (gcScheduler != null) {
      gcScheduler.start();
    }

    if (indexBackfiller != null) {
      indexBackfillScheduler = indexBackfiller.getScheduler();
      indexBackfillScheduler.start();
    }

    // TELEMETRY-RELATED

    // Fetch and listen to Remotely-configured telemetry configurations.
    loggingEnabled = false;
    tracingEnabled = false;
    remoteConfig = FirebaseRemoteConfig.getInstance();
    // TESTING: Set the fetch interval to 0 for testing so we always get fresh values.
    // Revert back to a reasonable number (e.g. 3600 - every one hour).
    remoteConfig.setConfigSettingsAsync(new FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build());
    remoteConfig
      .fetchAndActivate()
      .addOnCompleteListener(asyncQueue.getExecutor(),
        new OnCompleteListener<Boolean>() {
          @Override
          public void onComplete(@NonNull Task<Boolean> task) {
            if (task.isSuccessful()) {
              boolean updated = task.getResult();
              Log.d(LOG_TAG, "Config params updated: " + updated);
              loggingEnabled = remoteConfig.getBoolean(FIRESTORE_LOGGING_ENABLED_PARAMETER_NAME);
              tracingEnabled = remoteConfig.getBoolean(FIRESTORE_TRACING_ENABLED_PARAMETER_NAME);
              Log.d(LOG_TAG, "loggingEnabled: " + loggingEnabled);
              Log.d(LOG_TAG, "tracingEnabled: " + tracingEnabled);
            } else {
              Log.d(LOG_TAG, "Config params fetch failed.");
            }
          }
        }
      );

    remoteConfig.addOnConfigUpdateListener(new ConfigUpdateListener() {
      @Override
      public void onUpdate(ConfigUpdate configUpdate) {
        Log.d(LOG_TAG, "Updated keys: " + configUpdate.getUpdatedKeys());
        remoteConfig.activate().addOnCompleteListener(asyncQueue.getExecutor(), new OnCompleteListener<Boolean>() {
          @Override
          public void onComplete(@NonNull Task<Boolean> task) {
            loggingEnabled = remoteConfig.getBoolean(FIRESTORE_LOGGING_ENABLED_PARAMETER_NAME);
            tracingEnabled = remoteConfig.getBoolean(FIRESTORE_TRACING_ENABLED_PARAMETER_NAME);
            Log.d(LOG_TAG, "loggingEnabled: " + loggingEnabled);
            Log.d(LOG_TAG, "tracingEnabled: " + tracingEnabled);
          }
        });
      }
      @Override
      public void onError(FirebaseRemoteConfigException error) {
        Log.w(LOG_TAG, "Config update error with code: " + error.getCode(), error);
      }
    });

    // Set up OpenTelemetry.
    System.out.println("inside Query.addSnapshotListenerInternal");
    Resource resource =
            Resource.getDefault().merge(Resource.builder().put("service.name", "firebase-android-sdk").build());
    OtlpGrpcSpanExporter otlpGrpcSpanExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://34.44.91.83:4317")
            .build();

    // Configure a batch span processor
    BatchSpanProcessor otlpGrpcSpanProcessor = BatchSpanProcessor.builder(otlpGrpcSpanExporter)
            .setScheduleDelay(50, TimeUnit.MILLISECONDS)
            .build();
    LoggingSpanExporter loggingSpanExporter = LoggingSpanExporter.create();
    SpanProcessor loggingSpanProcessor = BatchSpanProcessor.builder(loggingSpanExporter).build();

    OtlpGrpcLogRecordExporter otlpGrpcLogRecordExporter =
            OtlpGrpcLogRecordExporter.builder()
                    .setEndpoint("http://34.44.91.83:4317")
                    .build();
    LogRecordProcessor otlpGrpcLogRecordProcessor = BatchLogRecordProcessor.builder(otlpGrpcLogRecordExporter).build();

    // Ideally we shouldn't even set up an OTEL instance if all of logging, tracing, and metrics are disabled.
    SdkTracerProvider sdkTracerProvider =
            SdkTracerProvider
                    .builder()
                    .setResource(resource)
                    .addSpanProcessor(otlpGrpcSpanProcessor)
                    .addSpanProcessor(loggingSpanProcessor)
                    .build();

    openTelemetry = OpenTelemetrySdk
            .builder()
            .setTracerProvider(sdkTracerProvider)
            .setLoggerProvider(
                    SdkLoggerProvider.builder()
                            .addLogRecordProcessor(otlpGrpcLogRecordProcessor)
                            .build()
            )
            .build();

    uuid = UUID.randomUUID().toString();
  }

  public void addSnapshotsInSyncListener(EventListener<Void> listener) {
    verifyNotTerminated();
    asyncQueue.enqueueAndForget(() -> eventManager.addSnapshotsInSyncListener(listener));
  }

  public void loadBundle(InputStream bundleData, LoadBundleTask resultTask) {
    verifyNotTerminated();
    BundleReader bundleReader = new BundleReader(bundleSerializer, bundleData);
    asyncQueue.enqueueAndForget(() -> syncEngine.loadBundle(bundleReader, resultTask));
  }

  public Task<Query> getNamedQuery(String queryName) {
    verifyNotTerminated();
    TaskCompletionSource<Query> completionSource = new TaskCompletionSource<>();
    asyncQueue.enqueueAndForget(
        () -> {
          NamedQuery namedQuery = localStore.getNamedQuery(queryName);
          if (namedQuery != null) {
            Target target = namedQuery.getBundledQuery().getTarget();
            completionSource.setResult(
                new Query(
                    target.getPath(),
                    target.getCollectionGroup(),
                    target.getFilters(),
                    target.getOrderBy(),
                    target.getLimit(),
                    namedQuery.getBundledQuery().getLimitType(),
                    target.getStartAt(),
                    target.getEndAt()));
          } else {
            completionSource.setResult(null);
          }
        });
    return completionSource.getTask();
  }

  public Task<Void> configureFieldIndexes(List<FieldIndex> fieldIndices) {
    verifyNotTerminated();
    return asyncQueue.enqueue(() -> localStore.configureFieldIndexes(fieldIndices));
  }

  public void setIndexAutoCreationEnabled(boolean isEnabled) {
    verifyNotTerminated();
    asyncQueue.enqueueAndForget(() -> localStore.setIndexAutoCreationEnabled(isEnabled));
  }

  public void deleteAllFieldIndexes() {
    verifyNotTerminated();
    asyncQueue.enqueueAndForget(() -> localStore.deleteAllFieldIndexes());
  }

  public void removeSnapshotsInSyncListener(EventListener<Void> listener) {
    // `enqueueAndForget` will no-op if client is already terminated.
    asyncQueue.enqueueAndForget(() -> eventManager.removeSnapshotsInSyncListener(listener));
  }

  private void verifyNotTerminated() {
    if (this.isTerminated()) {
      throw new IllegalStateException("The client has already been terminated");
    }
  }
}
