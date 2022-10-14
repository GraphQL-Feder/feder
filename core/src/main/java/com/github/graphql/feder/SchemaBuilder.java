package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLAPI.GraphQLRequest;
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
import java.util.Map;
import java.util.stream.Stream;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static java.util.stream.Collectors.toSet;

/**
 * Fetch the schema with a <code>{_service{sdl}}</code> query and convert it to a {@link GraphQLSchema} by adding
 * the Federation declarations.
 */
@Slf4j
class SchemaBuilder {
    static SchemaBuilder of(Map.Entry<String, URI> entry) {
        return new SchemaBuilder(entry.getKey(), entry.getValue());
    }

    final String name;
    final URI uri;
    final GraphQLAPI client;

    SchemaBuilder(String name, URI uri) {
        this(name, uri, RestClientBuilder.newBuilder().baseUri(uri).build(GraphQLAPI.class));
    }

    SchemaBuilder(String name, URI uri, GraphQLAPI client) {
        this.name = name;
        this.uri = uri;
        this.client = client;
    }

    GraphQLSchema build(DataFetcher<?> representationFetcher) {
        TypeDefinitionRegistry typeDefinitionRegistry = buildTypeDefinitions();

        var runtimeWiring = newRuntimeWiring();
        @SuppressWarnings("unchecked")
        var queries = (List<FieldDefinition>) typeDefinitionRegistry.getType("Query").orElseThrow().getChildren();
        queries.forEach(query ->
            runtimeWiring.type("Query", wire -> wire.dataFetcher(query.getName(), representationFetcher))
        );
        wireFederationDeclaration(runtimeWiring);

        return new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring.build());
    }

    private TypeDefinitionRegistry buildTypeDefinitions() {
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(FEDERATION_SCHEMA + fetchSchema());
        var entities = typeDefinitionRegistry.types().values().stream()
            .filter(this::isEntity)
            .map(NamedNode::getName)
            .collect(toSet());
        typeDefinitionRegistry.merge(new SchemaParser().parse(
            "union _Entity = " + String.join(" ", entities)));
        // TODO programmatic union type. something like: typeDefinitionRegistry.add(UnionTypeDefinition.newUnionTypeDefinition()
        //         .memberTypes(entities).build());
        return typeDefinitionRegistry;
    }

    private String fetchSchema() {
        try {
            GraphQLRequest request = GraphQLRequest.builder().query("{_service{sdl}}").build();
            var t0 = System.currentTimeMillis();
            var response = client.request(request);
            var t1 = System.currentTimeMillis();
            if (response == null) throw new SchemaFetchingException("null response");
            if (response.hasErrors()) throw new FederationException("errors from service " + uri + ": " + response.getErrors());
            if (response.getData() == null) throw new SchemaFetchingException("no data");
            if (response.getData().getJsonObject("_service") == null) throw new SchemaFetchingException("no _service");
            var sdl = response.getData().getJsonObject("_service").getString("sdl");
            log.debug("fetched schema from {} in {}ms:\n{}", uri, t1 - t0, sdl);
            return sdl;
        } catch (RuntimeException e) {
            throw new FederationException("can't fetch GraphQL schema from " + uri, e);
        }
    }

    private boolean isEntity(TypeDefinition<?> type) {
        return type.hasDirective("key") ||
               type.hasDirective("extends") ||
               (!STANDARD_TYPES.contains(type.getName()) && hasIdField(type));
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

    private class SchemaFetchingException extends FederationException {
        public SchemaFetchingException(String message) {super(message + " while fetching sdl from " + uri);}
    }
}
