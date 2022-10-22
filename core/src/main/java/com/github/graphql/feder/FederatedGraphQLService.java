package com.github.graphql.feder;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
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
@RequiredArgsConstructor
class FederatedGraphQLService implements DataFetcher<Object> {
    private final String name; // TODO add a `@boundedContext` directive to all fields from this service
    @Getter private final GraphQLSchema schema;
    private final URI uri;
    private final GraphQLAPI graphQLAPI;
    private final String idFieldName;

    FederatedGraphQLService(SchemaBuilder schemaBuilder) {
        this.name = schemaBuilder.name;
        this.uri = schemaBuilder.uri;
        this.graphQLAPI = schemaBuilder.graphQLAPI;
        this.schema = schemaBuilder.build(this);
        this.idFieldName = "id"; // TODO derive from @key
    }

    @Override
    public Object get(DataFetchingEnvironment env) {
        var representations = new RepresentationsQuery(schema, idFieldName, env);

        if (representations.getRequest() == null) {
            return new LinkedHashMap<>();
        }

        log.info("send request to {} at {}: {}", name, uri, representations.getRequest());
        var response = graphQLAPI.request(representations.getRequest());
        log.info("got response from {} at {}: {}", name, uri, response);

        if (response == null) throw new FederationServiceException("selecting " + representations.getSelectedFieldNames() + " => null response");
        if (response.hasErrors()) throw new FederationServiceException(response.getErrors());
        if (response.getData() == null) throw new FederationServiceException("no data");
        var entities = response.getData().getJsonArray("_entities");
        if (entities == null) throw new FederationServiceException("no _entities");
        if (entities.isEmpty()) throw new FederationServiceException("empty _entities");
        if (entities.size() > 1) throw new FederationServiceException("multiple _entities");
        var entity = entities.get(0).asJsonObject();

        // GraphQL-Java doesn't like JsonObjects: it wraps strings in quotes
        //| var out = Json.createObjectBuilder(entity);
        //| if (!selectedFields.contains("__typename")) out.remove("__typename");
        //| return out.build();
        var out = new LinkedHashMap<>();
        representations.getSelectedFieldNames().forEach(fieldName -> out.put(fieldName, map(entity.getValue("/" + fieldName))));
        return out;
    }

    private class FederationServiceException extends FederationException {
        public FederationServiceException(Object message) {super("[from service " + name + " at " + uri + "]: " + message);}
    }
}
