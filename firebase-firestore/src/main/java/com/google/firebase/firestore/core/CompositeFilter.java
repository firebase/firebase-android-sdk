package com.google.firebase.firestore.core;

import com.google.firebase.firestore.Filter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.model.Document;
import java.util.ArrayList;
import java.util.List;

interface FieldFilterCondition {
  boolean check(FieldFilter field);
}

/** Represents a filter that is the conjunction or disjunction of other filters. */
public class CompositeFilter extends Filter {
  private final List<Filter> filters;
  private final boolean isAnd;

  public CompositeFilter(List<Filter> filters, boolean isAnd) {
    this.filters = filters;
    this.isAnd = isAnd;
  }

  public List<Filter> getFilters() {
    return filters;
  }

  public boolean isAnd() {
    return isAnd;
  }

  /**
   * Returns true if this is an AND filter and all the filters in this composite filter are
   * FieldFilters (i.e. it does not contain any other composite filters).
   */
  public boolean isFlatAndFilter() {
    return isAnd && !containsCompositeFilters();
  }

  /**
   * Returns true if this composite filter contains any other composite filter. Returns false
   * otherwise.
   */
  public boolean containsCompositeFilters() {
    for (Filter filter : filters) {
      if (filter instanceof CompositeFilter) {
        return true;
      }
    }
    return false;
  }

  /**
   * Performs a depth-first search to find and return the first FieldFilter in the composite filter
   * that satisfies the condition. Returns null if none of the FieldFilters satisfy the condition.
   */
  public FieldFilter firstFieldFilterWhere(FieldFilterCondition condition) {
    for (Filter filter : filters) {
      if (filter instanceof FieldFilter && condition.check(((FieldFilter) filter))) {
        return (FieldFilter) filter;
      } else if (filter instanceof CompositeFilter) {
        FieldFilter found = ((CompositeFilter) filter).firstFieldFilterWhere(condition);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /**
   * Returns the first inequality filter contained within this composite filter. Returns null if it
   * does not contain any inequalities.
   */
  public FieldFilter getInequalityFilter() {
    return firstFieldFilterWhere(f -> f.isInequality());
  }

  public CompositeFilter parseValue(Query query, FirebaseFirestore firestore) {
    List<Filter> parsedFilters = new ArrayList<>();
    for (Filter filter : filters) {
      if (filter instanceof FieldFilter) {
        parsedFilters.add(((FieldFilter) filter).parseValue(query, firestore));
      } else if (filter instanceof CompositeFilter) {
        parsedFilters.add(((CompositeFilter) filter).parseValue(query, firestore));
      }
    }
    return new CompositeFilter(parsedFilters, isAnd);
  }

  @Override
  public Query apply(Query query, FirebaseFirestore firestore) {
    CompositeFilter parsedFilter = parseValue(query, firestore);
    return query.filter(parsedFilter);
  }

  @Override
  public boolean matches(Document doc) {
    if (isAnd) {
      // For conjunctions, all filters must match, so return false if any filter doesn't match.
      for (Filter filter : filters) {
        if (!filter.matches(doc)) {
          return false;
        }
      }
      return true;
    } else {
      // For disjunctions, at least one filter should match.
      for (Filter filter : filters) {
        if (filter.matches(doc)) {
          return true;
        }
      }
      return false;
    }
  }

  @Override
  public String getCanonicalId() {
    StringBuilder builder = new StringBuilder();
    for (Filter filter : filters) {
      if (builder.length() == 0) {
        builder.append(isAnd ? "and(" : "or(");
      } else {
        builder.append(",");
      }
      builder.append(filter.getCanonicalId());
    }
    builder.append(")");
    return builder.toString();
  }
}
