package com.github.graphql.feder.demo;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;

@GraphQLApi
public class Products {
    @Query
    public @NonNull Product product(@NonNull String id) {
        return Product.builder()
            .id(id)
            .name("Table")
            .description("A nice table with four legs")
            .build();
    }

    @Query
    public Sdl _service() {
        return new Sdl(
            "type Product {\n" +
            "  id: ID\n" +
            "  name: String\n" +
            "  description: String\n" +
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
