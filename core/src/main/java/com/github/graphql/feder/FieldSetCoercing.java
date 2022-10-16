package com.github.graphql.feder;

import graphql.language.ArrayValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;

class FieldSetCoercing implements Coercing<String, String> {
    @Override
    public String serialize(Object input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String parseValue(Object input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String parseLiteral(Object input) {
        if (input instanceof StringValue) return "[" + ((StringValue) input).getValue() + "]"; // TODO test
        if (!(input instanceof ArrayValue)) throw new CoercingParseLiteralException("Expected AST type 'ArrayValue' but was '" + input + "'.");
        var values = ((ArrayValue) input).getValues();
        values.forEach(value -> {
            if (!(value instanceof StringValue)) throw new CoercingParseLiteralException("Expected AST array type containing 'StringValue' but was '" + value + "'.");
        });
        return values.toString();
    }

    @Override
    public Value<?> valueToLiteral(Object input) {
        throw new UnsupportedOperationException();
    }
}
