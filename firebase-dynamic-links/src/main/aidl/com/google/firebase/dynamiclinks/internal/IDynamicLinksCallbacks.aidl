package com.google.firebase.dynamiclinks.internal;

import com.google.android.gms.common.api.Status;
import com.google.firebase.dynamiclinks.internal.DynamicLinkData;
import com.google.firebase.dynamiclinks.internal.ShortDynamicLinkImpl;
import javax.annotation.Nullable;

oneway interface IDynamicLinksCallbacks {
    void onGetDynamicLink(in Status status, @Nullable in DynamicLinkData dynamicLinksData) = 0;
    void onCreateShortDynamicLink(in Status status, @Nullable in ShortDynamicLinkImpl shortDynamicLink) = 1;
}
