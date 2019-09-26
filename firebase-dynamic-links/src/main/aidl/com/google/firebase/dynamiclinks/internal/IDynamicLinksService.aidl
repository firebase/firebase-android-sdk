package com.google.firebase.dynamiclinks.internal;

import android.os.Bundle;
import com.google.firebase.dynamiclinks.internal.IDynamicLinksCallbacks;

interface IDynamicLinksService {
    void getDynamicLink(in IDynamicLinksCallbacks callback, in String dynamicLink) = 0;
    void createShortDynamicLink(in IDynamicLinksCallbacks callback, in Bundle parameters) = 1;
}
