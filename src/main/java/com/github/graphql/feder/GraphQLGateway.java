package com.github.graphql.feder;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.net.URI;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Path("/graphql")
public class GraphQLGateway {

    @Inject
    @ConfigProperty(name = "graphql.services")
    List<URI> serviceUris;

    private List<GenericGraphQLAPI> services;

    @POST
    @Produces("application/graphql+json")
    public GraphQLResponse graphql(GraphQLRequest request) {
        if (services == null)
            services = serviceUris.stream()
                .map(uri -> RestClientBuilder.newBuilder()
                    .baseUri(uri)
                    .build(GenericGraphQLAPI.class))
                .collect(toList());
        return services.get(0).request(request);
    }

    @Path("/graphql")
    public interface GenericGraphQLAPI {
        @POST
        GraphQLResponse request(GraphQLRequest request);
    }

    @Data @SuperBuilder @NoArgsConstructor
    public static class GraphQLRequest {
        String query;
        JsonObject variables;
    }

    @Data
    public static class GraphQLResponse {
        JsonObject data;
        List<GraphQLError> errors;
    }

    public static class GraphQLError {
        String message;
    }
}
