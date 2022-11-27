package com.github.graphql.feder;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data @SuperBuilder @NoArgsConstructor
public class Product {
    private String id;
    private String name;
    private String description;
    private Price price;
    private List<Review> reviews;

    @Override public String toString() {
        return "Product" +
               ((id == null) ? "" : ":id=" + id) +
               ((name == null) ? "" : ":name=" + name) +
               ((description == null) ? "" : ":description=" + description) +
               ((price == null) ? "" : ":price=" + price.tag) +
               ((reviews == null) ? "" : ":reviews=" + reviews);
    }
}
