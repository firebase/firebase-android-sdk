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
import androidx.annotation.RestrictTo;
import com.google.firebase.database.snapshot.Node;

/**
 * The Transaction class encapsulates the functionality needed to perform a transaction on the data
 * at a location. <br>
 * <br>
 * To run a transaction, provide a {@link Handler} to {@link
 * DatabaseReference#runTransaction(com.google.firebase.database.Transaction.Handler)}. That handler
 * will be passed the current data at the location, and must return a {@link Result}. A {@link
 * Result} can be created using either {@link Transaction#success(MutableData)} or {@link
 * com.google.firebase.database.Transaction#abort()}.
 */
public class Transaction {

  /**
   * Instances of this class represent the desired outcome of a single run of a {@link Handler}'s
   * doTransaction method. The options are:
   *
   * <ul>
   *   <li>Set the data to the new value (success)
   *   <li>abort the transaction
   * </ul>
   *
   * Instances are created using {@link Transaction#success(MutableData)} or {@link
   * com.google.firebase.database.Transaction#abort()}.
   */
  public static class Result {

    private boolean success;
    private Node data;

    private Result(boolean success, Node data) {
      this.success = success;
      this.data = data;
    }

    /** @return Whether or not this result is a success */
    public boolean isSuccess() {
      return success;
    }

    /**
     * <strong>For internal use</strong>
     *
     * @hide
     * @return The data
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public Node getNode() {
      return data;
    }
  }

  /**
   * An object implementing this interface is used to run a transaction, and will be notified of the
   * results of the transaction.
   */
  public interface Handler {

    /**
     * This method will be called, <em>possibly multiple times</em>, with the current data at this
     * location. It is responsible for inspecting that data and returning a {@link Result}
     * specifying either the desired new data at the location or that the transaction should be
     * aborted. <br>
     * <br>
     * Since this method may be called repeatedly for the same transaction, be extremely careful of
     * any side effects that may be triggered by this method. In addition, this method is called
     * from within the Firebase Database library's run loop, so care is also required when accessing
     * data that may be in use by other threads in your application. <br>
     * <br>
     * Best practices for this method are to rely only on the data that is passed in.
     *
     * @param currentData The current data at the location. Update this to the desired data at the
     *     location
     * @return Either the new data, or an indication to abort the transaction
     */
    @NonNull
    public Result doTransaction(@NonNull MutableData currentData);

    /**
     * This method will be called once with the results of the transaction.
     *
     * @param error null if no errors occurred, otherwise it contains a description of the error
     * @param committed True if the transaction successfully completed, false if it was aborted or
     *     an error occurred
     * @param currentData The current data at the location or null if an error occurred
     */
    public void onComplete(
        @Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData);
  }

  /** @return A {@link Result} that aborts the transaction */
  @NonNull
  public static Result abort() {
    return new Result(false, null);
  }

  /**
   * @param resultData The desired data at the location
   * @return A {@link Result} indicating the new data to be stored at the location
   */
  @NonNull
  public static Result success(@NonNull MutableData resultData) {
    return new Result(true, resultData.getNode());
  }
}
