package com.github.graphql.feder;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data @SuperBuilder @NoArgsConstructor
public class Review {
    User user;
    Stars stars;
    String text;
}
