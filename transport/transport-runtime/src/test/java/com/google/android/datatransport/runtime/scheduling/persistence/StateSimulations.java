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

package com.google.android.datatransport.runtime.scheduling.persistence;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.EncodedPayload;
import com.google.android.datatransport.runtime.util.PriorityMapping;
import java.nio.charset.Charset;
import java.util.Random;

class StateSimulations {
  private static final Encoding PROTOBUF_ENCODING = Encoding.of("proto");

  interface StateSimulator {
    void simulate(SchemaManager schemaManager);
  }

  static class V1 implements StateSimulator {
    @Override
    public void simulate(SchemaManager schemaManager) {
      SQLiteDatabase db = schemaManager.getWritableDatabase();

      ContentValues record = new ContentValues();
      record.put("backend_name", "b1");
      record.put("priority", PriorityMapping.toInt(Priority.DEFAULT));
      record.put("next_request_ms", 0);
      long contextId = db.insert("transport_contexts", null, record);
      assertThat(contextId).isNotEqualTo(-1);

      ContentValues values = new ContentValues();
      values.put("context_id", contextId);
      values.put("transport_name", "42");
      values.put("timestamp_ms", 1);
      values.put("uptime_ms", 2);
      values.put(
          "payload",
          new EncodedPayload(PROTOBUF_ENCODING, "Hello".getBytes(Charset.defaultCharset()))
              .getBytes());
      values.put("code", 1);
      values.put("num_attempts", 0);
      long newEventId = db.insert("events", null, values);
      assertThat(newEventId).isNotEqualTo(-1);

      ContentValues metadata = new ContentValues();
      metadata.put("event_id", newEventId);
      metadata.put("name", "key1");
      metadata.put("value", "value1");
      long metadataId = db.insert("event_metadata", null, metadata);
      assertThat(metadataId).isNotEqualTo(-1);
    }
  }

  static class V2 implements StateSimulator {
    @Override
    public void simulate(SchemaManager schemaManager) {
      SQLiteDatabase db = schemaManager.getWritableDatabase();
      Random rd = new Random();
      byte[] arr = new byte[7];
      rd.nextBytes(arr);

      ContentValues record = new ContentValues();
      record.put("backend_name", "b1");
      record.put("priority", PriorityMapping.toInt(Priority.DEFAULT));
      record.put("next_request_ms", 0);
      record.put("extras", arr);
      long contextId = db.insert("transport_contexts", null, record);
      assertThat(contextId).isNotEqualTo(-1);

      ContentValues values = new ContentValues();
      values.put("context_id", contextId);
      values.put("transport_name", "42");
      values.put("timestamp_ms", 1);
      values.put("uptime_ms", 2);
      values.put(
          "payload",
          new EncodedPayload(PROTOBUF_ENCODING, "Hello".getBytes(Charset.defaultCharset()))
              .getBytes());
      values.put("code", 1);
      values.put("num_attempts", 0);
      long newEventId = db.insert("events", null, values);
      assertThat(newEventId).isNotEqualTo(-1);

      ContentValues metadata = new ContentValues();
      metadata.put("event_id", newEventId);
      metadata.put("name", "key1");
      metadata.put("value", "value1");
      long metadataId = db.insert("event_metadata", null, metadata);
      assertThat(metadataId).isNotEqualTo(-1);
    }
  }

  static class V3 implements StateSimulator {
    @Override
    public void simulate(SchemaManager schemaManager) {
      SQLiteDatabase db = schemaManager.getWritableDatabase();
      Random rd = new Random();
      byte[] arr = new byte[7];
      rd.nextBytes(arr);

      ContentValues record = new ContentValues();
      record.put("backend_name", "b1");
      record.put("priority", PriorityMapping.toInt(Priority.DEFAULT));
      record.put("next_request_ms", 0);
      record.put("extras", arr);
      long contextId = db.insert("transport_contexts", null, record);
      assertThat(contextId).isNotEqualTo(-1);

      ContentValues values = new ContentValues();
      values.put("context_id", contextId);
      values.put("transport_name", "42");
      values.put("timestamp_ms", 1);
      values.put("uptime_ms", 2);
      values.put(
          "payload",
          new EncodedPayload(PROTOBUF_ENCODING, "Hello".getBytes(Charset.defaultCharset()))
              .getBytes());
      values.put("code", 1);
      values.put("num_attempts", 0);
      values.put("payload_encoding", "encoding");
      long newEventId = db.insert("events", null, values);
      assertThat(newEventId).isNotEqualTo(-1);

      ContentValues metadata = new ContentValues();
      metadata.put("event_id", newEventId);
      metadata.put("name", "key1");
      metadata.put("value", "value1");
      long metadataId = db.insert("event_metadata", null, metadata);
      assertThat(metadataId).isNotEqualTo(-1);
    }
  }

  static class V4 implements StateSimulator {
    @Override
    public void simulate(SchemaManager schemaManager) {
      SQLiteDatabase db = schemaManager.getWritableDatabase();
      Random rd = new Random();
      byte[] arr = new byte[7];
      rd.nextBytes(arr);

      ContentValues record = new ContentValues();
      record.put("backend_name", "b1");
      record.put("priority", PriorityMapping.toInt(Priority.DEFAULT));
      record.put("next_request_ms", 0);
      record.put("extras", arr);
      long contextId = db.insert("transport_contexts", null, record);
      assertThat(contextId).isNotEqualTo(-1);

      ContentValues values = new ContentValues();
      values.put("context_id", contextId);
      values.put("transport_name", "42");
      values.put("timestamp_ms", 1);
      values.put("uptime_ms", 2);
      values.put(
          "payload",
          new EncodedPayload(PROTOBUF_ENCODING, "Hello".getBytes(Charset.defaultCharset()))
              .getBytes());
      values.put("code", 1);
      values.put("num_attempts", 0);
      values.put("payload_encoding", "encoding");
      values.put("inline", true);
      long newEventId = db.insert("events", null, values);
      assertThat(newEventId).isNotEqualTo(-1);

      ContentValues payloads = new ContentValues();
      values.put("sequence_num", newEventId);
      values.put("event_id", "42");
      values.put("bytes", "event".getBytes());
      long payloadId = db.insert("event_payloads", null, payloads);

      ContentValues metadata = new ContentValues();
      metadata.put("event_id", newEventId);
      metadata.put("name", "key1");
      metadata.put("value", "value1");
      long metadataId = db.insert("event_metadata", null, metadata);
      assertThat(metadataId).isNotEqualTo(-1);
    }
  }
}
