package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLGateway.GenericGraphQLAPI;
import com.github.graphql.feder.GraphQLGateway.GraphQLRequest;
import com.github.graphql.feder.GraphQLGateway.GraphQLResponse;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.FieldDefinition;
import graphql.language.TypeDefinition;
import graphql.scalar.GraphqlStringCoercing;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.SelectedField;
import graphql.schema.idl.RuntimeWiring.Builder;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import javax.json.Json;
import javax.json.JsonObject;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.github.graphql.feder.GatewayExceptionMapper.map;
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static java.util.stream.Collectors.toSet;

@Slf4j
@RequiredArgsConstructor
class FederatedGraphQLService {
    private final GenericGraphQLAPI api;
    private URI uri;

    FederatedGraphQLService(URI uri) {
        this.uri = uri;
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
        TypeDefinitionRegistry typeDefinitionRegistry = buildSchema();

        var runtimeWiring = newRuntimeWiring();
        @SuppressWarnings("unchecked")
        var queries = (List<FieldDefinition>) typeDefinitionRegistry.getType("Query").orElseThrow().getChildren();
        queries.forEach(query ->
            runtimeWiring.type("Query", wire -> wire.dataFetcher(query.getName(), this::fetch))
        );
        wireFederationDeclaration(runtimeWiring);

        return new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring.build());
    }

    private TypeDefinitionRegistry buildSchema() {
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(FEDERATION_SCHEMA + fetchSchema());
        var entities = typeDefinitionRegistry.types().entrySet().stream().filter(this::isEntity).map(Entry::getKey).collect(toSet());
        TypeDefinitionRegistry additions = new SchemaParser().parse(
            "\"This is a union of all types that use the @key directive, including both types native to the schema and extended types.\"\n" +
            "union _Entity = " + String.join(" ", entities));
        typeDefinitionRegistry.merge(additions);
        return typeDefinitionRegistry;
    }

    private String fetchSchema() {
        try {
            GraphQLRequest request = GraphQLRequest.builder().query("{_service{sdl}}").build();
            var t0 = System.currentTimeMillis();
            var response = api.request(request);
            var t1 = System.currentTimeMillis();
            var sdl = response.getData().getJsonObject("_service").getString("sdl");
            log.debug("fetched schema from {} in {}ms:\n{}", uri, t1 - t0, sdl);
            return sdl;
        } catch (RuntimeException e) {
            throw new RuntimeException("can't fetch GraphQL schema from " + uri, e);
        }
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

        // GraphQL-Java doesn't like JsonObjects: it wraps strings in quotes
        // var out = Json.createObjectBuilder(entity);
        // if (!selectedFields.contains("__typename")) out.remove("__typename");
        // return out.build();
        var out = new LinkedHashMap<>();
        selectedFields.forEach(fieldName -> out.put(fieldName, entity.getString(fieldName))); // TODO other types
        return out;
    }

    private boolean isEntity(@SuppressWarnings("rawtypes") Entry<String, TypeDefinition> type) {
        return type.getValue().hasDirective("key");
    }

    private static void wireFederationDeclaration(Builder runtimeWiring) {
        runtimeWiring.scalar(GraphQLScalarType.newScalar().name("_Any").coercing(new GraphqlStringCoercing()).build());
        runtimeWiring.scalar(GraphQLScalarType.newScalar().name("_FieldSet").coercing(new GraphqlStringCoercing()).build());
        runtimeWiring.directive("key", new SchemaDirectiveWiring() {});
        runtimeWiring.type(TypeRuntimeWiring.newTypeWiring("_Entity").typeResolver(env -> null));
    }

    /** The static part of the federation declarations, i.e. without the <code>_Entity</code> union. */
    private static final String FEDERATION_SCHEMA =
        "scalar _Any\n" +
        "scalar _FieldSet\n" +
        "\n" +
        "type _Service {\n" +
        "  sdl: String\n" +
        "}\n" +
        "\n" +
        "extend type Query {\n" +
        "  _entities(representations: [_Any!]!): [_Entity]!\n" +
        "  _service: _Service!\n" +
        "}\n" +
        "\n" +
        "directive @external on FIELD_DEFINITION\n" +
        "directive @requires(fields: _FieldSet!) on FIELD_DEFINITION\n" +
        "directive @provides(fields: _FieldSet!) on FIELD_DEFINITION\n" +
        "directive @key(fields: _FieldSet!) repeatable on OBJECT | INTERFACE\n" +
        "\n" +
        "# this is an optional directive discussed below\n" +
        "directive @extends on OBJECT | INTERFACE\n\n";
}
