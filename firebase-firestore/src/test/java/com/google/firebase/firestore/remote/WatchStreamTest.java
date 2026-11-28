// Copyright 2026 Google LLC
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

package com.google.firebase.firestore.remote;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static java.util.Collections.emptyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.util.Log;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChange;
import com.google.firebase.firestore.remote.WatchChange.WatchTargetChangeType;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firestore.v1.ListenResponse;
import com.google.firestore.v1.TargetChange;
import com.google.firestore.v1.TargetChange.TargetChangeType;
import com.google.protobuf.Timestamp;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class WatchStreamTest {
  private static final Timestamp READ_TIME = Timestamp.newBuilder().setSeconds(1).build();

  private WatchStream.Callback callback;
  private WatchStream watchStream;

  @Before
  public void setUp() {
    callback = mock(WatchStream.Callback.class);
    RemoteSerializer serializer = new RemoteSerializer(DatabaseId.forDatabase("p", "d"));
    watchStream = new WatchStream(/* channel= */ null, new AsyncQueue(), serializer, callback);
  }

  @Test
  public void ignoresTargetChangeWithUnknownTargetChangeType() {
    // Without target IDs and with a read time, this response would raise a global snapshot if its
    // target change type were known.
    ListenResponse response =
        ListenResponse.newBuilder()
            .setTargetChange(
                TargetChange.newBuilder().setTargetChangeTypeValue(99).setReadTime(READ_TIME))
            .build();

    watchStream.onNext(response);

    verifyNoInteractions(callback);
    assertWarningLogged("target change type: 99");
  }

  @Test
  public void ignoresListenResponseWithoutResponseType() {
    watchStream.onNext(ListenResponse.getDefaultInstance());

    verifyNoInteractions(callback);
    assertWarningLogged("response type: RESPONSETYPE_NOT_SET");
  }

  @Test
  public void forwardsWatchChangesAfterIgnoringUnknownOne() {
    watchStream.onNext(ListenResponse.getDefaultInstance());
    watchStream.onNext(
        ListenResponse.newBuilder()
            .setTargetChange(
                TargetChange.newBuilder()
                    .setTargetChangeType(TargetChangeType.NO_CHANGE)
                    .setReadTime(READ_TIME))
            .build());

    verify(callback)
        .onWatchChange(
            version(1, 0), new WatchTargetChange(WatchTargetChangeType.NoChange, emptyList()));
    verifyNoMoreInteractions(callback);
  }

  private static void assertWarningLogged(String expectedText) {
    List<ShadowLog.LogItem> logs = ShadowLog.getLogsForTag("Firestore");
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).type).isEqualTo(Log.WARN);
    assertThat(logs.get(0).msg).contains(expectedText);
  }
}
