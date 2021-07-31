package com.github.graphql.feder;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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

/**
 * Generic JAX-RS API to execute arbitrary GraphQL requests.
 */
@Path("/graphql")
public interface GraphQLAPI {
    Jsonb JSONB = JsonbBuilder.create();
    String APPLICATION_GRAPHQL_JSON_TYPE = "application/graphql+json;charset=utf-8";

    @Produces(APPLICATION_GRAPHQL_JSON_TYPE)
    @POST GraphQLResponse request(GraphQLRequest request);

    @GET
    @Produces(APPLICATION_GRAPHQL_JSON_TYPE)
    default GraphQLResponse request(@QueryParam("query") String query, JsonObject variables) {
        return request(GraphQLRequest.builder()
            .query(query)
            .variables(variables)
            .build());
    }

    @GET
    @Path("/schema.graphql")
    @Produces("text/plain;charset=utf-8")
    String schema();

    @Data @SuperBuilder @NoArgsConstructor
    class GraphQLRequest {
        String query;
        JsonObject variables;
    }

    @Data @SuperBuilder @NoArgsConstructor
    class GraphQLResponse {
        JsonObject data;
        List<GraphQLError> errors;

        public <T> T getData(String name, Class<T> type) {
            if (data == null) return null;
            var jsonValue = data.get(name);
            return (jsonValue == null) ? null : JSONB.fromJson(jsonValue.toString(), type);
        }

        public boolean hasErrors() { return errors != null && !errors.isEmpty(); }
    }

    @Data @SuperBuilder @NoArgsConstructor
    class GraphQLError {
        String message;
        Map<String, Object> extensions;

        public GraphQLError withExtension(String key, Object value) {
            if (extensions == null) extensions = new LinkedHashMap<>();
            extensions.put(key, value);
            return this;
        }
    }
}
