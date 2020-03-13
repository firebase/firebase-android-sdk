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

package com.google.firebase.crashlytics.internal.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.crashlytics.internal.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class GzipFileNativeSessionProcessingStrategy2 {

    public static GzipFileNativeSessionProcessingStrategy2 create(File nativeSessionsFilesDir, String sessionId) {
        final File nativeSessionDirectory = new File(nativeSessionsFilesDir, sessionId);

        if (!nativeSessionDirectory.mkdirs()) {
            Logger.getLogger().d("Couldn't create native sessions directory");
            return null;
        }
        return new GzipFileNativeSessionProcessingStrategy2(nativeSessionDirectory);
    }

    private final File nativeSessionDirectory;

    private GzipFileNativeSessionProcessingStrategy2(File nativeSessionDirectory) {
        this.nativeSessionDirectory = nativeSessionDirectory;
    }

    public void processNativeSessions(List<NativeSessionStreamProvider> streams)
            throws IOException {

        for (NativeSessionStreamProvider stream : streams) {
            InputStream inputStream = null;
            try {
                inputStream = stream.getStream();
                gzipInputStream(inputStream, new File(nativeSessionDirectory, stream.getName()));
            } finally {
                CommonUtils.closeQuietly(inputStream);
            }
        }
    }

    private static void gzipInputStream(@Nullable InputStream input, @NonNull File output)
            throws IOException {
        if (input == null) {
            return;
        }
        byte[] buffer = new byte[1024];
        GZIPOutputStream gos = null;
        try {
            gos = new GZIPOutputStream(new FileOutputStream(output));

            int read;

            while ((read = input.read(buffer)) > 0) {
                gos.write(buffer, 0, read);
            }

            gos.finish();
        } finally {
            CommonUtils.closeQuietly(gos);
        }
    }
}
