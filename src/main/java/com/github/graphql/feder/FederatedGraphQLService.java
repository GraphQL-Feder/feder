package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLAPI.GraphQLRequest;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.SelectedField;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.util.LinkedHashMap;

import static java.util.stream.Collectors.toSet;

/**
 * Holds a {@link GraphQLSchema} and fetches data from the Federation <code>_entities</code> query.
 */
@Slf4j
class FederatedGraphQLService {
    @Getter private final GraphQLSchema schema;
    private final GraphQLAPI client;

    FederatedGraphQLService(@SuppressWarnings("CdiInjectionPointsInspection") SchemaBuilder schemaBuilder) {
        this.schema = schemaBuilder.build(this::fetchRepresentation);
        this.client = schemaBuilder.client;
    }

    private Object fetchRepresentation(DataFetchingEnvironment env) {
        var typename = ((GraphQLObjectType) env.getFieldType()).getName();
        var selectedFields = env.getSelectionSet().getFields().stream().map(SelectedField::getName).collect(toSet());
        var fragment = typename + "{" + (selectedFields.contains("__typename") ? "" : "__typename ") + String.join(" ", selectedFields) + "}";
        GraphQLRequest representationsRequest = GraphQLRequest.builder()
            .query("query($representations:[_Any!]!){_entities(representations:$representations){...on " + fragment + "}}")
            .variables(Json.createObjectBuilder()
                .add("representations", Json.createObjectBuilder()
                    .add("__typename", typename)
                    .add("id", env.getArgument("id").toString()) // TODO derive from @key
                    .build())
                .build())
            .build();

        var response = client.request(representationsRequest);
        var entity = response.getData().getJsonArray("_entities").get(0).asJsonObject();

        // GraphQL-Java doesn't like JsonObjects: it wraps strings in quotes
        // var out = Json.createObjectBuilder(entity);
        // if (!selectedFields.contains("__typename")) out.remove("__typename");
        // return out.build();
        var out = new LinkedHashMap<>();
        selectedFields.forEach(fieldName -> out.put(fieldName, mapField(entity.getValue("/" + fieldName))));
        return out;
    }

    private Object mapField(JsonValue value) {
        switch (value.getValueType()) {
            case ARRAY:
                // TODO map arrays: return map(value.asJsonArray());
            case OBJECT:
                // TODO map objects: return map(value.asJsonObject());
                break;
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
        throw new IllegalStateException("unexpected json value type " + value.getValueType());
    }
}
