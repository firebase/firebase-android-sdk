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

package com.google.firebase.heartbeatinfo;

import androidx.annotation.NonNull;

/**
 * Class provides information about heartbeats.
 *
 * <p>This exposes a function which returns the `HeartBeatCode` if both sdk heartbeat and global
 * heartbeat needs to sent then HeartBeat.COMBINED is returned. if only sdk heart beat needs to be
 * sent then HeartBeat.SDK is returned. if only global heart beat needs to be sent then
 * HeartBeat.GLOBAL is returned. if no heart beat needs to be sent then HeartBeat.NONE is returned.
 */
public interface HeartBeatInfo {
  enum HeartBeat {
    NONE(0),
    SDK(1),
    GLOBAL(2),
    COMBINED(3);

    private final int code;

    HeartBeat(int code) {
      this.code = code;
    }

    public int getCode() {
      return this.code;
    }
  }

  @NonNull
  HeartBeat getHeartBeatCode(@NonNull String heartBeatTag);
}
