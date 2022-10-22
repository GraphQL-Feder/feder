package com.github.graphql.feder.demo.price;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.eclipse.microprofile.graphql.Description;

import java.util.List;
import java.util.Locale;

import static java.util.stream.Collectors.joining;

@Data @SuperBuilder @NoArgsConstructor
public class Price {
    @Description("e.g. euros and cents. not all currency have exactly 2!")
    List<Integer> parts;
    Currency currency;

    public String getTag(Locale locale) {
        return parts.stream().map(Object::toString).collect(joining(".")) + currency.getSymbol(locale);
    }
}
