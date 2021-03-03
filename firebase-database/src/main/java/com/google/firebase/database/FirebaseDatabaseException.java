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

package com.google.firebase.database;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseException;

import static com.google.firebase.components.Preconditions.checkNotNull;

public class FirebaseDatabaseException extends FirebaseException {

    public enum Code {
        Ok(0);

        private final int value;

        Code(int value) {this.value = value;}
    }

    private final Code code;

    public FirebaseDatabaseException(@NonNull String detailMessage, @NonNull Code code) {
        super(detailMessage);
        checkNotNull(detailMessage, "Provided message must not be null.");
        this.code = checkNotNull(code, "Provided code must not be null.");
    }
}
