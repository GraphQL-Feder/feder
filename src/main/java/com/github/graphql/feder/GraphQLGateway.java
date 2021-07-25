package com.github.graphql.feder;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/graphql")
public class GraphQLGateway {

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    List<FederatedGraphQLService> services;

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

    @Path("/graphql")
    public interface GenericGraphQLAPI {
        @POST GraphQLResponse request(GraphQLRequest request);
    }

    @Data @SuperBuilder @NoArgsConstructor
    public static class GraphQLRequest {
        String query;
        JsonObject variables;
    }

    @Data @SuperBuilder @NoArgsConstructor
    public static class GraphQLResponse {
        JsonObject data;
        List<GraphQLError> errors;

        public <T> T getData(String name, java.lang.Class<T> type) { return JSONB.fromJson(data.get(name).toString(), type); }
    }

    @Data @SuperBuilder @NoArgsConstructor
    public static class GraphQLError {
        String message;
        Map<String, Object> extensions;

        public GraphQLError withExtension(String key, Object value) {
            if (extensions == null) extensions = new LinkedHashMap<>();
            extensions.put(key, value);
            return this;
        }
    }

    static final Jsonb JSONB = JsonbBuilder.create();
}
