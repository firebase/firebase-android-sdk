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
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.Iterator;

/**
 * EventListener test harness.
 */
class TestEventListener<T>implements EventListener<T> {

    AsyncTaskAccumulator<T> events = new AsyncTaskAccumulator<>();

    @Override
    public synchronized void onEvent(@Nullable T value, @Nullable FirebaseFirestoreException error) {
        if (error == null) {
            events.onResult(value);
        } else {
            events.onException(error);
        }
    }

    @NonNull
    public synchronized Task<T> get(int index) {
        return events.get(index);
    }

    @NonNull
    public Iterator<Task<T>> iterator() {
        return events.iterator();
    }
}
