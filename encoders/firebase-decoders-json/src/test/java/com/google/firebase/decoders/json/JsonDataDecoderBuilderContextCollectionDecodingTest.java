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

package com.google.firebase.decoders.json;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.annotation.NonNull;
import com.google.firebase.decoders.DataDecoder;
import com.google.firebase.decoders.FieldRef;
import com.google.firebase.decoders.ObjectDecoder;
import com.google.firebase.decoders.ObjectDecoderContext;
import com.google.firebase.decoders.Safe;
import com.google.firebase.decoders.TypeCreator;
import com.google.firebase.decoders.TypeToken;
import com.google.firebase.encoders.FieldDescriptor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class JsonDataDecoderBuilderContextCollectionDecodingTest {

  @Test
  public void collections_shouldBeCorrectlyDecoded() throws IOException {
    DataDecoder decoder = new JsonDataDecoderBuilder().build();
    String json = "[1,2]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    Set<Integer> set = decoder.decode(input, TypeToken.of(new Safe<HashSet<Integer>>() {}));
    Set<Integer> set1 = new HashSet<>();
    set1.add(1);
    set1.add(2);
    assertThat(set).isEqualTo(set1);
  }

  static class ListFoo {
    List<String> l;

    ListFoo(List<String> l) {
      this.l = l;
    }
  }

  static class ListFooObjectDecoder implements ObjectDecoder<ListFoo> {
    @NonNull
    @Override
    public TypeCreator<ListFoo> decode(@NonNull ObjectDecoderContext<ListFoo> ctx) {
      FieldRef.Boxed<List<String>> lField =
          ctx.decode(FieldDescriptor.of("l"), TypeToken.of(new Safe<List<String>>() {}));
      return (creationCtx -> new ListFoo(creationCtx.get(lField)));
    }
  }

  @Test
  public void nestedCollection_shouldBeDecodedCorrectly() throws IOException {
    DataDecoder decoder =
        new JsonDataDecoderBuilder().register(ListFoo.class, new ListFooObjectDecoder()).build();

    String json = "{\"l\": [\"1\", \"2\"]}";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));
    ListFoo listFoo = decoder.decode(input, TypeToken.of(new Safe<ListFoo>() {}));

    assertThat(listFoo.l.get(0)).isEqualTo("1");
    assertThat(listFoo.l.get(1)).isEqualTo("2");
  }

  @Test
  public void list_shouldBeDecodedAsArrayListClassInDefault() throws IOException {
    DataDecoder decoder = new JsonDataDecoderBuilder().build();

    String json = "[1,2]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));

    List<Integer> l = decoder.decode(input, TypeToken.of(new Safe<List<Integer>>() {}));
    assertThat(l instanceof ArrayList).isTrue();
  }

  @Test
  public void set_shouldBeDecodedAsHashSetClassInDefault() throws IOException {
    DataDecoder decoder = new JsonDataDecoderBuilder().build();

    String json = "[1,2]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));

    Set<Integer> l = decoder.decode(input, TypeToken.of(new Safe<Set<Integer>>() {}));
    assertThat(l instanceof HashSet).isTrue();
  }

  @Test
  public void queue_shouldBeDecodedAsArrayDequeInDefault() throws IOException {
    DataDecoder decoder = new JsonDataDecoderBuilder().build();

    String json = "[1,2]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));

    Queue<Integer> l = decoder.decode(input, TypeToken.of(new Safe<Queue<Integer>>() {}));
    assertThat(l instanceof ArrayDeque).isTrue();
  }

  @Test
  public void dequeue_shouldBeDecodedAsArrayDequeInDefault() throws IOException {
    DataDecoder decoder = new JsonDataDecoderBuilder().build();

    String json = "[1,2]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));

    Deque<Integer> l = decoder.decode(input, TypeToken.of(new Safe<Deque<Integer>>() {}));
    assertThat(l instanceof ArrayDeque).isTrue();
  }

  @Test
  public void sortedSet_shouldBeDecodedAsTreeSetInDefault() throws IOException {
    DataDecoder decoder = new JsonDataDecoderBuilder().build();

    String json = "[1,2]";
    InputStream input = new ByteArrayInputStream(json.getBytes(UTF_8));

    SortedSet<Integer> l = decoder.decode(input, TypeToken.of(new Safe<SortedSet<Integer>>() {}));
    assertThat(l instanceof TreeSet).isTrue();
  }
}
