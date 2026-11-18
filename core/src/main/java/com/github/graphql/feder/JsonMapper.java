package com.github.graphql.feder;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toList;

class JsonMapper {
    static Object map(JsonValue value) {
        return switch (value.getValueType()) {
            case ARRAY -> map(value.asJsonArray());
            case OBJECT -> map(value.asJsonObject());
            case STRING -> ((JsonString) value).getString();
            case NUMBER -> ((JsonNumber) value).numberValue();
            case TRUE -> true;
            case FALSE -> false;
            case NULL -> null;
        };
    }

    private static List<Object> map(JsonArray value) {
        return value.stream()
            .map(JsonMapper::map)
            .collect(toList());
    }

    static Map<String, Object> map(JsonObject value) {
        // this is much simpler than using the streaming api and toMap
        Map<String, Object> map = new LinkedHashMap<>();
        value.keySet().forEach(key -> map.put(key, JsonMapper.map(value.get(key))));
        return map;
    }

    public static JsonValue toJson(Object value) {
        if (value == null) return JsonValue.NULL;
        if (value == TRUE) return JsonValue.TRUE;
        if (value == FALSE) return JsonValue.FALSE;
        return switch (value) {
            case String s -> Json.createValue(s);
            case Integer i -> Json.createValue(i);
            case Long l -> Json.createValue(l);
            case Double d -> Json.createValue(d);
            case BigInteger i -> Json.createValue(i);
            case BigDecimal d -> Json.createValue(d);
            case Collection<?> c -> toJson(c);
            case Map<?, ?> m ->
                //noinspection unchecked
                toJson((Map<String, Object>) m);
            default -> throw new IllegalArgumentException("can't map to json");
        };
    }

    public static JsonObject toJson(Map<String, Object> m) {
        var out = Json.createObjectBuilder();
        m.forEach((key, value) -> out.add(key, toJson(value)));
        return out.build();
    }

    public static JsonArray toJson(Collection<?> c) {
        var out = Json.createArrayBuilder();
        c.stream().map(JsonMapper::toJson).forEach(out::add);
        return out.build();
    }
}
