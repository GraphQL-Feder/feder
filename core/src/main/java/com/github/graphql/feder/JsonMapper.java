package com.github.graphql.feder;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
}
