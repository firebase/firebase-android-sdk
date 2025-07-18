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
package com.google.firebase.messaging.shadows;

import static org.robolectric.util.reflector.Reflector.reflector;

import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.reflector.Direct;
import org.robolectric.util.reflector.ForType;

@Implements(Messenger.class)
public class ShadowMessenger {

  @RealObject private Messenger realMessenger;

  private static RemoteException sendException = null;

  @ForType(Messenger.class)
  interface MessengerReflector {
    @Direct
    void send(Messenger messenger, Message message) throws RemoteException;
  }

  @Implementation
  protected void send(Message message) throws RemoteException {
    if (sendException != null) {
      throw sendException;
    } else {
      reflector(Messenger.class, realMessenger).send(message);
    }
  }

  public static void setSendException(RemoteException sendException) {
    ShadowMessenger.sendException = sendException;
  }
}
