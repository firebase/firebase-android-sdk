package com.google.firebase.firestore;

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import androidx.annotation.GuardedBy;
import androidx.core.util.Consumer;

import com.google.android.gms.tasks.Task;
import com.google.common.base.Function;
import com.google.firebase.firestore.core.FirestoreClient;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.protobuf.ByteString;

import java.util.concurrent.Executor;

/**
 * The FirestoreClientProvider handles the life cycle of FirestoreClients.
 */
final class FirestoreClientProvider {

    private final Function<AsyncQueue, FirestoreClient> clientFactory;
    @GuardedBy("this")
    private FirestoreClient client;

    @GuardedBy("this")
    private AsyncQueue asyncQueue;

    FirestoreClientProvider(Function<AsyncQueue, FirestoreClient> clientFactory) {
        this.clientFactory = clientFactory;
        this.asyncQueue = new AsyncQueue();
    }

    private synchronized void makeNewClient() {
        client = clientFactory.apply(asyncQueue);
    }

    boolean isConfigured() {
        return client != null;
    }

    synchronized void ensureConfigured() {
        if (!isConfigured()) {
            makeNewClient();
        }
    }

    synchronized void ifCurrentClient(FirestoreClient client, Runnable runnable) {
        if (this.client == client) {
            runnable.run();
        }
    }

    /**
     * To facilitate calls to FirestoreClient without risk of FirestoreClient being terminated
     * or restarted mid call.
     */
    synchronized <T> T call(Function<FirestoreClient, T> call) {
        ensureConfigured();
        return call.apply(client);
    }

    /**
     * To facilitate calls to FirestoreClient without risk of FirestoreClient being terminated
     * or restarted mid call.
     */
    synchronized void procedure(Consumer<FirestoreClient> call) {
        ensureConfigured();
        call.accept(client);
    }

    synchronized <T> T executeWhileShutdown(Function<Executor, T> call) {
        // This will block asyncQueue, prevent a new client from being started.
        if (client == null || client.isTerminated()) {
            return call.apply(asyncQueue.getExecutor());
        } else {
            client.terminate();
            asyncQueue = asyncQueue.reincarnate();
            T result = call.apply(asyncQueue.getExecutor());
            makeNewClient();
            return result;
        }
    }

    /**
     * Shuts down the AsyncQueue and releases resources after which no progress will ever be made
     * again.
     */
    synchronized Task<Void> terminate() {
        // The client must be initialized to ensure that all subsequent API usage throws an exception.
        ensureConfigured();

        Task<Void> terminate = client.terminate();

        // Will cause the executor to de-reference all threads, the best we can do
        asyncQueue.terminate();

        return terminate;
    }

    AsyncQueue getAsyncQueue() {
        return asyncQueue;
    }
}
