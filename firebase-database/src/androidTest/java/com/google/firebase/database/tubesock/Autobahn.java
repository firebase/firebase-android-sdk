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

import java.io.IOException;
import java.util.logging.Logger;

/**
 * This test is used to run the autobahn websocket protocol compliance test suite. {@see
 * http://autobahn.ws/testsuite}
 */
public class Autobahn {
  private static Logger LOGGER = Logger.getLogger(Autobahn.class.getName());

  private static final Boolean BLOCK_ON_START = false;

  private static void runTest(int i) {
    System.out.println("Running test #" + i);
    TestClient client = new TestClient();
    try {
      client.startTest("" + i);
    } catch (WebSocketException e) {
      LOGGER.log(WARNING, "unexpected error", e);
    } catch (InterruptedException e) {
      LOGGER.log(WARNING, "unexpected error", e);
    }
  }

  private static void updateResults() {
    UpdateClient updateClient = new UpdateClient();
    try {
      updateClient.update();
    } catch (WebSocketException e) {
      LOGGER.log(WARNING, "unexpected error", e);
    } catch (InterruptedException e) {
      LOGGER.log(WARNING, "unexpected error", e);
    }
  }

  private static void runSuite() {
    runSuite(1, 301);
  }

  private static void runSuite(int from, int to) {
    for (int i = from; i <= to; ++i) {
      runTest(i);
    }
    updateResults();
  }

  public static void main(String[] args) {
    if (BLOCK_ON_START) {
      // Block to allow time to attach a profiler or debugger or whatever
      try {
        System.in.read(new byte[1]);
      } catch (IOException e) {
        LOGGER.log(WARNING, "unexpected error", e);
      }
    }
    runSuite();
  }
}
