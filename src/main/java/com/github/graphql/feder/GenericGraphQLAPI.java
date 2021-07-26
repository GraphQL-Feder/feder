package com.github.graphql.feder;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.json.JsonObject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/graphql")
public interface GenericGraphQLAPI {
    @POST GraphQLResponse request(GraphQLRequest request);

    @Data @SuperBuilder @NoArgsConstructor
    class GraphQLRequest {
        String query;
        JsonObject variables;
    }

    @Data @SuperBuilder @NoArgsConstructor
    class GraphQLResponse {
        JsonObject data;
        List<GraphQLError> errors;

        public <T> T getData(String name, Class<T> type) { return GraphQLGateway.JSONB.fromJson(data.get(name).toString(), type); }
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
