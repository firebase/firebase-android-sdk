// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.network;

import com.google.firebase.perf.metrics.NetworkRequestMetricBuilder;
import com.google.firebase.perf.util.Timer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.security.Permission;
import java.util.List;
import java.util.Map;

/**
 * Collects network data from HttpURLConnection object. The HttpURLConnection request from the
 * developer's app is wrapped in these functions.
 */
public final class InstrHttpURLConnection extends HttpURLConnection {

  private final InstrURLConnectionBase delegate;

  /**
   * Instrumented HttpURLConnection object
   *
   * @param connection
   * @param timer
   */
  InstrHttpURLConnection(
      HttpURLConnection connection, Timer timer, NetworkRequestMetricBuilder builder) {
    super(connection.getURL());
    delegate = new InstrURLConnectionBase(connection, timer, builder);
  }

  @Override
  public void connect() throws IOException {
    delegate.connect();
  }

  @Override
  public void disconnect() {
    delegate.disconnect();
  }

  @Override
  public Object getContent() throws IOException {
    return delegate.getContent();
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Object getContent(final Class[] classes) throws IOException {
    return delegate.getContent(classes);
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return delegate.getInputStream();
  }

  @Override
  public long getLastModified() {
    return delegate.getLastModified();
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return delegate.getOutputStream();
  }

  @Override
  public Permission getPermission() throws IOException {
    return delegate.getPermission();
  }

  @Override
  public int getResponseCode() throws IOException {
    return delegate.getResponseCode();
  }

  @Override
  public String getResponseMessage() throws IOException {
    return delegate.getResponseMessage();
  }

  @Override
  public long getExpiration() {
    return delegate.getExpiration();
  }

  @Override
  public String getHeaderField(final int n) {
    return delegate.getHeaderField(n);
  }

  @Override
  public String getHeaderField(final String name) {
    return delegate.getHeaderField(name);
  }

  @Override
  public long getHeaderFieldDate(final String name, final long defaultDate) {
    return delegate.getHeaderFieldDate(name, defaultDate);
  }

  @Override
  public int getHeaderFieldInt(final String name, final int defaultInt) {
    return delegate.getHeaderFieldInt(name, defaultInt);
  }

  @Override
  public long getHeaderFieldLong(final String name, final long defaultLong) {
    return delegate.getHeaderFieldLong(name, defaultLong);
  }

  @Override
  public String getHeaderFieldKey(final int n) {
    return delegate.getHeaderFieldKey(n);
  }

  @Override
  public Map<String, List<String>> getHeaderFields() {
    return delegate.getHeaderFields();
  }

  @Override
  public String getContentEncoding() {
    return delegate.getContentEncoding();
  }

  @Override
  public int getContentLength() {
    return delegate.getContentLength();
  }

  @Override
  public long getContentLengthLong() {
    return delegate.getContentLengthLong();
  }

  @Override
  public String getContentType() {
    return delegate.getContentType();
  }

  @Override
  public long getDate() {
    return delegate.getDate();
  }

  @Override
  public void addRequestProperty(final String key, final String value) {
    delegate.addRequestProperty(key, value);
  }

  @Override
  public boolean equals(final Object obj) {
    return delegate.equals(obj);
  }

  @Override
  public boolean getAllowUserInteraction() {
    return delegate.getAllowUserInteraction();
  }

  @Override
  public int getConnectTimeout() {
    return delegate.getConnectTimeout();
  }

  @Override
  public boolean getDefaultUseCaches() {
    return delegate.getDefaultUseCaches();
  }

  @Override
  public boolean getDoInput() {
    return delegate.getDoInput();
  }

  @Override
  public boolean getDoOutput() {
    return delegate.getDoOutput();
  }

  @Override
  public InputStream getErrorStream() {
    return delegate.getErrorStream();
  }

  @Override
  public long getIfModifiedSince() {
    return delegate.getIfModifiedSince();
  }

  @Override
  public boolean getInstanceFollowRedirects() {
    return delegate.getInstanceFollowRedirects();
  }

  @Override
  public int getReadTimeout() {
    return delegate.getReadTimeout();
  }

  @Override
  public String getRequestMethod() {
    return delegate.getRequestMethod();
  }

  @Override
  public Map<String, List<String>> getRequestProperties() {
    return delegate.getRequestProperties();
  }

  @Override
  public String getRequestProperty(final String key) {
    return delegate.getRequestProperty(key);
  }

  @Override
  public URL getURL() {
    return delegate.getURL();
  }

  @Override
  public boolean getUseCaches() {
    return delegate.getUseCaches();
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public void setAllowUserInteraction(final boolean allowuserinteraction) {
    delegate.setAllowUserInteraction(allowuserinteraction);
  }

  @Override
  public void setChunkedStreamingMode(final int chunklen) {
    delegate.setChunkedStreamingMode(chunklen);
  }

  @Override
  public void setConnectTimeout(final int timeout) {
    delegate.setConnectTimeout(timeout);
  }

  @Override
  public void setDefaultUseCaches(final boolean defaultusecaches) {
    delegate.setDefaultUseCaches(defaultusecaches);
  }

  @Override
  public void setDoInput(final boolean doinput) {
    delegate.setDoInput(doinput);
  }

  @Override
  public void setDoOutput(final boolean dooutput) {
    delegate.setDoOutput(dooutput);
  }

  @Override
  public void setFixedLengthStreamingMode(final int contentLength) {
    delegate.setFixedLengthStreamingMode(contentLength);
  }

  @Override
  public void setFixedLengthStreamingMode(final long contentLength) {
    delegate.setFixedLengthStreamingMode(contentLength);
  }

  @Override
  public void setIfModifiedSince(final long ifmodifiedsince) {
    delegate.setIfModifiedSince(ifmodifiedsince);
  }

  @Override
  public void setInstanceFollowRedirects(final boolean followRedirects) {
    delegate.setInstanceFollowRedirects(followRedirects);
  }

  @Override
  public void setReadTimeout(final int timeout) {
    delegate.setReadTimeout(timeout);
  }

  @Override
  public void setRequestMethod(final String method) throws ProtocolException {
    delegate.setRequestMethod(method);
  }

  @Override
  public void setRequestProperty(final String key, final String value) {
    delegate.setRequestProperty(key, value);
  }

  @Override
  public void setUseCaches(final boolean usecaches) {
    delegate.setUseCaches(usecaches);
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  @Override
  public boolean usingProxy() {
    return delegate.usingProxy();
  }
}
