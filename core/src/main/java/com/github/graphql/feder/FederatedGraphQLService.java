package com.github.graphql.feder;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.LinkedHashMap;

import static com.github.graphql.feder.JsonMapper.map;

/**
 * Holds a {@link GraphQLSchema} and fetches data from the Federation <code>_entities</code> query.
 */
@Slf4j
@Dependent
@RequiredArgsConstructor(onConstructor_ = {@Inject})
class FederatedGraphQLService implements DataFetcher<Object> {
    private final String name; // TODO add a `@boundedContext` directive to all fields from this service
    @Getter private final GraphQLSchema schema;
    private final URI uri;
    private final GraphQLAPI graphQLAPI;
    private final String idFieldName;

    FederatedGraphQLService(SchemaBuilder schemaBuilder) {
        // we can't use the RequiredArgsConstructor, as we need to pass `this` to the schemaBuilder
        this.name = schemaBuilder.name;
        this.uri = schemaBuilder.uri;
        this.graphQLAPI = schemaBuilder.graphQLAPI;
        this.schema = schemaBuilder.build(this);
        this.idFieldName = "id"; // TODO derive from @key
    }

    @Override
    public Object get(DataFetchingEnvironment env) {
        var entitiesRequest = new EntitiesRequest(schema, idFieldName, env);

        if (entitiesRequest.getRequest() == null) {
            return new LinkedHashMap<>();
        }

        log.info("send request to {} at {}: {}", name, uri, entitiesRequest.getRequest());
        var response = graphQLAPI.request(entitiesRequest.getRequest());
        log.info("got response from {} at {}: {}", name, uri, response);

        if (response == null) throw new FederationServiceException("selecting " + entitiesRequest.getSelectedFieldNames() + " => null response");
        if (response.hasErrors()) throw new FederationServiceException(response.getErrors());
        if (response.getData() == null) throw new FederationServiceException("no data");
        var entitiesResponse = response.getData().getJsonArray("_entities");
        if (entitiesResponse == null) throw new FederationServiceException("no _entities");
        if (entitiesResponse.isEmpty()) throw new FederationServiceException("empty _entities");
        if (entitiesResponse.size() > 1) throw new FederationServiceException("multiple _entities");
        var entity = entitiesResponse.get(0).asJsonObject();

        // GraphQL-Java doesn't like JsonObjects: it wraps strings in quotes
        //| var out = Json.createObjectBuilder(entity);
        //| if (!selectedFields.contains("__typename")) out.remove("__typename");
        //| return out.build();
        var out = new LinkedHashMap<>();
        entitiesRequest.getSelectedFieldNames().forEach(fieldName -> out.put(fieldName, map(entity.getValue("/" + fieldName))));
        return out;
    }

    private class FederationServiceException extends FederationException {
        public FederationServiceException(Object message) {super("[from service " + name + " at " + uri + "]: " + message);}
    }
}
