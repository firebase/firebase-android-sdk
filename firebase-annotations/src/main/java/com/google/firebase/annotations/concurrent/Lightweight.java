package com.google.firebase.annotations.concurrent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

/**
 * An executor/coroutine dispatcher for lightweight tasks that never block(on IO or other tasks).
 */
@Qualifier
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
public @interface Lightweight {}
