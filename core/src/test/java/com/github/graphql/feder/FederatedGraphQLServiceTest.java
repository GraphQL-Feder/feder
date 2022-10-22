package com.github.graphql.feder;

import graphql.GraphQL;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeRuntimeWiring.Builder;
import jakarta.json.Json;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.UnaryOperator;

import static graphql.ExecutionInput.newExecutionInput;
import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static org.assertj.core.api.BDDAssertions.then;

class FederatedGraphQLServiceTest {
    @Test
    void shouldBuildNoQueryWhenNoFieldsMatch() throws Exception {
        var client = new GraphQLAPI() {
            @Override public GraphQLResponse request(GraphQLRequest request) {
                then(request.query).isEqualTo(
                    "query($representations:[_Any!]!){_entities(representations:$representations)" +
                    "{...on Product{__typename description id name price{__typename tag } }}}");
                return GraphQLResponse.builder()
                    .data(Json.createReader(new StringReader("""
                        {"_entities": [
                            {
                                "__typename": "Product",
                                "description": "A nice table with four legs",
                                "id": "1",
                                "name": "Table",
                                "price": {
                                    "tag": "12.34€"
                                }
                            }
                        ]}
                        """)).readObject())
                    .build();
            }

            @Override public String schema() {
                return null;
            }
        };
        var sdl = Files.readString(Path.of("target/test-classes/expected-schema.graphql"));
        var graphQLSchema = SchemaGenerator.createdMockedSchema(sdl);
        var graphQL = givenGraphQL(sdl, builder -> builder.dataFetcher(
            "product", new FederatedGraphQLService(
                "product-service", graphQLSchema, URI.create("urn:dummy"), client, "id")));

        var result = graphQL.execute(newExecutionInput()
            .query("""
                query product($id: ID) {
                  product(id:$id) {
                    id name description price {
                      tag
                    }
                  }
                }
                """)
            .variables(Map.of("id", "1"))
            .build());

        then(result.getErrors()).isEmpty();
        then(result.<Map<?, ?>>getData()).isEqualTo(
            Map.of("product", Map.of(
                "id", "1",
                "name", "Table",
                "description", "A nice table with four legs",
                "price", Map.of("tag", "12.34€"))));
    }

    @Test
    void helloWorld() {
        var graphQL = givenGraphQL("type Query{hello: String}", builder -> builder.dataFetcher("hello", new StaticDataFetcher("world")));

        var result = graphQL.execute("{hello}");

        then(result.getData().toString()).isEqualTo("{hello=world}");
    }

    private GraphQL givenGraphQL(String schema, UnaryOperator<Builder> queryWiring) {
        var typeDefinitionRegistry = new SchemaParser().parse(schema);

        var runtimeWiring = newRuntimeWiring()
            .type("Query", queryWiring)
            .build();

        var graphQLSchema = new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

        return GraphQL.newGraphQL(graphQLSchema).build();
    }
}
