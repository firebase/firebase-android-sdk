package com.google.firebase.annotations.concurrent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

/**
 * An executor/coroutine dispatcher for long running tasks including disk IO, heavy CPU
 * computations.
 */
@Qualifier
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
public @interface Background {}
