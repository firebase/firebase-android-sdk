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

package com.google.firebase.inappmessaging.internal;

import static android.content.Context.MODE_PRIVATE;

import android.app.Application;
import com.google.protobuf.AbstractMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.annotation.concurrent.ThreadSafe;

/**
 * File backed storage client for protos. Reads and writes are non atomic, but thread safe.
 *
 * <p>The client confers locking semantics per client read/written.
 *
 * <p>Creating multiple clients that read/write to the same file will violate the principles of this
 * thread safety
 *
 * <p>In the future we can consider using Android/Support's AtomicFile to provide atomic reads and
 * writes
 *
 * @hide
 */
@ThreadSafe
public class ProtoStorageClient {
  private final Application application;
  private final String fileName;

  public ProtoStorageClient(Application application, String fileName) {
    this.application = application;
    this.fileName = fileName;
  }

  /**
   * Write the proto to a file in the app' s file directory.
   *
   * <p>Writes are non atomic.
   *
   * <p>Readers are expected to deal with corrupt data resulting from faulty writes
   *
   * @param messageLite
   * @throws IOException
   */
  public Completable write(AbstractMessageLite messageLite) {
    return Completable.fromCallable(
        () -> {
          // reads / writes are synchronized per client instance
          synchronized (this) {
            try (FileOutputStream output = application.openFileOutput(fileName, MODE_PRIVATE)) {
              output.write(messageLite.toByteArray());
              return messageLite;
            }
          }
        });
  }

  /**
   * Read the contents of the file into a proto object using the parser. Since writes are not
   * atomic, the caller will receive {@link Maybe#empty()} when data is corrupt.
   *
   * <p>Some valid scenarios that can lead to corrupt data :
   *
   * <ul>
   *   <li>Out of disk space while writing
   *   <li>Power outage while writing
   *   <li>Process killed while writing
   * </ul>
   *
   * @param parser
   * @param <T>
   */
  public <T extends AbstractMessageLite> Maybe<T> read(Parser<T> parser) {
    return Maybe.fromCallable(
        () -> {
          // reads / writes are synchronized per client instance
          synchronized (this) {
            try (FileInputStream inputStream = application.openFileInput(fileName)) {
              return parser.parseFrom(inputStream);
            } catch (InvalidProtocolBufferException | FileNotFoundException e) {
              Logging.logi("Recoverable exception while reading cache: " + e.getMessage());
              return null;
            }
          }
        });
  }
}
