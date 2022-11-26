package com.github.graphql.feder.demo.review;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;

@GraphQLApi
public class Reviews {
    @Query
    public Product product(@NonNull String id) {
        return Product.builder()
            .id(id)
            .review(Review.builder()
                .user(User.builder().slug("t1").build())
                .text("The best four-legged table I've ever seen")
                .build())
            .build();
    }
}
