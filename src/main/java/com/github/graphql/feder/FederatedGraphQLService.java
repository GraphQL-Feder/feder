package com.github.graphql.feder;

import com.github.graphql.feder.GenericGraphQLAPI.GraphQLRequest;
import com.github.graphql.feder.GenericGraphQLAPI.GraphQLResponse;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;
import lombok.extern.slf4j.Slf4j;

import javax.json.Json;
import javax.json.JsonObject;
import java.util.Map;

import static com.github.graphql.feder.GatewayExceptionMapper.map;

@Slf4j
class FederatedGraphQLService {
    private final GraphQL graphql;
    private final GraphQLSchema schema;

    public FederatedGraphQLService(@SuppressWarnings("CdiInjectionPointsInspection") GraphQLSchema schema) {
        this.schema = schema;
        this.graphql = GraphQL.newGraphQL(schema).build();
    }

    GraphQLResponse request(GraphQLRequest request) {
        ExecutionResult executionResult = graphql.execute(request.getQuery());

        return GraphQLResponse.builder()
            .data(json(executionResult.getData()))
            .errors(map(executionResult.getErrors()))
            .build();
    }

    private static JsonObject json(Map<String, Object> data) { return Json.createObjectBuilder(data).build(); }

    public String getSchema() {
        return new SchemaPrinter().print(schema);
    }
}
