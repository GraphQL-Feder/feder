package com.github.graphql.feder.demo.price;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;

@GraphQLApi
public class Prices {
    @Query
    public @NonNull Product product(@NonNull String id) {
        return Product.builder()
            .id(id)
            .price(Integer.parseInt(id) * 10000 + 299_99)
            .build();
    }

    @Query
    public Sdl _service() {
        return new Sdl(
            "type Product {\n" +
            "  id: ID\n" +
            "  \"The price in cent\"\n" +
            "  price: Int\n" +
            "}\n" +
            "\n" +
            "\"Query root\"\n" +
            "type Query {\n" +
            "  product(id: ID): Product\n" +
            "}\n");
    }

    @Data @AllArgsConstructor
    public static class Sdl {
        String sdl;
    }
}
