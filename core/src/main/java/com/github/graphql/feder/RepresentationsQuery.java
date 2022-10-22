package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLAPI.GraphQLRequest;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.SelectedField;
import jakarta.json.Json;
import lombok.Getter;

import java.util.List;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

class RepresentationsQuery {
    @Getter private final GraphQLRequest request;
    private final List<SelectedField> selectedFields;

    RepresentationsQuery(GraphQLSchema schema, String idFieldName, DataFetchingEnvironment env) {
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
        this.request = GraphQLRequest.builder()
            .query(new QueryBuilder(typename).with(selectedFields))
            .variables(Json.createObjectBuilder()
                .add("representations", Json.createObjectBuilder()
                    .add("__typename", typename)
                    .add(idFieldName, env.getArgument(idFieldName).toString())
                    .build())
                .build())
            .build();
    }

    public Set<String> getSelectedFieldNames() {
        return fieldNames(selectedFields);
    }

    private static Set<String> fieldNames(List<SelectedField> fields) {
        return fields.stream().map(SelectedField::getName).collect(toSet());
    }


    private static class QueryBuilder {
        private final StringBuilder out = new StringBuilder();

        public QueryBuilder(String typename) {
            out.append("query($representations:[_Any!]!){_entities(representations:$representations){...on ");
            out.append(typename);
        }

        public String with(List<SelectedField> selectedFields) {
            new FragmentBuilder(out).with(selectedFields);
            out.append("}}");
            return out.toString();
        }
    }

    private record FragmentBuilder(StringBuilder out) {
        private void with(List<SelectedField> selectedFields) {
            out.append("{");
            if (!fieldNames(selectedFields).contains("__typename"))
                out.append("__typename ");
            selectedFields.forEach(this::toFragment);
            out.append("}");
        }

        private void toFragment(SelectedField selectedField) {
            out.append(selectedField.getName());
            var subFields = selectedField.getSelectionSet().getImmediateFields();
            // TODO add arguments
            if (!subFields.isEmpty())
                new FragmentBuilder(out).with(subFields);
            out.append(' ');
        }
    }
}
