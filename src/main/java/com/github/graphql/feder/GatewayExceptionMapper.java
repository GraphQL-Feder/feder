package com.github.graphql.feder;

import com.github.graphql.feder.GraphQLGateway.GraphQLError;
import com.github.graphql.feder.GraphQLGateway.GraphQLResponse;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

@Slf4j
@Provider
public class GatewayExceptionMapper implements ExceptionMapper<RuntimeException> {
    static List<GraphQLError> map(List<graphql.GraphQLError> errors) {
        return (errors == null) ? null : errors.stream().map(GatewayExceptionMapper::map).collect(toList());
    }

    static GraphQLError map(graphql.GraphQLError graphQLError) {
        return GraphQLError.builder().message(graphQLError.getMessage()).build(); // TODO map other fields
    }

    @Override public Response toResponse(RuntimeException exception) {
        var uuid = UUID.randomUUID();
        log.error("mapping exception to GraphQL error [id: {}]", uuid, exception);
        var error = map(exception)
            .withExtension("instance", uuid);
        return Response.ok().entity(GraphQLResponse.builder().errors(List.of(error)).build()).build();
    }

    private GraphQLError map(RuntimeException exception) {
        return GraphQLError.builder()
            .message(exception.getMessage())
            // TODO map more fields
            .build();
    }
}
