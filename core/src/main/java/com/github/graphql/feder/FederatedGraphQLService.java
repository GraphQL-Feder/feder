package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLAPI.GraphQLRequest;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.SelectedField;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Holds a {@link GraphQLSchema} and fetches data from the Federation <code>_entities</code> query.
 */
@Slf4j
class FederatedGraphQLService implements DataFetcher<Object> {
    private final String name; // TODO add a `@boundedContext` directive to all fields from this service
    @Getter private final GraphQLSchema schema;
    private final URI uri;
    private final GraphQLAPI client;
    private final String idFieldName;

    FederatedGraphQLService(SchemaBuilder schemaBuilder) {
        this.name = schemaBuilder.name;
        this.uri = schemaBuilder.uri;
        this.client = schemaBuilder.client;
        this.schema = schemaBuilder.build(this);
        this.idFieldName = "id"; // TODO derive from @key
    }

    @Override
    public Object get(DataFetchingEnvironment env) {
        var typename = ((GraphQLObjectType) env.getFieldType()).getName();
        var availableFields = schema.getObjectType(typename)
            .getFieldDefinitions().stream()
            .map(GraphQLFieldDefinition::getName)
            .collect(toList());
        var selectedFields = env.getSelectionSet()
            .getFields().stream()
            .map(SelectedField::getName)
            .filter(availableFields::contains)
            .collect(toSet());
        if (selectedFields.isEmpty() || selectedFields.equals(Set.of(idFieldName))) return new LinkedHashMap<>();
        var fragment = typename + "{" + (selectedFields.contains("__typename") ? "" : "__typename ") + String.join(" ", selectedFields) + "}";
        GraphQLRequest representationsRequest = GraphQLRequest.builder()
            .query("query($representations:[_Any!]!){_entities(representations:$representations){...on " + fragment + "}}")
            .variables(Json.createObjectBuilder()
                .add("representations", Json.createObjectBuilder()
                    .add("__typename", typename)
                    .add(idFieldName, env.getArgument(idFieldName).toString())
                    .build())
                .build())
            .build();

        var response = client.request(representationsRequest);

        if (response == null) throw new FederationServiceException("selecting " + selectedFields + " => null response");
        if (response.hasErrors()) throw new FederationServiceException(response.getErrors());
        if (response.getData() == null) throw new FederationServiceException("no data");
        var entities = response.getData().getJsonArray("_entities");
        if (entities == null) throw new FederationServiceException("no _entities");
        if (entities.isEmpty()) throw new FederationServiceException("empty _entities");
        if (entities.size() > 1) throw new FederationServiceException("multiple _entities");
        var entity = entities.get(0).asJsonObject();

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
        throw new UnsupportedOperationException("unexpected json value type " + value.getValueType());
    }

    private class FederationServiceException extends FederationException {
        public FederationServiceException(Object message) {super("[from service " + name + " at " + uri + "]: " + message);}
    }
}
