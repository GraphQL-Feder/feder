package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLAPI.GraphQLRequest;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.SelectedField;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

class EntitiesRequest {
    @Getter private final GraphQLRequest request;
    private final List<SelectedField> selectedFields;

    EntitiesRequest(GraphQLSchema schema, String idFieldName, DataFetchingEnvironment env) {
        var typename = ((GraphQLObjectType) env.getFieldType()).getName();
        var availableFields = schema.getObjectType(typename)
            .getFieldDefinitions().stream()
            .map(GraphQLFieldDefinition::getName)
            .toList();
        this.selectedFields = env.getSelectionSet()
            .getFields().stream()
            .filter(selectedField -> availableFields.contains(selectedField.getName()))
            .sorted(comparing(SelectedField::getName))
            .collect(toList());
        if (selectedFields.isEmpty() || selectedFields.size() == 1 && selectedFields.get(0).getName().equals(idFieldName)) {
            this.request = null;
            return;
        }
        var requestBuilder = new RequestBuilder(typename)
            .withRepresentations(idFieldName, env.getArgument(idFieldName));
        requestBuilder.withFields(selectedFields);
        this.request = requestBuilder.build();
    }

    public Set<String> getSelectedFieldNames() {
        return fieldNames(selectedFields);
    }

    private static Set<String> fieldNames(List<SelectedField> fields) {
        return fields.stream().map(SelectedField::getName).collect(toSet());
    }


    @RequiredArgsConstructor
    private static class RequestBuilder {
        private final String typename;
        private final StringBuilder prequel = new StringBuilder("query(");
        private final StringBuilder body = new StringBuilder(") {_entities(representations:$representations){...on ");
        private final JsonObjectBuilder variables = Json.createObjectBuilder();

        public RequestBuilder withRepresentations(String idFieldName, String value) {
            withVariable("representations", "[_Any!]!", Json.createObjectBuilder()
                .add("__typename", typename)
                .add(idFieldName, value)
                .build());
            return this;
        }

        public void withVariable(String variableName, String variableType, JsonValue jsonValue) {
            prequel.append(" $").append(variableName).append(":").append(variableType);
            variables.add(variableName, jsonValue);
        }

        public void withFields(List<SelectedField> selectedFields) {
            body.append(typename);
            new FragmentBuilder().with(selectedFields);
            body.append("}}");
        }

        public GraphQLRequest build() {
            return GraphQLRequest.builder()
                .query(prequel.toString() + body)
                .variables(variables.build())
                .build();
        }

        private class FragmentBuilder {
            private void with(List<SelectedField> selectedFields) {
                body.append("{");
                if (!fieldNames(selectedFields).contains("__typename"))
                    body.append("__typename ");
                selectedFields.forEach(this::toFragment);
                body.append("}");
            }

            private void toFragment(SelectedField selectedField) {
                body.append(selectedField.getName());
                if (!selectedField.getArguments().isEmpty()) {
                    body.append("(");
                    var fieldDefinition = selectedField.getFieldDefinitions().stream().filter(def -> def.getName().equals(selectedField.getName())).findFirst().orElseThrow();
                    for (var name : selectedField.getArguments().keySet()) {
                        var argument = fieldDefinition.getArgument(name);
                        addVariable(argument.toAppliedArgument(), selectedField.getArguments().get(name));
                    }
                    body.append(")");
                }
                var subFields = selectedField.getSelectionSet().getImmediateFields();
                if (!subFields.isEmpty())
                    new FragmentBuilder().with(subFields);
                body.append(' ');
            }

            private void addVariable(GraphQLAppliedDirectiveArgument argument, Object value) {
                var name = argument.getName();
                var variableType = ((GraphQLNamedType) argument.getType()).getName();
                RequestBuilder.this.withVariable(name, variableType, JsonMapper.toJson(value));
                body.append(name).append(":$").append(name);
            }
        }
    }
}
