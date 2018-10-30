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

package io.grpc.okhttp;

/*
 * Copyright (C) 2013 Square, Inc.
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

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * An HTTP request. Instances of this class are immutable if their {@link #body} is null or itself
 * immutable.
 */
public final class Request {
  private final HttpUrl url;
  private final String method;
  private final Headers headers;
  private final RequestBody body;
  private final Object tag;

  private volatile URL javaNetUrl; // Lazily initialized.
  private volatile URI javaNetUri; // Lazily initialized.

  private Request(io.grpc.okhttp.Request.Builder builder) {
    this.url = builder.url;
    this.method = builder.method;
    this.headers = builder.headers.build();
    this.body = builder.body;
    this.tag = builder.tag != null ? builder.tag : this;
  }

  public HttpUrl httpUrl() {
    return url;
  }

  public URL url() {
    URL result = javaNetUrl;
    return result != null ? result : (javaNetUrl = url.url());
  }

  public URI uri() throws IOException {
    try {
      URI result = javaNetUri;
      return result != null ? result : (javaNetUri = url.uri());
    } catch (IllegalStateException e) {
      throw new IOException(e.getMessage());
    }
  }

  public String urlString() {
    return url.toString();
  }

  public String method() {
    return method;
  }

  public Headers headers() {
    return headers;
  }

  public String header(String name) {
    return headers.get(name);
  }

  public List<String> headers(String name) {
    return headers.values(name);
  }

  public RequestBody body() {
    return body;
  }

  public Object tag() {
    return tag;
  }

  public io.grpc.okhttp.Request.Builder newBuilder() {
    return new io.grpc.okhttp.Request.Builder(this);
  }

  public boolean isHttps() {
    return url.isHttps();
  }

  @Override
  public String toString() {
    return "Request{method="
        + method
        + ", url="
        + url
        + ", tag="
        + (tag != this ? tag : null)
        + '}';
  }

  public static class Builder {
    private HttpUrl url;
    private String method;
    private Headers.Builder headers;
    private RequestBody body;
    private Object tag;

    public Builder() {
      this.method = "GET";
      this.headers = new Headers.Builder();
    }

    private Builder(io.grpc.okhttp.Request request) {
      this.url = request.url;
      this.method = request.method;
      this.body = request.body;
      this.tag = request.tag;
      this.headers = request.headers.newBuilder();
    }

    public io.grpc.okhttp.Request.Builder url(HttpUrl url) {
      if (url == null) throw new IllegalArgumentException("url == null");
      this.url = url;
      return this;
    }

    /**
     * Sets the URL target of this request.
     *
     * @throws IllegalArgumentException if {@code url} is not a valid HTTP or HTTPS URL. Avoid this
     *     exception by calling {@link HttpUrl#parse}; it returns null for invalid URLs.
     */
    public io.grpc.okhttp.Request.Builder url(String url) {
      if (url == null) throw new IllegalArgumentException("url == null");

      // Silently replace websocket URLs with HTTP URLs.
      if (url.regionMatches(true, 0, "ws:", 0, 3)) {
        url = "http:" + url.substring(3);
      } else if (url.regionMatches(true, 0, "wss:", 0, 4)) {
        url = "https:" + url.substring(4);
      }

      HttpUrl parsed = HttpUrl.parse(url);
      if (parsed == null) throw new IllegalArgumentException("unexpected url: " + url);
      return url(parsed);
    }

    /**
     * Sets the URL target of this request.
     *
     * @throws IllegalArgumentException if the scheme of {@code url} is not {@code http} or {@code
     *     https}.
     */
    public io.grpc.okhttp.Request.Builder url(URL url) {
      if (url == null) throw new IllegalArgumentException("url == null");
      HttpUrl parsed = HttpUrl.get(url);
      if (parsed == null) throw new IllegalArgumentException("unexpected url: " + url);
      return url(parsed);
    }

    /**
     * Sets the header named {@code name} to {@code value}. If this request already has any headers
     * with that name, they are all replaced.
     */
    public io.grpc.okhttp.Request.Builder header(String name, String value) {
      headers.set(name, value);
      return this;
    }

    /**
     * Adds a header with {@code name} and {@code value}. Prefer this method for multiply-valued
     * headers like "Cookie".
     *
     * <p>Note that for some headers including {@code Content-Length} and {@code Content-Encoding},
     * OkHttp may replace {@code value} with a header derived from the request body.
     */
    public io.grpc.okhttp.Request.Builder addHeader(String name, String value) {
      headers.add(name, value);
      return this;
    }

    public io.grpc.okhttp.Request.Builder removeHeader(String name) {
      headers.removeAll(name);
      return this;
    }

    /** Removes all headers on this builder and adds {@code headers}. */
    public io.grpc.okhttp.Request.Builder headers(Headers headers) {
      this.headers = headers.newBuilder();
      return this;
    }

    public io.grpc.okhttp.Request.Builder get() {
      return method("GET", null);
    }

    public io.grpc.okhttp.Request.Builder head() {
      return method("HEAD", null);
    }

    public io.grpc.okhttp.Request.Builder post(RequestBody body) {
      return method("POST", body);
    }

    public io.grpc.okhttp.Request.Builder delete(RequestBody body) {
      return method("DELETE", body);
    }

    public io.grpc.okhttp.Request.Builder delete() {
      return delete(RequestBody.create(null, new byte[0]));
    }

    public io.grpc.okhttp.Request.Builder put(RequestBody body) {
      return method("PUT", body);
    }

    public io.grpc.okhttp.Request.Builder patch(RequestBody body) {
      return method("PATCH", body);
    }

    public io.grpc.okhttp.Request.Builder method(String method, RequestBody body) {
      if (method == null || method.length() == 0) {
        throw new IllegalArgumentException("method == null || method.length() == 0");
      }
      if (body != null && !HttpMethod.permitsRequestBody(method)) {
        throw new IllegalArgumentException("method " + method + " must not have a request body.");
      }
      if (body == null && HttpMethod.requiresRequestBody(method)) {
        throw new IllegalArgumentException("method " + method + " must have a request body.");
      }
      this.method = method;
      this.body = body;
      return this;
    }

    /**
     * Attaches {@code tag} to the request. It can be used later to cancel the request. If the tag
     * is unspecified or null, the request is canceled by using the request itself as the tag.
     */
    public io.grpc.okhttp.Request.Builder tag(Object tag) {
      this.tag = tag;
      return this;
    }

    public io.grpc.okhttp.Request build() {
      if (url == null) throw new IllegalStateException("url == null");
      return new io.grpc.okhttp.Request(this);
    }
  }

  public static final class HttpMethod {
    public static boolean invalidatesCache(String method) {
      return method.equals("POST")
          || method.equals("PATCH")
          || method.equals("PUT")
          || method.equals("DELETE")
          || method.equals("MOVE"); // WebDAV
    }

    public static boolean requiresRequestBody(String method) {
      return method.equals("POST")
          || method.equals("PUT")
          || method.equals("PATCH")
          || method.equals("PROPPATCH") // WebDAV
          || method.equals("REPORT"); // CalDAV/CardDAV (defined in WebDAV Versioning)
    }

    public static boolean permitsRequestBody(String method) {
      return requiresRequestBody(method)
          || method.equals("OPTIONS")
          || method.equals("DELETE") // Permitted as spec is ambiguous.
          || method.equals("PROPFIND") // (WebDAV) without body: request <allprop/>
          || method.equals("MKCOL") // (WebDAV) may contain a body, but behaviour is unspecified
          || method.equals("LOCK"); // (WebDAV) body: create lock, without body: refresh lock
    }

    public static boolean redirectsToGet(String method) {
      // All requests but PROPFIND should redirect to a GET request.
      return !method.equals("PROPFIND");
    }

    private HttpMethod() {}
  }

  /*
   *  Licensed to the Apache Software Foundation (ASF) under one or more
   *  contributor license agreements.  See the NOTICE file distributed with
   *  this work for additional information regarding copyright ownership.
   *  The ASF licenses this file to You under the Apache License, Version 2.0
   *  (the "License"); you may not use this file except in compliance with
   *  the License.  You may obtain a copy of the License at
   *
   *     http://www.apache.org/licenses/LICENSE-2.0
   *
   *  Unless required by applicable law or agreed to in writing, software
   *  distributed under the License is distributed on an "AS IS" BASIS,
   *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   *  See the License for the specific language governing permissions and
   *  limitations under the License.
   */

  public static final class Headers {
    private final String[] namesAndValues;

    private Headers(Builder builder) {
      this.namesAndValues =
          builder.namesAndValues.toArray(new String[builder.namesAndValues.size()]);
    }

    private Headers(String[] namesAndValues) {
      this.namesAndValues = namesAndValues;
    }

    /** Returns the last value corresponding to the specified field, or null. */
    public String get(String name) {
      return get(namesAndValues, name);
    }

    /**
     * Returns the last value corresponding to the specified field parsed as an HTTP date, or null
     * if either the field is absent or cannot be parsed as a date.
     */
    public Date getDate(String name) {
      String value = get(name);
      return value != null ? HttpDate.parse(value) : null;
    }

    /** Returns the number of field values. */
    public int size() {
      return namesAndValues.length / 2;
    }

    /** Returns the field at {@code position} or null if that is out of range. */
    public String name(int index) {
      int nameIndex = index * 2;
      if (nameIndex < 0 || nameIndex >= namesAndValues.length) {
        return null;
      }
      return namesAndValues[nameIndex];
    }

    /** Returns the value at {@code index} or null if that is out of range. */
    public String value(int index) {
      int valueIndex = index * 2 + 1;
      if (valueIndex < 0 || valueIndex >= namesAndValues.length) {
        return null;
      }
      return namesAndValues[valueIndex];
    }

    /** Returns an immutable case-insensitive set of header names. */
    public Set<String> names() {
      TreeSet<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      for (int i = 0, size = size(); i < size; i++) {
        result.add(name(i));
      }
      return Collections.unmodifiableSet(result);
    }

    /** Returns an immutable list of the header values for {@code name}. */
    public List<String> values(String name) {
      List<String> result = null;
      for (int i = 0, size = size(); i < size; i++) {
        if (name.equalsIgnoreCase(name(i))) {
          if (result == null) result = new ArrayList<>(2);
          result.add(value(i));
        }
      }
      return result != null
          ? Collections.unmodifiableList(result)
          : Collections.<String>emptyList();
    }

    public Builder newBuilder() {
      Builder result = new Builder();
      Collections.addAll(result.namesAndValues, namesAndValues);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder();
      for (int i = 0, size = size(); i < size; i++) {
        result.append(name(i)).append(": ").append(value(i)).append("\n");
      }
      return result.toString();
    }

    public Map<String, List<String>> toMultimap() {
      Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
      for (int i = 0, size = size(); i < size; i++) {
        String name = name(i);
        List<String> values = result.get(name);
        if (values == null) {
          values = new ArrayList<>(2);
          result.put(name, values);
        }
        values.add(value(i));
      }
      return result;
    }

    private static String get(String[] namesAndValues, String name) {
      for (int i = namesAndValues.length - 2; i >= 0; i -= 2) {
        if (name.equalsIgnoreCase(namesAndValues[i])) {
          return namesAndValues[i + 1];
        }
      }
      return null;
    }

    /**
     * Returns headers for the alternating header names and values. There must be an even number of
     * arguments, and they must alternate between header names and values.
     */
    public static Headers of(String... namesAndValues) {
      if (namesAndValues == null || namesAndValues.length % 2 != 0) {
        throw new IllegalArgumentException("Expected alternating header names and values");
      }

      // Make a defensive copy and clean it up.
      namesAndValues = namesAndValues.clone();
      for (int i = 0; i < namesAndValues.length; i++) {
        if (namesAndValues[i] == null) throw new IllegalArgumentException("Headers cannot be null");
        namesAndValues[i] = namesAndValues[i].trim();
      }

      // Check for malformed headers.
      for (int i = 0; i < namesAndValues.length; i += 2) {
        String name = namesAndValues[i];
        String value = namesAndValues[i + 1];
        if (name.length() == 0 || name.indexOf('\0') != -1 || value.indexOf('\0') != -1) {
          throw new IllegalArgumentException("Unexpected header: " + name + ": " + value);
        }
      }

      return new Headers(namesAndValues);
    }

    /** Returns headers for the header names and values in the {@link Map}. */
    public static Headers of(Map<String, String> headers) {
      if (headers == null) {
        throw new IllegalArgumentException("Expected map with header names and values");
      }

      // Make a defensive copy and clean it up.
      String[] namesAndValues = new String[headers.size() * 2];
      int i = 0;
      for (Map.Entry<String, String> header : headers.entrySet()) {
        if (header.getKey() == null || header.getValue() == null) {
          throw new IllegalArgumentException("Headers cannot be null");
        }
        String name = header.getKey().trim();
        String value = header.getValue().trim();
        if (name.length() == 0 || name.indexOf('\0') != -1 || value.indexOf('\0') != -1) {
          throw new IllegalArgumentException("Unexpected header: " + name + ": " + value);
        }
        namesAndValues[i] = name;
        namesAndValues[i + 1] = value;
        i += 2;
      }

      return new Headers(namesAndValues);
    }

    public static final class Builder {
      private final List<String> namesAndValues = new ArrayList<>(20);

      /**
       * Add a header line without any validation. Only appropriate for headers from the remote peer
       * or cache.
       */
      Builder addLenient(String line) {
        int index = line.indexOf(":", 1);
        if (index != -1) {
          return addLenient(line.substring(0, index), line.substring(index + 1));
        } else if (line.startsWith(":")) {
          // Work around empty header names and header names that start with a
          // colon (created by old broken SPDY versions of the response cache).
          return addLenient("", line.substring(1)); // Empty header name.
        } else {
          return addLenient("", line); // No header name.
        }
      }

      /** Add an header line containing a field name, a literal colon, and a value. */
      public Builder add(String line) {
        int index = line.indexOf(":");
        if (index == -1) {
          throw new IllegalArgumentException("Unexpected header: " + line);
        }
        return add(line.substring(0, index).trim(), line.substring(index + 1));
      }

      /** Add a field with the specified value. */
      public Builder add(String name, String value) {
        checkNameAndValue(name, value);
        return addLenient(name, value);
      }

      /**
       * Add a field with the specified value without any validation. Only appropriate for headers
       * from the remote peer or cache.
       */
      Builder addLenient(String name, String value) {
        namesAndValues.add(name);
        namesAndValues.add(value.trim());
        return this;
      }

      public Builder removeAll(String name) {
        for (int i = 0; i < namesAndValues.size(); i += 2) {
          if (name.equalsIgnoreCase(namesAndValues.get(i))) {
            namesAndValues.remove(i); // name
            namesAndValues.remove(i); // value
            i -= 2;
          }
        }
        return this;
      }

      /**
       * Set a field with the specified value. If the field is not found, it is added. If the field
       * is found, the existing values are replaced.
       */
      public Builder set(String name, String value) {
        checkNameAndValue(name, value);
        removeAll(name);
        addLenient(name, value);
        return this;
      }

      private void checkNameAndValue(String name, String value) {
        if (name == null) throw new IllegalArgumentException("name == null");
        if (name.isEmpty()) throw new IllegalArgumentException("name is empty");
        for (int i = 0, length = name.length(); i < length; i++) {
          char c = name.charAt(i);
          if (c <= '\u001f' || c >= '\u007f') {
            throw new IllegalArgumentException(
                String.format("Unexpected char %#04x at %d in header name: %s", (int) c, i, name));
          }
        }
        if (value == null) throw new IllegalArgumentException("value == null");
        for (int i = 0, length = value.length(); i < length; i++) {
          char c = value.charAt(i);
          if (c <= '\u001f' || c >= '\u007f') {
            throw new IllegalArgumentException(
                String.format(
                    "Unexpected char %#04x at %d in header value: %s", (int) c, i, value));
          }
        }
      }

      /** Equivalent to {@code build().get(name)}, but potentially faster. */
      public String get(String name) {
        for (int i = namesAndValues.size() - 2; i >= 0; i -= 2) {
          if (name.equalsIgnoreCase(namesAndValues.get(i))) {
            return namesAndValues.get(i + 1);
          }
        }
        return null;
      }

      public Headers build() {
        return new Headers(this);
      }
    }

    public static final class HttpDate {

      private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

      /**
       * Most websites serve cookies in the blessed format. Eagerly create the parser to ensure such
       * cookies are on the fast path.
       */
      private static final ThreadLocal<DateFormat> STANDARD_DATE_FORMAT =
          new ThreadLocal<DateFormat>() {
            @Override
            protected DateFormat initialValue() {
              // RFC 2616 specified: RFC 822, updated by RFC 1123 format with fixed GMT.
              DateFormat rfc1123 =
                  new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
              rfc1123.setLenient(false);
              rfc1123.setTimeZone(GMT);
              return rfc1123;
            }
          };

      /**
       * If we fail to parse a date in a non-standard format, try each of these formats in sequence.
       */
      private static final String[] BROWSER_COMPATIBLE_DATE_FORMAT_STRINGS =
          new String[] {
            // HTTP formats required by RFC2616 but with any timezone.
            "EEE, dd MMM yyyy HH:mm:ss zzz", // RFC 822, updated by RFC 1123 with any TZ
            "EEEE, dd-MMM-yy HH:mm:ss zzz", // RFC 850, obsoleted by RFC 1036 with any TZ.
            "EEE MMM d HH:mm:ss yyyy", // ANSI C's asctime() format
            // Alternative formats.
            "EEE, dd-MMM-yyyy HH:mm:ss z",
            "EEE, dd-MMM-yyyy HH-mm-ss z",
            "EEE, dd MMM yy HH:mm:ss z",
            "EEE dd-MMM-yyyy HH:mm:ss z",
            "EEE dd MMM yyyy HH:mm:ss z",
            "EEE dd-MMM-yyyy HH-mm-ss z",
            "EEE dd-MMM-yy HH:mm:ss z",
            "EEE dd MMM yy HH:mm:ss z",
            "EEE,dd-MMM-yy HH:mm:ss z",
            "EEE,dd-MMM-yyyy HH:mm:ss z",
            "EEE, dd-MM-yyyy HH:mm:ss z",

            /* RI bug 6641315 claims a cookie of this format was once served by www.yahoo.com */
            "EEE MMM d yyyy HH:mm:ss z",
          };

      private static final DateFormat[] BROWSER_COMPATIBLE_DATE_FORMATS =
          new DateFormat[BROWSER_COMPATIBLE_DATE_FORMAT_STRINGS.length];

      /** Returns the date for {@code value}. Returns null if the value couldn't be parsed. */
      public static Date parse(String value) {
        if (value.length() == 0) {
          return null;
        }

        ParsePosition position = new ParsePosition(0);
        Date result = STANDARD_DATE_FORMAT.get().parse(value, position);
        if (position.getIndex() == value.length()) {
          // STANDARD_DATE_FORMAT must match exactly; all text must be consumed, e.g. no ignored
          // non-standard trailing "+01:00". Those cases are covered below.
          return result;
        }
        synchronized (BROWSER_COMPATIBLE_DATE_FORMAT_STRINGS) {
          for (int i = 0, count = BROWSER_COMPATIBLE_DATE_FORMAT_STRINGS.length; i < count; i++) {
            DateFormat format = BROWSER_COMPATIBLE_DATE_FORMATS[i];
            if (format == null) {
              format = new SimpleDateFormat(BROWSER_COMPATIBLE_DATE_FORMAT_STRINGS[i], Locale.US);
              // Set the timezone to use when interpreting formats that don't have a timezone. GMT
              // is
              // specified by RFC 2616.
              format.setTimeZone(GMT);
              BROWSER_COMPATIBLE_DATE_FORMATS[i] = format;
            }
            position.setIndex(0);
            result = format.parse(value, position);
            if (position.getIndex() != 0) {
              // Something was parsed. It's possible the entire string was not consumed but we
              // ignore
              // that. If any of the BROWSER_COMPATIBLE_DATE_FORMAT_STRINGS ended in "'GMT'" we'd
              // have
              // to also check that position.getIndex() == value.length() otherwise parsing might
              // have
              // terminated early, ignoring things like "+01:00". Leaving this as != 0 means that
              // any
              // trailing junk is ignored.
              return result;
            }
          }
        }
        return null;
      }

      /** Returns the string for {@code value}. */
      public static String format(Date value) {
        return STANDARD_DATE_FORMAT.get().format(value);
      }

      private HttpDate() {}
    }
  }
}
