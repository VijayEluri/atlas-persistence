package org.atlasapi.persistence.output;

import static com.metabroadcast.common.persistence.mongo.MongoBuilders.select;
import static com.metabroadcast.common.persistence.mongo.MongoBuilders.where;
import static com.metabroadcast.common.persistence.translator.TranslatorUtils.toDBObjectList;
import static com.metabroadcast.common.persistence.translator.TranslatorUtils.toDateTime;

import org.atlasapi.media.entity.ChildRef;
import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.EntityType;
import org.atlasapi.media.entity.Person;
import org.atlasapi.persistence.content.ContentCategory;
import org.joda.time.DateTime;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.persistence.mongo.DatabasedMongo;
import com.metabroadcast.common.persistence.mongo.MongoConstants;
import com.metabroadcast.common.persistence.translator.TranslatorUtils;
import com.metabroadcast.common.time.Clock;
import com.metabroadcast.common.time.SystemClock;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

public class MongoUpcomingItemsResolver implements UpcomingItemsResolver {
    
    private final String versions = "versions";
    private final String broadcasts = "broadcasts";
    private final String transmissionEndTime = "transmissionEndTime";
    
    private final String transmissionEndTimeKey = Joiner.on(".").join(versions, broadcasts, transmissionEndTime);
    private final String containerKey = "container";
    
    private final DBObject fields = select().field(transmissionEndTimeKey).build();

    private final DBCollection children;
    private final DBCollection topLevelItems;
    private final Clock clock;
    
    public MongoUpcomingItemsResolver(DatabasedMongo db) {
        this(db, new SystemClock());
    }
    
    public MongoUpcomingItemsResolver(DatabasedMongo db, Clock clock) {
        this.children = db.collection(ContentCategory.CHILD_ITEM.tableName());
        this.topLevelItems = db.collection(ContentCategory.TOP_LEVEL_ITEM.tableName());
        this.clock = clock;
    }
    
    @Override
    public Iterable<ChildRef> upcomingItemsFor(Container container) {
        final DateTime now = clock.now();
        return filterToChildRef(now, broadcastEndsForChildrenOf(container, now));
    }
    
    @Override
    public Iterable<ChildRef> upcomingItemsFor(Person person) {
        final DateTime now = clock.now();
        return filterToChildRef(now, broadcastEndsForItemsOf(person, now));
    }

    private Iterable<ChildRef> filterToChildRef(final DateTime now,
            Iterable<DBObject> broadcastEnds) {
        return Iterables.filter(Iterables.transform(broadcastEnds, new Function<DBObject, ChildRef>() {

            @Override
            public ChildRef apply(DBObject input) {
                for (DBObject version : toDBObjectList(input, versions)) {
                    for (DBObject broadcast : toDBObjectList(version, broadcasts)) {
                            if (after(toDateTime(broadcast, transmissionEndTime), now)) {
                                String uri = TranslatorUtils.toString(input, MongoConstants.ID);
                                Long aid = TranslatorUtils.toLong(input, "aid");
                                String type = TranslatorUtils.toString(input, null);
                                String sortKey = TranslatorUtils.toString(input, "sortKey");
                                DateTime lastUpdated = TranslatorUtils.toDateTime(input, "lastUpdated");
                                return new ChildRef(aid, uri, sortKey, lastUpdated, EntityType.from(type));
                            }
                        }
                    }
                return null;
            }
        }),Predicates.notNull());
    }
    
    private boolean after(DateTime dateTime, DateTime now) {
        return dateTime != null && dateTime.isAfter(now);
    }

    private Iterable<DBObject> broadcastEndsForChildrenOf(Container container, DateTime time) {
        DBObject query = where()
            .fieldEquals(containerKey, container.getCanonicalUri())
            .fieldAfter(transmissionEndTimeKey, time)
            .build();
        return children.find(query,fields);
    }

    private Iterable<DBObject> broadcastEndsForItemsOf(Person person, DateTime time) {
        DBObject query = where()
            .idIn(Iterables.transform(person.getContents(), ChildRef.TO_URI))
            .fieldAfter(transmissionEndTimeKey, time)
            .build();
        return Iterables.concat(children.find(query,fields), topLevelItems.find(query,fields));
    }
    
}
