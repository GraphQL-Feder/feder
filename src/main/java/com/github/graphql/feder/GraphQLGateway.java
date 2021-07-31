package com.github.graphql.feder;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import lombok.RequiredArgsConstructor;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import java.util.Map;

import static com.github.graphql.feder.GatewayExceptionMapper.map;

@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class GraphQLGateway implements GraphQLAPI {
    private final GraphQLSchema schema;

    @Override public String schema() {
        return new SchemaPrinter().print(schema);
    }

    @Override public GraphQLResponse request(GraphQLRequest request) {
        var graphQL = GraphQL.newGraphQL(schema).build();
        ExecutionResult executionResult = graphQL.execute(request.getQuery());

        return GraphQLResponse.builder()
            .data(json(executionResult.getData()))
            .errors(map(executionResult.getErrors()))
            .build();
    }

    private static JsonObject json(Map<String, Object> data) {
        return (data == null) ? null : Json.createObjectBuilder(data).build();
    }
}
