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

package com.google.firebase.database.core.view;

import static com.google.firebase.database.UnitTestHelpers.ck;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.database.util.JsonMapper;
import java.io.IOException;
import java.util.Map;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class QueryParamsTest {

  @Test
  public void startAtNullIsSerializable() {
    QueryParams params = QueryParams.DEFAULT_PARAMS;
    params = params.startAt(EmptyNode.Empty(), ck("key"));
    Map<String, Object> serialized = params.getWireProtocolParams();
    QueryParams parsed = QueryParams.fromQueryObject(serialized);
    assertEquals(params, parsed);
    assertTrue(params.hasStart());
  }

  @Test
  public void endAtNullIsSerializable() {
    QueryParams params = QueryParams.DEFAULT_PARAMS;
    params = params.endAt(EmptyNode.Empty(), ck("key"));
    Map<String, Object> serialized = params.getWireProtocolParams();
    QueryParams parsed = QueryParams.fromQueryObject(serialized);
    assertEquals(params, parsed);
    assertTrue(params.hasEnd());
  }

  @Test
  public void queryParamsRoundTripThroughJSON() throws IOException {
    QueryParams def = QueryParams.DEFAULT_PARAMS;
    roundTrip(def);
    roundTrip(def.startAt(NodeUtilities.NodeFromJSON(100.0), null));
    roundTrip(def.startAt(NodeUtilities.NodeFromJSON(100.4), null));
    roundTrip(def.startAt(NodeUtilities.NodeFromJSON("one hundred"), null));
    roundTrip(
        def.startAt(NodeUtilities.NodeFromJSON(100.0), ChildKey.fromString("a"))
            .endAt(NodeUtilities.NodeFromJSON("one hundred"), ChildKey.fromString("b"))
            .limitToFirst(5));
    roundTrip(
        def.startAt(NodeUtilities.NodeFromJSON(100.0), ChildKey.fromString("a")).limitToLast(5));
  }

  private void roundTrip(QueryParams queryParams) throws IOException {
    String jsonString = queryParams.toJSON();
    Map<String, Object> jsonObject = JsonMapper.parseJson(jsonString);
    QueryParams fromJSON = QueryParams.fromQueryObject(jsonObject);

    assertEquals(queryParams, fromJSON);
    assertEquals(queryParams.hashCode(), fromJSON.hashCode());
  }
}
