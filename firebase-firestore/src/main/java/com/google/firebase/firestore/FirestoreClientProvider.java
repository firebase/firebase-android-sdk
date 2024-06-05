package com.google.firebase.firestore;

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import android.content.Context;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.core.util.Supplier;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.common.base.Function;
import com.google.firebase.emulators.EmulatedServiceSettings;
import com.google.firebase.firestore.auth.CredentialsProvider;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.ComponentProvider;
import com.google.firebase.firestore.core.DatabaseInfo;
import com.google.firebase.firestore.core.FirestoreClient;
import com.google.firebase.firestore.core.MemoryComponentProvider;
import com.google.firebase.firestore.core.SQLiteComponentProvider;
import com.google.firebase.firestore.local.SQLitePersistence;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.remote.Datastore;
import com.google.firebase.firestore.remote.GrpcMetadataProvider;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Logger;
import com.google.protobuf.ByteString;

import java.util.concurrent.Executor;

/**
 * The FirestoreClientProvider handles the life cycle of FirestoreClients.
 *
 * Coupling to FirestoreClient should go through the {@link FirestoreClientProvider#get()}
 * method. The returned FirestoreClient can change over time if there is an event that requires
 * restarting the FirestoreClient internally.
 */
final class FirestoreClientProvider {

    private final Context context;
    private final DatabaseId databaseId;
    private final String persistenceKey;
    private final GrpcMetadataProvider metadataProvider;
    private final Supplier<CredentialsProvider<User>> authProviderFactory;
    private final Supplier<CredentialsProvider<String>> appCheckTokenProviderFactory;

    @GuardedBy("this")
    private FirestoreClient client;
    private volatile AsyncQueue asyncQueue;

    @GuardedBy("this")
    private FirebaseFirestoreSettings settings;

    @GuardedBy("this")
    private boolean networkEnabled = true;

    @GuardedBy("this")
    @Nullable private EmulatedServiceSettings emulatorSettings;

    @GuardedBy("this")
    private ByteString sessionToken;

    FirestoreClientProvider(
            Context context,
            DatabaseId databaseId,
            @NonNull String persistenceKey,
            @NonNull Supplier<CredentialsProvider<User>> authProviderFactory,
            @NonNull Supplier<CredentialsProvider<String>> appCheckTokenProviderFactory,
            @Nullable GrpcMetadataProvider metadataProvider) {
        this.context = checkNotNull(context);
        this.databaseId = checkNotNull(checkNotNull(databaseId));
        this.persistenceKey = checkNotNull(persistenceKey);
        this.authProviderFactory = checkNotNull(authProviderFactory);
        this.appCheckTokenProviderFactory = checkNotNull(appCheckTokenProviderFactory);
        this.metadataProvider = metadataProvider;
        this.settings = new FirebaseFirestoreSettings.Builder().build();
        this.asyncQueue = new AsyncQueue();
    }

    synchronized FirebaseFirestoreSettings getFirestoreSettings() {
        return settings;
    }

    synchronized void setFirestoreSettings(@NonNull FirebaseFirestoreSettings settings) {
        settings = mergeEmulatorSettings(settings, this.emulatorSettings);

        checkNotNull(settings, "Provided settings must not be null.");

        // As a special exception, don't throw if the same settings are passed repeatedly. This
        // should make it simpler to get a Firestore instance in an activity.
        if (client != null && !this.settings.equals(settings)) {
            throw new IllegalStateException(
                    "FirebaseFirestore has already been started and its settings can no longer be changed. "
                            + "You can only call setFirestoreSettings() before calling any other methods on a "
                            + "FirebaseFirestore object.");
        }

        this.settings = settings;
    }

    /**
     * Modifies this FirebaseDatabase instance to communicate with the Cloud Firestore emulator.
     *
     * <p>Note: Call this method before using the instance to do any database operations.
     *
     * @param host the emulator host (for example, 10.0.2.2)
     * @param port the emulator port (for example, 8080)
     */
    synchronized void useEmulator(@NonNull String host, int port) {
        if (client != null) {
            throw new IllegalStateException(
                    "Cannot call useEmulator() after instance has already been initialized.");
        }

        this.emulatorSettings = new EmulatedServiceSettings(host, port);
        this.settings = mergeEmulatorSettings(this.settings, this.emulatorSettings);
    }

    /**
     * Sets any custom settings used to configure this {@code FirebaseFirestore} object. This method
     * can only be called before calling any other methods on this object.
     */
    private static FirebaseFirestoreSettings mergeEmulatorSettings(
            @NonNull FirebaseFirestoreSettings settings,
            @Nullable EmulatedServiceSettings emulatorSettings) {
        if (emulatorSettings == null) {
            return settings;
        }

        if (!FirebaseFirestoreSettings.DEFAULT_HOST.equals(settings.getHost())) {
            Logger.warn(
                    "FirestoreClientProvider",
                    "Host has been set in FirebaseFirestoreSettings and useEmulator, emulator host will be used.");
        }

        return new FirebaseFirestoreSettings.Builder(settings)
                .setHost(emulatorSettings.getHost() + ":" + emulatorSettings.getPort())
                .setSslEnabled(false)
                .build();
    }

    private synchronized FirestoreClient newClient() {
        DatabaseInfo databaseInfo =
                new DatabaseInfo(databaseId, persistenceKey, settings.getHost(), settings.isSslEnabled());

        CredentialsProvider<User> authProvider = authProviderFactory.get();
        CredentialsProvider<String> appCheckProvider = appCheckTokenProviderFactory.get();
        FirestoreClient client = new FirestoreClient(
                databaseInfo,
                settings,
                authProvider,
                appCheckProvider,
                asyncQueue);

        Datastore datastore = new Datastore(
                databaseInfo, asyncQueue, authProvider, appCheckProvider, context, metadataProvider);

        client.setClearPersistenceCallback(sessionToken -> {
            synchronized (this) {
                // If this callback is attached to an old FirestoreClient, we want to ignore it.
                if (this.client == client) {
                    this.sessionToken = sessionToken;
                    clearPersistence();
                }
            }
        });

        ComponentProvider componentProvider = settings.isPersistenceEnabled()
                ? new SQLiteComponentProvider()
                : new MemoryComponentProvider();

        client.start(
                context,
                componentProvider,
                datastore);

        if (sessionToken != null) {
            client.setSessionToken(sessionToken);
        }

        if (networkEnabled) {
            client.enableNetwork();
        }

        return client;
    }

    @NonNull
    private synchronized ComponentProvider getComponentProvider() {
        return settings.isPersistenceEnabled()
                ? new SQLiteComponentProvider()
                : new MemoryComponentProvider();
    }


    void ensureClientConfigured() {
        if (client == null) {
            ensureClientConfiguredInternal();
        }
    }

    synchronized private void ensureClientConfiguredInternal() {
        if (client == null) {
            client = newClient();
        }
    }

    /**
     * To facilitate calls to FirestoreClient without risk of FirestoreClient being terminated
     * ir restarted by {@link #clearPersistence()} mid call.
     */
    synchronized <T> T safeCall(Function<FirestoreClient, T> call) {
        ensureClientConfiguredInternal();
        return call.apply(client);
    }

    /**
     * To facilitate calls to FirestoreClient without risk of FirestoreClient being terminated
     * ir restarted by {@link #clearPersistence()} mid call.
     */
    synchronized void safeCallVoid(Consumer<FirestoreClient> call) {
        ensureClientConfiguredInternal();
        call.accept(client);
    }

    /**
     * Shuts down the AsyncQueue and releases resources after which no progress will ever be made
     * again.
     */
    synchronized Task<Void> terminate() {
        // The client must be initialized to ensure that all subsequent API usage throws an exception.
        ensureClientConfiguredInternal();

        Task<Void> terminate = client.terminate();

        // Will cause the executor to de-reference all threads, the best we can do
        asyncQueue.terminate();

        return terminate;
    }

    synchronized Task<Void> clearPersistence() {
        // This will block asyncQueue, prevent a new client from being started.
        if (client == null || client.isTerminated()) {
            return clearPersistence(asyncQueue.getExecutor());
        } else {
            client.terminate();
            asyncQueue = asyncQueue.reincarnate();
            Task<Void> task = clearPersistence(asyncQueue.getExecutor());
            client = newClient();
            return task;
        }
    }

    private Task<Void> clearPersistence(Executor executor) {
        final TaskCompletionSource<Void> source = new TaskCompletionSource<>();
        executor.execute(() -> {
            try {
                SQLitePersistence.clearPersistence(context, databaseId, persistenceKey);
                source.setResult(null);
            } catch (FirebaseFirestoreException e) {
                source.setException(e);
            }
        });
        return source.getTask();
    }

    AsyncQueue getAsyncQueue() {
        return asyncQueue;
    }

    synchronized Task<Void> enableNetwork() {
        networkEnabled = true;
        ensureClientConfiguredInternal();
        return client.enableNetwork();
    }

    synchronized Task<Void> disableNetwork() {
        networkEnabled = false;
        ensureClientConfiguredInternal();
        return client.disableNetwork();
    }
}
