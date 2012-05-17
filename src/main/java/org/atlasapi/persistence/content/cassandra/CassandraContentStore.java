package org.atlasapi.persistence.content.cassandra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.metabroadcast.common.base.Maybe;
import com.netflix.astyanax.AstyanaxContext;
import com.netflix.astyanax.Keyspace;
import com.netflix.astyanax.MutationBatch;
import com.netflix.astyanax.connectionpool.NodeDiscoveryType;
import com.netflix.astyanax.connectionpool.OperationResult;
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor;
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl;
import com.netflix.astyanax.model.ColumnList;
import com.netflix.astyanax.model.ConsistencyLevel;
import com.netflix.astyanax.model.Row;
import com.netflix.astyanax.model.Rows;
import com.netflix.astyanax.thrift.ThriftFamilyFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.atlasapi.media.entity.ChildRef;
import org.atlasapi.media.entity.Clip;
import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.ParentRef;
import org.atlasapi.media.entity.Version;
import org.atlasapi.persistence.cassandra.CassandraPersistenceException;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.content.ResolvedContent;
import org.atlasapi.serialization.json.JsonFactory;
import static org.atlasapi.persistence.cassandra.CassandraSchema.*;
import org.atlasapi.persistence.content.ContentCategory;
import org.atlasapi.persistence.content.listing.ContentLister;
import org.atlasapi.persistence.content.listing.ContentListingCriteria;
import org.atlasapi.serialization.json.ContainerConfiguration;
import org.atlasapi.serialization.json.ItemConfiguration;

/**
 */
public class CassandraContentStore implements ContentWriter, ContentResolver, ContentLister {

    private final ObjectMapper mapper = JsonFactory.makeJsonMapper();
    //
    private final AstyanaxContext<Keyspace> context;
    private final int requestTimeout;
    //
    private Keyspace keyspace;

    public CassandraContentStore(List<String> seeds, int port, int maxConnections, int connectionTimeout, int requestTimeout) {
        this.context = new AstyanaxContext.Builder().forCluster(CLUSTER).forKeyspace(KEYSPACE).
                withAstyanaxConfiguration(new AstyanaxConfigurationImpl().setDiscoveryType(NodeDiscoveryType.NONE)).
                withConnectionPoolConfiguration(new ConnectionPoolConfigurationImpl(CLUSTER).setPort(port).
                setMaxBlockedThreadsPerHost(maxConnections).
                setMaxConnsPerHost(maxConnections).
                setConnectTimeout(connectionTimeout).
                setSeeds(Joiner.on(",").join(seeds))).
                withConnectionPoolMonitor(new CountingConnectionPoolMonitor()).
                buildKeyspace(ThriftFamilyFactory.getInstance());
        this.requestTimeout = requestTimeout;
    }

    @PostConstruct
    public void init() {
        context.start();
        keyspace = context.getEntity();
    }

    @PreDestroy
    public void close() {
        context.shutdown();
    }

    @Override
    public void createOrUpdate(Item item) {
        try {
            writeItem(item);
            attachItemToParent(item);
        } catch (Exception ex) {
            throw new CassandraPersistenceException(ex.getMessage(), ex);
        }
    }

    @Override
    public void createOrUpdate(Container container) {
        try {
            writeContainer(container);
        } catch (Exception ex) {
            throw new CassandraPersistenceException(ex.getMessage(), ex);
        }
    }

    @Override
    public ResolvedContent findByCanonicalUris(Iterable<String> canonicalUris) {
        try {
            Map<String, Maybe<Identified>> results = new HashMap<String, Maybe<Identified>>();
            for (String uri : canonicalUris) {
                Content found = readContent(uri);
                //
                if (found != null) {
                    results.put(uri, Maybe.<Identified>just(found));
                } else {
                    results.put(uri, Maybe.<Identified>nothing());
                }
            }
            return new ResolvedContent(results);
        } catch (Exception ex) {
            throw new CassandraPersistenceException(ex.getMessage(), ex);
        }
    }
    
    @Override
    public Iterator<Content> listContent(ContentListingCriteria criteria) {
        try {
            Iterator<Content> items = Iterators.emptyIterator();
            Iterator<Content> containers = Iterators.emptyIterator();
            if (criteria.getCategories().contains(ContentCategory.CHILD_ITEM) || criteria.getCategories().contains(ContentCategory.TOP_LEVEL_ITEM)) {
             OperationResult<Rows<String, String>> result = keyspace.prepareQuery(ITEMS_CF).getAllRows().execute();
             items = Iterators.transform(result.getResult().iterator(), new Function<Row, Content>() {

                    @Override
                    public Content apply(Row input) {
                        try {
                        return unmarshalItem(input.getColumns());
                        } catch(Exception ex) {
                            return null;
                        }
                    }
                 
             });
            }
            if (criteria.getCategories().contains(ContentCategory.CONTAINER)) {
             OperationResult<Rows<String, String>> result = keyspace.prepareQuery(CONTAINER_CF).getAllRows().execute();
             containers = Iterators.transform(result.getResult().iterator(), new Function<Row, Content>() {

                    @Override
                    public Content apply(Row input) {
                        try {
                        return unmarshalContainer(input.getColumns());
                        } catch(Exception ex) {
                            return null;
                        }
                    }
                 
             });
            }
            return Iterators.concat(containers, items);
        } catch (Exception ex) {
            throw new CassandraPersistenceException(ex.getMessage(), ex);
        }
    }

    private void writeItem(Item item) throws Exception {
        MutationBatch mutation = keyspace.prepareMutationBatch();
        mutation.setConsistencyLevel(ConsistencyLevel.CL_QUORUM);

        FilterProvider filters = new SimpleFilterProvider().addFilter(ItemConfiguration.FILTER, SimpleBeanPropertyFilter.serializeAllExcept(ItemConfiguration.CLIPS_FILTER, ItemConfiguration.VERSIONS_FILTER));
        ObjectWriter writer = mapper.writer(filters);
        byte[] itemBytes = writer.writeValueAsBytes(item);
        byte[] clipsBytes = writer.writeValueAsBytes(item.getClips());
        byte[] versionsBytes = writer.writeValueAsBytes(item.getVersions());

        mutation.withRow(ITEMS_CF, item.getCanonicalUri()).
                putColumn(ITEM_COLUMN, itemBytes, null).
                putColumn(CLIPS_COLUMN, clipsBytes, null).
                putColumn(VERSIONS_COLUMN, versionsBytes, null);

        Future<OperationResult<Void>> result = mutation.executeAsync();
        try {
            result.get(requestTimeout, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            throw new CassandraPersistenceException(ex.getMessage(), ex);
        }
    }

    private void writeContainer(Container container) throws Exception {
        MutationBatch mutation = keyspace.prepareMutationBatch();
        mutation.setConsistencyLevel(ConsistencyLevel.CL_QUORUM);

        FilterProvider filters = new SimpleFilterProvider().addFilter(ContainerConfiguration.FILTER, SimpleBeanPropertyFilter.serializeAllExcept(ContainerConfiguration.CLIPS_FILTER, ContainerConfiguration.SUB_ITEMS_FILTER));
        ObjectWriter writer = mapper.writer(filters);
        byte[] containerBytes = writer.writeValueAsBytes(container);
        byte[] clipsBytes = writer.writeValueAsBytes(container.getClips());
        byte[] subItemsBytes = writer.writeValueAsBytes(container.getChildRefs());

        mutation.withRow(CONTAINER_CF, container.getCanonicalUri().toString()).
                putColumn(CONTAINER_COLUMN, containerBytes, null).
                putColumn(CLIPS_COLUMN, clipsBytes, null).
                putColumn(CHILDREN_COLUMN, subItemsBytes, null);

        Future<OperationResult<Void>> result = mutation.executeAsync();
        try {
            result.get(requestTimeout, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            throw new CassandraPersistenceException(ex.getMessage(), ex);
        }
    }

    private void attachItemToParent(Item item) throws Exception {
        ParentRef parent = item.getContainer();
        if (parent != null) {
            Container container = readContainer(parent.getUri());
            if (container != null) {
                container.setChildRefs(ChildRef.dedupeAndSort(Iterables.concat(container.getChildRefs(), ImmutableList.of(item.childRef()))));
                writeContainer(container);
            }
        }
    }

    private Content readContent(String id) throws Exception {
        try {
            Future<OperationResult<ColumnList<String>>> result = keyspace.prepareQuery(ITEMS_CF).getKey(id.toString()).executeAsync();
            OperationResult<ColumnList<String>> columns = result.get(requestTimeout, TimeUnit.MILLISECONDS);
            if (!columns.getResult().isEmpty()) {
                return unmarshalItem(columns.getResult());
            } else {
                return null;
            }
        } catch (Exception ex) {
            throw new CassandraPersistenceException(ex.getMessage(), ex);
        }
    }

    private Container readContainer(String id) throws Exception {
        try {
            Future<OperationResult<ColumnList<String>>> result = keyspace.prepareQuery(CONTAINER_CF).getKey(id.toString()).executeAsync();
            OperationResult<ColumnList<String>> columns = result.get(requestTimeout, TimeUnit.MILLISECONDS);
            if (!columns.getResult().isEmpty()) {
                return unmarshalContainer(columns.getResult());
            } else {
                return null;
            }
        } catch (Exception ex) {
            throw new CassandraPersistenceException(ex.getMessage(), ex);
        }
    }
    
    private Content unmarshalItem(ColumnList<String> columns) throws IOException {
        Item item = mapper.readValue(columns.getColumnByName(ITEM_COLUMN).getByteArrayValue(), Item.class);
        List<Clip> clips = mapper.readValue(columns.getColumnByName(CLIPS_COLUMN).getByteArrayValue(), TypeFactory.defaultInstance().constructCollectionType(List.class, Clip.class));
        Set<Version> versions = mapper.readValue(columns.getColumnByName(VERSIONS_COLUMN).getByteArrayValue(), TypeFactory.defaultInstance().constructCollectionType(Set.class, Version.class));
        item.setClips(clips);
        item.setVersions(versions);
        return item;
    }
    
    private Container unmarshalContainer(ColumnList<String> columns) throws IOException {
        Container container = mapper.readValue(columns.getColumnByName(ITEM_COLUMN).getByteArrayValue(), Container.class);
        List<Clip> clips = mapper.readValue(columns.getColumnByName(CLIPS_COLUMN).getByteArrayValue(), TypeFactory.defaultInstance().constructCollectionType(List.class, Clip.class));
        List<ChildRef> children = mapper.readValue(columns.getColumnByName(CHILDREN_COLUMN).getByteArrayValue(), TypeFactory.defaultInstance().constructCollectionType(List.class, ChildRef.class));
        container.setClips(clips);
        container.setChildRefs(children);
        return container;
    }
}
