package com.github.graphql.feder.demo.review;

import io.smallrye.graphql.api.federation.Extends;
import io.smallrye.graphql.api.federation.Key;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.experimental.SuperBuilder;
import org.eclipse.microprofile.graphql.Id;

import java.util.List;

@Extends @Key(fields = "id")
@Data @SuperBuilder @NoArgsConstructor
public class Product {
    @Id String id;
    @Singular List<Review> reviews;
}
