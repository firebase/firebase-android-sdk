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

package com.google.firebase.storage;

import android.support.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import junit.framework.Assert;
import org.robolectric.RuntimeEnvironment;

/** Test helpers. */
public class TestUtil {

  static FirebaseApp createApp() {
    return FirebaseApp.initializeApp(
        RuntimeEnvironment.application.getApplicationContext(),
        new FirebaseOptions.Builder()
            .setApiKey("AIzaSyCkEhVjf3pduRDt6d1yKOMitrUEke8agEM")
            .setApplicationId("fooey")
            .setStorageBucket("project-5516366556574091405.appspot.com")
            .build());
    // Point to staging:
    // NetworkRequest.sNetworkRequestUrl = "https://staging-firebasestorage.sandbox.googleapis"
    // + ".com/v0";
    // NetworkRequest.sUploadUrl = "https://staging-firebasestorage.sandbox.googleapis.com/v0/b/";
  }

  static void verifyTaskStateChanges(
      String testName, TestDownloadHelper.StreamDownloadResponse response) {
    ClassLoader classLoader = TestUtil.class.getClassLoader();

    System.out.println("Verifying task file.");
    String filename = "assets/" + testName + "_task.txt";
    InputStream inputStream = classLoader.getResourceAsStream(filename);
    verifyTaskStateChanges(inputStream, response.mainTask.toString());

    System.out.println("Verifying background file.");
    filename = "assets/" + testName + "_background.txt";
    inputStream = classLoader.getResourceAsStream(filename);
    verifyTaskStateChanges(inputStream, response.backgroundTask.toString());
  }

  static void verifyTaskStateChanges(String testName, String contents) {
    ClassLoader classLoader = TestUtil.class.getClassLoader();
    String filename = "assets/" + testName + "_task.txt";

    InputStream inputStream = classLoader.getResourceAsStream(filename);
    verifyTaskStateChanges(inputStream, contents);
  }

  private static void verifyTaskStateChanges(@Nullable InputStream inputStream, String contents) {
    if (inputStream == null) {
      if (!contents.isEmpty()) {
        System.err.println("Original:");
        System.err.println("New:");
        System.err.println(contents);
        Assert.fail("Content provided, but Robolectric file is missing.");
      }
      return;
    }

    StringBuilder baselineContents = new StringBuilder();
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
      // skip to first <new>
      String line;
      while ((line = br.readLine()) != null) {
        baselineContents.append(line).append("\n");
      }
      inputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    try {
      BufferedReader current = new BufferedReader(new StringReader(contents));
      BufferedReader baseline = new BufferedReader(new StringReader(baselineContents.toString()));
      String originalLine;
      String newLine;
      // skip to first <new>
      int line = 0;
      while ((originalLine = baseline.readLine()) != null
          && (newLine = current.readLine()) != null) {
        if (originalLine.length() == newLine.length() + 1
            && originalLine.charAt(originalLine.length() - 1) == ' ') {
          // fix trailing spaces
          originalLine = originalLine.substring(0, originalLine.length() - 1);
        }
        if (originalLine.contains("currentState:")) {
          if (!originalLine.equals(newLine)) {
            System.out.println("Warning!!! Line " + line + " is different.");
          }
        } else {
          if (!originalLine.equals(newLine)) {
            System.err.println("Original:");
            System.err.println(baselineContents.toString());
            System.err.println("New:");
            System.err.println(contents);
          }
          Assert.assertEquals("line:" + line + " is different.", originalLine, newLine);
        }
        line++;
      }
      current.close();
      baseline.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    try {
      inputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
