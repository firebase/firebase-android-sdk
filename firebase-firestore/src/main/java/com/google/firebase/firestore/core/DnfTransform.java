package com.google.firebase.firestore.core;

import java.util.Collections;
import java.util.List;

public class DnfTransform {
  /**
   * Given a composite filter, returns the list of terms in its disjunctive normal form. Each
   * element in the return value is one term of the resulting DNF. For instance, for the input: (A
   * || B) && C the DNF form is: (A && C) || (B && C) the return value is a list with two elements:
   * the first element is a composite filter that performs (A && C). the second element is a
   * composite filter that performs (B && C).
   *
   * @param filter the composite filter to calculate DNF transform for.
   * @return the terms in the DNF transform.
   */
  public static List<Filter> get(CompositeFilter filter) {
    // TODO(orquery): write the DNF transform algorithm here.
    // For now, assume all inputs are of the form AND(A, B, ...). Therefore the resulting DNF form
    // is the same as the input.
    if (filter.getFilters().isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.singletonList(filter);
  }
}
