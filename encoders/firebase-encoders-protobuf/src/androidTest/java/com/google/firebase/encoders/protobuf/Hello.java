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

import androidx.annotation.NonNull;
import com.google.firebase.encoders.annotations.Encodable;
import java.util.List;
import java.util.Map;

@Encodable
public class Hello {

  private final int myInt;
  private final String str;
  private final List<World> worlds;

  public Hello(int myInt, @NonNull String str, List<World> worlds) {
    this.myInt = myInt;
    this.str = str;
    this.worlds = worlds;
  }

  @Protobuf(tag = 1)
  public int getMyInt() {
    return myInt;
  }

  @NonNull
  @Protobuf(tag = 2)
  public String getStr() {
    return str;
  }

  @Protobuf(tag = 3)
  public List<World> getWorlds() {
    return worlds;
  }

  public static class World {
    private final boolean bool;
    private final Map<String, Integer> myMap;
    private final HeyHey heyHey;

    public World(boolean bool, Map<String, Integer> myMap, HeyHey heyHey) {
      this.bool = bool;
      this.myMap = myMap;
      this.heyHey = heyHey;
    }

    @Protobuf(tag = 1)
    public boolean isBool() {
      return bool;
    }

    @Protobuf(tag = 2)
    public Map<String, Integer> getMyMap() {
      return myMap;
    }

    @Protobuf(tag = 3)
    public HeyHey getHeyHey() {
      return heyHey;
    }
  }

  public static class HeyHey {
    private final String bar;

    public HeyHey(String bar) {
      this.bar = bar;
    }

    @Protobuf(tag = 1)
    public String getBar() {
      return bar;
    }
  }
}
