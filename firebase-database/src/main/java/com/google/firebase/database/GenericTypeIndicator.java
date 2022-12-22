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

/**
 * Due to the way that Java implements generics (type-erasure), it is necessary to use a slightly
 * more complicated method to properly resolve types for generic collections at runtime. To solve
 * this problem, Firebase Database accepts subclasses of this class in calls to getValue ({@link
 * com.google.firebase.database.DataSnapshot#getValue(GenericTypeIndicator)}, {@link
 * MutableData#getValue(GenericTypeIndicator)}) and returns a properly-typed generic collection.
 *
 * <p>As an example, you might do something like this to get a list of Message instances from a
 * {@link DataSnapshot}: <br>
 * <br>
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
 *     // Later ...
 *
 *     GenericTypeIndicator&lt;List&lt;Message&gt;&gt; t = new GenericTypeIndicator&lt;List&lt;Message&gt;&gt;() {};
 *     List&lt;Message&gt; messages = snapshot.getValue(t);
 *
 * </code></pre>
 *
 * @param <T> The type of generic collection that this instance servers as an indicator for
 */
public abstract class GenericTypeIndicator<T> {
  // TODO: This is a legacy class that inherited from TypeIndicator from Jackson to be
  // able to resolve generic types. We need a new solution going forward.
}
