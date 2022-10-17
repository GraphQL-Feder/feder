package com.github.graphql.feder.demo.price;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;
import org.eclipse.microprofile.graphql.Source;

import java.util.Locale;

@GraphQLApi
public class Prices {
    @Query
    public Product product(@NonNull String id) {
        return Product.builder()
            .id(id)
            .price(Integer.parseInt(id) * 10000 + 299_99)
            .currency(Currency.EUR)
            .build();
    }

    public String displayName(@Source Currency currency, String languageTag) {
        var locale = (languageTag == null) ? Locale.ROOT : Locale.forLanguageTag(languageTag);
        return currency.getDisplayName(locale);
    }

    public String symbol(@Source Currency currency, String languageTag) {
        var locale = (languageTag == null) ? Locale.ROOT : Locale.forLanguageTag(languageTag);
        return currency.getSymbol(locale);
    }
}
