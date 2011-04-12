package org.atlasapi.persistence.media.entity;

import org.atlasapi.media.entity.Broadcast;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import com.metabroadcast.common.persistence.translator.TranslatorUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class BroadcastTranslator  {
	
    private static final String TRANSMISSION_END_TIME_KEY = "transmissionEndTime";
	private static final String TRANSMISSION_TIME_KEY = "transmissionTime";
	private static final String REPEAT_KEY = "repeat";
    
    public Broadcast fromDBObject(DBObject dbObject) {
        
        String broadcastOn = (String) dbObject.get("broadcastOn");
        DateTime transmissionTime = TranslatorUtils.toDateTime(dbObject, TRANSMISSION_TIME_KEY);
		
        Integer duration = (Integer) dbObject.get("broadcastDuration");
        Boolean activelyPublished = (dbObject.containsField("activelyPublished") ? (Boolean) dbObject.get("activelyPublished") : Boolean.TRUE);
        String id = (String) dbObject.get("id");
        
        Broadcast broadcast = new Broadcast(broadcastOn, transmissionTime, Duration.standardSeconds(duration), activelyPublished).withId(id);
        
        broadcast.setScheduleDate(TranslatorUtils.toLocalDate(dbObject, "scheduleDate"));
        broadcast.setAliases(TranslatorUtils.toSet(dbObject, DescriptionTranslator.ALIASES));
        broadcast.setLastUpdated(TranslatorUtils.toDateTime(dbObject, DescriptionTranslator.LAST_UPDATED));
        broadcast.setRepeat(TranslatorUtils.toBoolean(dbObject, REPEAT_KEY));
        
        return broadcast;
    }

    public DBObject toDBObject(Broadcast entity) {
    	DBObject dbObject = new BasicDBObject();
        TranslatorUtils.from(dbObject, "broadcastDuration", entity.getBroadcastDuration());
        TranslatorUtils.from(dbObject, "broadcastOn", entity.getBroadcastOn());
        TranslatorUtils.fromLocalDate(dbObject, "scheduleDate", entity.getScheduleDate());
        TranslatorUtils.fromDateTime(dbObject, TRANSMISSION_TIME_KEY, entity.getTransmissionTime());
        TranslatorUtils.fromDateTime(dbObject, TRANSMISSION_END_TIME_KEY, entity.getTransmissionEndTime());
        TranslatorUtils.fromSet(dbObject, entity.getAliases(), DescriptionTranslator.ALIASES);
        TranslatorUtils.fromDateTime(dbObject, DescriptionTranslator.LAST_UPDATED, entity.getLastUpdated());
        TranslatorUtils.from(dbObject, "activelyPublished", entity.isActivelyPublished());
        TranslatorUtils.from(dbObject, "id", entity.getId());
        TranslatorUtils.from(dbObject, REPEAT_KEY, entity.isRepeat());
        return dbObject;
    }

}
