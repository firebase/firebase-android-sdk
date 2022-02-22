package com.google.firebase.annotations;

import com.google.android.gms.common.annotation.KeepForSdk;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Indicates that this object (class, method, etc) is experimental and that both its signature and
 * implementation are subject to change. Any API marked with this annotation provides no guarantee
 * of API stability or backward-compatibility.
 */
@KeepForSdk
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface PreviewApi {
}
