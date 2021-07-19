package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLGateway.GraphQLRequest;
import com.github.t1.wunderbar.junit.consumer.Service;
import com.github.t1.wunderbar.junit.consumer.SystemUnderTest;
import com.github.t1.wunderbar.junit.consumer.WunderBarApiConsumer;
import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import org.eclipse.microprofile.graphql.Query;
import org.junit.jupiter.api.Test;

import javax.json.bind.JsonbBuilder;
import java.util.List;

import static com.github.t1.wunderbar.junit.consumer.Level.INTEGRATION;
import static com.github.t1.wunderbar.junit.consumer.WunderBarApiConsumer.NONE;
import static com.github.t1.wunderbar.junit.consumer.WunderbarExpectationBuilder.given;
import static org.assertj.core.api.BDDAssertions.then;

@WunderBarApiConsumer(level = INTEGRATION, fileName = NONE)
class GraphQLGatewayTest {
    private static final Product PRODUCT = Product.builder().id("1").name("Table").description("Elegant designer table with four legs").build();

    @Service Products service;
    @SystemUnderTest GraphQLGateway gateway;

    @GraphQLClientApi
    public interface Products {
        @Query Product product(String id);
    }

    @Test
    void shouldProxyResponse() {
        given(service.product("1"))
            .whileSettingBaseUri(uri -> gateway.serviceUris = List.of(uri))
            .willReturn(PRODUCT);

        var response = gateway.graphql(GraphQLRequest.builder()
            .query("{product(id:\"1\"){id name description}}")
            .build());

        var product = JsonbBuilder.create().fromJson(response.data.get("product").toString(), Product.class);
        then(product).isEqualTo(PRODUCT);
    }
}
