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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.snapshot.EmptyNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class ObjectMapTest {
  @Rule public RetryRule retryRule = new RetryRule(3);

  @ThrowOnExtraProperties
  private static class Author {
    private String name;
    private int id;

    private Author() {}

    private Author(String name, int id) {
      this.name = name;
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public int getId() {
      return id;
    }

    @Override
    public String toString() {
      return "Name: " + name + " id: " + id;
    }

    @Override
    public boolean equals(Object o) {
      return (o instanceof Author) && ((Author) o).name.equals(name) && ((Author) o).id == id;
    }

    @Override
    public int hashCode() {
      return 31 * name.hashCode() + Integer.hashCode(id);
    }
  }

  @ThrowOnExtraProperties
  private static class Message {
    private String text;
    private Author author;

    private Message() {}

    private Message(String text, Author author) {
      this.text = text;
      this.author = author;
    }

    public String getText() {
      return text;
    }

    public Author getAuthor() {
      return author;
    }

    @Override
    public String toString() {
      return "Message: " + text + " Author: " + author.toString();
    }

    @Override
    public boolean equals(Object o) {
      return (o instanceof Message)
          && ((Message) o).text.equals(text)
          && (((((Message) o).author == null) && (author == null))
              || ((Message) o).author.equals(author));
    }

    @Override
    public int hashCode() {
      return 31 * text.hashCode() + author.hashCode();
    }
  }

  private MutableData emptyData() {
    return new MutableData(EmptyNode.Empty());
  }

  @Test
  public void basicObjectMapping() throws IOException {
    MutableData data = emptyData();

    Author author = new Author("Greg", 3);
    Message message = new Message("hello world", author);

    data.setValue(message);

    Message result = data.getValue(Message.class);

    assertEquals(message, result);

    try {
      Author badResult = data.getValue(Author.class);
      fail("Should throw");
    } catch (DatabaseException e) {
      // No-op, expected to throw
    }
  }

  @Test
  public void nativeToObject() {
    MutableData data = emptyData();

    Map<String, Object> toSet =
        new MapBuilder()
            .put("text", "hello world")
            .put("author", new MapBuilder().put("name", "Greg").put("id", 3).build())
            .build();

    data.setValue(toSet);

    Message m = data.getValue(Message.class);
    assertEquals("hello world", m.getText());
    Author a = m.getAuthor();
    assertEquals("Greg", a.getName());
    assertEquals(3, a.getId());

    // An extra field should cause the deserializer to throw
    data.child("foo").setValue("bar");
    try {
      m = data.getValue(Message.class);
      fail("Should throw");
    } catch (DatabaseException e) {
      // No-op, expected to throw
    }
  }

  @Test
  public void objectToNative() {
    MutableData data = emptyData();

    Author a = new Author("Greg", 3);
    Message m = new Message("hello world", a);
    data.setValue(m);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) data.getValue();
    Map<String, Object> expected =
        new MapBuilder()
            .put("text", "hello world")
            .put("author", new MapBuilder().put("name", "Greg").put("id", 3L).build())
            .build();

    DeepEquals.assertEquals(expected, result);
  }

  @Test
  public void nullsWork() {
    MutableData data = emptyData();
    Message m = new Message("hello world", null);
    data.setValue(m);

    // Author is gone, equivalent to setting child("author") to null
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) data.getValue();
    Map<String, Object> expected = new MapBuilder().put("text", "hello world").build();

    DeepEquals.assertEquals(expected, result);

    Message resultMessage = data.getValue(Message.class);
    assertEquals(m, resultMessage);
  }

  @Test
  public void numbersCanBeConverted() {
    MutableData data = emptyData();

    data.setValue(3);

    Object result = data.getValue();
    assertTrue(result instanceof Long);
    result = data.getValue(Integer.class);
    assertTrue(result != null);
  }

  @Test
  public void basicGenerics() {
    List<Author> authors = new ArrayList<Author>();
    authors.addAll(
        Arrays.asList(new Author("Greg", 3), new Author("Vikrum", 4), new Author("Michael", 5)));

    MutableData data = emptyData();
    data.setValue(authors);

    GenericTypeIndicator<List<Author>> t = new GenericTypeIndicator<List<Author>>() {};
    List<Author> result = data.getValue(t);
    DeepEquals.assertEquals(authors, result);
  }

  private static class AuthorMessages {
    private Author author;
    private List<Message> messages;

    private AuthorMessages() {}

    private AuthorMessages(Author author, List<Message> messages) {
      this.author = author;
      this.messages = messages;
    }

    public Author getAuthor() {
      return author;
    }

    public List<Message> getMessages() {
      return messages;
    }

    @Override
    public String toString() {
      return "Author: " + author + " messages: " + messages;
    }
  }

  @Test
  public void nestedGenerics() throws IOException {
    MutableData data = emptyData();
    Author author = new Author("Greg", 3);
    List<Message> messages = new ArrayList<Message>();
    messages.add(new Message("hello world", author));
    messages.add(new Message("foo bar", author));
    AuthorMessages am = new AuthorMessages(author, messages);

    data.setValue(am);

    AuthorMessages result = data.getValue(AuthorMessages.class);

    assertEquals(am.getAuthor(), result.getAuthor());
    DeepEquals.assertEquals(am.getMessages(), result.getMessages());
  }

  private static class Incomplete {
    private int foo;
    private int bar;

    private Incomplete() {}

    private Incomplete(int foo, int bar) {
      this.foo = foo;
      this.bar = bar;
    }

    public int getFoo() {
      return foo;
    }

    int getBar() {
      return bar;
    }
  }

  @Test
  public void incompleteObject() {
    MutableData data = emptyData();

    Incomplete i = new Incomplete(1, 2);
    data.setValue(i);

    i = data.getValue(Incomplete.class);
    assertEquals(1, i.getFoo());
    // bar doesn't get serialized, its getter isn't public
    assertEquals(0, i.getBar());
  }

  @Test
  public void intsAndLongs() {
    MutableData data = emptyData();

    // Verify that jackson does the right thing when it encounters a number too large for an int
    data.setValue(new MapBuilder().put("foo", (long) Integer.MAX_VALUE + 1).build());
    Map<String, Object> output = data.getValue(new GenericTypeIndicator<Map<String, Object>>() {});
    Object value = output.get("foo");
    assertEquals(Long.class, value.getClass());

    // Verify that our default is Long
    data.setValue(3);
    value = data.getValue();
    assertEquals(Long.class, value.getClass());
  }
}
