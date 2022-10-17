package com.github.graphql.feder.demo.price;

import io.smallrye.graphql.api.federation.Key;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Id;

@Key(fields = "id")
@Data @SuperBuilder @NoArgsConstructor
public class Product {
    @Id String id;
    @Description("The price in cent")
    int price;
    Currency currency;
}
