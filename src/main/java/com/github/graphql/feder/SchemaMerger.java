package com.github.graphql.feder;

import graphql.scalar.GraphqlIntCoercing;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldDefinition.Builder;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.SchemaTraverser;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import lombok.RequiredArgsConstructor;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Merge the federated services into one global GraphQL schema.
 * Application scoped as a cache. Invalidate the cache by restarting ;-)
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = {@Inject})
class SchemaMerger extends GraphQLTypeVisitorStub {
    private final List<FederatedGraphQLService> services;

    private final Map<String, GraphQLObjectType.Builder> typeBuilders = new LinkedHashMap<>();
    private final List<FieldBuilder> fieldBuilders = new ArrayList<>();
    private final GraphQLCodeRegistry.Builder codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry();
    private final GraphQLSchema.Builder out = GraphQLSchema.newSchema()
        .clearDirectives()
        .clearSchemaDirectives();

    private GraphQLSchema currentlyMergingSchema;
    private GraphQLObjectType.Builder currentTypeBuilder;
    private FieldBuilder currentFieldBuilder;

    @Produces
    GraphQLSchema merge() {
        services.forEach(this::merge);

        closeBuilders();

        // TODO why do we need these and what other types are missing?
        out.additionalType(GraphQLScalarType.newScalar()
            .name("Int")
            .coercing(new GraphqlIntCoercing()).build());

        out.codeRegistry(codeRegistryBuilder.build());

        return out.build();
    }

    private void merge(FederatedGraphQLService service) {
        this.currentlyMergingSchema = service.getSchema();
        new SchemaTraverser().depthFirst(this, currentlyMergingSchema.getAllTypesAsList());
        currentlyMergingSchema = null;
    }

    @Override public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        currentTypeBuilder = null;
        if (hasStandardNodeName(node))
            this.currentTypeBuilder = typeBuilders.computeIfAbsent(node.getName(), name -> GraphQLObjectType.newObject()
                .name(name)
                .description(node.getDescription()));

        return super.visitGraphQLObjectType(node, context);
    }

    @Override public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        currentFieldBuilder = null;
        if (currentTypeBuilder != null && hasStandardNodeName(node))
            fieldBuilders.add(this.currentFieldBuilder = new FieldBuilder(node));

        return super.visitGraphQLFieldDefinition(node, context);
    }

    @Override public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context) {
        if (currentFieldBuilder != null && context.getParentNode() instanceof GraphQLFieldDefinition)
            currentFieldBuilder.argument(node);

        return super.visitGraphQLArgument(node, context);
    }

    private static boolean hasStandardNodeName(GraphQLNamedSchemaElement node) {return !node.getName().startsWith("_");}

    private void closeBuilders() {
        fieldBuilders.forEach(FieldBuilder::build);
        typeBuilders.values().forEach(typeBuilder -> {
            var type = typeBuilder.build();
            if (type.getName().equals("Query")) out.query(type);
            else out.additionalType(type);
        });
    }

    class FieldBuilder {
        private final GraphQLSchema mergingSchema;
        private final GraphQLObjectType.Builder typeBuilder;
        private final Builder field;

        FieldBuilder(@SuppressWarnings("CdiInjectionPointsInspection") GraphQLFieldDefinition node) {
            this.mergingSchema = currentlyMergingSchema;
            this.typeBuilder = currentTypeBuilder;
            this.field = GraphQLFieldDefinition.newFieldDefinition()
                .name(node.getName())
                .type(ref(node.getType()));
        }

        private GraphQLTypeReference ref(GraphQLType type) {
            while (type instanceof GraphQLModifiedType)
                type = ((GraphQLModifiedType) type).getWrappedType();
            return GraphQLTypeReference.typeRef(((GraphQLNamedType) type).getName());
        }

        public void argument(GraphQLArgument node) {
            field.argument(node);
        }

        public void build() {
            var field = this.field.build();
            typeBuilder.field(field);
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

        public MergedDataFetcher(@SuppressWarnings("CdiInjectionPointsInspection") DataFetcher<?>... dataFetchers) {
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
                if (out == null) out = value;
                else out.putAll(value);
            }
            return out;
        }
    }
}
