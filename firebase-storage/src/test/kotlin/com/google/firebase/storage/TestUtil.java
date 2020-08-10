package com.google.firebase.storage;

import androidx.annotation.Nullable;
import java.util.List;

public class TestUtil {
    public static ListResult listResult(
            List<StorageReference> prefixes, List<StorageReference> items, @Nullable String pageToken) {
        return new ListResult(prefixes, items, pageToken);
    }
}