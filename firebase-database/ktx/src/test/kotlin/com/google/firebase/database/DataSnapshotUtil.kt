// Copyright 2019 Google LLC
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

package com.google.firebase.database

import com.google.firebase.database.snapshot.IndexedNode
import com.google.firebase.database.snapshot.NodeUtilities

/**
 * Creates a custom DataSnapshot.
 *
 * This method is a workaround that enables the creation of a custom
 * DataSnapshot using package-private methods.
 */
fun createDataSnapshot(data: Any?, db: FirebaseDatabase): DataSnapshot {
    var ref = DatabaseReference("https://test.firebaseio.com", db.config)
    val node = NodeUtilities.NodeFromJSON(data)
    return DataSnapshot(ref, IndexedNode.from(node))
}

/**
 * Creates a custom MutableData.
 *
 * This method is a workaround that enables the creation of a custom
 * MutableData using package-private methods.
 */
fun createMutableData(data: Any?): MutableData {
    val node = NodeUtilities.NodeFromJSON(data)
    return MutableData(node)
}
