package com.github.graphql.feder.demo.product;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;

@GraphQLApi
public class Products {
    @Query
    public Product product(@NonNull String id) {
        return Product.builder()
            .id(id)
            .name("Table")
            .description("A nice table with four legs")
            .build();
    }
}
