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

package com.google.firebase.storage;

import android.annotation.TargetApi;
import android.net.Uri;
import android.support.annotation.Nullable;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executor;

/** Test for commands. */
@TargetApi(26)
public class TestCommandHelper {

  private static final Executor executor = ExecutorProviderHelper.getInstance();

  public static Task<StringBuilder> testDownloadUrl() {
    final StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://project-5516366556574091405.appspot.com/flubbertest.txt");

    TaskCompletionSource<StringBuilder> result = new TaskCompletionSource<>();
    StringBuilder builder = new StringBuilder();
    builder.append("Getting Download Url.\n");
    Task<Uri> getDownloadUrl = ref.getDownloadUrl();

    getDownloadUrl.addOnCompleteListener(
        executor,
        task -> {
          builder.append("Received Download Url.\n");
          builder.append("getDownloadUrl:").append(task.getResult().toString());
          builder.append("\nonComplete:Success=\n").append(task.isSuccessful());
          result.setResult(builder);
        });
    return result.getTask();
  }

  private static Task<StringBuilder> getMetadata(StorageReference ref) {
    TaskCompletionSource<StringBuilder> result = new TaskCompletionSource<>();
    StringBuilder builder = new StringBuilder();
    builder.append("Getting Metadata.\n");
    Task<StorageMetadata> getMetadata = ref.getMetadata();
    getMetadata.addOnSuccessListener(
        executor,
        storageMetadata -> {
          builder.append("Received Metadata.\n");
          dumpMetadata(builder, storageMetadata);
          result.setResult(builder);
        });
    return result.getTask();
  }

  private static Task<StringBuilder> updateMetadata(
      StorageReference ref, StorageMetadata metadata) {
    TaskCompletionSource<StringBuilder> source = new TaskCompletionSource<>();

    StringBuilder builder = new StringBuilder();

    OnSuccessListener<StringBuilder> verifyCompletion =
        verifiedMetadata -> {
          builder.append(verifiedMetadata);
          source.setResult(builder);
        };

    OnSuccessListener<StorageMetadata> updateCompletion =
        updatedMetadata -> {
          builder.append("Updated Metadata.\n");
          dumpMetadata(builder, updatedMetadata);
          getMetadata(ref).addOnSuccessListener(executor, verifyCompletion);
        };

    OnSuccessListener<StringBuilder> getCompletion =
        originalMetadata -> {
          builder.append(originalMetadata);
          builder.append("Updating Metadata.\n");
          ref.updateMetadata(metadata).addOnSuccessListener(executor, updateCompletion);
        };

    getMetadata(ref).addOnSuccessListener(executor, getCompletion);

    return source.getTask();
  }

  public static Task<StringBuilder> testUpdateMetadata() {
    final StorageReference storage =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://project-5516366556574091405.appspot.com/flubbertest.txt");

    StorageMetadata.Builder metadataBuilder = new StorageMetadata.Builder();
    StorageMetadata metadata =
        metadataBuilder
            .setCustomMetadata("newKey", "newValue")
            .setCustomMetadata("newKey2", "newValue2")
            .build();

    return updateMetadata(storage, metadata);
  }

  public static Task<StringBuilder> testUnicodeMetadata() {
    final StorageMetadata unicodeMetadata =
        new StorageMetadata.Builder().setCustomMetadata("unicode", "☺").build();
    final StorageReference storage =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://project-5516366556574091405.appspot.com/flubbertest.txt");

    return updateMetadata(storage, unicodeMetadata);
  }

  public static Task<StringBuilder> testClearMetadata() {

    TaskCompletionSource<StringBuilder> source = new TaskCompletionSource<>();

    StorageMetadata fullMetadata =
        new StorageMetadata.Builder()
            .setCacheControl("cache-control")
            .setContentDisposition("content-disposition")
            .setContentEncoding("gzip")
            .setContentLanguage("de")
            .setCustomMetadata("key", "value")
            .setContentType("content-type")
            .build();

    StorageMetadata emptyMetadata =
        new StorageMetadata.Builder()
            .setCacheControl(null)
            .setContentDisposition(null)
            .setContentEncoding(null)
            .setContentLanguage(null)
            .setCustomMetadata("key", null)
            .setContentType(null)
            .build();

    StorageReference storage =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://project-5516366556574091405.appspot.com/flubbertest.txt");

    updateMetadata(storage, fullMetadata)
        .continueWithTask(
            fullMetadataTask -> {
              updateMetadata(storage, emptyMetadata)
                  .continueWith(
                      (updatedMetadataTask) -> {
                        fullMetadataTask.getResult().append(updatedMetadataTask.getResult());
                        source.setResult(fullMetadataTask.getResult());
                        return null;
                      });
              return null;
            });

    return source.getTask();
  }

  public static void dumpMetadata(final StringBuilder builder, @Nullable StorageMetadata metadata) {
    if (metadata == null) {
      builder.append("metadata:null\n");
      return;
    }
    builder.append("getBucket:").append(metadata.getBucket()).append("\n");
    builder.append("getCacheControl:").append(metadata.getCacheControl()).append("\n");
    builder.append("getContentDisposition:").append(metadata.getContentDisposition()).append("\n");
    builder.append("getContentEncoding:").append(metadata.getContentEncoding()).append("\n");
    builder.append("getContentLanguage:").append(metadata.getContentLanguage()).append("\n");
    builder.append("getContentType:").append(metadata.getContentType()).append("\n");
    builder.append("getName:").append(metadata.getName()).append("\n");
    builder.append("getPath:").append(metadata.getPath()).append("\n");
    builder.append("getMD5Hash:").append(metadata.getMd5Hash()).append("\n");
    builder.append("getGeneration:").append(metadata.getGeneration()).append("\n");
    builder.append("getMetadataGeneration:").append(metadata.getMetadataGeneration()).append("\n");
    builder.append("getSizeBytes:").append(Long.toString(metadata.getSizeBytes())).append("\n");
    builder.append("getReference:").append(metadata.getReference().getName()).append("\n");
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
    sdf.setTimeZone(TimeZone.getTimeZone("PST"));
    builder
        .append("getCreationTimeMillis:")
        .append(sdf.format(new Date(metadata.getCreationTimeMillis())))
        .append("\n");
    builder
        .append("getUpdatedTimeMillis:")
        .append(sdf.format(new Date(metadata.getUpdatedTimeMillis())))
        .append("\n");
    builder.append("Type:FILE\n");

    for (String key : asSortedList(metadata.getCustomMetadataKeys())) {
      builder.append(key).append(":").append(metadata.getCustomMetadata(key)).append("\n");
    }
  }

  public static <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
    List<T> list = new ArrayList<>(c);
    java.util.Collections.sort(list);
    return list;
  }

  public static Task<StringBuilder> deleteBlob() {
    final StringBuilder builder = new StringBuilder();

    builder.append("deleting.\n");
    StorageReference storage =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://project-5516366556574091405.appspot.com/flubbertest.txt");
    Task<Void> result = storage.delete();

    result.addOnSuccessListener(executor, deleteResult -> builder.append("onComplete.\n"));
    result.addOnFailureListener(
        executor,
        e -> {
          StorageException se = (StorageException) e;
          builder.append("onError.\n");
          builder
              .append(se.getErrorCode())
              .append(se.getCause() != null ? se.getCause().toString() : "no cause");
        });
    return result.continueWith(task -> builder);
  }
}
