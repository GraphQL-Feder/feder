package com.github.graphql.feder.demo.price;

import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;

import java.util.List;
import java.util.Locale;

@GraphQLApi
public class Prices {
    @Query
    public Product product(@NonNull String id) {
        return Product.builder()
            .id(id)
            .price(Price.builder()
                .parts(List.of(Integer.parseInt(id) * 10000 + 299, 99))
                .currency(Currency.EUR)
                .build())
            .build();
    }

    public String displayName(@Source Currency currency, String locale) {
        return currency.getDisplayName(parse(locale));
    }

    public String symbol(@Source Currency currency, String locale) {
        return currency.getSymbol(parse(locale));
    }

    @Description("human readable representation of the price")
    public String tag(@Source Price price, String locale) {
        return price.getTag(parse(locale));
    }

    private static Locale parse(String locale) {
        return (locale == null) ? Locale.ROOT : Locale.forLanguageTag(locale);
    }
}
