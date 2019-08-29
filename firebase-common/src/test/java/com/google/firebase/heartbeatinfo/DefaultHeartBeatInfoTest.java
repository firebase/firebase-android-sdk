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

package com.google.firebase.heartbeatinfo;

import android.content.Context;
import android.content.SharedPreferences;


import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DefaultHeartBeatInfoTest {
    private final String testSdk = "fire-test";
    private final SharedPreferences sharedPreferences = mock(SharedPreferences.class);
    private HeartBeatInfo heartBeatInfo;
    @Before
    public void before() {
        final Context context = mock(Context.class);
        when(context.getSharedPreferences(anyString(), any())).thenReturn(sharedPreferences);
        heartBeatInfo = new DefaultHeartBeatInfo(context);
    }

    @Test
    public void getHeartBeatCode_noHeartBeat() {
        assertThat(heartBeatInfo.getHeartBeatCode(testSdk)).isEqualTo(0);

    }

}