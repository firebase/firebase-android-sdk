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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class handles blocking write operations to the websocket. Given an opcode and some bytes, it
 * frames a message and sends it over the wire. The actual sending happens in a separate thread.
 */
class WebSocketWriter {

  private BlockingQueue<ByteBuffer> pendingBuffers;
  private final Random random = new Random();
  private volatile boolean stop = false;
  private boolean closeSent = false;
  private WebSocket websocket;
  private WritableByteChannel channel;
  private final Thread innerThread;

  WebSocketWriter(WebSocket websocket, String threadBaseName, int clientId) {
    innerThread =
        WebSocket.getThreadFactory()
            .newThread(
                new Runnable() {
                  @Override
                  public void run() {
                    runWriter();
                  }
                });

    WebSocket.getIntializer().setName(getInnerThread(), threadBaseName + "Writer-" + clientId);
    this.websocket = websocket;
    pendingBuffers = new LinkedBlockingQueue<ByteBuffer>();
  }

  void setOutput(OutputStream output) {
    channel = Channels.newChannel(output);
  }

  private ByteBuffer frameInBuffer(byte opcode, boolean masking, byte[] data) throws IOException {
    int headerLength = 2; // This is just an assumed headerLength, as we use a ByteArrayOutputStream
    if (masking) {
      headerLength += 4;
    }
    int length = data.length;
    if (length < 126) {
      // nothing add to header length
    } else if (length <= 65535) {
      headerLength += 2;
    } else {
      headerLength += 8;
    }
    ByteBuffer frame = ByteBuffer.allocate(data.length + headerLength);

    byte fin = (byte) 0x80;
    byte startByte = (byte) (fin | opcode);
    frame.put(startByte);

    int length_field;

    if (length < 126) {
      if (masking) {
        length = 0x80 | length;
      }
      frame.put((byte) length);
    } else if (length <= 65535) {
      length_field = 126;
      if (masking) {
        length_field = 0x80 | length_field;
      }
      frame.put((byte) length_field);
      // We check the size above, so we know we aren't losing anything with the cast
      frame.putShort((short) length);
    } else {
      length_field = 127;
      if (masking) {
        length_field = 0x80 | length_field;
      }
      frame.put((byte) length_field);
      // Since an integer occupies just 4 bytes we fill the 4 leading length bytes with zero
      frame.putInt(0);
      frame.putInt(length);
    }

    byte[] mask;
    if (masking) {
      mask = generateMask();
      frame.put(mask);

      for (int i = 0; i < data.length; i++) {
        frame.put((byte) (data[i] ^ mask[i % 4]));
      }
    }

    frame.flip();
    return frame;
  }

  private byte[] generateMask() {
    final byte[] mask = new byte[4];
    random.nextBytes(mask);
    return mask;
  }

  synchronized void send(byte opcode, boolean masking, byte[] data) throws IOException {
    ByteBuffer frame = frameInBuffer(opcode, masking, data);
    if (stop && (closeSent || opcode != WebSocket.OPCODE_CLOSE)) {
      throw new WebSocketException("Shouldn't be sending");
    }
    if (opcode == WebSocket.OPCODE_CLOSE) {
      closeSent = true;
    }
    pendingBuffers.add(frame);
  }

  private void writeMessage() throws InterruptedException, IOException {
    ByteBuffer msg = pendingBuffers.take();
    channel.write(msg);
  }

  void stopIt() {
    stop = true;
  }

  private void handleError(WebSocketException e) {
    websocket.handleReceiverError(e);
  }

  private void runWriter() {
    try {
      while (!stop && !Thread.interrupted()) {
        writeMessage();
      }
      // We're stopping, clear any remaining messages
      for (int i = 0; i < pendingBuffers.size(); ++i) {
        writeMessage();
      }
    } catch (IOException e) {
      handleError(new WebSocketException("IO Exception", e));
    } catch (InterruptedException e) {
      // this thread is regularly terminated via an interrupt
      // e.printStackTrace();
    }
  }

  Thread getInnerThread() {
    return innerThread;
  }
}
