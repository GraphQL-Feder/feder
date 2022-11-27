package com.github.graphql.feder.demo.review;

import org.eclipse.microprofile.graphql.Description;

@Description("Five-star rating")
public enum Stars {
    @Description("the minimum, totally bad")
    ONE,
    @Description("just mediocre")
    TWO,
    @Description("really okay")
    THREE,
    @Description("good, but some minor flaws")
    FOUR,
    @Description("the maximum, awesome")
    FIVE
}
