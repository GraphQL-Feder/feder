package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLAPI.GraphQLRequest;
import com.github.graphql.feder.GraphQLAPI.GraphQLResponse;
import com.github.t1.wunderbar.junit.consumer.Service;
import com.github.t1.wunderbar.junit.consumer.WunderBarApiConsumer;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static com.github.graphql.feder.GraphQLGatewayTest.RunMode.WITH_DIRECTIVES;
import static com.github.t1.wunderbar.junit.consumer.Level.INTEGRATION;
import static com.github.t1.wunderbar.junit.consumer.WunderBarApiConsumer.NONE;
import static com.github.t1.wunderbar.junit.consumer.WunderbarExpectationBuilder.given;
import static org.assertj.core.api.BDDAssertions.contentOf;
import static org.assertj.core.api.BDDAssertions.then;

@WunderBarApiConsumer(level = INTEGRATION, fileName = NONE)
class GraphQLGatewayTest {

    @Service
    GraphQLAPI products;
    @Service
    GraphQLAPI prices;

    GraphQLGateway gateway = new GraphQLGateway();

    enum RunMode implements Function<String, String> {
        WITH_DIRECTIVES {
            @Override public String directive(String directive) {return directive;}
        },
        WITHOUT_DIRECTIVES {
            @Override public String directive(String directive) {return "";}
        };

        @Override public String apply(String string) {
            return string.replaceAll("«(.*)»", directive("$1"));
        }

        public abstract String directive(String directive);
    }

    @Test
    void shouldGetSchema() {
        setupData(WITH_DIRECTIVES);
        setupSchema(products(), prices());

        var schema = gateway.schema();

        then(schema).isEqualTo(contentOf(file("src/test/resources/expected-schema.graphql")));
    }

    // TODO schema with non-null arguments
    // TODO schema with list arguments
    // TODO fail when selecting a field unknown to all backends
    // TODO partial results when some backends fail

    private File file(@SuppressWarnings("SameParameterValue") String relative) {
        var path = Path.of(".").normalize().toAbsolutePath();
        if (path.endsWith("target")) path = path.getParent(); // i.e. running in Quarkus Continuous Testing mode
        return path.resolve(relative).toFile();
    }

    @ParameterizedTest
    @EnumSource
    void shouldGetProduct(RunMode runMode) {
        setupData(runMode);
        setupSchema(products(), prices());

        var response = gateway.request("{product(id:\"1\"){id name}}", null);

        then(response.getErrors()).isNull();
        then(response.getData("product", Product.class)).isEqualTo(Product.builder().id("1").name("Table").build());
    }

    @ParameterizedTest
    @EnumSource
    void shouldGetProductPrice(RunMode runMode) {
        setupData(runMode);
        setupSchema(prices(), products());

        var response = gateway.request("{product(id:\"1\"){price{tag}}}", null);

        then(response.getErrors()).isNull();
        then(response.getData("product", Product.class)).isEqualTo(Product.builder()
            .price(Price.builder().tag("399.99 €").build())
            .build());
    }

    @ParameterizedTest
    @EnumSource
    void shouldGetProductNameAndPrice(RunMode runMode) {
        setupData(runMode);
        setupSchema(products(), prices());

        var response = gateway.request("{product(id:\"1\"){name price{tag}}}", null);

        then(response.getErrors()).isNull();
        then(response.getData("product", Product.class)).isEqualTo(
            Product.builder()
                .name("Table")
                .price(Price.builder().tag("399.99 €").build())
                .build());
    }

    void setupData(RunMode runMode) {
        setupProducts(runMode);
        setupPrices(runMode);
    }

    private void setupProducts(RunMode runMode) {
        givenSchema(products, """
            "Something you can buy"
            type Product «@key(fields: "id")»{
              id: ID
              name: String
              description: String
            }
                            
            "Query root"
            type Query {
              product(id: ID): Product
            }
            """.transform(runMode));
        givenRepresentation(products, "Product{__typename id name }", """
            "__typename": "Product",
            "id": "1",
            "name": "Table"
            """);
        givenRepresentation(products, "Product{__typename name }", """
            "__typename": "Product",
            "name": "Table"
            """);
    }

    private void setupPrices(RunMode runMode) {
        givenSchema(prices, """
            type Currency {
              code: String
              displayName(locale: String): String
              fractionDigits: Int!
              numericCode: String
              symbol(locale: String): String
            }
            
            type Product «@extends @key(fields: "id")»{
              id: ID« @external»
              "The price in cent"
              price: Price
            }
            
            type Price {
              currency: Currency
              "e.g. euros and cents. not all currency have exactly 2!"
              parts: [Int]
              "human readable representation of the price"
              tag(locale: String): String
            }
            
            "Query root"
            type Query {
              product(id: ID): Product
            }
            """.transform(runMode));
        givenRepresentation(prices, "Product{__typename price{__typename tag } }", """
            "__typename": "Product",
            "price": {"tag": "399.99 €"}
            """);
    }

    private static void givenSchema(GraphQLAPI service, String schema) {
        given(service.request(GraphQLRequest.builder()
            .query("{_service{sdl}}")
            .build())
        ).willReturn(GraphQLResponse.builder().data(parse(
            "{\n" +
            "    \"_service\": {\n" +
            "        \"sdl\": \"" + escape(schema) + "\"\n" +
            "    }\n" +
            "}")).build());
    }

    private static String escape(String schema) {
        return schema
            .replaceAll("\"", "\\\\\"")
            .replaceAll("\n", "\\\\n");
    }

    private static void givenRepresentation(GraphQLAPI service, String fragment, String data) {
        given(service.request(GraphQLRequest.builder()
            .query("query($representations:[_Any!]!) {_entities(representations:$representations){...on " + fragment + "}}")
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


    private void setupSchema(FederatedGraphQLService... services) {
        this.gateway.schema = new SchemaMerger(List.of(services)).merge();
    }

    private FederatedGraphQLService prices() {
        return service("price", "urn:mock:prices", prices);
    }

    private FederatedGraphQLService products() {
        return service("product", "urn:mock:products", products);
    }

    private FederatedGraphQLService service(String name, String uri, GraphQLAPI api) {
        return new FederatedGraphQLService(new FederatedSchemaBuilder(name, URI.create(uri), api));
    }
}
