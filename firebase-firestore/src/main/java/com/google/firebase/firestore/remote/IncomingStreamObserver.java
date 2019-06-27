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

package com.google.firebase.firestore.remote;

import io.grpc.Metadata;
import io.grpc.Status;

/** Interface used for incoming/receiving gRPC streams. */
interface IncomingStreamObserver<RespT> {
  /** Headers were received for this stream. */
  void onHeaders(Metadata headers);

  /** A message was received on the stream. */
  void onNext(RespT response);

  /** The stream is open and able to accept messages. */
  void onOpen();

  /** The stream has closed. Status.isOk() is false if there an error occurred. */
  void onClose(Status status);
}
