package org.atlasapi.persistence.media.entity;

import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Topic;
import org.atlasapi.persistence.content.mongo.DbObjectHashCodeDebugger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.metabroadcast.common.persistence.mongo.MongoConstants;
import com.metabroadcast.common.persistence.translator.ModelTranslator;
import com.metabroadcast.common.persistence.translator.TranslatorUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class TopicTranslator implements ModelTranslator<Topic> {

    private static final Logger log = LoggerFactory.getLogger(TopicTranslator.class);
    
    public static final String VALUE = "value";
    public static final String NAMESPACE = "namespace";
    public static final String TOPIC_TYPE = "topicType";
    public static final String PUBLISHER = "publisher";
    
    private final DescribedTranslator describedTranslator;
    private final DbObjectHashCodeDebugger dboHashCodeDebugger = new DbObjectHashCodeDebugger();

    public TopicTranslator() {
        this.describedTranslator = new DescribedTranslator(new IdentifiedTranslator(true), new ImageTranslator());
    }
    
    public DBObject toDBObject(Topic model) {
        return this.toDBObject(new BasicDBObject(), model);
    }
    
    @Override
    public DBObject toDBObject(DBObject dbObject, Topic model) {
        if (dbObject == null) {
            dbObject = new BasicDBObject();
        }
        
        describedTranslator.toDBObject(dbObject, model);
        
        if (model.getType() != null) {
            TranslatorUtils.from(dbObject, TOPIC_TYPE, model.getType().key());
        }
        TranslatorUtils.from(dbObject, NAMESPACE, model.getNamespace());
        TranslatorUtils.from(dbObject, VALUE, model.getValue());
        TranslatorUtils.from(dbObject, PUBLISHER, Publisher.TO_KEY.apply(model.getPublisher()));
        
        return dbObject;
    }
    
    public Topic fromDBObject(DBObject dbObject) {
        return this.fromDBObject(dbObject, null);
    }

    @Override
    public Topic fromDBObject(DBObject dbObject, Topic model) {
        if (model == null) {
            model = new Topic(TranslatorUtils.toLong(dbObject, MongoConstants.ID));
        }
        
        describedTranslator.fromDBObject(dbObject, model);
        
        String typeKey = TranslatorUtils.toString(dbObject, TOPIC_TYPE);
        if (typeKey != null) {
            model.setType(Topic.Type.fromKey(typeKey));
        }
        model.setNamespace(TranslatorUtils.toString(dbObject, NAMESPACE));
        model.setValue(TranslatorUtils.toString(dbObject, VALUE));
        model.setPublisher(Publisher.fromKey(TranslatorUtils.toString(dbObject, PUBLISHER)).valueOrNull());
        model.setReadHash(generateHashByRemovingFieldsFromTheDbo(dbObject));
        return model;
    }
    
    public String hashCodeOf(Topic topic) {
        return generateHashByRemovingFieldsFromTheDbo(toDBObject(topic));
    }

    private String generateHashByRemovingFieldsFromTheDbo(DBObject dbObject) {
        removeFieldsForHash(dbObject);
        if (log.isTraceEnabled()) {
            dboHashCodeDebugger.logHashCodes(dbObject, log);
        }
        return String.valueOf(dbObject.hashCode());
    }

    public void removeFieldsForHash(DBObject dbObject) {
        describedTranslator.removeFieldsForHash(dbObject);
    }
 
}
