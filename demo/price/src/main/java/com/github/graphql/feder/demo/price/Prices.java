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

    public String displayName(@Source Currency currency, String languageTag) {
        var locale = (languageTag == null) ? Locale.ROOT : Locale.forLanguageTag(languageTag);
        return currency.getDisplayName(locale);
    }

    public String symbol(@Source Currency currency, String languageTag) {
        var locale = (languageTag == null) ? Locale.ROOT : Locale.forLanguageTag(languageTag);
        return currency.getSymbol(locale);
    }

    @Description("human readable representation of the price")
    public String tag(@Source Price price, String languageTag) {
        var locale = (languageTag == null) ? Locale.ROOT : Locale.forLanguageTag(languageTag);
        return price.getTag(locale);
    }
}
