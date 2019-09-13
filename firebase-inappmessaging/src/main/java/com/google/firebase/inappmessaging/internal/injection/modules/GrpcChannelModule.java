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

package com.google.firebase.inappmessaging.internal.injection.modules;

import dagger.Module;
import dagger.Provides;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Bindings for grpc channel
 *
 * @hide
 */
@Module
public class GrpcChannelModule {
  @Provides
  @Named("host")
  @Singleton
  public String providesServiceHost() {
    return "firebaseinappmessaging.googleapis.com";
  }

  @Provides
  @Singleton
  public Channel providesGrpcChannel(@Named("host") String host) {
    return ManagedChannelBuilder.forTarget(host).build();
  }
}
