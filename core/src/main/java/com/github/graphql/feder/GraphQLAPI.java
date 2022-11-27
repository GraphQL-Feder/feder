package com.github.graphql.feder;

import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Closeable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generic JAX-RS API to execute arbitrary GraphQL requests.
 */
@Path("/graphql")
public interface GraphQLAPI extends Closeable {
    Jsonb JSONB = JsonbBuilder.create();
    String APPLICATION_GRAPHQL_JSON_TYPE = "application/graphql+json;charset=utf-8";

    @Produces(APPLICATION_GRAPHQL_JSON_TYPE)
    @POST GraphQLResponse request(GraphQLRequest request);

    @GET
    @Produces(APPLICATION_GRAPHQL_JSON_TYPE)
    default GraphQLResponse request(@QueryParam("query") String query, JsonObject variables) {
        // TODO verify that the query is not a mutation
        return request(GraphQLRequest.builder()
            .query(query)
            .variables(variables)
            .build());
    }

    @GET
    @Path("/schema.graphql")
    @Produces("text/plain;charset=utf-8")
    String schema();

    /* Implement Closeable and a default implementation, so MP REST Client will close the actual client class */
    default void close() {}

    @Data @SuperBuilder @NoArgsConstructor
    class GraphQLRequest {
        String query;
        JsonObject variables;
        String operationName;

        public Optional<JsonObject> variables() {return Optional.ofNullable(variables);}

        public Optional<String> operationName() {return Optional.ofNullable(operationName);}
    }

    @Data @SuperBuilder @NoArgsConstructor
    class GraphQLResponse {
        JsonObject data;
        List<GraphQLError> errors;

        public <T> T getData(String name, Class<T> type) {
            var value = (data == null) ? null : data.get(name);
            return (value == null) ? null : JSONB.fromJson(value.toString(), type);
        }

        public boolean hasErrors() {return errors != null && !errors.isEmpty();}
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
