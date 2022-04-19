// Copyright 2022 Google LLC
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

package com.google.firebase.firestore;

/**
 * Parameter for {@link FirebaseFirestore#runTransaction(TransactionOptions, Transaction.Function)}.
 */
public final class TransactionOptions {

  static final TransactionOptions DEFAULT = new TransactionOptions.Builder().build();
  static final int DEFAULT_MAX_ATTEMPTS_COUNT = 5;

  private final int maxAttempts;

  private TransactionOptions(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  /** A Builder for creating {@code TransactionOptions}. */
  public static final class Builder {
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS_COUNT;

    /** Constructs a new {@code TransactionOptions} Builder object. */
    public Builder() {}

    /**
     * Constructs a new {@code TransactionOptions} Builder based on an existing {@code
     * TransactionOptions} object.
     */
    public Builder(TransactionOptions options) {
      maxAttempts = options.maxAttempts;
    }

    /**
     * Set maximum number of attempts to commit, after which transaction fails. Default is 5.
     *
     * @return this builder
     */
    public Builder setMaxAttempts(int maxAttempts) {
      if (maxAttempts < 1) throw new IllegalArgumentException("Max attempts must be at least 1");
      this.maxAttempts = maxAttempts;
      return this;
    }


    /**
     * Build the {@code TransactionOptions} object.
     *
     * @return the built {@code TransactionOptions} object
     */
    public TransactionOptions build() {
      return new TransactionOptions(maxAttempts);
    }
  }

  /**
   * Get maximum number of attempts to commit, after which transaction fails. Default is 5.
   *
   * @return maximum number of attempts
   */
  public int getMaxAttempts() {
    return maxAttempts;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TransactionOptions that = (TransactionOptions) o;

    return maxAttempts == that.maxAttempts;
  }

  @Override
  public int hashCode() {
    return maxAttempts;
  }

  @Override
  public String toString() {
    return "TransactionOptions{" + "maxAttempts=" + maxAttempts + '}';
  }
}
