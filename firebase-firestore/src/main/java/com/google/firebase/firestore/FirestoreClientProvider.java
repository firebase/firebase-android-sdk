// Copyright 2024 Google LLC
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

package com.google.firebase.firestore;

import androidx.annotation.GuardedBy;
import androidx.core.util.Consumer;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.core.FirestoreClient;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Function;

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

    boolean isConfigured() {
        return client != null;
    }

    synchronized void ensureConfigured() {
        if (!isConfigured()) {
            client = clientFactory.apply(asyncQueue);
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
        if (client != null && !client.isTerminated()) {
            client.terminate();
        }
        Executor executor = command -> asyncQueue.enqueueAndForgetEvenAfterShutdown(command);
        return call.apply(executor);
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
        asyncQueue.shutdown();

        return terminate;
    }
}
