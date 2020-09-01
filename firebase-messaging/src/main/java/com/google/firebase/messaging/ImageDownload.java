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
package com.google.firebase.messaging;

import static com.google.android.gms.common.internal.Preconditions.checkNotNull;
import static com.google.firebase.messaging.Constants.TAG;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Executor;

/** Abstraction around downloading an image in a background executor. */
class ImageDownload implements Closeable {

  /** Maximum image size to download in bytes (1 MiB). */
  private static final int MAX_IMAGE_SIZE_BYTES = 1024 * 1024;

  private final URL url;
  @Nullable private Task<Bitmap> task;
  @Nullable private volatile InputStream connectionInputStream;

  @Nullable
  public static ImageDownload create(String imageUrl) {
    if (TextUtils.isEmpty(imageUrl)) {
      return null;
    }
    try {
      return new ImageDownload(new URL(imageUrl));
    } catch (MalformedURLException e) {
      Log.w(TAG, "Not downloading image, bad URL: " + imageUrl);
      return null;
    }
  }

  private ImageDownload(URL url) {
    this.url = url;
  }

  public void start(Executor executor) {
    task = Tasks.call(executor, this::blockingDownload);
  }

  public Task<Bitmap> getTask() {
    return checkNotNull(task); // will throw if getTask() is called without a call to start()
  }

  public Bitmap blockingDownload() throws IOException {
    Log.i(TAG, "Starting download of: " + url);

    byte[] imageBytes = blockingDownloadBytes();
    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, /* offset= */ 0, imageBytes.length);
    if (bitmap == null) {
      throw new IOException("Failed to decode image: " + url);
    }

    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "Successfully downloaded image: " + url);
    }
    return bitmap;
  }

  @SuppressWarnings("UrlConnectionChecker")
  private byte[] blockingDownloadBytes() throws IOException {
    URLConnection connection = url.openConnection();

    // First check the content length, and fail if it's too high
    int contentLength = connection.getContentLength();
    if (contentLength > MAX_IMAGE_SIZE_BYTES) {
      throw new IOException("Content-Length exceeds max size of " + MAX_IMAGE_SIZE_BYTES);
    }

    // Now actually try to download the content
    byte[] bytes;
    try (InputStream connectionInputStream = connection.getInputStream()) {
      // Save to a field so that it can be closed on timeout
      this.connectionInputStream = connectionInputStream;

      // Read one byte over the limit so we can tell if the data is too big, as in many cases
      // BitmapFactory will happily decode a partial image.
      bytes =
          ByteStreams.toByteArray(
              ByteStreams.limit(connectionInputStream, MAX_IMAGE_SIZE_BYTES + 1));
    }

    if (Log.isLoggable(TAG, Log.VERBOSE)) {
      Log.v(TAG, "Downloaded " + bytes.length + " bytes from " + url);
    }

    if (bytes.length > MAX_IMAGE_SIZE_BYTES) {
      throw new IOException("Image exceeds max size of " + MAX_IMAGE_SIZE_BYTES);
    }
    return bytes;
  }

  @Override
  public void close() {
    // Close the stream to prevent downloaded any additional data. This will cause the input stream
    // to throw an IOException on read, which will finish the task.
    try {
      Closeables.closeQuietly(connectionInputStream);
    } catch (NullPointerException npe) {
      // Older versions of okio don't handle closing on a different thread than the one that it was
      // started on, so just catch it for now.
      Log.e(TAG, "Failed to close the image download stream.", npe);
    }
  }
}
