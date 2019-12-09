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

package com.google.firebase.database.tubesock;

import static java.util.logging.Level.WARNING;

import com.google.firebase.database.IntegrationTestHelpers;
import java.net.URI;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

/** This test is just a quick smoke test to make sure we can connect to a wss endpoint. */
public class FirebaseClient {

  private static Logger LOGGER = Logger.getLogger(FirebaseClient.class.getName());

  private WebSocket client;
  private Semaphore semaphore;

  private class Handler implements WebSocketEventHandler {

    @Override
    public void onOpen() {
      System.out.println("Opened socket");
      client.close();
    }

    @Override
    public void onMessage(WebSocketMessage message) {}

    @Override
    public void onClose() {
      System.out.println("Closed socket");
      semaphore.release(1);
    }

    @Override
    public void onError(WebSocketException e) {
      LOGGER.log(WARNING, "unexpected error", e);
    }

    @Override
    public void onLogMessage(String msg) {
      System.err.println(msg);
    }
  }

  public void start() throws WebSocketException, InterruptedException {
    semaphore = new Semaphore(0);
    URI uri = URI.create("wss://gsoltis.firebaseio-demo.com/.ws?v=5");
    client = new WebSocket(IntegrationTestHelpers.getContext(0).getConnectionContext(), uri);
    client.setEventHandler(new Handler());
    client.connect();
    semaphore.acquire(1);
    client.blockClose();
  }

  public static void main(String[] args) {
    try {
      new FirebaseClient().start();
    } catch (InterruptedException e) {
      LOGGER.log(WARNING, "unexpected error", e);
    }
  }
}
