package com.github.graphql.feder.demo.review;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data @SuperBuilder @NoArgsConstructor
public class User {
    String slug;
}
