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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

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
      CreationContextImpl creationContext = decodeObject(classToken);
      @SuppressWarnings("unchecked")
      // Safe, because typeToken and TypeCreator always have same type parameter
      TypeCreator<T> creator = (TypeCreator<T>) typeCreators.get(classToken);
      if (creator == null)
        throw new IllegalArgumentException(
            "TypeCreator of " + classToken.getRawType() + " is not register.");
      return (T) creator.create(creationContext);

    } else if (typeToken instanceof TypeToken.ArrayToken) {
      // TODO: Change typeParameter T in ArrayToken<T> to represent component type.
      /**
       * reader.beginArray(); List<T> l = new LinkedList<>(); while reader.hasNext: T val =
       * decode(arrayToken.getComponentType()); l.add(val); reader.endArray(); return l.toArray();
       */
    }
    return null;
  }

  private <T> CreationContextImpl decodeObject(TypeToken.ClassToken<T> classToken)
      throws IOException {
    CreationContextImpl creationCtx = new CreationContextImpl();
    ObjectDecoderContextImpl<T> decoderCtx = getObjectDecodersCtx(classToken);
    reader.beginObject();
    while (reader.hasNext()) {
      String fieldName = reader.nextName();
      FieldRef<?> fieldRef = decoderCtx.getFieldRef(fieldName);
      if (reader.peek().equals(JsonToken.NULL)) {
        handleNullValue(fieldRef, creationCtx);
      } else if (fieldRef instanceof FieldRef.Primitive) {
        decodePrimitive(fieldRef, creationCtx);
      } else if (isSingleValue(fieldRef)) {
        decodeSingleValue(fieldRef, creationCtx);
      } else if (fieldRef instanceof FieldRef.Boxed) {
        creationCtx.put(fieldRef, decode(fieldRef.getTypeToken()));
      }
    }
    reader.endObject();
    return creationCtx;
  }

  private <T> void handleNullValue(FieldRef<T> fieldRef, CreationContextImpl creationCtx)
      throws IOException {
    reader.nextNull();
    if (fieldRef.isRequired())
      throw new IllegalArgumentException(fieldRef + " is required.\n" + "But null was found.");
    if (fieldRef instanceof FieldRef.Primitive && fieldRef.getDefaultValue() == null) {
      throw new IllegalArgumentException(
          fieldRef
              + " is optional primitive type.\n"
              + "Consider assign an non-null default value.");
    }
    creationCtx.put(fieldRef, fieldRef.getDefaultValue());
  }

  private <T> boolean isSingleValue(FieldRef<T> fieldRef) {
    TypeToken typeToken = fieldRef.getTypeToken();
    if (typeToken instanceof TypeToken.ClassToken) {
      Class<?> clazz = ((TypeToken.ClassToken) typeToken).getRawType();
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
    return false;
  }

  // TODO: support Date
  private <T> void decodeSingleValue(FieldRef ref, CreationContextImpl creationContext)
      throws IOException {
    TypeToken.ClassToken<?> classToken = (TypeToken.ClassToken<?>) ref.getTypeToken();
    Class<?> clazz = classToken.getRawType();
    if (clazz.equals(Boolean.class)) {
      creationContext.put(ref, (Boolean) reader.nextBoolean());
    } else if (clazz.equals(Integer.class)) {
      creationContext.put(ref, (Integer) reader.nextInt());
    } else if (clazz.equals(Short.class)) {
      creationContext.put(ref, (Short) ((Integer) reader.nextInt()).shortValue());
    } else if (clazz.equals(Long.class)) {
      creationContext.put(ref, (Long) reader.nextLong());
    } else if (clazz.equals(Double.class)) {
      creationContext.put(ref, (Double) reader.nextDouble());
    } else if (clazz.equals(Float.class)) {
      creationContext.put(ref, (Float) ((Double) reader.nextDouble()).floatValue());
    } else if (clazz.equals(String.class)) {
      creationContext.put(ref, reader.nextString());
    } else if (clazz.equals(Character.class)) {
      creationContext.put(ref, (Character) reader.nextString().charAt(0));
    }
  }

  private <T> void decodePrimitive(FieldRef ref, CreationContextImpl creationContext)
      throws IOException {
    TypeToken.ClassToken<?> classToken = (TypeToken.ClassToken<?>) ref.getTypeToken();
    Class<?> clazz = classToken.getRawType();

    if (clazz.equals(boolean.class)) {
      creationContext.put(ref, reader.nextBoolean());
    } else if (clazz.equals(int.class)) {
      creationContext.put(ref, reader.nextInt());
    } else if (clazz.equals(short.class)) {
      creationContext.put(ref, reader.nextInt());
    } else if (clazz.equals(long.class)) {
      creationContext.put(ref, reader.nextLong());
    } else if (clazz.equals(double.class)) {
      creationContext.put(ref, reader.nextDouble());
    } else if (clazz.equals(float.class)) {
      creationContext.put(ref, reader.nextDouble());
    } else if (clazz.equals(char.class)) {
      creationContext.put(ref, reader.nextString());
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
