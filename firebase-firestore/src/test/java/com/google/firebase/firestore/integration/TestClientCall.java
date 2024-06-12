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

import android.util.Pair;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

import java.util.ArrayList;
import java.util.List;

import io.grpc.ClientCall;
import io.grpc.Metadata;

/**
 * Construct a ClientCall test harness.
 */
public class TestClientCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {

    public Metadata headers;
    public Listener<RespT> listener;
    private int sentIndex = -1;
    private List<TaskCompletionSource<ReqT>> requests = new ArrayList<>();
    private TaskCompletionSource<TestClientCall<ReqT, RespT>> startTask = new TaskCompletionSource<>();
    private TaskCompletionSource<Pair<String, Throwable>> cancelTask = new TaskCompletionSource<>();

    /**
     * Construct a ClientCall test harness.
     *
     * The {@code #headers} and {@code #listener} will be populated when {@code #startTask}
     * completes.
     *
     * @param startTask Will complete when ClientCall has start callback invoked.
     */
    public TestClientCall(TaskCompletionSource<TestClientCall<ReqT, RespT>> startTask) {
        this.startTask = startTask;
    }

    @Override
    public void start(Listener<RespT> responseListener, Metadata headers) {
        this.listener = responseListener;
        this.headers = headers;
        startTask.setResult(this);
    }

    @Override
    public void request(int numMessages) {
    }

    @Override
    public void cancel(@Nullable String message, @Nullable Throwable cause) {
        cancelTask.setResult(Pair.create(message, cause));
    }

    @Override
    public void halfClose() {
    }

    @Override
    public void sendMessage(ReqT message) {
        final TaskCompletionSource<ReqT> sourceTask;
        synchronized (requests) {
            sentIndex++;
            if (requests.size() > sentIndex) {
                sourceTask = requests.get(sentIndex);
            } else {
                sourceTask = new TaskCompletionSource<>();
                requests.add(sourceTask);
            }
        }
        sourceTask.setResult(message);
    }

    public Task<ReqT> getRequest(int index) {
        synchronized (requests) {
            while (requests.size() <= index) {
                requests.add(new TaskCompletionSource<>());
            }
            return requests.get(index).getTask();
        }
    }
}
