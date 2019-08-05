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

package com.google.firebase.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.utilities.Validation;
import com.google.firebase.database.core.utilities.encoding.CustomClassMapper;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import java.util.Iterator;

/**
 * A DataSnapshot instance contains data from a Firebase Database location. Any time you read
 * Database data, you receive the data as a DataSnapshot. <br>
 * <br>
 * DataSnapshots are passed to the methods in listeners that you attach with {@link
 * DatabaseReference#addValueEventListener(ValueEventListener)}, {@link
 * DatabaseReference#addChildEventListener(ChildEventListener)}, or {@link
 * DatabaseReference#addListenerForSingleValueEvent(ValueEventListener)}. <br>
 * <br>
 * They are efficiently-generated immutable copies of the data at a Firebase Database location. They
 * can't be modified and will never change. To modify data at a location, use a {@link
 * DatabaseReference DatabaseReference} reference (e.g. with {@link
 * DatabaseReference#setValue(Object)}).
 */
public class DataSnapshot {

  private final IndexedNode node;
  private final DatabaseReference query;

  /**
   * @param ref A DatabaseReference
   * @param node The indexed node
   */
  DataSnapshot(DatabaseReference ref, IndexedNode node) {
    this.node = node;
    this.query = ref;
  }

  /**
   * Get a DataSnapshot for the location at the specified relative path. The relative path can
   * either be a simple child key (e.g. 'fred') or a deeper slash-separated path (e.g.
   * 'fred/name/first'). If the child location has no data, an empty DataSnapshot is returned.
   *
   * @param path A relative path to the location of child data
   * @return The DataSnapshot for the child location
   */
  @NonNull
  public DataSnapshot child(@NonNull String path) {
    DatabaseReference childRef = query.child(path);
    Node childNode = this.node.getNode().getChild(new Path(path));
    return new DataSnapshot(childRef, IndexedNode.from(childNode));
  }

  /**
   * Can be used to determine if this DataSnapshot has data at a particular location
   *
   * @param path A relative path to the location of child data
   * @return Whether or not the specified child location has data
   */
  public boolean hasChild(@NonNull String path) {
    if (query.getParent() == null) {
      Validation.validateRootPathString(path);
    } else {
      Validation.validatePathString(path);
    }
    return !node.getNode().getChild(new Path(path)).isEmpty();
  }

  /**
   * Indicates whether this snapshot has any children
   *
   * @return True if the snapshot has any children, otherwise false
   */
  public boolean hasChildren() {
    return node.getNode().getChildCount() > 0;
  }

  /**
   * Returns true if the snapshot contains a non-null value.
   *
   * @return True if the snapshot contains a non-null value, otherwise false
   */
  public boolean exists() {
    return !node.getNode().isEmpty();
  }

  /**
   * getValue() returns the data contained in this snapshot as native types. The possible types
   * returned are:
   *
   * <ul>
   *   <li>Boolean
   *   <li>String
   *   <li>Long
   *   <li>Double
   *   <li>Map&lt;String, Object&gt;
   *   <li>List&lt;Object&gt;
   * </ul>
   *
   * This list is recursive; the possible types for {@link java.lang.Object} in the above list is
   * given by the same list. These types correspond to the types available in JSON.
   *
   * @return The data contained in this snapshot as native types or null if there is no data at this
   *     location.
   */
  @Nullable
  public Object getValue() {
    return node.getNode().getValue();
  }

  /**
   * getValue() returns the data contained in this snapshot as native types. The possible types
   * returned are:
   *
   * <ul>
   *   <li>Boolean
   *   <li>String
   *   <li>Long
   *   <li>Double
   *   <li>Map&lt;String, Object&gt;
   *   <li>List&lt;Object&gt;
   * </ul>
   *
   * This list is recursive; the possible types for {@link java.lang.Object} in the above list is
   * given by the same list. These types correspond to the types available in JSON.
   *
   * <p>If useExportFormat is set to true, priority information will be included in the output.
   * Priority information shows up as a .priority key in a map. For data that would not otherwise be
   * a map, the map will also include a .value key with the data.
   *
   * @param useExportFormat Whether or not to include priority information
   * @return The data in native types, along with its priority, or null if there is no data at this
   *     location.
   */
  @Nullable
  public Object getValue(boolean useExportFormat) {
    return node.getNode().getValue(useExportFormat);
  }

  /**
   * This method is used to marshall the data contained in this snapshot into a class of your
   * choosing. The class must fit 2 simple constraints:
   *
   * <ol>
   *   <li>The class must have a default constructor that takes no arguments
   *   <li>The class must define public getters for the properties to be assigned. Properties
   *       without a public getter will be set to their default value when an instance is
   *       deserialized
   * </ol>
   *
   * An example class might look like:
   *
   * <pre><code>
   *     class Message {
   *         private String author;
   *         private String text;
   *
   *         private Message() {}
   *
   *         public Message(String author, String text) {
   *             this.author = author;
   *             this.text = text;
   *         }
   *
   *         public String getAuthor() {
   *             return author;
   *         }
   *
   *         public String getText() {
   *             return text;
   *         }
   *     }
   *
   *
   *     // Later
   *     Message m = snapshot.getValue(Message.class);
   * </code></pre>
   *
   * @param valueType The class into which this snapshot should be marshalled
   * @param <T> The type to return. Implicitly defined from the class passed in
   * @return An instance of the class passed in, populated with the data from this snapshot, or null
   *     if there is no data at this location.
   */
  @Nullable
  public <T> T getValue(@NonNull Class<T> valueType) {
    Object value = node.getNode().getValue();
    return CustomClassMapper.convertToCustomClass(value, valueType);
  }

  /**
   * Due to the way that Java implements generics, it takes an extra step to get back a
   * properly-typed Collection. So, in the case where you want a {@link java.util.List} of Message
   * instances, you will need to do something like the following:
   *
   * <pre><code>
   *     GenericTypeIndicator&lt;List&lt;Message&gt;&gt; t = new GenericTypeIndicator&lt;List&lt;Message&gt;&gt;() {};
   *     List&lt;Message&gt; messages = snapshot.getValue(t);
   * </code></pre>
   *
   * It is important to use a subclass of {@link GenericTypeIndicator}. See {@link
   * GenericTypeIndicator} for more details
   *
   * @param t A subclass of {@link GenericTypeIndicator} indicating the type of generic collection
   *     to be returned.
   * @param <T> The type to return. Implicitly defined from the {@link GenericTypeIndicator} passed
   *     in
   * @return A properly typed collection, populated with the data from this snapshot, or null if
   *     there is no data at this location.
   */
  @Nullable
  public <T> T getValue(@NonNull GenericTypeIndicator<T> t) {
    Object value = node.getNode().getValue();
    return CustomClassMapper.convertToCustomClass(value, t);
  }

  /** @return The number of immediate children in the this snapshot */
  public long getChildrenCount() {
    return node.getNode().getChildCount();
  }

  /**
   * Used to obtain a reference to the source location for this snapshot.
   *
   * @return A DatabaseReference corresponding to the location that this snapshot came from
   */
  @NonNull
  public DatabaseReference getRef() {
    return query;
  }

  /**
   * @return The key name for the source location of this snapshot or null if this snapshot points
   *     to the database root.
   */
  @Nullable
  public String getKey() {
    return query.getKey();
  }

  /**
   * Gives access to all of the immediate children of this snapshot. Can be used in native for
   * loops: <code>
   * <br>    for (DataSnapshot child : parent.getChildren()) {
   * <br>    &nbsp;&nbsp;&nbsp;&nbsp;...
   * <br>    }
   * </code>
   *
   * @return The immediate children of this snapshot
   */
  @NonNull
  public Iterable<DataSnapshot> getChildren() {
    final Iterator<NamedNode> iter = node.iterator();
    return new Iterable<DataSnapshot>() {

      @Override
      public Iterator<DataSnapshot> iterator() {
        return new Iterator<DataSnapshot>() {
          @Override
          public boolean hasNext() {
            return iter.hasNext();
          }

          @Override
          @NonNull
          public DataSnapshot next() {
            NamedNode namedNode = iter.next();
            return new DataSnapshot(
                query.child(namedNode.getName().asString()), IndexedNode.from(namedNode.getNode()));
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException("remove called on immutable collection");
          }
        };
      }
    };
  }

  /**
   * Returns the priority of the data contained in this snapshot as a native type. Possible return
   * types:
   *
   * <ul>
   *   <li>Double
   *   <li>String
   * </ul>
   *
   * Note that null is also allowed
   *
   * @return the priority of the data contained in this snapshot as a native type
   */
  @Nullable
  public Object getPriority() {
    Object priority = node.getNode().getPriority().getValue();
    if (priority instanceof Long) {
      return Double.valueOf((Long) priority);
    } else {
      return priority;
    }
  }

  @Override
  public String toString() {
    return "DataSnapshot { key = "
        + this.query.getKey()
        + ", value = "
        + this.node.getNode().getValue(true)
        + " }";
  }
}
