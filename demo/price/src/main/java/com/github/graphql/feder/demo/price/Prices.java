package com.github.graphql.feder.demo.price;

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
}
