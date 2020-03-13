// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class ByteSessionStreamProvider implements NativeSessionStreamProvider {
    private final byte[] bytes;
    private final String name;

    public ByteSessionStreamProvider(@NonNull String name, @Nullable byte[] bytes) {
        this.name = name;
        this.bytes = bytes;
    }

    @NonNull public String getName() {
        return this.name;
    }

    @Override
    @Nullable public InputStream getStream() {
        return (bytes != null && bytes.length > 0) ? new ByteArrayInputStream(bytes) : null;
    }
}
