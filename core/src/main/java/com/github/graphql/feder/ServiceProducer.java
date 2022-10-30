package com.github.graphql.feder;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

@Dependent // TODO ApplicationScoped
class ServiceProducer {
    @Inject
    @ConfigProperty(name = "graphql.service")
    Map<String, URI> serviceUris;

    @Produces
    List<FederatedGraphQLService> services() {
        return serviceUris.entrySet().stream()
            .map(FederatedSchemaBuilder::of)
            .map(FederatedGraphQLService::new)
            .collect(toList());
    }
}
