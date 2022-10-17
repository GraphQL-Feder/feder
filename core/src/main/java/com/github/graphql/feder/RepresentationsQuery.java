package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLAPI.GraphQLRequest;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.SelectedField;
import jakarta.json.Json;
import lombok.Getter;

import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

class RepresentationsQuery {
    @Getter private final GraphQLRequest request;
    @Getter private final Set<String> selectedFieldNames;

    RepresentationsQuery(GraphQLSchema schema, String idFieldName, DataFetchingEnvironment env) {
        var typename = ((GraphQLObjectType) env.getFieldType()).getName();
        var availableFields = schema.getObjectType(typename)
            .getFieldDefinitions().stream()
            .map(GraphQLFieldDefinition::getName)
            .collect(toList());
        var selectedFields = env.getSelectionSet()
            .getFields().stream()
            .filter(selectedField -> availableFields.contains(selectedField.getName()))
            .collect(toSet());
        this.selectedFieldNames = selectedFields.stream().map(SelectedField::getName).collect(toSet());
        if (selectedFieldNames.isEmpty() || selectedFieldNames.equals(Set.of(idFieldName))) {
            this.request = null;
            return;
        }
        var fragment = typename + "{" + (selectedFieldNames.contains("__typename") ? "" : "__typename ") + toFragment(selectedFields) + "}";
        this.request = GraphQLRequest.builder()
            .query("query($representations:[_Any!]!){_entities(representations:$representations){...on " + fragment + "}}")
            .variables(Json.createObjectBuilder()
                .add("representations", Json.createObjectBuilder()
                    .add("__typename", typename)
                    .add(idFieldName, env.getArgument(idFieldName).toString())
                    .build())
                .build())
            .build();
    }

    private String toFragment(Set<SelectedField> selectedFields) {
        return selectedFields.stream().map(RepresentationsQuery::toFragment).collect(joining(" "));
    }

    private static String toFragment(SelectedField selectedField) {
        var out = new StringBuilder(selectedField.getName());
        var subFields = selectedField.getSelectionSet().getImmediateFields();
        if (!subFields.isEmpty())
            out.append(subFields.stream()
                .map(SelectedField::getName)
                .collect(Collectors.joining(" ", "{", "}")));
        if (!selectedField.getArguments().isEmpty()) {
            out.append("(").append("code").append(")");
        }
        return out.toString();
    }
}
