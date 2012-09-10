package org.atlasapi.persistence;

import java.io.IOException;

import org.atlasapi.persistence.content.elasticsearch.ESContentIndexer;
import org.atlasapi.persistence.content.elasticsearch.EsScheduleIndex;
import org.atlasapi.persistence.content.elasticsearch.schema.ESSchema;
import org.atlasapi.persistence.topic.elasticsearch.ESTopicSearcher;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

public class ElasticSearchContentIndexModule {

    private final ESContentIndexer contentIndexer;
    private final EsScheduleIndex scheduleIndex;
    private final ESTopicSearcher topicSearcher;

    public ElasticSearchContentIndexModule(String seeds, long requestTimeout) {
        Node index = NodeBuilder.nodeBuilder().client(true).
                clusterName(ESSchema.CLUSTER_NAME).
                settings(ImmutableSettings.settingsBuilder().put("discovery.zen.ping.unicast.hosts", seeds)).
                build().start();
        this.contentIndexer = new ESContentIndexer(index, requestTimeout);
        this.scheduleIndex = new EsScheduleIndex(index);
        this.topicSearcher = new ESTopicSearcher(index, requestTimeout);
    }

    public void init() {
        try {
            contentIndexer.init();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public ESContentIndexer contentIndexer() {
        return contentIndexer;
    }

    public EsScheduleIndex scheduleIndex() {
        return scheduleIndex;
    }

    public ESTopicSearcher topicSearcher() {
        return topicSearcher;
    }
}