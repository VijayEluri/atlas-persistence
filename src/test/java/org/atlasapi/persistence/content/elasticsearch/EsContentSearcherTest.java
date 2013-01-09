package org.atlasapi.persistence.content.elasticsearch;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.ParentRef;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Specialization;
import org.atlasapi.media.entity.Version;
import org.atlasapi.persistence.content.elasticsearch.schema.EsSchema;
import org.atlasapi.search.model.SearchQuery;
import org.atlasapi.search.model.SearchResults;
import org.elasticsearch.client.Requests;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.metabroadcast.common.query.Selection;

public class EsContentSearcherTest {

    private final Node esClient = NodeBuilder.nodeBuilder()
        .local(true).clusterName(UUID.randomUUID().toString())
        .build().start();

    @BeforeClass
    public static void before() throws Exception {
        Logger root = Logger.getRootLogger();
        root.addAppender(new ConsoleAppender(
            new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN)));
        root.setLevel(Level.WARN);
    }

    @After
    public void after() throws Exception {
        esClient.client().admin().indices()
            .delete(Requests.deleteIndexRequest(EsSchema.INDEX_NAME)).get();
        esClient.close();
    }
    
    @Test
    public void testSearch() throws Exception {
        Broadcast broadcast1 = new Broadcast("MB", new DateTime(), new DateTime().plusHours(1));
        Version version1 = new Version();
        Broadcast broadcast2 = new Broadcast("MB", new DateTime().plusHours(3), new DateTime().plusHours(4));
        Version version2 = new Version();
        version1.addBroadcast(broadcast1);
        version2.addBroadcast(broadcast2);
        //
        Item item1 = new Item("uri1", "curie1", Publisher.METABROADCAST);
        item1.setTitle("title1");
        item1.addVersion(version1);
        Item item2 = new Item("uri2", "curie2", Publisher.METABROADCAST);
        item2.setTitle("title2");
        item2.addVersion(version1);
        Item item3 = new Item("uri3", "curie3", Publisher.METABROADCAST);
        item3.setTitle("pippo");
        item3.addVersion(version2);
        Item item4 = new Item("uri4", "curie4", Publisher.METABROADCAST);
        item4.setTitle("title4");
        item4.addVersion(version2);
        //
        Brand brand1 = new Brand("buri1", "buri1", Publisher.METABROADCAST);
        brand1.setTitle("title");
        brand1.setChildRefs(Arrays.asList(item1.childRef(), item2.childRef()));
        Brand brand2 = new Brand("buri2", "buri2", Publisher.METABROADCAST);
        brand2.setTitle("b");
        brand2.setChildRefs(Arrays.asList(item3.childRef()));
        //
        item1.setParentRef(ParentRef.parentRefFrom(brand1));
        item2.setParentRef(ParentRef.parentRefFrom(brand1));
        item3.setParentRef(ParentRef.parentRefFrom(brand2));

        EsContentIndexer contentIndexer = new EsContentIndexer(esClient);
        contentIndexer.startAndWait();

        EsContentSearcher contentSearcher = new EsContentSearcher(esClient);

        contentIndexer.index(brand1);
        contentIndexer.index(brand2);
        contentIndexer.index(item1);
        contentIndexer.index(item2);
        contentIndexer.index(item3);
        contentIndexer.index(item4);

        Thread.sleep(1000);

        ListenableFuture<SearchResults> future = contentSearcher.search(new SearchQuery("title",
                Selection.offsetBy(0),
                ImmutableSet.<Specialization>of(),
                ImmutableSet.<Publisher>of(),
                1, 0f, 0f));
        SearchResults results = future.get();
        assertEquals(2, results.toUris().size());
        assertEquals("buri1", results.toUris().get(0));
        assertEquals("uri4", results.toUris().get(1));
    }
}
