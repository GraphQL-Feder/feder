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

    @Service GenericGraphQLAPI products;
    @Service GenericGraphQLAPI prices;
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
    void shouldGetProduct(RunMode runMode) {
        setup(runMode);
        gateway.services = List.of(service("urn:mock:products", products), service("urn:mock:prices", prices));

        var response = gateway.graphql("{product(id:\"1\"){id name}}", null);

        then(response.getErrors()).isEmpty();
        then(response.getData("product", Product.class)).isEqualTo(Product.builder().id("1").name("Table").build());
    }

    @ParameterizedTest
    @EnumSource
    void shouldGetProductPrice(RunMode runMode) {
        setup(runMode);
        gateway.services = List.of(service("urn:mock:prices", prices), service("urn:mock:products", products));

        var response = gateway.graphql("{product(id:\"1\"){price}}", null);

        then(response.getErrors()).isEmpty();
        then(response.getData("product", ProductWithPrice.class)).isEqualTo(ProductWithPrice.builder().price(399_99).build());
    }

    void setup(RunMode runMode) {
        givenSchema(products,
            "\"Something you can buy\"\n" +
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
        givenSchema(prices,
            "type Product @extends @key(fields: \"id\") {\n" +
            "  id: String @external\n" +
            "  \"The price in cent\"\n" +
            "  price: Int\n" +
            "}\n" +
            "\n" +
            "\"Query root\"\n" +
            "type Query {\n" +
            "  product(id: String): Product\n" +
            "}\n");
        givenRepresentation(products, "Product{__typename name id}",
            "\"__typename\": \"Product\",\n" +
            "\"id\": \"1\",\n" +
            "\"name\": \"Table\"\n");
        givenRepresentation(prices, "Product{__typename price}",
            "\"__typename\": \"Product\",\n" +
            "\"price\": 39999\n");
    }

    private FederatedGraphQLService service(String uri, GenericGraphQLAPI api) {
        return new FederatedGraphQLService(new SchemaBuilder(URI.create(uri), api));
    }

    private static void givenSchema(GenericGraphQLAPI service, String schema) {
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

    private static void givenRepresentation(GenericGraphQLAPI service, String fragment, String data) {
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
            data +
            "        }\n" +
            "    ]\n" +
            "}"
        )).build());
    }

    private static JsonObject parse(String json) {
        return Json.createReader(new StringReader(json)).readObject();
    }
}
