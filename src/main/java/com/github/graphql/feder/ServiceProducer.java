package com.github.graphql.feder;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.net.URI;
import java.util.List;

import static java.util.stream.Collectors.toList;

@ApplicationScoped
public class ServiceProducer {
    @Inject
    @ConfigProperty(name = "graphql.services")
    List<URI> serviceUris;

    @Produces
    public List<FederatedGraphQLService> services() {
        return serviceUris.stream()
            .map(SchemaBuilder::new)
            .map(FederatedGraphQLService::new)
            .collect(toList());
    }
}
