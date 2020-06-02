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

package com.google.firebase.encoders.protobuf;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.encoders.GenericEncoder;
import com.google.firebase.encoders.protobuf.Hello.HeyHey;
import com.google.firebase.encoders.protobuf.Hello.World;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Benchmark {
  @Rule public BenchmarkRule benchmarkRule = new BenchmarkRule();

  @Test
  public void benchFirebaseEncoders() throws InterruptedException {
    GenericEncoder<OutputStream, byte[]> encoder =
        new ProtobufDataEncoderBuilder().configureWith(AutoHelloEncoder.CONFIG).build();
    HashMap<String, Integer> map = new HashMap<>();
    map.put("key", 42);
    // Thread.sleep(20000);

    Hello hello =
        new Hello(
            150,
            "testing",
            Arrays.asList(
                new World(true, map, new HeyHey("bar")),
                new World(false, new LinkedHashMap<>(), new HeyHey("baz"))));
    BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) {
      encoder.encode(hello);
    }
    // Thread.sleep(10000);
  }

  @Test
  public void benchProtobufJavalite() {

    BenchmarkState state = benchmarkRule.getState();
    while (state.keepRunning()) {
      state.pauseTiming();
      // creating a new instance on every iteration, otherwise it seems to cache its encoded
      // representation, which makes encoding instant.
      com.google.firebase.encoders.proto.Hello hello =
          com.google.firebase.encoders.proto.Hello.newBuilder()
              .setMyInt(150)
              .setStr("testing")
              .addWorld(
                  com.google.firebase.encoders.proto.Hello.World.newBuilder()
                      .setMyBool(true)
                      .putMyMap("key", 42)
                      .setHeyHey(
                          com.google.firebase.encoders.proto.Hello.HeyHey.newBuilder()
                              .setBar("bar")
                              .build())
                      .build())
              .addWorld(
                  com.google.firebase.encoders.proto.Hello.World.newBuilder()
                      .setMyBool(false)
                      .setHeyHey(
                          com.google.firebase.encoders.proto.Hello.HeyHey.newBuilder()
                              .setBar("baz")
                              .build())
                      .build())
              .build();
      state.resumeTiming();
      hello.toByteArray();
    }
  }
}
