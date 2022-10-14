package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLAPI.GraphQLError;
import com.github.graphql.feder.GraphQLAPI.GraphQLResponse;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.List;
import java.util.UUID;

import static com.github.graphql.feder.GraphQLAPI.APPLICATION_GRAPHQL_JSON_TYPE;
import static com.github.graphql.feder.GraphQLAPI.JSONB;
import static java.util.stream.Collectors.toList;

/**
 * Map exceptions to GraphQL error responses.
 */
@Slf4j
@Provider
public class GatewayExceptionMapper implements ExceptionMapper<RuntimeException> {
    static List<GraphQLError> map(List<graphql.GraphQLError> errors) {
        return (errors == null || errors.isEmpty()) ? null : errors.stream().map(GatewayExceptionMapper::map).collect(toList());
    }

    static GraphQLError map(graphql.GraphQLError graphQLError) {
        return GraphQLError.builder().message(graphQLError.getMessage()).build(); // TODO map other fields
    }

    @Override public Response toResponse(RuntimeException exception) {
        var uuid = UUID.randomUUID();
        log.error("mapping exception to GraphQL error [id: " + uuid + "]", exception);
        var error = GraphQLError.builder()
            .message(exception.getMessage())
            // TODO map more fields
            .build()
            .withExtension("instance", uuid);
        GraphQLResponse graphQLResponse = GraphQLResponse.builder().errors(List.of(error)).build();
        return Response
            .ok(JSONB.toJson(graphQLResponse), APPLICATION_GRAPHQL_JSON_TYPE)
            .build();
    }
}
