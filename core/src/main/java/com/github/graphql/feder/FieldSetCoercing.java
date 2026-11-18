package com.github.graphql.feder;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.ArrayValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

class FieldSetCoercing implements Coercing<String, String> {
    @Override
    public String parseLiteral(@NonNull Value<?> input, @NonNull CoercedVariables variables, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) {
        if (input instanceof StringValue) return "[" + ((StringValue) input).getValue() + "]"; // TODO test
        if (!(input instanceof ArrayValue)) throw new CoercingParseLiteralException("Expected AST type 'ArrayValue' but was '" + input + "'.");
        var values = ((ArrayValue) input).getValues();
        values.forEach(value -> {
            if (!(value instanceof StringValue)) throw new CoercingParseLiteralException("Expected AST array type containing 'StringValue' but was '" + value + "'.");
        });
        return values.toString();
    }
}
