package com.github.graphql.feder;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.SchemaTraverser;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static graphql.Scalars.GraphQLID;
import static graphql.Scalars.GraphQLInt;
import static java.util.stream.Collectors.toList;

/**
 * Merge the federated services into one global GraphQL schema.
 * Application scoped as a cache. Invalidate the cache by restarting ;-)
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = {@Inject})
class SchemaMerger extends GraphQLTypeVisitorStub {
    private final List<FederatedGraphQLService> services;

    private final Map<String, TypeBuilder> typeBuilders = new LinkedHashMap<>();
    private final List<EnumBuilder> enumBuilders = new ArrayList<>();
    private final List<FieldBuilder> fieldBuilders = new ArrayList<>();
    private final GraphQLCodeRegistry.Builder codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry();
    private final GraphQLSchema.Builder out = GraphQLSchema.newSchema()
        .clearDirectives()
        .clearSchemaDirectives();
    private GraphQLSchema schema;

    private GraphQLSchema currentlyMergingSchema;
    private TypeBuilder currentTypeBuilder;
    private EnumBuilder currentEnumBuilder;
    private FieldBuilder currentFieldBuilder;

    @Produces
    GraphQLSchema merge() {
        if (schema == null)
            schema = build();
        return schema;
    }

    private GraphQLSchema build() {
        services.forEach(this::merge);

        closeBuilders();

        // TODO why do we need these and what other types are missing?
        out.additionalType(GraphQLInt);
        out.additionalType(GraphQLID);

        out.codeRegistry(codeRegistryBuilder.build());

        return out.build();
    }

    private void merge(FederatedGraphQLService service) {
        this.currentlyMergingSchema = service.getSchema();
        new SchemaTraverser().depthFirst(this, currentlyMergingSchema.getAllTypesAsList());
        currentlyMergingSchema = null;
    }

    @Override public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        this.currentTypeBuilder = hasStandardNodeName(node)
            ? typeBuilders.computeIfAbsent(node.getName(), name -> new TypeBuilder(node))
            : null;

        return super.visitGraphQLObjectType(node, context);
    }

    @Override public TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLSchemaElement> context) {
        if (hasStandardNodeName(node))
            enumBuilders.add(currentEnumBuilder = new EnumBuilder(node));
        else currentEnumBuilder = null;

        return super.visitGraphQLEnumType(node, context);
    }

    @Override public TraversalControl visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        if (currentEnumBuilder != null)
            currentEnumBuilder.add(node);
        return super.visitGraphQLEnumValueDefinition(node, context);
    }

    @Override public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        if (currentTypeBuilder != null && hasStandardNodeName(node))
            fieldBuilders.add(this.currentFieldBuilder = new FieldBuilder(node));
        else currentFieldBuilder = null;

        return super.visitGraphQLFieldDefinition(node, context);
    }

    @Override public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context) {
        if (currentFieldBuilder != null && context.getParentNode() instanceof GraphQLFieldDefinition)
            currentFieldBuilder.argument(node);

        return super.visitGraphQLArgument(node, context);
    }

    private static boolean hasStandardNodeName(GraphQLNamedSchemaElement node) {
        return !node.getName().startsWith("_");
    }

    private void closeBuilders() {
        fieldBuilders.forEach(FieldBuilder::build);
        enumBuilders.forEach(EnumBuilder::build);
        typeBuilders.values().forEach(typeBuilder -> {
            var type = typeBuilder.build();
            if (type.getName().equals("Query"))
                out.query(type);
            else
                out.additionalType(type);
        });
    }

    static class TypeBuilder {
        private final GraphQLObjectType.Builder type;

        TypeBuilder(GraphQLNamedSchemaElement node) {
            this.type = GraphQLObjectType.newObject()
                .name(node.getName())
                .description(node.getDescription());
        }

        @Override public String toString() {
            return "TypeBuilder(" + type + ')';
        }

        public void add(GraphQLFieldDefinition field) {
            type.field(field);
        }

        public GraphQLObjectType build() {
            return type.build();
        }
    }

    class EnumBuilder {
        private final GraphQLEnumType.Builder type;

        EnumBuilder(GraphQLNamedSchemaElement node) {
            this.type = GraphQLEnumType.newEnum()
                .name(node.getName())
                .description(node.getDescription());
        }

        @Override public String toString() {
            return "EnumBuilder(" + type + ')';
        }

        public void add(GraphQLEnumValueDefinition value) {
            type.value(value);
        }

        public void build() {
            var type = this.type.build();
            out.additionalType(type);
        }
    }

    class FieldBuilder {
        private final GraphQLSchema mergingSchema;
        private final TypeBuilder typeBuilder;
        private final GraphQLFieldDefinition.Builder field;

        FieldBuilder(GraphQLFieldDefinition node) {
            this.mergingSchema = currentlyMergingSchema;
            this.typeBuilder = currentTypeBuilder;
            this.field = GraphQLFieldDefinition.newFieldDefinition()
                .name(node.getName())
                .type(out(node.getType()));
        }

        private GraphQLOutputType out(GraphQLType type) {
            // GraphQLList and GraphQLNonNull are the only classes implementing GraphQLModifiedType,
            // and we have to restore them around the nested output type we want to return
            if (type instanceof GraphQLList)
                return GraphQLList.list(out(unwrap(type)));
            if (type instanceof GraphQLNonNull)
                return GraphQLNonNull.nonNull(out(unwrap(type)));
            return GraphQLTypeReference.typeRef(((GraphQLNamedType) type).getName());
        }

        private GraphQLType unwrap(GraphQLType type) {
            return ((GraphQLModifiedType) type).getWrappedType();
        }

        public void argument(GraphQLArgument node) {
            field.argument(node);
        }

        public void build() {
            var field = this.field.build();
            typeBuilder.add(field);
            var type = typeBuilder.build();
            var coordinates = FieldCoordinates.coordinates(type, field);
            var fieldDefinition = mergingSchema.getObjectType(type.getName()).getFieldDefinition(field.getName());
            var dataFetcher = mergingSchema.getCodeRegistry().getDataFetcher(coordinates, fieldDefinition);
            DataFetcher<?> mergedDataFetcher = (dataFetcher instanceof FederatedGraphQLService)
                ? new MergedDataFetcher(codeRegistryBuilder.getDataFetcher(coordinates, fieldDefinition), dataFetcher)
                : dataFetcher;
            codeRegistryBuilder.dataFetcher(coordinates, mergedDataFetcher);
        }
    }

    private static class MergedDataFetcher implements DataFetcher<Object> {
        private final List<DataFetcher<?>> dataFetchers;

        private MergedDataFetcher(DataFetcher<?>... dataFetchers) {
            this.dataFetchers = Stream.of(dataFetchers).flatMap(dataFetcher ->
                (dataFetcher instanceof MergedDataFetcher)
                    ? ((MergedDataFetcher) dataFetcher).dataFetchers.stream()
                    : Stream.of(dataFetcher)).collect(toList());
        }

        @Override public Object get(DataFetchingEnvironment environment) throws Exception {
            Map<String, Object> out = null;
            for (var dataFetcher : dataFetchers) {
                @SuppressWarnings("unchecked")
                var value = (Map<String, Object>) dataFetcher.get(environment);
                if (out == null)
                    out = value;
                else
                    out.putAll(value);
            }
            return out;
        }
    }
}
