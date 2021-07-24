package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLGateway.GenericGraphQLAPI;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

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
    public List<GenericGraphQLAPI> services() {
        return serviceUris.stream().map(ServiceProducer::buildService).collect(toList());
    }

    private static GenericGraphQLAPI buildService(URI uri) {return RestClientBuilder.newBuilder().baseUri(uri).build(GenericGraphQLAPI.class);}
}
