package com.github.graphql.feder;

import graphql.scalar.GraphqlIntCoercing;
import graphql.scalar.GraphqlStringCoercing;
import graphql.schema.GraphQLArgument;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.graphql.feder.SchemaMerger.FieldBuilder.key;

/**
 * Merge the federated services into one global GraphQL schema.
 * Application scoped as a cache. Invalidate the cache by restarting ;-)
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = {@Inject})
class SchemaMerger extends GraphQLTypeVisitorStub {
    private final List<FederatedGraphQLService> services;
    private final Map<String, GraphQLObjectType.Builder> typeBuilders = new LinkedHashMap<>();
    private final Map<String, FieldBuilder> fieldBuilders = new LinkedHashMap<>();
    private final GraphQLSchema.Builder out = GraphQLSchema.newSchema()
        .clearDirectives()
        .clearSchemaDirectives();

    private GraphQLObjectType.Builder currentTypeBuilder;
    private FieldBuilder currentFieldBuilder;

    @Produces
    GraphQLSchema merge() {
        for (var service : services) {
            new SchemaTraverser().depthFirst(this, service.getSchema().getAllTypesAsList());
        }
        out.codeRegistry(services.get(0).getSchema().getCodeRegistry()); // TODO merge code registries
        fieldBuilders.values().forEach(FieldBuilder::build);
        typeBuilders.values().forEach(typeBuilder -> {
            var type = typeBuilder.build();
            if (type.getName().equals("Query")) out.query(type);
            else out.additionalType(type);
        });
        // TODO why do we need these and what other types are missing?
        out.additionalType(GraphQLScalarType.newScalar()
            .name("Int")
            .coercing(new GraphqlIntCoercing()).build());
        out.additionalType(GraphQLScalarType.newScalar()
            .name("ID")
            .coercing(new GraphqlStringCoercing()).build());
        return out.build();
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
            this.currentFieldBuilder = fieldBuilders.computeIfAbsent(key(node, context.getParentNode()),
                key -> new FieldBuilder(currentTypeBuilder, node));

        return super.visitGraphQLFieldDefinition(node, context);
    }

    @Override public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context) {
        if (currentFieldBuilder != null && context.getParentNode() instanceof GraphQLFieldDefinition)
            currentFieldBuilder.argument(node);

        return super.visitGraphQLArgument(node, context);
    }

    private static boolean hasStandardNodeName(GraphQLNamedSchemaElement node) {return !node.getName().startsWith("_");}

    static class FieldBuilder {
        static String key(GraphQLFieldDefinition node, GraphQLSchemaElement parentNode) {
            return ((GraphQLNamedSchemaElement) parentNode).getName() + "#" + node.getName();
        }

        private final GraphQLObjectType.Builder typeBuilder;
        private final Builder field;

        FieldBuilder(GraphQLObjectType.Builder currentTypeBuilder, GraphQLFieldDefinition node) {
            typeBuilder = currentTypeBuilder;
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
            typeBuilder.field(field);
        }
    }
}
