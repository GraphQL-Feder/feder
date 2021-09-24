package com.github.graphql.feder;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@Dependent
class ServiceProducer {
    @Inject
    @ConfigProperty(name = "graphql.service")
    Map<String, URI> serviceUris;

    @Produces
    List<FederatedGraphQLService> services() {
        return serviceUris.entrySet().stream()
            .map(SchemaBuilder::new)
            .map(FederatedGraphQLService::new)
            .collect(toList());
    }
}
