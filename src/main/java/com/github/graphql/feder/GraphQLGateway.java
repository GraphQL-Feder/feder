package com.github.graphql.feder;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;

@Path("/graphql")
public class GraphQLGateway {

    @Inject
    @SuppressWarnings("CdiInjectionPointsInspection")
    List<GenericGraphQLAPI> services;

    @POST
    @Produces("application/graphql+json")
    public GraphQLResponse graphql(GraphQLRequest request) {
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

    @Data @SuperBuilder @NoArgsConstructor
    public static class GraphQLResponse {
        JsonObject data;
        List<GraphQLError> errors;

        public <T> T getData(String name, java.lang.Class<T> type) {
            return JSONB.fromJson(data.getJsonObject("data").get(name).toString(), type);
        }
    }

    public static class GraphQLError {
        // String message;
    }

    static final Jsonb JSONB = JsonbBuilder.create();
}
