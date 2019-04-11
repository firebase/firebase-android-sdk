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

package com.google.android.datatransport.cct;

import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.matching.MatchResult;
import com.github.tomakehurst.wiremock.matching.ValueMatcher;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ProtoMatchers {

  /** Creates a matcher that checks can be added to via {@link PredicateMatcher#test(Predicate)}. */
  public static <T extends MessageLite> PredicateMatcher<Request, T> protoMatcher(
      Class<T> messageType) {
    try {
      @SuppressWarnings("unchecked")
      T value = (T) messageType.getDeclaredMethod("getDefaultInstance").invoke(null);
      return new PredicateMatcher<>(
          req -> {
            try {
              @SuppressWarnings("unchecked")
              T parsed = (T) value.getParserForType().parseFrom(req.getBody());
              return parsed;
            } catch (InvalidProtocolBufferException e) {
              throw new RuntimeException(e);
            }
          });
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException(String.format("%s is not a protobuf type", messageType));
    }
  }

  /** Composable value matcher. */
  public static class PredicateMatcher<T, U> implements ValueMatcher<T> {
    private final Function<T, U> converter;
    private final Predicate<U> predicate;

    private PredicateMatcher(Function<T, U> converter) {
      this(converter, u -> true);
    }

    private PredicateMatcher(Function<T, U> converter, Predicate<U> predicate) {
      this.converter = converter;
      this.predicate = predicate;
    }

    /** Adds a test to the matcher. */
    public PredicateMatcher<T, U> test(Predicate<U> predicate) {
      return new PredicateMatcher<>(converter, this.predicate.and(predicate));
    }

    /**
     * Allows to zoom into a type.
     *
     * <p>For example, given a type and a matcher:
     *
     * <pre>{@code
     * class MyProto {
     *   MyNestedProto getNested();
     * }
     * PredicateMatcher<Request, MyProto> matcher = protoMatcher(MyProto.class);
     * matcher.test(myProto -> myProto != null)
     * }</pre>
     *
     * To get a matcher that tests the nested field, one can zoom on it as follows:
     *
     * <pre>{@code
     * PredicateMatcher<Request, MyNestedProto> matcher = protoMatcher(MyProto.class).zoom(MyProto::getNested);
     * matcher.test(myNestedProto -> myNestedProto != null);
     * }</pre>
     */
    public <V> PredicateMatcher<T, V> zoom(Function<U, V> lens) {
      return new PredicateMatcher<>(u -> lens.apply(converter.apply(u)));
    }

    @Override
    public MatchResult match(T value) {
      return MatchResult.of(predicate.test(converter.apply(value)));
    }
  }
}
