package com.github.graphql.feder;

import com.github.graphql.feder.GenericGraphQLAPI.GraphQLRequest;
import com.github.graphql.feder.GenericGraphQLAPI.GraphQLResponse;
import com.github.t1.wunderbar.junit.consumer.Service;
import com.github.t1.wunderbar.junit.consumer.WunderBarApiConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.StringReader;
import java.net.URI;
import java.util.List;

import static com.github.t1.wunderbar.junit.consumer.Level.INTEGRATION;
import static com.github.t1.wunderbar.junit.consumer.WunderBarApiConsumer.NONE;
import static com.github.t1.wunderbar.junit.consumer.WunderbarExpectationBuilder.given;
import static org.assertj.core.api.BDDAssertions.then;

@WunderBarApiConsumer(level = INTEGRATION, fileName = NONE)
class GraphQLGatewayTest {

    @Service GenericGraphQLAPI service;
    GraphQLGateway gateway = new GraphQLGateway();

    enum RunMode {
        WITH_DIRECTIVES {
            @Override public String directive(String directive) { return directive; }
        },
        WITHOUT_DIRECTIVES {
            @Override public String directive(String directive) { return ""; }
        };

        public abstract String directive(String directive);
    }

    @ParameterizedTest
    @EnumSource
    void shouldProxyResponse(RunMode runMode) {
        setup(runMode);

        var response = gateway.graphql("{product(id:\"1\"){id name}}", null);

        then(response.getData("product", Product.class)).isEqualTo(Product.builder().id("1").name("Table").build());
    }

    void setup(RunMode runMode) {
        givenSchema("\"Something you can buy\"\n" +
                    "type Product " + runMode.directive("@key(fields: \"id\") ") + "{\n" +
                    "  id: ID\n" +
                    "  name: String\n" +
                    "  description: String\n" +
                    "}\n" +
                    "\n" +
                    "\"Query root\"\n" +
                    "type Query {\n" +
                    "  product(id: String): Product\n" +
                    "}\n");
        givenRepresentation("Product{__typename name id}");

        gateway.services = List.of(new FederatedGraphQLService(new SchemaBuilder(URI.create("urn:mock"), service)));
    }

    private void givenSchema(String schema) {
        given(service.request(GraphQLRequest.builder()
            .query("{_service{sdl}}")
            .build())
        ).willReturn(GraphQLResponse.builder().data(parse(
            "{\n" +
            "    \"_service\": {\n" +
            "        \"sdl\": \"" +
            schema
                .replaceAll("\"", "\\\\\"")
                .replaceAll("\n", "\\\\n") +
            "\"\n" +
            "    }\n" +
            "}")).build());
    }

    private void givenRepresentation(String fragment) {
        given(service.request(GraphQLRequest.builder()
            .query("query($representations:[_Any!]!){_entities(representations:$representations){...on " + fragment + "}}")
            .variables(Json.createObjectBuilder()
                .add("representations", Json.createObjectBuilder()
                    .add("__typename", "Product")
                    .add("id", "1")
                    .build())
                .build())
            .build())
        ).willReturn(GraphQLResponse.builder().data(parse(
            "{\n" +
            "    \"_entities\": [\n" +
            "        {\n" +
            "            \"__typename\": \"Product\",\n" +
            "            \"id\": \"1\",\n" +
            "            \"name\": \"Table\"\n" +
            "        }\n" +
            "    ]\n" +
            "}"
        )).build());
    }

    private static JsonObject parse(String json) {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
