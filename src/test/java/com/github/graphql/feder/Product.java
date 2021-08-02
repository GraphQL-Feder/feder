package com.github.graphql.feder;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data @SuperBuilder @NoArgsConstructor
public class Product {
    private String id;
    private String name;
    private String description;
    private Integer price;

    @Override public String toString() {
        return "Product" +
               ((id == null) ? "" : ":id=" + id) +
               ((name == null) ? "" : ":name=" + name) +
               ((description == null) ? "" : ":description=" + description) +
               ((price == null) ? "" : ":price=" + price / 100 + "." + price % 100);
    }
}
