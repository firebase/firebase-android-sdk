// Copyright 2021 Google LLC
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
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.annotations.Nullable;
import java.util.concurrent.ExecutionException;

public class DatabaseTransaction {

  private final com.google.firebase.database.core.Transaction transaction;
  private final FirebaseDatabase database;

  DatabaseTransaction(
      com.google.firebase.database.core.Transaction transaction, FirebaseDatabase database) {
    this.transaction = transaction;
    this.database = database;
  }

  @Nullable
  public DataSnapshot get(@NonNull Query query) throws FirebaseDatabaseException {
    try {
      return Tasks.await(transaction.get(query));
    } catch (ExecutionException | InterruptedException e) {
      if (e.getCause() instanceof FirebaseDatabaseException) {
        throw ((FirebaseDatabaseException) e.getCause());
      }
      throw new FirebaseDatabaseException(e.getMessage(), FirebaseDatabaseException.Code.User);
    }
  }

  @Nullable
  public void set(@NonNull DatabaseReference ref, @NonNull Object data) {
    transaction.set(ref, data);
  };

  @Nullable
  public void update(@NonNull DatabaseReference ref, @NonNull Object data) {};

  @Nullable
  public void remove(@NonNull DatabaseReference ref) {};

  public interface Function<T> {

    @Nullable
    T apply(@NonNull DatabaseTransaction transaction) throws FirebaseDatabaseException;
  }
}
