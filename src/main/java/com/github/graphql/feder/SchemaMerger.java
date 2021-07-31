package com.github.graphql.feder;

import graphql.schema.GraphQLSchema;
import lombok.RequiredArgsConstructor;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.util.List;

/**
 * Merge the federated services into one global GraphQL schema.
 * Application scoped as a cache. Invalidate the cache by restarting ;-)
 */
@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = {@Inject})
class SchemaMerger {
    private final List<FederatedGraphQLService> services;

    @Produces
    GraphQLSchema merge() {
        var builder = GraphQLSchema.newSchema();
        return services.get(0).getSchema();
    }
}
