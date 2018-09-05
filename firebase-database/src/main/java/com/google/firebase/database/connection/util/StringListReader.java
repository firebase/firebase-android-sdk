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

package com.google.firebase.database.connection.util;

import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;

public class StringListReader extends Reader {

  private List<String> strings = null;
  private boolean closed = false;

  private int charPos;
  private int stringListPos;

  private int markedCharPos = charPos;
  private int markedStringListPos = stringListPos;

  private boolean frozen = false;

  public StringListReader() {
    strings = new ArrayList<String>();
  }

  public void addString(String string) {
    if (frozen) {
      throw new IllegalStateException("Trying to add string after reading");
    }
    if (string.length() > 0) {
      strings.add(string);
    }
  }

  public void freeze() {
    if (frozen) {
      throw new IllegalStateException("Trying to freeze frozen StringListReader");
    }
    frozen = true;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    for (String string : this.strings) {
      builder.append(string);
    }
    return builder.toString();
  }

  @Override
  public void reset() throws IOException {
    charPos = markedCharPos;
    stringListPos = markedStringListPos;
  }

  private String currentString() {
    return (stringListPos < this.strings.size()) ? this.strings.get(stringListPos) : null;
  }

  private int currentStringRemainingChars() {
    String current = currentString();
    return (current == null) ? 0 : current.length() - charPos;
  }

  private void checkState() throws IOException {
    if (this.closed) {
      throw new IOException("Stream already closed");
    }
    if (!frozen) {
      throw new IOException("Reader needs to be frozen before read operations can be called");
    }
  }

  private long advance(long numChars) {
    long advanced = 0;
    while (stringListPos < strings.size() && advanced < numChars) {
      int remainingStringChars = currentStringRemainingChars();
      long remainingChars = numChars - advanced;
      if (remainingChars < remainingStringChars) {
        charPos = (int) (charPos + remainingChars);
        advanced += remainingChars;
      } else {
        advanced += remainingStringChars;
        charPos = 0;
        stringListPos++;
      }
    }
    return advanced;
  }

  @Override
  public int read(CharBuffer target) throws IOException {
    checkState();
    int remaining = target.remaining();
    int total = 0;
    String current = currentString();
    while (remaining > 0 && current != null) {
      int strLength = Math.min(current.length() - charPos, remaining);
      target.put(this.strings.get(stringListPos), charPos, charPos + strLength);
      remaining -= strLength;
      total += strLength;
      advance(strLength);
      current = currentString();
    }
    if (total > 0 || current != null) {
      return total;
    } else {
      return -1;
    }
  }

  @Override
  public int read() throws IOException {
    checkState();
    String current = currentString();
    if (current == null) {
      return -1;
    } else {
      char c = current.charAt(charPos);
      advance(1);
      return c;
    }
  }

  @Override
  public long skip(long n) throws IOException {
    checkState();
    return advance(n);
  }

  @Override
  public boolean ready() throws IOException {
    checkState();
    return true;
  }

  @Override
  public boolean markSupported() {
    return true;
  }

  @Override
  public void mark(int readAheadLimit) throws IOException {
    checkState();
    markedCharPos = charPos;
    markedStringListPos = stringListPos;
  }

  @Override
  public int read(char[] cbuf, int off, int len) throws IOException {
    checkState();
    int charsCopied = 0;
    String current = currentString();
    while (current != null && charsCopied < len) {
      int copyLength = Math.min(currentStringRemainingChars(), len - charsCopied);
      current.getChars(charPos, charPos + copyLength, cbuf, off + charsCopied);
      charsCopied += copyLength;
      advance(copyLength);
      current = currentString();
    }
    if (charsCopied > 0 || current != null) {
      return charsCopied;
    } else {
      return -1;
    }
  }

  @Override
  public void close() throws IOException {
    checkState();
    this.closed = true;
  }
}
