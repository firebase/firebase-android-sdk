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

package com.google.apksize;

import android.content.Context;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.android.datatransport.cct.CCTDestination;
import com.google.android.datatransport.runtime.TransportRuntime;

public class TransportSample implements SampleCode {

  @Override
  public void runSample(Context context) {

    TransportRuntime.initialize(context);

    TransportFactory f =
        TransportRuntime.getInstance().newFactory(CCTDestination.INSTANCE);
    Transport<String> t = f.getTransport("123", String.class, String::getBytes);
    t.send(Event.ofData("Hello"));
    t.send(Event.ofTelemetry(1, "World"));
  }
}
