package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLGateway.GenericGraphQLAPI;
import com.github.graphql.feder.GraphQLGateway.GraphQLError;
import com.github.graphql.feder.GraphQLGateway.GraphQLRequest;
import com.github.graphql.feder.GraphQLGateway.GraphQLResponse;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.FieldDefinition;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.SelectedField;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import javax.json.Json;
import javax.json.JsonObject;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@RequiredArgsConstructor
class FederatedGraphQLService {
    private final GenericGraphQLAPI api;

    FederatedGraphQLService(URI uri) {
        this.api = RestClientBuilder.newBuilder().baseUri(uri).build(GenericGraphQLAPI.class);
    }

    public GraphQLResponse request(GraphQLRequest request) {
        var schema = schema();
        GraphQL build = GraphQL.newGraphQL(schema).build();
        ExecutionResult executionResult = build.execute(request.getQuery());

        Map<String, Object> rawData = executionResult.getData();
        JsonObject data = Json.createObjectBuilder(rawData).build();

        return GraphQLResponse.builder().data(data).errors(map(executionResult.getErrors())).build();
    }

    private GraphQLSchema schema() {
        var response = api.request(GraphQLRequest.builder().query("{_service{sdl}}").build());
        var string = response.getData().getJsonObject("_service").getString("sdl");
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(string);

        var runtimeWiring = newRuntimeWiring();
        @SuppressWarnings("unchecked")
        var queries = (List<FieldDefinition>) typeDefinitionRegistry.getType("Query").orElseThrow().getChildren();
        queries.forEach(query ->
            runtimeWiring.type("Query", wire -> wire.dataFetcher(query.getName(), this::fetch))
        );

        return new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring.build());
    }

    private Object fetch(DataFetchingEnvironment env) {
        var typename = ((GraphQLObjectType) env.getFieldType()).getName();
        var selectedFields = env.getSelectionSet().getFields().stream().map(SelectedField::getName).collect(toSet());
        var fragment = typename + "{" + (selectedFields.contains("__typename") ? "" : "__typename ") + String.join(" ", selectedFields) + "}";
        GraphQLRequest representationsRequest = GraphQLRequest.builder()
            .query("query($representations:[_Any!]!){_entities(representations:$representations){...on " + fragment + "}}")
            .variables(Json.createObjectBuilder()
                .add("representations", Json.createObjectBuilder()
                    .add("__typename", typename)
                    .add("id", env.getArgument("id").toString()) // TODO derive from @key
                    .build())
                .build())
            .build();

        var response = api.request(representationsRequest);
        var entity = response.getData().getJsonArray("_entities").get(0).asJsonObject();

        // GraphQL-Java doesn't like:
        // var out = Json.createObjectBuilder(entity);
        // if (!selectedFields.contains("__typename")) out.remove("__typename");
        // return out.build();
        var out = new LinkedHashMap<>();
        selectedFields.forEach(fieldName -> out.put(fieldName, entity.getString(fieldName)));
        return out;
    }

    private List<GraphQLError> map(List<graphql.GraphQLError> errors) {
        return (errors == null) ? null : errors.stream().map(this::maps).collect(toList());
    }

    private GraphQLError maps(graphql.GraphQLError graphQLError) {
        return GraphQLError.builder().message(graphQLError.getMessage()).build(); // TODO map other fields
    }
}
