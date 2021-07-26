package com.github.graphql.feder;

import com.github.graphql.feder.GenericGraphQLAPI.GraphQLRequest;
import com.github.graphql.feder.GenericGraphQLAPI.GraphQLResponse;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.List;

@Path("/graphql")
public class GraphQLGateway {

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    List<FederatedGraphQLService> services;

    @GET
    @Path("/schema.graphql")
    @Produces("text/plain;charset=utf-8")
    public String schema() {
        return services.get(0).getSchema();
    }

    @GET
    @Produces("application/graphql+json")
    public GraphQLResponse graphql(@QueryParam("query") String query, JsonObject variables) {
        return graphql(GraphQLRequest.builder()
            .query(query)
            .variables(variables)
            .build());
    }

    @POST
    @Produces("application/graphql+json")
    public GraphQLResponse graphql(GraphQLRequest request) {
        return services.get(0).request(request);
    }

    static final Jsonb JSONB = JsonbBuilder.create();
}
