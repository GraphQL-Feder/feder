package com.github.graphql.feder.demo.price;

import io.smallrye.graphql.api.federation.Key;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.graphql.Id;

@Key(fields = "id")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Product {
    @Id String id;
    String name;
    String description;
}
