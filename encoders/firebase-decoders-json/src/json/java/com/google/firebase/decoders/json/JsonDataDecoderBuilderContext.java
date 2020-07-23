// Copyright 2020 Google LLC
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

package com.google.firebase.decoders.json;

import android.util.JsonReader;
import android.util.JsonToken;
import androidx.annotation.NonNull;
import com.google.firebase.decoders.DataDecoder;
import com.google.firebase.decoders.FieldRef;
import com.google.firebase.decoders.ObjectDecoder;
import com.google.firebase.decoders.TypeCreator;
import com.google.firebase.decoders.TypeToken;
import com.google.firebase.encoders.EncodingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class JsonDataDecoderBuilderContext implements DataDecoder {
  private Map<Class<?>, ObjectDecoder<?>> objectDecoders = new HashMap<>();
  private Map<TypeToken.ClassToken<?>, ObjectDecoderContextImpl<?>> objectDecoderContexts =
      new HashMap<>();
  private Map<TypeToken.ClassToken<?>, TypeCreator<?>> typeCreators = new HashMap<>();
  private JsonReader reader;

  JsonDataDecoderBuilderContext(@NonNull Map<Class<?>, ObjectDecoder<?>> objectDecoders) {
    this.objectDecoders = objectDecoders;
  }

  @NonNull
  @Override
  public <T> T decode(@NonNull InputStream input, @NonNull TypeToken<T> typeToken)
      throws IOException {
    this.reader = new JsonReader(new InputStreamReader(input, "UTF-8"));
    return decode(typeToken);
  }

  private <T> T decode(TypeToken<T> typeToken) throws IOException {
    if (typeToken instanceof TypeToken.ClassToken) {
      TypeToken.ClassToken<T> classToken = (TypeToken.ClassToken<T>) typeToken;
      return decodeClassToken(classToken);
    } else if (typeToken instanceof TypeToken.ArrayToken) {
      TypeToken.ArrayToken<T> arrayToken = (TypeToken.ArrayToken<T>) typeToken;
      return (T) decodeArrayToken(arrayToken);
    }
    throw new EncodingException("Unknown typeToken: " + typeToken);
  }

  private <T> T decodeClassToken(TypeToken.ClassToken<T> classToken) throws IOException {
    if (classToken.getRawType().isPrimitive()) {
      return decodePrimitive(classToken);
    } else if (isSingleValue(classToken)) {
      return decodeSingleValue(classToken);
    } else if (isMap(classToken)) {
      @SuppressWarnings("unchecked")
      T map = (T) decodeMap((TypeToken.ClassToken<? extends Map>) classToken);
      return map;
    } else if (isCollection(classToken)) {
      @SuppressWarnings("unchecked")
      T collection = (T) decodeCollection((TypeToken.ClassToken<? extends Collection>) classToken);
      return collection;
    } else {
      return decodeObject(classToken);
    }
  }

  private <T> T decodeArrayToken(TypeToken.ArrayToken<T> arrayToken) throws IOException {
    TypeToken<?> componentTypeToken = arrayToken.getComponentType();
    List<Object> list = new ArrayList<>();
    reader.beginArray();
    while (reader.hasNext()) {
      list.add(decode(componentTypeToken));
    }
    reader.endArray();
    return convertGenericListToArray(list, arrayToken);
  }

  private static <T, E> T convertGenericListToArray(
      List<Object> list, TypeToken.ArrayToken<T> arrayToken) {
    @SuppressWarnings("unchecked")
    TypeToken<E> componentTypeToken = (TypeToken<E>) arrayToken.getComponentType();
    if (componentTypeToken.getRawType().isPrimitive()) {
      return convertGenericListToPrimitiveArray(list, componentTypeToken.getRawType(), arrayToken);
    }
    @SuppressWarnings("unchecked") // Safe, list is not empty
    E[] arr = (E[]) Array.newInstance(componentTypeToken.getRawType(), list.size());
    @SuppressWarnings("unchecked") // Safe, because T == E[]
    T t = (T) list.toArray(arr);
    return t;
  }

  private static <T> T convertGenericListToPrimitiveArray(
      List<Object> list, Class<?> clazz, TypeToken.ArrayToken<T> arrayToken) {
    if (clazz.equals(int.class)) {
      int[] arr = new int[list.size()];
      for (int i = 0; i < list.size(); i++) {
        arr[i] = (int) list.get(i);
      }
      return (T) arr;
    } else if (clazz.equals(short.class)) {
      short[] arr = new short[list.size()];
      for (int i = 0; i < list.size(); i++) {
        arr[i] = (short) list.get(i);
      }
      return (T) arr;
    } else if (clazz.equals(long.class)) {
      long[] arr = new long[list.size()];
      for (int i = 0; i < list.size(); i++) {
        arr[i] = (long) list.get(i);
      }
      return (T) arr;
    } else if (clazz.equals(double.class)) {
      double[] arr = new double[list.size()];
      for (int i = 0; i < list.size(); i++) {
        arr[i] = (double) list.get(i);
      }
      return (T) arr;
    } else if (clazz.equals(float.class)) {
      float[] arr = new float[list.size()];
      for (int i = 0; i < list.size(); i++) {
        arr[i] = (float) list.get(i);
      }
      return (T) arr;
    } else if (clazz.equals(char.class)) {
      char[] arr = new char[list.size()];
      for (int i = 0; i < list.size(); i++) {
        arr[i] = (char) list.get(i);
      }
      return (T) arr;
    } else if (clazz.equals(boolean.class)) {
      boolean[] arr = new boolean[list.size()];
      for (int i = 0; i < list.size(); i++) {
        arr[i] = (boolean) list.get(i);
      }
      return (T) arr;
    }
    return null;
  }

  private <T> boolean isMap(TypeToken.ClassToken<T> classToken) {
    Class<T> clazz = classToken.getRawType();
    return Map.class.isAssignableFrom(clazz);
  }

  private <T> boolean isCollection(TypeToken.ClassToken<T> classToken) {
    Class<T> clazz = classToken.getRawType();
    return Collection.class.isAssignableFrom(clazz);
  }

  private <K, V, T extends Map<K, V>> T decodeMap(TypeToken.ClassToken<T> classToken)
      throws IOException {
    TypeToken<K> keyTypeToken = classToken.getTypeArguments().at(0);
    TypeToken<V> valTypeToken = classToken.getTypeArguments().at(1);
    if (!isSingleValue(keyTypeToken))
      throw new IllegalArgumentException(keyTypeToken + " cannot be used as Map key.");

    T map = newMap(classToken);
    reader.beginObject();
    while (reader.hasNext()) {
      String keyLiteral = reader.nextName();
      K key = getActualKey(keyLiteral, keyTypeToken.getRawType());
      V val = decode(valTypeToken);
      V replaced = map.put(key, val);
      if (replaced != null) {
        throw new IllegalArgumentException("duplicate key: " + key);
      }
    }
    reader.endObject();

    return map;
  }

  private <E, T extends Collection<E>> T decodeCollection(TypeToken.ClassToken<T> classToken)
      throws IOException {
    TypeToken<E> componentTypeToken = classToken.getTypeArguments().at(0);
    T collection = newCollection(classToken);
    reader.beginArray();
    while (reader.hasNext()) {
      collection.add(decode(componentTypeToken));
    }
    reader.endArray();
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <T> T newMap(TypeToken.ClassToken<T> classToken) {
    Class<T> clazz = classToken.getRawType();
    if (clazz.equals(HashMap.class) || clazz.equals(Map.class)) {
      return (T) new HashMap<>();
    } else if (clazz.equals(LinkedHashMap.class)) {
      return (T) new LinkedHashMap<>();
    } else if (clazz.equals(TreeMap.class) || clazz.equals(SortedMap.class)) {
      return (T) new TreeMap<>();
    }
    throw new EncodingException(clazz + " not supported.");
  }

  @SuppressWarnings("unchecked")
  private <T> T newCollection(TypeToken.ClassToken<T> classToken) {
    Class<T> clazz = classToken.getRawType();
    if (clazz.equals(ArrayList.class) || clazz.equals(List.class)) {
      return (T) new ArrayList<>();
    } else if (clazz.equals(ArrayDeque.class)
        || clazz.equals(Deque.class)
        || clazz.equals(Queue.class)) {
      return (T) new ArrayDeque<>();
    } else if (clazz.equals(PriorityQueue.class)) {
      return (T) new PriorityQueue<>();
    } else if (clazz.equals(HashSet.class) || clazz.equals(Set.class)) {
      return (T) new HashSet<>();
    } else if (clazz.equals(LinkedHashSet.class)) {
      return (T) new LinkedHashSet<>();
    } else if (clazz.equals(TreeSet.class) || clazz.equals(SortedSet.class)) {
      return (T) new TreeSet<>();
    }
    throw new EncodingException(classToken + " not supported.");
  }

  @SuppressWarnings("unchecked")
  private <K> K getActualKey(String keyLiteral, Class<K> clazz) {
    if (clazz.equals(Integer.class)) {
      return (K) (Integer) Integer.parseInt(keyLiteral);
    } else if (clazz.equals(Double.class)) {
      return (K) (Double) Double.parseDouble(keyLiteral);
    } else if (clazz.equals(Float.class)) {
      return (K) (Float) Float.parseFloat(keyLiteral);
    } else if (clazz.equals(Short.class)) {
      return (K) (Short) Short.parseShort(keyLiteral);
    } else if (clazz.equals(Long.class)) {
      return (K) (Long) Long.parseLong(keyLiteral);
    } else if (clazz.equals(Character.class)) {
      return (K) (Character) keyLiteral.charAt(0);
    } else if (clazz.equals(Byte.class)) {
      return (K) (Byte) Byte.parseByte(keyLiteral);
    } else if (clazz.equals(String.class)) {
      return (K) keyLiteral;
    } else {
      throw new EncodingException("Excepted Single Value Type. " + clazz + " was found.");
    }
  }

  private <T> T decodeObject(TypeToken.ClassToken<T> classToken) throws IOException {
    CreationContextImpl creationContext = decodeObjectContext(classToken);
    @SuppressWarnings("unchecked")
    // Safe, because typeToken and TypeCreator always have same type parameter
    TypeCreator<T> creator = (TypeCreator<T>) typeCreators.get(classToken);
    if (creator == null)
      throw new IllegalArgumentException(
          "TypeCreator of " + classToken.getRawType() + " is not register.");
    return (T) creator.create(creationContext);
  }

  private <T> CreationContextImpl decodeObjectContext(TypeToken.ClassToken<T> classToken)
      throws IOException {
    CreationContextImpl creationCtx = new CreationContextImpl();
    ObjectDecoderContextImpl<T> decoderCtx = getObjectDecodersCtx(classToken);
    reader.beginObject();
    while (reader.hasNext()) {
      String fieldName = reader.nextName();
      FieldRef<?> fieldRef = decoderCtx.getFieldRef(fieldName);
      if (reader.peek().equals(JsonToken.NULL)) {
        reader.nextNull();
        creationCtx.put(fieldRef, null);
      } else if (fieldRef instanceof FieldRef.Primitive) {
        decodePrimitive(fieldRef, creationCtx);
      } else if (isSingleValue(fieldRef)) {
        decodeSingleValue(fieldRef, creationCtx);
      } else if (fieldRef instanceof FieldRef.Boxed) {
        creationCtx.put(fieldRef, decode(fieldRef.getTypeToken()));
      }
    }
    reader.endObject();
    decoderCtx.decodeInlineObjIfAny(creationCtx);
    return creationCtx;
  }

  private <T> boolean isSingleValue(FieldRef<T> fieldRef) {
    TypeToken<T> typeToken = fieldRef.getTypeToken();
    return isSingleValue(typeToken);
  }

  private <T> boolean isSingleValue(TypeToken<T> typeToken) {
    if (typeToken instanceof TypeToken.ClassToken) {
      Class<?> clazz = ((TypeToken.ClassToken) typeToken).getRawType();
      return isSingleValue(clazz);
    }
    return false;
  }

  private <T> boolean isSingleValue(Class<T> clazz) {
    return clazz.equals(Character.class)
        || clazz.equals(Byte.class)
        || clazz.equals(Short.class)
        || clazz.equals(Integer.class)
        || clazz.equals(Long.class)
        || clazz.equals(Float.class)
        || clazz.equals(Double.class)
        || clazz.equals(String.class)
        || clazz.equals(Boolean.class);
  }

  private <T> void decodeSingleValue(FieldRef ref, CreationContextImpl creationContext)
      throws IOException {
    @SuppressWarnings("unchecked")
    TypeToken.ClassToken<T> classToken = (TypeToken.ClassToken<T>) ref.getTypeToken();
    creationContext.put(ref, decodeSingleValue(classToken));
  }

  // TODO: support Date
  @SuppressWarnings("unchecked")
  private <T> T decodeSingleValue(TypeToken.ClassToken<T> classToken) throws IOException {
    Class<T> clazz = classToken.getRawType();
    if (clazz.equals(Boolean.class)) {
      return (T) (Boolean) reader.nextBoolean();
    } else if (clazz.equals(Integer.class)) {
      return (T) (Integer) reader.nextInt();
    } else if (clazz.equals(Short.class)) {
      return (T) ((Short) ((Integer) reader.nextInt()).shortValue());
    } else if (clazz.equals(Long.class)) {
      return (T) (Long) reader.nextLong();
    } else if (clazz.equals(Double.class)) {
      return (T) (Double) reader.nextDouble();
    } else if (clazz.equals(Float.class)) {
      return (T) (Float) ((Double) reader.nextDouble()).floatValue();
    } else if (clazz.equals(String.class)) {
      return (T) reader.nextString();
    } else if (clazz.equals(Character.class)) {
      return (T) (Character) reader.nextString().charAt(0);
    } else {
      throw new IllegalArgumentException("Excepted Single Value Type. " + clazz + " was found.");
    }
  }

  // TODO: Avoid auto-boxing and un-boxing
  private <T> void decodePrimitive(FieldRef ref, CreationContextImpl creationContext)
      throws IOException {
    @SuppressWarnings("unchecked")
    TypeToken<T> typeToken = (TypeToken<T>) ref.getTypeToken();
    if (typeToken instanceof TypeToken.ClassToken) {
      @SuppressWarnings("unchecked")
      TypeToken.ClassToken<T> classToken = (TypeToken.ClassToken<T>) ref.getTypeToken();
      creationContext.put(ref, decodePrimitive(classToken));
    } else {
      throw new IllegalArgumentException(
          "FieldRef should contain ClassToken type.\n" + typeToken + " was found.");
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T decodePrimitive(TypeToken.ClassToken<T> classToken) throws IOException {
    Class<T> clazz = classToken.getRawType();
    if (clazz.equals(boolean.class)) {
      return (T) (Boolean) reader.nextBoolean();
    } else if (clazz.equals(int.class)) {
      return (T) (Integer) reader.nextInt();
    } else if (clazz.equals(short.class)) {
      return (T) (Short) ((Integer) reader.nextInt()).shortValue();
    } else if (clazz.equals(long.class)) {
      return (T) (Long) reader.nextLong();
    } else if (clazz.equals(double.class)) {
      return (T) (Double) reader.nextDouble();
    } else if (clazz.equals(float.class)) {
      return (T) (Float) ((Double) reader.nextDouble()).floatValue();
    } else if (clazz.equals(char.class)) {
      return (T) (Character) reader.nextString().charAt(0);
    } else {
      throw new IllegalArgumentException("Excepted primitive type. But " + clazz + " was found.");
    }
  }

  private <T> ObjectDecoderContextImpl<T> getObjectDecodersCtx(TypeToken.ClassToken<T> classToken) {
    if (objectDecoderContexts.containsKey(classToken)) {
      @SuppressWarnings("unchecked")
      // Safe,
      // because key and value of objectDecoderContexts always have the same actual type parameter
      ObjectDecoderContextImpl<T> decoderCxt =
          (ObjectDecoderContextImpl<T>) objectDecoderContexts.get(classToken);
      return decoderCxt;
    }
    ObjectDecoder objectDecoder = objectDecoders.get(classToken.getRawType());
    if (objectDecoder == null)
      throw new IllegalArgumentException(classToken.getRawType() + " is not register.");
    ObjectDecoderContextImpl<T> objectDecoderCtx = ObjectDecoderContextImpl.of(classToken);
    @SuppressWarnings("unchecked")
    // Safe, because creator and and classToken always have the same actual type parameter
    TypeCreator<T> creator = objectDecoder.decode(objectDecoderCtx);
    objectDecoderContexts.put(classToken, objectDecoderCtx);
    typeCreators.put(classToken, creator);
    return objectDecoderCtx;
  }
}
