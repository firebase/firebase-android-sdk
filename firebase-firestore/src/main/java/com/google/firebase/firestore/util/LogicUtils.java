package com.google.firebase.firestore.util;

import com.google.firebase.firestore.core.CompositeFilter;
import com.google.firebase.firestore.core.Filter;
import java.util.Collections;
import java.util.List;

/**
 * Provides utility functions that help with boolean logic transformations needed for handling
 * complex filters used in queries.
 */
public class LogicUtils {
  /**
   * Given a composite filter, returns the list of terms in its disjunctive normal form.
   *
   * <p>Each element in the return value is one term of the resulting DNF.
   *
   * <p>For instance, for the input: (A || B) && C
   *
   * <p>The DNF form is: (A && C) || (B && C)
   *
   * <p>The return value is a list with two elements:
   *
   * <p>The first element is a composite filter that performs (A && C).
   *
   * <p>The second element is a composite filter that performs (B && C).
   *
   * @param filter the composite filter to calculate DNF transform for.
   * @return the terms in the DNF transform.
   */
  public static List<Filter> DnfTransform(CompositeFilter filter) {
    // TODO(orquery): write the DNF transform algorithm here.
    // For now, assume all inputs are of the form AND(A, B, ...). Therefore the resulting DNF form
    // is the same as the input.
    if (filter.getFilters().isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.singletonList(filter);
  }
}
