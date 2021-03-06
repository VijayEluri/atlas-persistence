package org.atlasapi.persistence.content.mongo;

import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;
import static com.metabroadcast.common.persistence.mongo.MongoBuilders.where;
import static org.atlasapi.persistence.media.entity.ContainerTranslator.CHILDREN_KEY;

import java.util.List;
import java.util.Set;

import com.mongodb.ReadPreference;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.ChildRef;
import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Series;
import org.atlasapi.media.entity.SeriesRef;
import org.atlasapi.persistence.content.ContentCategory;
import org.atlasapi.persistence.media.entity.ContainerTranslator;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.ids.SubstitutionTableNumberCodec;
import com.metabroadcast.common.persistence.mongo.DatabasedMongo;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

public class ChildRefWriter {

    private static final Logger timerLog = LoggerFactory.getLogger("TIMER");
    private static final Logger log = LoggerFactory.getLogger(ChildRefWriter.class);
            
    private final DBCollection containers;
    private final DBCollection programmeGroups;
    
    private final ContainerTranslator containerTranslator;

    public ChildRefWriter(DatabasedMongo mongo) {
        MongoContentTables mongoTables = new MongoContentTables(mongo);
        this.containers = mongoTables.collectionFor(ContentCategory.CONTAINER);
        this.programmeGroups = mongoTables.collectionFor(ContentCategory.PROGRAMME_GROUP);
        this.containerTranslator = new ContainerTranslator(new SubstitutionTableNumberCodec());
        this.containers.setReadPreference(ReadPreference.primary());
        this.programmeGroups.setReadPreference(ReadPreference.primary());
    }

    public void includeEpisodeInSeriesAndBrand(Episode episode) {

        if (Boolean.TRUE.equals(episode.getGenericDescription())
                && episode.getEpisodeNumber() == null
                && episode.getSeriesNumber() == null) {
            log.debug("Not including episode " + episode.getCanonicalUri() + "in series and brand.");
            return;
        }
        
        ChildRef childRef = episode.childRef();

        if (episode.getSeriesRef() == null) { // just ensure item in container.
            includeChildRefInContainer(episode, childRef, containers, CHILDREN_KEY, episode.isActivelyPublished());
            return;
        }

        // otherwise retrieve the child references for both series and brand, if
        // either are missing, change nothing and error out.
        String brandUri = episode.getContainer().getUri();
        String seriesUri = episode.getSeriesRef().getUri();

        Maybe<Container> maybeBrand = getContainer(brandUri, containers);
        Maybe<Container> maybeSeries = getContainer(seriesUri, programmeGroups);

        if (maybeBrand.isNothing() || maybeSeries.isNothing()) {
            throw new IllegalStateException(
                    String.format("Container %s or series %s not found for episode %s", 
                                  brandUri, seriesUri, episode.getCanonicalUri()));
        }

        episode.setContainer(maybeBrand.requireValue());
        episode.setSeries((Series)maybeSeries.requireValue());
        
        addChildRef(childRef, containers, maybeBrand.requireValue(), episode.isActivelyPublished());
        addChildRef(childRef, programmeGroups, maybeSeries.requireValue(), episode.isActivelyPublished());

    }

    public void includeSeriesInTopLevelContainer(Series series) {
        String containerUri = series.getParent().getUri();
        
        Maybe<Container> maybeContainer = getContainer(containerUri, containers);

        if (!maybeContainer.hasValue()) {
            throw new IllegalStateException(String.format("Container %s not found in %s for series child ref %s", containerUri, containers.getName(), series.getCanonicalUri()));
        }
        Container container = maybeContainer.requireValue();
        if(!(container instanceof Brand)) {
            throw new IllegalStateException(String.format("Container %s for series child ref %s is not brand", containerUri, series.getCanonicalUri()));
        }

        Brand brand = (Brand) container;
        List<SeriesRef> merged = mergeSeriesRefs(series.seriesRef(), brand.getSeriesRefs(), series.isActivelyPublished());
        brand.setSeriesRefs(merged);
        brand.setThisOrChildLastUpdated(laterOf(brand.getThisOrChildLastUpdated(), series.getThisOrChildLastUpdated()));
        save(brand, containers);
        series.setParent(brand);
    }

    private void save(Container container, DBCollection collection) {
        if (!container.hashChanged(containerTranslator.hashCodeOf(container, true))) {
            log.debug("Container {} hash not changed. Not writing.", container.getCanonicalUri());
            return;
        }
        
        log.debug("Container {} hash changed so writing to db. There are {} ChildRefs", 
                container.getCanonicalUri(), container.getChildRefs().size());
        Long startTime = System.nanoTime();
        timerLog.debug("TIMER CCR saving {}. {}", String.format("%s kids=%s equiv=%s to %s",container.getCanonicalUri(), container.getChildRefs().size(), container.getEquivalentTo().size(),collection.getName()), Thread.currentThread().getName());
        collection.save(containerTranslator.toDBO(container, true));
        timerLog.debug("TIMER CCR saving took {}ms. {}", (System.nanoTime() - startTime)/1000000, Thread.currentThread().getName());

    }

    private DateTime laterOf(DateTime left, DateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    public void includeItemInTopLevelContainer(Item item) {
        includeChildRefInContainer(item, item.childRef(), containers, CHILDREN_KEY, item.isActivelyPublished());
    }

    private void includeChildRefInContainer(Item item, ChildRef ref, DBCollection collection, String key, boolean activelyPublished) {

        String containerUri = item.getContainer().getUri();
        Maybe<Container> maybeContainer = getContainer(containerUri, collection);

        if (maybeContainer.hasValue()) {
            Container container = maybeContainer.requireValue();
            addChildRef(ref, collection, container, item.isActivelyPublished());
            item.setContainer(container);
        } else {
            throw new IllegalStateException(String.format("Container %s not found in %s for child ref %s", containerUri, collection.getName(), ref.getUri()));
        }
    }

    private void addChildRef(ChildRef ref, DBCollection collection, Container container, boolean activelyPublished) {
        List<ChildRef> merged = mergeChildRefs(ref, container.getChildRefs(), activelyPublished);
        container.setThisOrChildLastUpdated(laterOf(container.getThisOrChildLastUpdated(), ref.getUpdated()));
        container.setChildRefs(merged);
        save(container, collection);
    }

    private List<ChildRef> mergeChildRefs(ChildRef newChildRef, Iterable<ChildRef> currentChildRefs, boolean activelyPublished) {
        // filter out new refs so they can  be overwritten.
        currentChildRefs = filter(currentChildRefs, not(equalTo(newChildRef)));
        if (activelyPublished) {
            return ChildRef.dedupeAndSort(ImmutableList.<ChildRef> builder().addAll(currentChildRefs).add(newChildRef).build());
        } else {
            return ChildRef.dedupeAndSort(ImmutableList.<ChildRef> builder().addAll(currentChildRefs).build());
        }
        
    }
    
    private List<SeriesRef> mergeSeriesRefs(SeriesRef newSeriesRef, Iterable<SeriesRef> currentSeriesRefs, Boolean activelyPublished) {
        // filter out new refs so they can  be overwritten.
        currentSeriesRefs = filter(currentSeriesRefs, not(equalTo(newSeriesRef)));
        if(activelyPublished) {
            return SeriesRef.dedupeAndSort(ImmutableList.<SeriesRef>builder().addAll(currentSeriesRefs).add(newSeriesRef).build());
        } else {
            return SeriesRef.dedupeAndSort(ImmutableList.<SeriesRef>builder().addAll(currentSeriesRefs).build());
        }
    }

    private Maybe<Container> getContainer(String canonicalUri, DBCollection collection) {
        DBObject dbo = collection.findOne(where().idEquals(canonicalUri).build());
        if (dbo == null) {
            return Maybe.nothing();
        }
        return Maybe.<Container> fromPossibleNullValue(containerTranslator.fromDB(dbo, true));
    }
    
}
