package org.atlasapi.persistence.media.entity;

import org.atlasapi.media.entity.Broadcast;

import com.metabroadcast.common.persistence.translator.TranslatorUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.joda.time.DateTime;
import org.joda.time.Duration;

public class BroadcastTranslator  {
	
    private static final String NEW_SERIES_KEY = "newSeries";
    private static final String NEW_EPISODE_KEY = "newEpisode";
    private static final String PREMIER_KEY = "premier";
    private static final String TRANSMISSION_END_TIME_KEY = "transmissionEndTime";
	private static final String TRANSMISSION_TIME_KEY = "transmissionTime";
    private static final String ACTUAL_TRANSMISSION_TIME_KEY = "actualTransmissionTime";
    private static final String ACTUAL_TRANSMISSION_END_TIME_KEY = "actualTransmissionEndTime";
    private static final String REPEAT_KEY = "repeat";
	private static final String SUBTITLED_KEY = "subtitled";
	private static final String SIGNED_KEY = "signed";
	private static final String AUDIO_DESCRIBED_KEY = "audioDescribed";
	private static final String HD_KEY = "highDefinition";
    private static final String WIDESCREEN_KEY = "widescreen";
	private static final String SURROUND_KEY = "surround";
	private static final String LIVE_KEY = "live";
	private static final String BLACKOUT_RESTRICTION_KEY = "blackoutRestriction";
	private static final String REVISED_REPEAT = "revisedRepeat";
    private static final String CONTINUATION = "continuation";
    private static final String NEW_ONE_OFF = "newOneOff";
	
	private final AliasTranslator aliasTranslator = new AliasTranslator();
	private final BlackoutRestrictionTranslator blackoutTranslator = new BlackoutRestrictionTranslator();
    
    public Broadcast fromDBObject(DBObject dbObject) {
        
        String broadcastOn = (String) dbObject.get("broadcastOn");
        DateTime transmissionTime = TranslatorUtils.toDateTime(dbObject, TRANSMISSION_TIME_KEY);
		
        Integer duration = (Integer) dbObject.get("broadcastDuration");
        Boolean activelyPublished = (dbObject.containsField("activelyPublished") ? (Boolean) dbObject.get("activelyPublished") : Boolean.TRUE);
        String id = (String) dbObject.get("id");
        
        Broadcast broadcast = new Broadcast(broadcastOn, transmissionTime, Duration.standardSeconds(duration), activelyPublished).withId(id);
        
        broadcast.setActualTransmissionTime(TranslatorUtils.toDateTime(dbObject, ACTUAL_TRANSMISSION_TIME_KEY));
        broadcast.setActualTransmissionEndTime(TranslatorUtils.toDateTime(dbObject, ACTUAL_TRANSMISSION_END_TIME_KEY));
        broadcast.setScheduleDate(TranslatorUtils.toLocalDate(dbObject, "scheduleDate"));
        broadcast.setAliasUrls(TranslatorUtils.toSet(dbObject, IdentifiedTranslator.ALIASES));
        broadcast.setAliases(aliasTranslator.fromDBObjects(TranslatorUtils.toDBObjectList(dbObject, IdentifiedTranslator.IDS)));
        broadcast.setLastUpdated(TranslatorUtils.toDateTime(dbObject, IdentifiedTranslator.LAST_UPDATED));
        broadcast.setRepeat(TranslatorUtils.toBoolean(dbObject, REPEAT_KEY));
        broadcast.setSubtitled(TranslatorUtils.toBoolean(dbObject, SUBTITLED_KEY));
        broadcast.setSigned(TranslatorUtils.toBoolean(dbObject, SIGNED_KEY));
        broadcast.setAudioDescribed(TranslatorUtils.toBoolean(dbObject, AUDIO_DESCRIBED_KEY));
        broadcast.setHighDefinition(TranslatorUtils.toBoolean(dbObject, HD_KEY));
        broadcast.setWidescreen(TranslatorUtils.toBoolean(dbObject, WIDESCREEN_KEY));
        broadcast.setSurround(TranslatorUtils.toBoolean(dbObject, SURROUND_KEY));
        broadcast.setLive(TranslatorUtils.toBoolean(dbObject, LIVE_KEY));
        broadcast.setPremiere(TranslatorUtils.toBoolean(dbObject, PREMIER_KEY));
        broadcast.setNewSeries(TranslatorUtils.toBoolean(dbObject, NEW_SERIES_KEY));
        broadcast.setNewEpisode(TranslatorUtils.toBoolean(dbObject, NEW_EPISODE_KEY));
        broadcast.setBlackoutRestriction(blackoutTranslator.fromDbObject((DBObject) dbObject.get(BLACKOUT_RESTRICTION_KEY)));
        broadcast.setRevisedRepeat(TranslatorUtils.toBoolean(dbObject, REVISED_REPEAT));
        broadcast.setContinuation(TranslatorUtils.toBoolean(dbObject, CONTINUATION));
        broadcast.setNewOneOff(TranslatorUtils.toBoolean(dbObject, NEW_ONE_OFF));
        
        return broadcast;
    }

    public DBObject toDBObject(Broadcast entity) {
    	DBObject dbObject = new BasicDBObject();
        TranslatorUtils.from(dbObject, "broadcastDuration", entity.getBroadcastDuration());
        TranslatorUtils.from(dbObject, "broadcastOn", entity.getBroadcastOn());
        TranslatorUtils.fromLocalDate(dbObject, "scheduleDate", entity.getScheduleDate());
        TranslatorUtils.fromDateTime(dbObject, TRANSMISSION_TIME_KEY, entity.getTransmissionTime());
        TranslatorUtils.fromDateTime(dbObject, TRANSMISSION_END_TIME_KEY, entity.getTransmissionEndTime());
        TranslatorUtils.fromDateTime(dbObject, ACTUAL_TRANSMISSION_TIME_KEY, entity.getActualTransmissionTime());
        TranslatorUtils.fromDateTime(dbObject, ACTUAL_TRANSMISSION_END_TIME_KEY, entity.getActualTransmissionEndTime());
        TranslatorUtils.fromSet(dbObject, entity.getAliasUrls(), IdentifiedTranslator.ALIASES);
        TranslatorUtils.from(dbObject, IdentifiedTranslator.IDS, aliasTranslator.toDBList(entity.getAliases()));
        TranslatorUtils.fromDateTime(dbObject, IdentifiedTranslator.LAST_UPDATED, entity.getLastUpdated());
        TranslatorUtils.from(dbObject, "activelyPublished", entity.isActivelyPublished());
        TranslatorUtils.from(dbObject, "id", entity.getSourceId());
        TranslatorUtils.from(dbObject, REPEAT_KEY, entity.getRepeat());
        TranslatorUtils.from(dbObject, SUBTITLED_KEY, entity.getSubtitled());
        TranslatorUtils.from(dbObject, SIGNED_KEY, entity.getSigned());
        TranslatorUtils.from(dbObject, AUDIO_DESCRIBED_KEY, entity.getAudioDescribed());
        TranslatorUtils.from(dbObject, HD_KEY, entity.getHighDefinition());
        TranslatorUtils.from(dbObject, WIDESCREEN_KEY, entity.getWidescreen());
        TranslatorUtils.from(dbObject, SURROUND_KEY, entity.getSurround());
        TranslatorUtils.from(dbObject, LIVE_KEY, entity.getLive());
        TranslatorUtils.from(dbObject, PREMIER_KEY, entity.getPremiere());
        TranslatorUtils.from(dbObject, NEW_SERIES_KEY, entity.getNewSeries());
        TranslatorUtils.from(dbObject, NEW_EPISODE_KEY, entity.getNewEpisode());
        TranslatorUtils.from(dbObject, BLACKOUT_RESTRICTION_KEY, blackoutTranslator.toDbObject(entity.getBlackoutRestriction()));
        TranslatorUtils.from(dbObject, REVISED_REPEAT, entity.getRevisedRepeat());
        TranslatorUtils.from(dbObject, CONTINUATION, entity.getContinuation());
        TranslatorUtils.from(dbObject, NEW_ONE_OFF, entity.getNewOneOff());
        
        return dbObject;
    }

}
