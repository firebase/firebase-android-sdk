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
package com.google.firebase.messaging.testing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Test helper rule for creating an in memory image server. */
public class TestImageServer implements TestRule {

  private TestHttpServer httpServer;

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          httpServer = new TestHttpServer();
          httpServer.start();
          base.evaluate();
        } finally {
          httpServer.shutdown();
        }
      }
    };
  }

  public String serveBitmap(String path, Bitmap bitmap) {
    return serveByteArray(path, bitmapToByteArray(bitmap));
  }

  public String serveByteArray(String path, byte[] bytes) {
    return serveEntity(path, new ByteArrayEntity(bytes));
  }

  public String serveByteArrayWithoutContentLength(String path, byte[] bytes) {
    return serveEntity(
        path,
        new ByteArrayEntity(bytes) {
          @Override
          public long getContentLength() {
            return -1; // Negative number indicates unknown content length
          }
        });
  }

  private String serveEntity(String path, HttpEntity entity) {
    httpServer.registerHandler(
        path,
        (request, response, context) -> {
          response.setEntity(entity);
          response.setStatusCode(200);
        });
    return createUrlPath(path);
  }

  @SuppressWarnings("InputStreamSlowMultibyteRead")
  public String serveBitmapAfterDelay(String path, int delaySeconds, Bitmap bitmap) {
    byte[] bitmapBytes = bitmapToByteArray(bitmap);
    ByteArrayInputStream bitmapInputStream = new ByteArrayInputStream(bitmapBytes);

    httpServer.registerHandler(
        path,
        (request, response, context) -> {
          response.setEntity(
              new InputStreamEntity(
                  new InputStream() {
                    boolean slept = false;

                    @Override
                    public int read() {
                      // Sleep the first time read() is called
                      if (!slept) {
                        try {
                          TimeUnit.SECONDS.sleep(delaySeconds);
                        } catch (InterruptedException e) {
                          throw new AssertionError(e);
                        }
                        slept = true;
                      }
                      // Then delegate to real bitmap input stream
                      return bitmapInputStream.read();
                    }
                  },
                  /* length= */ bitmapBytes.length));
          response.setStatusCode(200);
        });
    return createUrlPath(path);
  }

  public String serveError(String path) {
    httpServer.registerHandler(
        path,
        (request, response, context) -> {
          response.setStatusCode(404);
        });
    return createUrlPath(path);
  }

  public static Bitmap getBitmapFromResource(Context context, int id) {
    return BitmapFactory.decodeResource(context.getResources(), id);
  }

  private static byte[] bitmapToByteArray(Bitmap bitmap) {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    // Use PNG as it is lossless
    bitmap.compress(CompressFormat.PNG, /* quality= */ 100, byteStream);
    return byteStream.toByteArray();
  }

  private String createUrlPath(String path) {
    return "http://localhost:" + httpServer.getPort() + path;
  }
}
