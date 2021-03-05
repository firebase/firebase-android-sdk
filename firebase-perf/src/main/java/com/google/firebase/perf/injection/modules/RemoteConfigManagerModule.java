// Copyright 2021 Google LLC
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

package com.google.firebase.perf.injection.modules;

import androidx.annotation.Nullable;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import dagger.Module;
import dagger.Provides;

@Module
public class RemoteConfigManagerModule {

    @Provides
    FirebaseRemoteConfig providesFirebaseRemoteConfig() {
        return null;
    }

    @Provides
    Executor providesExecutor() {
        return new ThreadPoolExecutor(
                /* corePoolSize= */ 0,
                /* maximumPoolSize= */ 1,
                /* keepAliveTime= */ 0L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
    }
}
