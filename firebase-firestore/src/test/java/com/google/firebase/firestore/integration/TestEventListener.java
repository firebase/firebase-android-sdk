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

package com.google.firebase.firestore.integration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * EventListener test harness.
 */
class TestEventListener<T> implements EventListener<T>, Iterable<Task<T>> {

    private int eventCount = 0;
    private TaskCompletionSource<T> next;
    private List<TaskCompletionSource<T>> events = new ArrayList<>();

    public TestEventListener() {
        next = computeIfAbsentIndex(eventCount);
    }

    private TaskCompletionSource<T> computeIfAbsentIndex(int i) {
        while (events.size() <= i) {
            events.add(new TaskCompletionSource<>());
        }
        return events.get(i);
    }

    @Override
    public synchronized void onEvent(@Nullable T value, @Nullable FirebaseFirestoreException error) {
        if (error == null) {
            next.setResult(value);
        } else {
            next.setException(error);
        }
        eventCount++;
        next = computeIfAbsentIndex(eventCount);
    }

    /**
     * Get result from {@code #onEvent}. Task that that completes with event result.
     * @param index 0 indexed sequence of {@code #onEvent} invocations.
     * @return Task that completes when {@code #onEvent} is called.
     */
    @NonNull
    public synchronized Task<T> get(int index) {
        return computeIfAbsentIndex(index).getTask();
    }

    /**
     * Iterates over {@code #onEvent}.
     *
     * This will return the same as calling {@code #get(0..n)}.
     * The Iterator is thread safe.
     * Iteration will stop upon event task that fails or cancels.
     *
     * A loop that waits for event task to complete before getting next event task will continue to
     * iterate indefinitely. The next event task is not available until previous task is successful.
     * Attempting to iterate past an event task that is not yet successful will throw
     * {#code NoSuchElementException} and {@code #hasNext()} will be false. In this way, iteration
     * can complete without blocking for already received events.
     *
     * @return Iterator of Tasks that complete when {@code #onEvent} is called.
     */
    @NonNull
    @Override
    public Iterator<Task<T>> iterator() {
        return new Iterator<Task<T>>() {

            private int i = -1;
            private Task<T> current;

            @Override
            public synchronized boolean hasNext() {
                // We always return first, and continue to return tasks so long as previous
                // is successful. A task that hasn't completed, will also mark the end of
                // iteration.
                return i < 0 || current.isSuccessful();
            }

            @Override
            public synchronized Task<T> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                i++;
                current = get(i);
                return current;
            }
        };
    }
}
