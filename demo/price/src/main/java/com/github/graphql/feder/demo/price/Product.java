package com.github.graphql.feder.demo.price;

import io.smallrye.graphql.api.federation.Extends;
import io.smallrye.graphql.api.federation.FieldSet;
import io.smallrye.graphql.api.federation.Key;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.eclipse.microprofile.graphql.Id;

@Extends @Key(fields = @FieldSet("id"))
@Data @SuperBuilder @NoArgsConstructor
public class Product {
    @Id String id;
    Price price;
}
