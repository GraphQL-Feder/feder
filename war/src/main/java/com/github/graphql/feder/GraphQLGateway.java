package com.github.graphql.feder;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;

import java.util.Map;

import static com.github.graphql.feder.GatewayExceptionMapper.map;

@Dependent
public class GraphQLGateway implements GraphQLAPI {
    @Inject
    GraphQLSchema schema;

    @Override public String schema() {
        return new SchemaPrinter().print(schema);
    }

    @Override public GraphQLResponse request(GraphQLRequest request) {
        var graphQL = GraphQL.newGraphQL(schema).build();
        var executionInput = ExecutionInput.newExecutionInput()
            .query(request.getQuery());
        request.variables().map(JsonMapper::map).ifPresent(executionInput::variables);
        request.operationName().ifPresent(executionInput::operationName);
        ExecutionResult executionResult = graphQL.execute(executionInput);

        return GraphQLResponse.builder()
            .data(json(executionResult.getData()))
            .errors(map(executionResult.getErrors()))
            .build();
    }

    private static JsonObject json(Map<String, Object> data) {
        return (data == null) ? null : Json.createObjectBuilder(data).build();
    }
}
