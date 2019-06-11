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

package com.example.firebase.fiamui;

import android.graphics.Bitmap;
import android.util.Log;
import androidx.test.runner.screenshot.ScreenCapture;
import androidx.test.runner.screenshot.Screenshot;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** A set of utility methods used to take screenshots from tests on an android virtual device. */
public class ScreenShotter {
  private static final String FILE_NAME_DELIMITER = "-";
  private static final String IMAGE_TYPE = "jpg";
  private static final String LOG_TAG = "cloud_screenshotter";
  private static final String SCREENSHOT_PATH = "/sdcard/screenshots/";
  private static String lastClassName = "";
  private static String lastMethodName = "";
  private static AtomicInteger counter = new AtomicInteger(0);

  /**
   * Immediately take a screenshot with the given name.
   *
   * @param name The name of the screenshot
   */
  public static void takeScreenshot(String name) {
    String fileName = getScreenshotFileName(name);
    try {
      takeScreenshotInternal(fileName);
    } catch (Exception e) {
      Log.e(LOG_TAG, "Exception taking screenshot: " + e.toString());
    }
  }

  /**
   * Take a screenshot on a device and save it as the specified file name.
   *
   * @param fileName name of file in which to save the screenshot.
   */
  private static void takeScreenshotInternal(final String fileName) {

    ScreenCapture capture = Screenshot.capture();
    Bitmap bitmap = capture.getBitmap();
    File imageFolder = new File(SCREENSHOT_PATH);
    imageFolder.mkdirs();

    File imageFile = new File(imageFolder, fileName + "." + IMAGE_TYPE);
    OutputStream out = null;

    try {
      out = new FileOutputStream(imageFile);
      bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
      out.flush();
    } catch (IOException e) {
      Log.e(LOG_TAG, "Exception taking screenshot: " + e.toString());
    } finally {
      try {
        if (out != null) {
          out.close();
        }
      } catch (IOException e) {
        Log.e(LOG_TAG, "There was an error closing the FileOutputStream " + e.toString());
      }
    }
  }

  /**
   * Construct a screenshot file name from the specified screenshot name
   *
   * @param screenshotName name for this screenshot
   * @return screenshot file name with the following format <fully qualified class name>-<test
   *     method name>-<screenshot name>-<step number>
   */
  private static String getScreenshotFileName(String screenshotName) {
    for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
      String elementClassName = element.getClassName();
      String elementMethodName = element.getMethodName();
      if (elementMethodName.startsWith("test")
          || isJUnit4Test(elementClassName, elementMethodName)) {
        if (!elementClassName.equals(lastClassName) || !elementMethodName.equals(lastMethodName)) {
          counter = new AtomicInteger(0);
        }
        lastClassName = elementClassName;
        lastMethodName = elementMethodName;
        String filename =
            elementClassName
                + FILE_NAME_DELIMITER
                + elementMethodName
                + FILE_NAME_DELIMITER
                + screenshotName
                + FILE_NAME_DELIMITER
                + counter.incrementAndGet();

        return filename;
      }
    }

    lastClassName = "";
    lastMethodName = "";
    return "UnknownTestClass"
        + FILE_NAME_DELIMITER
        + "unknownTestMethod"
        + FILE_NAME_DELIMITER
        + screenshotName
        + FILE_NAME_DELIMITER
        + counter.incrementAndGet();
  }

  private static boolean isJUnit4Test(String elementClassName, String elementMethodName) {
    try {
      Class<?> clazz = Class.forName(elementClassName);
      for (Method method : clazz.getMethods()) {
        if (method.getName().equals(elementMethodName)
            && method.getAnnotation(Test.class) != null) {
          return true;
        }
      }
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.getName().equals(elementMethodName)
            && method.getAnnotation(Test.class) != null) {
          return true;
        }
      }
    } catch (ClassNotFoundException e) {
      return false;
    }
    return false;
  }
}
