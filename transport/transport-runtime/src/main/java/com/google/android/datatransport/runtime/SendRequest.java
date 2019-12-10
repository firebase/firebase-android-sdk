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

package com.google.android.datatransport.runtime;

import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transformer;
import com.google.auto.value.AutoValue;

@AutoValue
abstract class SendRequest {
  public abstract TransportContext getTransportContext();

  public abstract String getTransportName();

  abstract Event<?> getEvent();

  abstract Transformer<?, byte[]> getTransformer();

  public abstract Encoding getEncoding();

  public byte[] getPayload() {
    return ((Transformer<Object, byte[]>) getTransformer()).apply(getEvent().getPayload());
  }

  public static Builder builder() {
    return new AutoValue_SendRequest.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setTransportContext(TransportContext transportContext);

    public abstract Builder setTransportName(String name);

    abstract Builder setEvent(Event<?> event);

    abstract Builder setTransformer(Transformer<?, byte[]> transformer);

    abstract Builder setEncoding(Encoding encoding);

    public abstract SendRequest build();

    public <T> Builder setEvent(
        Event<T> event, Encoding encoding, Transformer<T, byte[]> transformer) {
      setEvent(event);
      setEncoding(encoding);
      setTransformer(transformer);
      return this;
    }
  }
}
