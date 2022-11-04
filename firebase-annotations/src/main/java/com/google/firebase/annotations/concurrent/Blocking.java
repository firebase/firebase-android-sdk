package com.google.firebase.annotations.concurrent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

/**
 * An executor/coroutine dispatcher for tasks that can block for long periods of time, e.g network
 * IO.
 */
@Qualifier
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
public @interface Blocking {}
