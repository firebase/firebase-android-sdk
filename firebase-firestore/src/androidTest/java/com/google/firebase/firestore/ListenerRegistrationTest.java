// Copyright 2018 Google LLC
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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ListenerRegistrationTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  void test() {
    CollectionReference reference = testCollection();
    ListenerRegistration one = reference.addSnapshotListener((value, error) -> {});
    ListenerRegistration two = reference.document().addSnapshotListener((value, error) -> {});

    one.remove();
    one.remove();

    two.remove();
    two.remove();
  }

  @Test
  public void test1() {
    test();
  }

  @Test
  public void test2() {
    test();
  }

  @Test
  public void test34() {
    test();
  }

  @Test
  public void test4() {
    test();
  }

  @Test
  public void test5() {
    test();
  }

  @Test
  public void test6() {
    test();
  }

  @Test
  public void test7() {
    test();
  }

  @Test
  public void test8() {
    test();
  }

  @Test
  public void test9() {
    test();
  }

  @Test
  public void test10() {
    test();
  }

  @Test
  public void test11() {
    test();
  }

  @Test
  public void test12() {
    test();
  }

  @Test
  public void test13() {
    test();
  }

  @Test
  public void test14() {
    test();
  }

  @Test
  public void test15() {
    test();
  }

  @Test
  public void test16() {
    test();
  }

  @Test
  public void test17() {
    test();
  }

  @Test
  public void test18() {
    test();
  }

  @Test
  public void test19() {
    test();
  }

  @Test
  public void test20() {
    test();
  }

  @Test
  public void test21() {
    test();
  }

  @Test
  public void test22() {
    test();
  }

  @Test
  public void test23() {
    test();
  }

  @Test
  public void test24() {
    test();
  }

  @Test
  public void test25() {
    test();
  }

  @Test
  public void test26() {
    test();
  }

  @Test
  public void test27() {
    test();
  }

  @Test
  public void test28() {
    test();
  }

  @Test
  public void test29() {
    test();
  }

  @Test
  public void test30() {
    test();
  }
}
