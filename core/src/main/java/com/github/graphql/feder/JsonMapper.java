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
        switch (value.getValueType()) {
            case ARRAY:
                return map(value.asJsonArray());
            case OBJECT:
                return map(value.asJsonObject());
            case STRING:
                return ((JsonString) value).getString();
            case NUMBER:
                return ((JsonNumber) value).numberValue();
            case TRUE:
                return true;
            case FALSE:
                return false;
            case NULL:
                return null;
        }
        throw new UnsupportedOperationException("unexpected json value type " + value.getValueType());
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
