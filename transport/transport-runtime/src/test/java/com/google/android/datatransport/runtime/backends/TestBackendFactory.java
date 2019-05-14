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

package com.google.android.datatransport.runtime.backends;

import static org.mockito.Mockito.mock;

import com.google.android.datatransport.runtime.EventInternal;

public class TestBackendFactory implements BackendFactory {
  @Override
  public TransportBackend create(CreationContext creationContext) {
    return mock(TestBackend.class);
  }

  public static class TestBackend implements TransportBackend {
    @Override
    public EventInternal decorate(EventInternal event) {
      return event;
    }

    @Override
    public BackendResponse send(BackendRequest backendRequest) {
      return BackendResponse.ok(0);
    }
  }
}
