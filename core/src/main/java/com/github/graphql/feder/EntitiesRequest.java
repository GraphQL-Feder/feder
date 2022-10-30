package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLAPI.GraphQLRequest;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.SelectedField;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.graphql.feder.JsonMapper.toJson;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Slf4j
class EntitiesRequest {
    @Getter private final GraphQLRequest request;
    private final List<SelectedField> selectedFields;

    EntitiesRequest(GraphQLObjectType objectType, String idFieldName, String idValue, DataFetchingFieldSelectionSet selectionSet) {
        var availableFields = availableFieldNames(objectType);
        this.selectedFields = selectedFields(availableFields, selectionSet);
        this.request = (selectedFields.isEmpty() || selectedOnly(idFieldName))
            ? null
            : new RequestBuilder(objectType)
            .withRepresentations(idFieldName, idValue)
            .withFields(selectedFields)
            .build();
    }

    private static List<String> availableFieldNames(GraphQLObjectType type) {
        return type.getFieldDefinitions().stream()
            .map(GraphQLFieldDefinition::getName)
            .toList();
    }

    private List<SelectedField> selectedFields(List<String> availableFields, DataFetchingFieldSelectionSet selectionSet) {
        return selectionSet.getFields().stream()
            .filter(selectedField -> availableFields.contains(selectedField.getName()))
            .sorted(comparing(SelectedField::getName))
            .collect(toList());
    }

    private boolean selectedOnly(String idFieldName) {
        return selectedFields.size() == 1 && selectedFields.get(0).getName().equals(idFieldName);
    }

    Set<String> selectedFieldNames() {
        return fieldNames(selectedFields);
    }

    private static Set<String> fieldNames(List<SelectedField> fields) {
        return fields.stream().map(SelectedField::getName).collect(toSet());
    }


    @RequiredArgsConstructor
    private static class RequestBuilder {
        private final GraphQLObjectType objectType;
        private final Fragment fragment = new Fragment();
        private final Variables variables = new Variables();

        RequestBuilder withRepresentations(String idFieldName, String value) {
            addVariable("representations", "[_Any!]!", Map.of(
                "__typename", objectType.getName(),
                idFieldName, value));
            return this;
        }

        void addVariable(String variableName, String variableType, Object variableValue) {
            variables.add(variableName, variableType, variableValue);
        }

        RequestBuilder withFields(List<SelectedField> selectedFields) {
            fragment.with(selectedFields);
            return this;
        }

        GraphQLRequest build() {
            return GraphQLRequest.builder()
                .query(query())
                .variables(variables.valueMap())
                .build();
        }

        private String query() {
            return "query(" + variables.declaration() + ") " +
                   "{_entities(representations:$representations){...on " + objectType.getName() + fragment + "}}";
        }


        private static class Variables {
            private final Map<String, Variable> variables = new LinkedHashMap<>();

            void add(String variableName, String variableType, Object variableValue) {
                if (variables.containsKey(variableName)) {
                    log.debug("duplicate variable name: {}:{}", variableName, variableType);
                } else {
                    variables.put(variableName, new Variable(variableType, variableValue));
                }
            }

            String declaration() {
                return variables.entrySet().stream()
                    .map(entry -> "$" + entry.getKey() + ":" + entry.getValue().type)
                    .collect(Collectors.joining(" "));
            }

            JsonObject valueMap() {
                var out = Json.createObjectBuilder();
                variables.forEach((name, variable) -> out.add(name, toJson(variable.value)));
                return out.build();
            }

            private record Variable(String type, Object value) {}
        }

        private class Fragment {
            private final StringBuilder fragment = new StringBuilder();

            @Override public String toString() {return fragment.toString();}

            private Fragment with(List<SelectedField> selectedFields) {
                fragment.append("{");
                if (!fieldNames(selectedFields).contains("__typename"))
                    fragment.append("__typename ");
                selectedFields.forEach(this::addField);
                fragment.append("}");
                return this;
            }

            private void addField(SelectedField selectedField) {
                fragment.append(selectedField.getName());
                addArguments(selectedField);
                addSubFields(selectedField);
                fragment.append(' ');
            }

            private void addSubFields(SelectedField selectedField) {
                var subFields = selectedField.getSelectionSet().getImmediateFields();
                if (!subFields.isEmpty())
                    fragment.append(new Fragment().with(subFields));
            }

            private void addArguments(SelectedField selectedField) {
                if (!selectedField.getArguments().isEmpty()) {
                    fragment.append("(");
                    var fieldDefinition = selectedField.getFieldDefinitions().stream().filter(def -> def.getName().equals(selectedField.getName())).findFirst().orElseThrow();
                    for (var name : selectedField.getArguments().keySet()) {
                        var argument = fieldDefinition.getArgument(name);
                        // TODO the value can be from the variables or a literal
                        add(argument.toAppliedArgument(), selectedField.getArguments().get(name));
                    }
                    fragment.append(")");
                }
            }

            private void add(GraphQLAppliedDirectiveArgument argument, Object value) {
                var name = argument.getName();
                var variableType = ((GraphQLNamedType) argument.getType()).getName();
                RequestBuilder.this.addVariable(name, variableType, value);
                fragment.append(name).append(":$").append(name);
            }
        }
    }
}
