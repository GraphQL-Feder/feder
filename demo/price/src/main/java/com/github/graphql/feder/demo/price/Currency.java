package com.github.graphql.feder.demo.price;

import java.util.Locale;

public class Currency {
    public static final Currency EUR = new Currency("EUR");

    private final java.util.Currency currency;

    public Currency(String code) {
        this.currency = java.util.Currency.getInstance(code);
    }

    public String getCode() {return currency.getCurrencyCode();}

    public int getFractionDigits() {return currency.getDefaultFractionDigits();}

    public String getDisplayName(Locale locale) {return currency.getDisplayName(locale);}

    public String getNumericCode() {return currency.getNumericCodeAsString();}

    public String getSymbol(Locale locale) {return currency.getSymbol(locale);}
}
