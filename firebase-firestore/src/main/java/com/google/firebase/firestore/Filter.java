package com.google.firebase.firestore;

import com.google.firebase.annotations.PublicApi;
import com.google.firebase.firestore.core.NaNFilter;
import com.google.firebase.firestore.core.NullFilter;
import com.google.firebase.firestore.core.RelationFilter;

import javax.annotation.Nullable;

@PublicApi
public final class Filter {

    @PublicApi
    public enum Operator {
        LESS_THAN("<"),
        LESS_THAN_OR_EQUAL("<="),
        EQUAL("=="),
        GREATER_THAN(">"),
        GREATER_THAN_OR_EQUAL(">="),
        ARRAY_CONTAINS("array_contains");

        private final String text;

        Operator(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private final com.google.firebase.firestore.core.Filter filter;

    /** Initializes a filter */
    public Filter(com.google.firebase.firestore.core.Filter filter) {
        this.filter = filter;
    }

    @PublicApi
    public boolean isRelationFilter() {
        return filter instanceof RelationFilter;
    }

    @PublicApi
    public String getFieldPath() {
        return filter.getField().canonicalString();
    }

    @Nullable
    @PublicApi
    public Operator getOperator() {
        if (isRelationFilter()) {
            return ((RelationFilter) filter).getOperator();
        } else {
            return null;
        }
    }

    @Nullable
    @PublicApi
    public Object getFieldValue() {
        if (filter instanceof NaNFilter) {
            return Double.NaN;
        } else {
            if (isRelationFilter()) {
                return ((RelationFilter) filter).getValue().value();
            } else {
                return null;
            }
        }
    }


}
