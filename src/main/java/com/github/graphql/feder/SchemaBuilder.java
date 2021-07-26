package com.github.graphql.feder;

import com.github.graphql.feder.GenericGraphQLAPI.GraphQLRequest;
import graphql.language.FieldDefinition;
import graphql.language.NamedNode;
import graphql.language.Node;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.scalar.GraphqlStringCoercing;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring.Builder;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static java.util.stream.Collectors.toSet;

@Slf4j
class SchemaBuilder {
    final URI uri;
    final GenericGraphQLAPI api;

    SchemaBuilder(URI uri) {
        this(uri, RestClientBuilder.newBuilder().baseUri(uri).build(GenericGraphQLAPI.class));
    }

    public SchemaBuilder(URI uri, GenericGraphQLAPI api) {
        this.uri = uri;
        this.api = api;
    }

    GraphQLSchema build(DataFetcher<?> representationFetcher) {
        TypeDefinitionRegistry typeDefinitionRegistry = buildSchema();

        var runtimeWiring = newRuntimeWiring();
        @SuppressWarnings("unchecked")
        var queries = (List<FieldDefinition>) typeDefinitionRegistry.getType("Query").orElseThrow().getChildren();
        queries.forEach(query ->
            runtimeWiring.type("Query", wire -> wire.dataFetcher(query.getName(), representationFetcher))
        );
        wireFederationDeclaration(runtimeWiring);

        return new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring.build());
    }

    private TypeDefinitionRegistry buildSchema() {
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(FEDERATION_SCHEMA + fetchSchema());
        var entities = typeDefinitionRegistry.types().values().stream()
            .filter(this::isEntity)
            .map(NamedNode::getName)
            .collect(toSet());
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

    private boolean isEntity(TypeDefinition<?> type) {
        return type.hasDirective("key") || (!STANDARD_TYPES.contains(type.getName()) && hasIdField(type));
    }

    private boolean hasIdField(TypeDefinition<?> typeDefinition) {
        return typeDefinition.getChildren().stream()
            .flatMap(this::typeNameFields)
            .anyMatch(type -> type.getName().equals("ID"));
    }

    private Stream<TypeName> typeNameFields(Node<?> typeNode) {
        return Stream.of(typeNode)
            .filter(node -> node instanceof FieldDefinition)
            .map(node -> ((FieldDefinition) node).getType())
            .filter(type -> type instanceof TypeName)
            .map(type -> (TypeName) type);
    }

    private static void wireFederationDeclaration(Builder runtimeWiring) {
        runtimeWiring.scalar(GraphQLScalarType.newScalar().name("_Any").coercing(new GraphqlStringCoercing()).build());
        runtimeWiring.scalar(GraphQLScalarType.newScalar().name("_FieldSet").coercing(new GraphqlStringCoercing()).build());
        runtimeWiring.directive("key", new SchemaDirectiveWiring() {});
        runtimeWiring.type(TypeRuntimeWiring.newTypeWiring("_Entity").typeResolver(env -> null));
    }

    private static final List<String> STANDARD_TYPES = List.of("Query", "Mutation", "Subscription");

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
