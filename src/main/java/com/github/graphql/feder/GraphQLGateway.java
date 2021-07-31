package com.github.graphql.feder;

import graphql.schema.idl.SchemaPrinter;

import javax.inject.Inject;
import java.util.List;

public class GraphQLGateway implements GraphQLAPI {

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    List<FederatedGraphQLService> services;

    @Override public String schema() {
        return new SchemaPrinter().print(services.get(0).getSchema());
    }

    @Override public GraphQLResponse request(GraphQLRequest request) {
        return services.get(0).request(request);
    }
}
