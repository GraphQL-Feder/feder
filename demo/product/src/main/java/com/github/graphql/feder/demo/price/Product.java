package com.github.graphql.feder.demo.price;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.eclipse.microprofile.graphql.Id;

@Data @SuperBuilder @NoArgsConstructor
public class Product {
    @Id String id;
    String name;
    String description;
}
