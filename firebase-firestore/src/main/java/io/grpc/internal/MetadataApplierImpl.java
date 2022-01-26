/*
 * Copyright 2016 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.internal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import io.grpc.CallCredentials.MetadataApplier;
import io.grpc.CallOptions;
import io.grpc.ClientStreamTracer;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

final class MetadataApplierImpl extends MetadataApplier {
  private final io.grpc.internal.ClientTransport transport;
  private final MethodDescriptor<?, ?> method;
  private final Metadata origHeaders;
  private final CallOptions callOptions;
  private final Context ctx;

  private final ClientStreamTracer[] tracers;
  private final Object lock = new Object();

  // null if neither apply() or returnStream() are called.
  // Needs this lock because apply() and returnStream() may race
  @GuardedBy("lock")
  @Nullable
  private io.grpc.internal.ClientStream returnedStream;

  boolean finalized;

  // not null if returnStream() was called before apply()
  io.grpc.internal.DelayedStream delayedStream;

  MetadataApplierImpl(
      ClientTransport transport,
      MethodDescriptor<?, ?> method,
      Metadata origHeaders,
      CallOptions callOptions,
      ClientStreamTracer[] tracers) {
    this.transport = transport;
    this.method = method;
    this.origHeaders = origHeaders;
    this.callOptions = callOptions;
    this.ctx = Context.current();
    this.tracers = tracers;
  }

  @Override
  public void apply(Metadata headers) {
    checkState(!finalized, "apply() or fail() already called");
    checkNotNull(headers, "headers");
    origHeaders.merge(headers);
    io.grpc.internal.ClientStream realStream;
    Context origCtx = ctx.attach();
    try {
      realStream = transport.newStream(method, origHeaders, callOptions, tracers);
    } finally {
      ctx.detach(origCtx);
    }
    finalizeWith(realStream);
  }

  @Override
  public void fail(Status status) {
    checkArgument(!status.isOk(), "Cannot fail with OK status");
    checkState(!finalized, "apply() or fail() already called");
    finalizeWith(new FailingClientStream(status, tracers));
  }

  private void finalizeWith(io.grpc.internal.ClientStream stream) {
    checkState(!finalized, "already finalized");
    finalized = true;
    synchronized (lock) {
      if (returnedStream == null) {
        // Fast path: returnStream() hasn't been called, the call will use the
        // real stream directly.
        returnedStream = stream;
        return;
      }
    }
    // returnStream() has been called before me, thus delayedStream must have been
    // created.
    checkState(delayedStream != null, "delayedStream is null");
    Runnable slow = delayedStream.setStream(stream);
    if (slow != null) {
      // TODO(ejona): run this on a separate thread
      slow.run();
    }
  }

  /** Return a stream on which the RPC will run on. */
  ClientStream returnStream() {
    synchronized (lock) {
      if (returnedStream == null) {
        // apply() has not been called, needs to buffer the requests.
        delayedStream = new io.grpc.internal.DelayedStream();
        return returnedStream = delayedStream;
      } else {
        return returnedStream;
      }
    }
  }
}
