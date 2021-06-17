package com.google.firebase.dynamiclinks.internal;

import com.google.android.gms.common.api.Status;
import com.google.firebase.dynamiclinks.internal.DynamicLinkData;
import com.google.firebase.dynamiclinks.internal.ShortDynamicLinkImpl;

oneway interface IDynamicLinksCallbacks {
    void onGetDynamicLink(in Status status, in DynamicLinkData dynamicLinksData) = 0;
    void onCreateShortDynamicLink(in Status status, in ShortDynamicLinkImpl shortDynamicLink) = 1;
}
