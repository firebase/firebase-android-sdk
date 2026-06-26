/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.testutil

import java.net.InetSocketAddress
import java.net.SocketAddress

/**
 * The [SocketAddress] to specify to `OkHttpServerBuilder.forPort()` to have a server find to a
 * random available port on the loopback interface.
 *
 * This is better than specifying just a port number to `OkHttpServerBuilder.forPort()` because
 * without specifying the loopback interface it will (attempt to) bind to _all_ network interfaces,
 * which is both undesirable from a security standpoint and also wasteful work.
 */
fun loopbackAddressForPort(port: Int) = InetSocketAddress("127.0.0.1", port)
