package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLGateway.GenericGraphQLAPI;
import com.github.graphql.feder.GraphQLGateway.GraphQLRequest;
import com.github.graphql.feder.GraphQLGateway.GraphQLResponse;
import com.github.t1.wunderbar.junit.consumer.Service;
import com.github.t1.wunderbar.junit.consumer.WunderBarApiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.StringReader;
import java.util.List;

import static com.github.t1.wunderbar.junit.consumer.Level.INTEGRATION;
import static com.github.t1.wunderbar.junit.consumer.WunderBarApiConsumer.NONE;
import static com.github.t1.wunderbar.junit.consumer.WunderbarExpectationBuilder.given;
import static org.assertj.core.api.BDDAssertions.then;

@WunderBarApiConsumer(level = INTEGRATION, fileName = NONE)
class GraphQLGatewayTest {
    private static final Product PRODUCT = Product.builder().id("1").name("Table")
        .description("Elegant designer table with four legs").build();

    @Service GenericGraphQLAPI service;
    GraphQLGateway gateway = new GraphQLGateway();

    @BeforeEach
    void setup() {
        gateway.services = List.of(service);

        given(service.request(GraphQLRequest.builder()
            .query("{product(id:\"1\"){id name description}}")
            .build())
        ).willReturn(GraphQLResponse.builder().data(parse(
            "{\n" +
            "    \"data\": {\n" +
            "        \"product\": {\n" +
            "            \"description\": \"Elegant designer table with four legs\",\n" +
            "            \"id\": \"1\",\n" +
            "            \"name\": \"Table\"\n" +
            "        }\n" +
            "    }\n" +
            "}"
        )).build());
    }

    private static JsonObject parse(String json) {
        return Json.createReader(new StringReader(json)).readObject();
    }

    @Test
    void shouldProxyResponse() {
        var response = gateway.graphql("{product(id:\"1\"){id name description}}", null);

        then(response.getData("product", Product.class)).isEqualTo(PRODUCT);
    }
}
