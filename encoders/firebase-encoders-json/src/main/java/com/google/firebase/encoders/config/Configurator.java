// Copyright 2019 Google LLC
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

package com.google.firebase.encoders.config;

import androidx.annotation.NonNull;

/**
 * Callback that is accepted by encoder builders to configure them.
 *
 * <p>Example:
 *
 * <pre>{@code
 * DataEncoder encoder = new JsonDataEncoderBuilder()
 *     .configureWith(cfg ->
 *         cfg.registerEncoder(Foo.class, new FooEncoder())
 *            .registerEncoder(Bar.class, new BarEncoder()))
 *     .build();
 * }</pre>
 */
public interface Configurator {
  void configure(@NonNull EncoderConfig<?> configuration);
}
