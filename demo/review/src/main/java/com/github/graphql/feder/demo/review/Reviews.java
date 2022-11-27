package com.github.graphql.feder.demo.review;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;

import static com.github.graphql.feder.demo.review.Stars.FIVE;

@GraphQLApi
public class Reviews {
    @Query
    public Product product(@NonNull String id) {
        return Product.builder()
            .id(id)
            .review(Review.builder()
                .user(User.builder().slug("t1").build())
                .stars(FIVE)
                .text("The best four-legged table I've ever seen")
                .build())
            .build();
    }
}
