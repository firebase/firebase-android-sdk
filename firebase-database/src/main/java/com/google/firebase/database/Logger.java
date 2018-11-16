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

package com.google.firebase.database;

import com.google.firebase.annotations.PublicApi;

/** This interface is used to setup logging for Firebase Database. */
@PublicApi
public interface Logger {

  /** The log levels used by the Firebase Database library */
  @PublicApi
  enum Level {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    NONE
  };
}
