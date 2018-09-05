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

import android.support.annotation.NonNull;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class stores running {@link StorageTask} objects so that developers can re attach callbacks
 * after an Activity lifecycle event.
 *
 * @hide
 */
@SuppressWarnings("JavaDoc")
/*package*/ class StorageTaskManager {
  private static final StorageTaskManager _instance = new StorageTaskManager();

  private final Map<String, WeakReference<StorageTask>> mInProgressTasks = new HashMap<>();

  private final Object mSyncObject = new Object();

  static StorageTaskManager getInstance() {
    return _instance;
  }

  public List<UploadTask> getUploadTasksUnder(@NonNull StorageReference parent) {
    synchronized (mSyncObject) {
      ArrayList<UploadTask> inProgressList = new ArrayList<>();
      String parentPath = parent.toString();
      for (Map.Entry<String, WeakReference<StorageTask>> entry : mInProgressTasks.entrySet()) {
        if (entry.getKey().startsWith(parentPath)) {
          StorageTask task = entry.getValue().get();
          if (task instanceof UploadTask) {
            inProgressList.add((UploadTask) task);
          }
        }
      }
      return Collections.unmodifiableList(inProgressList);
    }
  }

  public List<FileDownloadTask> getDownloadTasksUnder(@NonNull StorageReference parent) {
    synchronized (mSyncObject) {
      ArrayList<FileDownloadTask> inProgressList = new ArrayList<>();
      String parentPath = parent.toString();
      for (Map.Entry<String, WeakReference<StorageTask>> entry : mInProgressTasks.entrySet()) {
        if (entry.getKey().startsWith(parentPath)) {
          StorageTask task = entry.getValue().get();
          if (task instanceof FileDownloadTask) {
            inProgressList.add((FileDownloadTask) task);
          }
        }
      }
      return Collections.unmodifiableList(inProgressList);
    }
  }

  public void ensureRegistered(StorageTask targetTask) {
    synchronized (mSyncObject) {
      // ensure *this* is added to the in progress list
      mInProgressTasks.put(targetTask.getStorage().toString(), new WeakReference<>(targetTask));
    }
  }

  public void unRegister(StorageTask targetTask) {
    synchronized (mSyncObject) {
      // ensure *this* is added to the in progress list
      String key = targetTask.getStorage().toString();
      WeakReference<StorageTask> weakReference = mInProgressTasks.get(key);
      StorageTask task = weakReference != null ? weakReference.get() : null;
      if (task == null || task == targetTask) {
        mInProgressTasks.remove(key);
      }
    }
  }
}
