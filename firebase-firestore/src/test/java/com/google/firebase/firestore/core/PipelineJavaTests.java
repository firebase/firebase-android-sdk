package com.google.firebase.firestore.core;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.model.Values.timestamp;
import static com.google.firebase.firestore.pipeline.Evaluation.minus;
import static com.google.firebase.firestore.pipeline.Evaluation.plus;

import com.google.protobuf.Timestamp;

import org.junit.Test;

/**
 * Testing of Timestamp must be written in Java.
 *
 * The is a bug where kotlin accessing internal methods from kotlin tests can fail to resolve
 * standard proto classes such as Timestamp. We overcome this by writing the tests in Java.
 *
 * Usage of library is no impeded since developers should not access internal functions directly.
 */
public class PipelineJavaTests {

  @Test
  public void xxx() {
    Timestamp zero = timestamp(0, 0);

    assertThat(plus(timestamp(1, 1000), 1, 1000)).isEqualTo(timestamp(2, 2000));

    assertThat(plus(zero, 0, 0))
        .isEqualTo(zero);

    assertThat(plus(timestamp(1, 1000), 1, 1000))
        .isEqualTo(timestamp(2, 2000));

    assertThat(plus(timestamp(1, 1000), 0, 1000))
        .isEqualTo(timestamp(1, 2000));

    assertThat(plus(timestamp(1, 1000), 1, 0))
        .isEqualTo(timestamp(2, 1000));

    assertThat(minus(zero, 0, 0))
        .isEqualTo(zero);

    assertThat(minus(timestamp(1, 1000), 1, 1000))
        .isEqualTo(zero);

    assertThat(minus(timestamp(1, 1000), 0, 1000))
        .isEqualTo(timestamp(1, 0));

    assertThat(minus(timestamp(1, 1000), 1, 0))
        .isEqualTo(timestamp(0, 1000));


    assertThat(plus(timestamp(-1, 1000), 1, 1000))
        .isEqualTo(timestamp(0, 2000));

    assertThat(plus(timestamp(-1, 1000), 0, 1000))
        .isEqualTo(timestamp(-1, 2000));

    assertThat(plus(timestamp(-1, 1000), 1, 0))
        .isEqualTo(timestamp(0, 1000));

    assertThat(minus(timestamp(-1, 1000), 1, 1000))
        .isEqualTo(timestamp(-2, 0));

    assertThat(minus(timestamp(-1, 1000), 0, 1000))
        .isEqualTo(timestamp(-1, 0));

    assertThat(minus(timestamp(-1, 1000), 1, 0))
        .isEqualTo(timestamp(-2, 1000));


    assertThat(plus(timestamp(-1, 1000), -1, 1000))
        .isEqualTo(timestamp(-2, 2000));

    assertThat(plus(timestamp(-1, 1000), 0, 1000))
        .isEqualTo(timestamp(-1, 2000));

    assertThat(plus(timestamp(-1, 1000), -1, 0))
        .isEqualTo(timestamp(-2, 1000));

    assertThat(minus(timestamp(-1, 1000), -1, 1000))
        .isEqualTo(timestamp(0, 0));

    assertThat(minus(timestamp(-1, 1000), 0, 1000))
        .isEqualTo(timestamp(-1, 0));

    assertThat(minus(timestamp(-1, 1000), -1, 0))
        .isEqualTo(timestamp(0, 1000));

    assertThat(plus(timestamp(1, 1000), -1, 1000))
        .isEqualTo(timestamp(0, 2000));

    assertThat(plus(timestamp(1, 1000), 0, 1000))
        .isEqualTo(timestamp(1, 2000));

    assertThat(plus(timestamp(1, 1000), -1, 0))
        .isEqualTo(timestamp(0, 1000));

    assertThat(minus(timestamp(1, 1000), -1, 1000))
        .isEqualTo(timestamp(2, 0));

    assertThat(minus(timestamp(1, 1000), 0, 1000))
        .isEqualTo(timestamp(1, 0));

    assertThat(minus(timestamp(1, 1000), -1, 0))
        .isEqualTo(timestamp(2, 1000));

  }
}
