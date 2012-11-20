package org.atlasapi.persistence.content.elasticsearch;

import static org.atlasapi.persistence.content.elasticsearch.schema.ESSchema.INDEX_NAME;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.media.entity.ChildRef;
import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.Encoding;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Location;
import org.atlasapi.media.entity.ParentRef;
import org.atlasapi.media.entity.Policy;
import org.atlasapi.media.entity.TopicRef;
import org.atlasapi.media.entity.Version;
import org.atlasapi.persistence.content.ContentIndexer;
import org.atlasapi.persistence.content.IndexException;
import org.atlasapi.persistence.content.elasticsearch.schema.ESBroadcast;
import org.atlasapi.persistence.content.elasticsearch.schema.ESContent;
import org.atlasapi.persistence.content.elasticsearch.schema.ESLocation;
import org.atlasapi.persistence.content.elasticsearch.schema.ESTopic;
import org.atlasapi.persistence.content.elasticsearch.support.Strings;
import org.atlasapi.persistence.elasticsearch.ESPersistenceException;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.NoShardAvailableActionException;
import org.elasticsearch.action.admin.indices.exists.IndicesExistsResponse;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.node.Node;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AbstractIdleService;
import com.metabroadcast.common.time.Clock;
import com.metabroadcast.common.time.DateTimeZones;
import com.metabroadcast.common.time.SystemClock;

/**
 */
public class ESContentIndexer extends AbstractIdleService implements ContentIndexer {

    private final Logger log = LoggerFactory.getLogger(ESContentIndexer.class);
    
    private final Node esClient;
    private final EsScheduleIndexNames scheduleNames;
    private final long requestTimeout;
    
    private Set<String> existingIndexes;
    
    protected ESContentIndexer(Node esClient) {
        this(esClient, new SystemClock());
    }

    protected ESContentIndexer(Node esClient, Clock clock) {
        this(esClient, clock, 60000);
    }

    public ESContentIndexer(Node esClient, Clock clock, long requestTimeout) {
        this.esClient = esClient;
        this.scheduleNames = new EsScheduleIndexNames(esClient, clock);
        this.requestTimeout = requestTimeout;
    }

    @Override
    protected void startUp() throws IOException {
        if (createIndex(INDEX_NAME)) {
            putTopContentMapping(INDEX_NAME);
            putChildContentMapping();
        }
        this.existingIndexes = Sets.newHashSet(scheduleNames.existingIndexNames());
        log.info("Found existing indices {}", existingIndexes);
    }
    
    @Override
    protected void shutDown() throws Exception {
        
    }

    private boolean createIndex(String name) {
        ActionFuture<IndicesExistsResponse> exists = esClient.client().admin().indices().exists(
            Requests.indicesExistsRequest(name)
        );
        if (!timeoutGet(exists).isExists()) {
            log.info("Creating index {}", name);
            timeoutGet(esClient.client().admin().indices().create(Requests.createIndexRequest(name)));
            return true;
        } else {
            log.info("Index {} exists", name);
            return false;
        }
    }

    private void putTopContentMapping(String index) throws IOException, ElasticSearchException {
        log.info("Putting mapping for index {}", index);
        ActionFuture<PutMappingResponse> putMapping = esClient
            .client()
            .admin()
            .indices()
            .putMapping(Requests.putMappingRequest(index)
                .type(ESContent.TOP_LEVEL_TYPE)
                .source(XContentFactory.jsonBuilder()
                    .startObject()
                        .startObject(ESContent.TOP_LEVEL_TYPE)
                            .startObject("properties")
                                .startObject(ESContent.URI)
                                    .field("type").value("string")
                                    .field("index").value("not_analyzed")
                                .endObject()
                                .startObject(ESContent.TITLE)
                                    .field("type").value("string")
                                    .field("index").value("analyzed")
                                .endObject()
                                .startObject(ESContent.FLATTENED_TITLE)
                                    .field("type").value("string")
                                    .field("index").value("analyzed")
                                .endObject()
                                .startObject(ESContent.PUBLISHER)
                                    .field("type").value("string")
                                    .field("index").value("not_analyzed")
                                .endObject()
                                .startObject(ESContent.SPECIALIZATION)
                                    .field("type").value("string")
                                    .field("index").value("not_analyzed")
                                .endObject()
                                .startObject(ESContent.BROADCASTS)
                                    .field("type").value("nested")
                                    .startObject("properties")
                                        .startObject(ESBroadcast.CHANNEL)
                                            .field("type").value("string")
                                            .field("index").value("not_analyzed")
                                        .endObject()
                                    .endObject()
                                .endObject()
                                .startObject(ESContent.LOCATIONS)
                                    .field("type").value("nested")
                                .endObject()
                            .endObject()
                        .endObject()
                    .endObject()
                )
            );
        putMapping.actionGet(requestTimeout, TimeUnit.MILLISECONDS);
    }

    private void putChildContentMapping() throws ElasticSearchException, IOException {
        ActionFuture<PutMappingResponse> putMapping = esClient
            .client()
            .admin()
            .indices()
            .putMapping(Requests.putMappingRequest(INDEX_NAME)
                .type(ESContent.CHILD_TYPE)
                .source(XContentFactory.jsonBuilder()
                    .startObject()
                        .startObject(ESContent.CHILD_TYPE)
                            .startObject("_parent")
                                .field("type").value(ESContent.TOP_LEVEL_TYPE)
                            .endObject()
                            .startObject("properties")
                                .startObject(ESContent.URI)
                                    .field("type").value("string")
                                    .field("index").value("not_analyzed")
                                .endObject()
                                .startObject(ESContent.TITLE)
                                    .field("type").value("string")
                                    .field("index").value("analyzed")
                                .endObject()
                                .startObject(ESContent.FLATTENED_TITLE)
                                        .field("type").value("string")
                                        .field("index").value("analyzed")
                                .endObject()
                                .startObject(ESContent.PUBLISHER)
                                    .field("type").value("string")
                                    .field("index").value("not_analyzed")
                                .endObject()
                                .startObject(ESContent.SPECIALIZATION)
                                    .field("type").value("string")
                                    .field("index").value("not_analyzed")
                                .endObject()
                                .startObject(ESContent.BROADCASTS)
                                    .field("type").value("nested")
                                    .startObject("properties")
                                        .startObject(ESBroadcast.CHANNEL)
                                            .field("type").value("string")
                                            .field("index").value("not_analyzed")
                                        .endObject()
                                    .endObject()
                                .endObject()
                                .startObject(ESContent.LOCATIONS)
                                    .field("type").value("nested")
                                .endObject()
                            .endObject()
                        .endObject()
                    .endObject()
                )
            );
        putMapping.actionGet(requestTimeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public void index(Item item) throws IndexException {
        try {
            ESContent esContent = toEsContent(item);
            
            BulkRequest requests = Requests.bulkRequest();
            IndexRequest mainIndexRequest;
            if (item.getContainer() != null) {
                fillParentData(esContent, item.getContainer());
                mainIndexRequest = Requests.indexRequest(INDEX_NAME)
                    .type(ESContent.CHILD_TYPE)
                    .id(item.getCanonicalUri())
                    .source(esContent.toMap())
                    .parent(item.getContainer().getUri());
            } else {
                mainIndexRequest = Requests.indexRequest(INDEX_NAME)
                    .type(ESContent.TOP_LEVEL_TYPE)
                    .id(item.getCanonicalUri())
                    .source(esContent.hasChildren(false).toMap());
            }
            
            requests.add(mainIndexRequest);
            Map<String, ActionRequest> scheduleRequests = scheduleIndexRequests(item);
            ensureIndices(scheduleRequests);
            requests.add(scheduleRequests.values());
            timeoutGet(esClient.client().bulk(requests));
        } catch (Exception e) {
            throw new IndexException("Error indexing " + item, e);
        }
    }

    private ESContent toEsContent(Item item) {
        return new ESContent()
            .uri(item.getCanonicalUri())
            .title(item.getTitle())
            .flattenedTitle(flattenedOrNull(item.getTitle()))
            .parentTitle(item.getTitle())
            .parentFlattenedTitle(flattenedOrNull(item.getTitle()))
            .publisher(item.getPublisher() != null ? item.getPublisher().key() : null)
            .specialization(item.getSpecialization() != null ? item.getSpecialization().name() : null)
            .broadcasts(makeESBroadcasts(item))
            .locations(makeESLocations(item))
            .topics(makeESTopics(item));
    }

    private void ensureIndices(Map<String, ActionRequest> scheduleRequests) throws IOException {
        Set<String> missingIndices = Sets.difference(scheduleRequests.keySet(), existingIndexes);
        for (String missingIndex : missingIndices) {
            if (createIndex(missingIndex)) {
                putTopContentMapping(missingIndex);
                existingIndexes.add(missingIndex);
            }
        }
    }

    private Map<String,ActionRequest> scheduleIndexRequests(Item item) {
        Multimap<String, ESBroadcast> indicesBroadcasts = HashMultimap.create();
        for (Version version : item.getVersions()) {
            for (Broadcast broadcast : version.getBroadcasts()) {
                ESBroadcast esBroadcast = toEsBroadcast(broadcast);
                Iterable<String> indices = scheduleNames.indexingNamesFor(
                    broadcast.getTransmissionTime(),
                    broadcast.getTransmissionEndTime()
                );
                for (String index : indices) {
                    indicesBroadcasts.put(index, esBroadcast);
                };
            }
        }
        
        Builder<String, ActionRequest> requests = ImmutableMap.builder();
        for (Entry<String, Collection<ESBroadcast>> indexBroadcasts : indicesBroadcasts.asMap().entrySet()) {
            requests.put(
                indexBroadcasts.getKey(), 
                Requests.indexRequest(indexBroadcasts.getKey())
                    .type(ESContent.TOP_LEVEL_TYPE)
                    .id(item.getCanonicalUri())
                    .source(new ESContent()
                        .uri(item.getCanonicalUri())
                        .publisher(item.getPublisher() != null ? item.getPublisher().key() : null)
                        .broadcasts(indexBroadcasts.getValue())
                        .hasChildren(false)
                        .toMap()
                    )
            );
        }
        
        return requests.build();
    }

    @Override
    public void index(Container container) {
        ESContent indexed = new ESContent()
            .uri(container.getCanonicalUri())
            .title(container.getTitle())
            .flattenedTitle(flattenedOrNull(container.getTitle()))
            .parentTitle(container.getTitle())
            .parentFlattenedTitle(flattenedOrNull(container.getTitle()))
            .publisher(container.getPublisher() != null ? container.getPublisher().key() : null)
            .specialization(container.getSpecialization() != null ? container.getSpecialization().name() : null);
        
        if (!container.getChildRefs().isEmpty()) {
            indexed.hasChildren(Boolean.TRUE);
            indexChildrenData(container);
        } else {
            indexed.hasChildren(Boolean.FALSE);
        }
        IndexRequest request = Requests.indexRequest(INDEX_NAME)
            .type(ESContent.TOP_LEVEL_TYPE)
            .id(container.getCanonicalUri())
            .source(indexed.toMap());
        doIndex(request);
    }

    private void doIndex(IndexRequest request) {
        timeoutGet(esClient.client().index(request));
    }

    private String flattenedOrNull(String string) {
        return string != null ? Strings.flatten(string) : null;
    }
    
    private Collection<ESBroadcast> makeESBroadcasts(Item item) {
        Collection<ESBroadcast> esBroadcasts = new LinkedList<ESBroadcast>();
        for (Version version : item.getVersions()) {
            for (Broadcast broadcast : version.getBroadcasts()) {
                if (broadcast.isActivelyPublished()) {
                    esBroadcasts.add(toEsBroadcast(broadcast));
                }
            }
        }
        return esBroadcasts;
    }

    private ESBroadcast toEsBroadcast(Broadcast broadcast) {
        return new ESBroadcast()
            .id(broadcast.getSourceId())
            .channel(broadcast.getBroadcastOn())
            .transmissionTime(toUtc(broadcast.getTransmissionTime()).toDate())
            .transmissionEndTime(toUtc(broadcast.getTransmissionEndTime()).toDate())
            .transmissionTimeInMillis(toUtc(broadcast.getTransmissionTime()).getMillis())
            .repeat(broadcast.getRepeat() != null ? broadcast.getRepeat() : false);
    }

    private DateTime toUtc(DateTime transmissionTime) {
        return transmissionTime.toDateTime(DateTimeZones.UTC);
    }

    private Collection<ESLocation> makeESLocations(Item item) {
        Collection<ESLocation> esLocations = new LinkedList<ESLocation>();
        for (Version version : item.getVersions()) {
            for (Encoding encoding : version.getManifestedAs()) {
                for (Location location : encoding.getAvailableAt()) {
                    if (location.getPolicy() != null 
                        && location.getPolicy().getAvailabilityStart() != null
                        && location.getPolicy().getAvailabilityEnd() != null) {
                            esLocations.add(toEsLocation(location.getPolicy()));
                    }
                }
            }
        }
        return esLocations;
    }

    private ESLocation toEsLocation(Policy policy) {
        return new ESLocation()
            .availabilityTime(toUtc(policy.getAvailabilityStart()).toDate())
            .availabilityEndTime(toUtc(policy.getAvailabilityEnd()).toDate());
    }

    private Collection<ESTopic> makeESTopics(Item item) {
        Collection<ESTopic> esTopics = new LinkedList<ESTopic>();
        for (TopicRef topic : item.getTopicRefs()) {
            esTopics.add(new ESTopic().id(topic.getTopic()));
        }
        return esTopics;
    }

    private void fillParentData(ESContent child, ParentRef parent) {
        Map<String, Object> indexedContainer = trySearchParent(parent);
        if (indexedContainer != null) {
            Object title = indexedContainer.get(ESContent.TITLE);
            child.parentTitle(title != null ? title.toString() : null);
            Object flatTitle = indexedContainer.get(ESContent.FLATTENED_TITLE);
            child.parentFlattenedTitle(flatTitle != null ? flatTitle.toString() : null);
        }
    }

    private void indexChildrenData(Container parent) {
        BulkRequest bulk = Requests.bulkRequest();
        for (ChildRef child : parent.getChildRefs()) {
            Map<String, Object> indexedChild = trySearchChild(parent, child);
            if (indexedChild != null) {
                if (parent.getTitle() != null) {
                    indexedChild.put(ESContent.PARENT_TITLE, parent.getTitle());
                    indexedChild.put(ESContent.PARENT_FLATTENED_TITLE, Strings.flatten(parent.getTitle()));
                    bulk.add(Requests.indexRequest(INDEX_NAME).
                            type(ESContent.CHILD_TYPE).
                            parent(parent.getCanonicalUri()).
                            id(child.getUri()).
                            source(indexedChild));
                }
            }
        }
        if (bulk.numberOfActions() > 0) {
            BulkResponse response = timeoutGet(esClient.client().bulk(bulk));
            if (response.hasFailures()) {
                throw new ESPersistenceException("Failed to index children for container: " + parent.getCanonicalUri());
            }
        }
    }

    private Map<String, Object> trySearchParent(ParentRef parent) {
        GetRequest request = Requests.getRequest(INDEX_NAME).id(parent.getUri());
        GetResponse response = timeoutGet(esClient.client().get(request));
        if (response.exists()) {
            return response.sourceAsMap();
        } else {
            return null;
        }
    }

    private Map<String, Object> trySearchChild(Container parent, ChildRef child) {
        try {
            GetRequest request = Requests.getRequest(INDEX_NAME)
                    .parent(parent.getCanonicalUri())
                    .id(child.getUri());
            GetResponse response = timeoutGet(esClient.client().get(request));
            if (response.exists()) {
                return response.sourceAsMap();
            } else {
                return null;
            }
        } catch (NoShardAvailableActionException ex) {
            return null;
        }
    }
    
    private <T> T timeoutGet(ActionFuture<T> future) {
        return future.actionGet(requestTimeout, TimeUnit.MILLISECONDS);
    }

}
