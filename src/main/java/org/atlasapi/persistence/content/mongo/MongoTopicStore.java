package org.atlasapi.persistence.content.mongo;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.metabroadcast.common.persistence.mongo.MongoBuilders.where;
import static com.metabroadcast.common.persistence.mongo.MongoConstants.ID;
import static com.metabroadcast.common.persistence.mongo.MongoConstants.SINGLE;
import static com.metabroadcast.common.persistence.mongo.MongoConstants.UPSERT;
import static org.atlasapi.persistence.media.entity.TopicTranslator.NAMESPACE;
import static org.atlasapi.persistence.media.entity.TopicTranslator.PUBLISHER;
import static org.atlasapi.persistence.media.entity.TopicTranslator.VALUE;

import org.atlasapi.content.criteria.ContentQuery;
import org.atlasapi.media.common.Id;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.topic.Topic;
import org.atlasapi.persistence.media.entity.TopicTranslator;
import org.atlasapi.persistence.topic.TopicStore;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.persistence.mongo.DatabasedMongo;
import com.metabroadcast.common.persistence.mongo.MongoConstants;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class MongoTopicStore implements TopicStore {

    private final DBCollection collection;
    private final TopicTranslator translator;
    private final MongoDBQueryBuilder queryBuilder;

    public MongoTopicStore(DatabasedMongo mongo) {
        this.collection = mongo.collection("topics");
        this.translator = new TopicTranslator();
        this.queryBuilder = new MongoDBQueryBuilder();
    }

    @Override
    public void write(Topic topic) {
        checkNotNull(topic.getId(), "Can't persist topic with no ID");

        DBObject dbo = translator.toDBObject(topic);

        collection.update(where().idEquals((Long)dbo.get(ID)).build(), dbo, UPSERT, SINGLE);
    }

    @Override
    public Maybe<Topic> topicForId(Id id) {
        return topicForQuery(where().idEquals(id.longValue()).build());
    }

    private Maybe<Topic> topicForQuery(DBObject query) {
        DBObject dbo = collection.findOne(query);
        if (dbo == null) {
            return Maybe.nothing();
        }
        return Maybe.just(translator.fromDBObject(dbo));
    }

    @Override
    public Maybe<Topic> topicFor(String namespace, String value) {
        return topicForQuery(where().fieldEquals(VALUE, value).fieldEquals(NAMESPACE, namespace).build());
    }
    
    @Override
    public Maybe<Topic> topicFor(Publisher publisher, String namespace, String value) {
    	return topicForQuery(where().fieldEquals(VALUE, value).fieldEquals(NAMESPACE, namespace).fieldEquals(PUBLISHER, Publisher.TO_KEY.apply(publisher)).build());
    }

    @Override
    public Iterable<Topic> topicsForIds(Iterable<Id> ids) {
        Iterable<Long> longIds = Iterables.transform(ids,Id.toLongValue());
        DBCursor dbos = collection.find(new BasicDBObject(MongoConstants.ID, new BasicDBObject(MongoConstants.IN, longIds)));
        return transform(dbos);
    }

    private Iterable<Topic> transform(DBCursor dbos) {
        return Iterables.transform(dbos, new Function<DBObject, Topic>() {
            @Override
            public Topic apply(DBObject input) {
                return translator.fromDBObject(input);
            }
        });
    }

    @Override
    public Iterable<Topic> topicsFor(ContentQuery query) {
        DBObject dbQuery = queryBuilder.buildQuery(query);
        return transform(collection.find(dbQuery));
    }
    
    @Override
    public Iterable<Topic> topics() {
        return transform(collection.find());
    }
}
