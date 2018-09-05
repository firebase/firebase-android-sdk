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

package com.google.firebase.database.tubesock;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;

/**
 * Instances provide a builder for a full WebSocketMessage that could be split across multiple
 * websocket frames. Depending on the opcode, the returned builders will buffer and assemble either
 * bytes or a String.
 */
class MessageBuilderFactory {

  interface Builder {
    boolean appendBytes(byte[] bytes);

    WebSocketMessage toMessage();
  }

  static class BinaryBuilder implements Builder {
    private List<byte[]> pendingBytes;
    private int pendingByteCount = 0;

    BinaryBuilder() {
      pendingBytes = new ArrayList<byte[]>();
    }

    @Override
    public boolean appendBytes(byte[] bytes) {
      pendingBytes.add(bytes);
      pendingByteCount += bytes.length;
      return true;
    }

    @Override
    public WebSocketMessage toMessage() {
      byte[] payload = new byte[pendingByteCount];
      int offset = 0;
      for (int i = 0; i < pendingBytes.size(); ++i) {
        byte[] segment = pendingBytes.get(i);
        System.arraycopy(segment, 0, payload, offset, segment.length);
        offset += segment.length;
      }
      return new WebSocketMessage(payload);
    }
  }

  static class TextBuilder implements Builder {
    private static ThreadLocal<CharsetDecoder> localDecoder =
        new ThreadLocal<CharsetDecoder>() {
          @Override
          protected CharsetDecoder initialValue() {
            Charset utf8 = Charset.forName("UTF8");
            CharsetDecoder decoder = utf8.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder;
          }
        };
    private static ThreadLocal<CharsetEncoder> localEncoder =
        new ThreadLocal<CharsetEncoder>() {
          @Override
          protected CharsetEncoder initialValue() {
            Charset utf8 = Charset.forName("UTF8");
            CharsetEncoder encoder = utf8.newEncoder();
            encoder.onMalformedInput(CodingErrorAction.REPORT);
            encoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            return encoder;
          }
        };

    private StringBuilder builder;
    private ByteBuffer carryOver;

    TextBuilder() {
      builder = new StringBuilder();
    }

    @Override
    public boolean appendBytes(byte[] bytes) {
      // Uncomment if you want slower but more precise decoding. Useful if you're splitting
      // multi-byte utf8 chars
      // across websocket frames
      // String nextFrame = decodeStringStreaming(bytes);
      String nextFrame = decodeString(bytes);
      if (nextFrame != null) {
        builder.append(nextFrame);
        return true;
      }
      return false;
    }

    @Override
    public WebSocketMessage toMessage() {
      if (carryOver != null) {
        return null;
      }
      return new WebSocketMessage(builder.toString());
    }

    /**
     * Quicker but less precise utf8 decoding. Does not handle characters split across websocket
     * frames.
     *
     * @param bytes Bytes representing a utf8 string
     * @return The decoded string
     */
    private String decodeString(byte[] bytes) {
      try {
        ByteBuffer input = ByteBuffer.wrap(bytes);
        CharBuffer buf = localDecoder.get().decode(input);
        String text = buf.toString();
        return text;
      } catch (CharacterCodingException e) {
        return null;
      }
    }

    /**
     * Left in for reference. Less efficient, but potentially catches more errors. Behavior is
     * largely dependent on how strict the JVM's utf8 decoder is. It is possible on some JVMs to
     * decode a string that then throws an error when attempting to return it to bytes.
     *
     * @param bytes Bytes representing a utf8 string
     * @return The decoded string
     */
    private String decodeStringStreaming(byte[] bytes) {
      try {
        ByteBuffer input = getBuffer(bytes);
        int bufSize = (int) (input.remaining() * localDecoder.get().averageCharsPerByte());
        CharBuffer output = CharBuffer.allocate(bufSize);
        for (; ; ) {
          CoderResult result = localDecoder.get().decode(input, output, false);
          if (result.isError()) {
            return null;
          }
          if (result.isUnderflow()) {
            break;
          }
          if (result.isOverflow()) {
            // We need more room in our output buffer
            bufSize = 2 * bufSize + 1;
            CharBuffer o = CharBuffer.allocate(bufSize);
            output.flip();
            o.put(output);
            output = o;
          }
        }
        if (input.remaining() > 0) {
          carryOver = input;
        }
        // Re-encode to work around bugs in UTF-8 decoder
        CharBuffer test = CharBuffer.wrap(output);
        localEncoder.get().encode(test);
        output.flip();
        String text = output.toString();
        return text;
      } catch (CharacterCodingException e) {
        return null;
      }
    }

    private ByteBuffer getBuffer(byte[] bytes) {
      if (carryOver != null) {
        ByteBuffer buffer = ByteBuffer.allocate(bytes.length + carryOver.remaining());
        buffer.put(carryOver);
        carryOver = null;
        buffer.put(bytes);
        buffer.flip();
        return buffer;
      } else {
        return ByteBuffer.wrap(bytes);
      }
    }
  }

  static Builder builder(byte opcode) {
    if (opcode == WebSocket.OPCODE_BINARY) {
      return new BinaryBuilder();
    } else {
      // Text
      return new TextBuilder();
    }
  }
}
