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

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;
import static com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON;

import com.google.firebase.database.core.view.filter.IndexedFilter;
import com.google.firebase.database.core.view.filter.LimitedFilter;
import com.google.firebase.database.core.view.filter.NodeFilter;
import com.google.firebase.database.core.view.filter.RangedFilter;
import com.google.firebase.database.snapshot.BooleanNode;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.DoubleNode;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Index;
import com.google.firebase.database.snapshot.LongNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.PriorityIndex;
import com.google.firebase.database.snapshot.PriorityUtilities;
import com.google.firebase.database.snapshot.StringNode;
import com.google.firebase.database.util.JsonMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class QueryParams {
  public static final QueryParams DEFAULT_PARAMS = new QueryParams();

  private static final String INDEX_START_VALUE = "sp";
  private static final String INDEX_START_NAME = "sn";
  private static final String INDEX_END_VALUE = "ep";
  private static final String INDEX_END_NAME = "en";
  private static final String LIMIT = "l";
  private static final String VIEW_FROM = "vf";
  private static final String INDEX = "i";

  private static enum ViewFrom {
    LEFT,
    RIGHT
  }

  private Integer limit;
  private ViewFrom viewFrom;
  private Node indexStartValue = null;
  private ChildKey indexStartName = null;
  private Node indexEndValue = null;
  private ChildKey indexEndName = null;

  private Index index = PriorityIndex.getInstance();

  private String jsonSerialization = null;

  public boolean hasStart() {
    return indexStartValue != null;
  }

  public Node getIndexStartValue() {
    if (!hasStart()) {
      throw new IllegalArgumentException("Cannot get index start value if start has not been set");
    }
    return indexStartValue;
  }

  public ChildKey getIndexStartName() {
    if (!hasStart()) {
      throw new IllegalArgumentException("Cannot get index start name if start has not been set");
    }
    if (indexStartName != null) {
      return indexStartName;
    } else {
      return ChildKey.getMinName();
    }
  }

  public boolean hasEnd() {
    return indexEndValue != null;
  }

  public Node getIndexEndValue() {
    if (!hasEnd()) {
      throw new IllegalArgumentException("Cannot get index end value if start has not been set");
    }
    return indexEndValue;
  }

  public ChildKey getIndexEndName() {
    if (!hasEnd()) {
      throw new IllegalArgumentException("Cannot get index end name if start has not been set");
    }
    if (indexEndName != null) {
      return indexEndName;
    } else {
      return ChildKey.getMaxName();
    }
  }

  public boolean hasLimit() {
    return limit != null;
  }

  public boolean hasAnchoredLimit() {
    return hasLimit() && this.viewFrom != null;
  }

  public int getLimit() {
    if (!hasLimit()) {
      throw new IllegalArgumentException("Cannot get limit if limit has not been set");
    }
    return this.limit;
  }

  public Index getIndex() {
    return this.index;
  }

  private QueryParams copy() {
    QueryParams params = new QueryParams();
    params.limit = limit;
    params.indexStartValue = indexStartValue;
    params.indexStartName = indexStartName;
    params.indexEndValue = indexEndValue;
    params.indexEndName = indexEndName;
    params.viewFrom = viewFrom;
    params.index = index;
    return params;
  }

  public QueryParams limitToFirst(int limit) {
    QueryParams copy = copy();
    copy.limit = limit;
    copy.viewFrom = ViewFrom.LEFT;
    return copy;
  }

  public QueryParams limitToLast(int limit) {
    QueryParams copy = copy();
    copy.limit = limit;
    copy.viewFrom = ViewFrom.RIGHT;
    return copy;
  }

  public QueryParams startAt(Node indexStartValue, ChildKey indexStartName) {
    hardAssert(indexStartValue.isLeafNode() || indexStartValue.isEmpty());
    // We can't tolerate longs as query endpoints.  See comment in normalizeValue();
    hardAssert(!(indexStartValue instanceof LongNode));
    QueryParams copy = copy();
    copy.indexStartValue = indexStartValue;
    copy.indexStartName = indexStartName;
    return copy;
  }

  public QueryParams endAt(Node indexEndValue, ChildKey indexEndName) {
    hardAssert(indexEndValue.isLeafNode() || indexEndValue.isEmpty());
    // We can't tolerate longs as query endpoints.  See comment in normalizeValue();
    hardAssert(!(indexEndValue instanceof LongNode));
    QueryParams copy = copy();
    copy.indexEndValue = indexEndValue;
    copy.indexEndName = indexEndName;
    return copy;
  }

  public QueryParams orderBy(Index index) {
    QueryParams copy = copy();
    copy.index = index;
    return copy;
  }

  public boolean isViewFromLeft() {
    return this.viewFrom != null ? this.viewFrom == ViewFrom.LEFT : hasStart();
  }

  // NOTE: Don't change this unless you're changing the wire protocol!
  public Map<String, Object> getWireProtocolParams() {
    Map<String, Object> queryObject = new HashMap<String, Object>();
    if (hasStart()) {
      queryObject.put(INDEX_START_VALUE, indexStartValue.getValue());
      if (indexStartName != null) {
        queryObject.put(INDEX_START_NAME, indexStartName.asString());
      }
    }
    if (hasEnd()) {
      queryObject.put(INDEX_END_VALUE, indexEndValue.getValue());
      if (indexEndName != null) {
        queryObject.put(INDEX_END_NAME, indexEndName.asString());
      }
    }
    if (limit != null) {
      queryObject.put(LIMIT, limit);
      ViewFrom viewFromToAdd = viewFrom;
      if (viewFromToAdd == null) {
        // limit(), rather than limitToFirst or limitToLast was called.
        // This means that only one of hasStart() and hasEnd() is true. Use them
        // to calculate which side of the view to anchor to. If neither is set,
        // anchor to the end.
        if (hasStart()) {
          viewFromToAdd = ViewFrom.LEFT;
        } else {
          // endSet_ or neither set
          viewFromToAdd = ViewFrom.RIGHT;
        }
      }
      switch (viewFromToAdd) {
        case LEFT:
          queryObject.put(VIEW_FROM, "l");
          break;
        case RIGHT:
          queryObject.put(VIEW_FROM, "r");
          break;
      }
    }
    if (!index.equals(PriorityIndex.getInstance())) {
      queryObject.put(INDEX, index.getQueryDefinition());
    }
    return queryObject;
  }

  public boolean loadsAllData() {
    return !(hasStart() || hasEnd() || hasLimit());
  }

  public boolean isDefault() {
    return loadsAllData() && index.equals(PriorityIndex.getInstance());
  }

  public boolean isValid() {
    return !(hasStart() && hasEnd() && hasLimit() && !hasAnchoredLimit());
  }

  public String toJSON() {
    if (jsonSerialization == null) {
      try {
        jsonSerialization = JsonMapper.serializeJson(getWireProtocolParams());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return jsonSerialization;
  }

  public static QueryParams fromQueryObject(Map<String, Object> map) {
    QueryParams params = new QueryParams();
    params.limit = (Integer) map.get(LIMIT);

    if (map.containsKey(INDEX_START_VALUE)) {
      Object indexStartValue = map.get(INDEX_START_VALUE);
      params.indexStartValue = normalizeValue(NodeFromJSON(indexStartValue));
      String indexStartName = (String) map.get(INDEX_START_NAME);
      if (indexStartName != null) {
        params.indexStartName = ChildKey.fromString(indexStartName);
      }
    }

    if (map.containsKey(INDEX_END_VALUE)) {
      Object indexEndValue = map.get(INDEX_END_VALUE);
      params.indexEndValue = normalizeValue(NodeFromJSON(indexEndValue));
      String indexEndName = (String) map.get(INDEX_END_NAME);
      if (indexEndName != null) {
        params.indexEndName = ChildKey.fromString(indexEndName);
      }
    }

    String viewFrom = (String) map.get(VIEW_FROM);
    if (viewFrom != null) {
      params.viewFrom = viewFrom.equals("l") ? ViewFrom.LEFT : ViewFrom.RIGHT;
    }

    String indexStr = (String) map.get(INDEX);
    if (indexStr != null) {
      params.index = Index.fromQueryDefinition(indexStr);
    }

    return params;
  }

  public NodeFilter getNodeFilter() {
    if (this.loadsAllData()) {
      return new IndexedFilter(this.getIndex());
    } else if (this.hasLimit()) {
      return new LimitedFilter(this);
    } else {
      return new RangedFilter(this);
    }
  }

  @Override
  public String toString() {
    return getWireProtocolParams().toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    QueryParams that = (QueryParams) o;

    if (limit != null ? !limit.equals(that.limit) : that.limit != null) {
      return false;
    }
    if (index != null ? !index.equals(that.index) : that.index != null) {
      return false;
    }
    if (indexEndName != null
        ? !indexEndName.equals(that.indexEndName)
        : that.indexEndName != null) {
      return false;
    }
    if (indexEndValue != null
        ? !indexEndValue.equals(that.indexEndValue)
        : that.indexEndValue != null) {
      return false;
    }
    if (indexStartName != null
        ? !indexStartName.equals(that.indexStartName)
        : that.indexStartName != null) {
      return false;
    }
    if (indexStartValue != null
        ? !indexStartValue.equals(that.indexStartValue)
        : that.indexStartValue != null) {
      return false;
    }
    // viewFrom might be null, but we really want to compare left vs right
    if (isViewFromLeft() != that.isViewFromLeft()) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = limit != null ? limit : 0;
    result = 31 * result + (isViewFromLeft() ? 1231 : 1237);
    result = 31 * result + (indexStartValue != null ? indexStartValue.hashCode() : 0);
    result = 31 * result + (indexStartName != null ? indexStartName.hashCode() : 0);
    result = 31 * result + (indexEndValue != null ? indexEndValue.hashCode() : 0);
    result = 31 * result + (indexEndName != null ? indexEndName.hashCode() : 0);
    result = 31 * result + (index != null ? index.hashCode() : 0);
    return result;
  }

  private static Node normalizeValue(Node value) {
    if (value instanceof StringNode
        || value instanceof BooleanNode
        || value instanceof DoubleNode
        || value instanceof EmptyNode) {

      return value;
    } else if (value instanceof LongNode) {
      // We normalize longs to doubles.  This is *ESSENTIAL* to prevent our persistence
      // code from breaking, since integer-valued doubles get turned into longs after being
      // saved to persistence (as JSON) and then read back. (see http://b/30153920/)
      return new DoubleNode(
          ((Long) value.getValue()).doubleValue(), PriorityUtilities.NullPriority());
    } else {
      throw new IllegalStateException(
          "Unexpected value passed to normalizeValue: " + value.getValue());
    }
  }
}
