package org.atlasapi.persistence.media.entity;

import junit.framework.TestCase;

import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.media.entity.Encoding;
import org.atlasapi.media.entity.Version;
import org.joda.time.Duration;

import com.metabroadcast.common.time.Clock;
import com.metabroadcast.common.time.SystemClock;
import com.mongodb.DBObject;

public class VersionTranslatorTest extends TestCase {
	
	private final Clock clock = new SystemClock();
	
    VersionTranslator vt = new VersionTranslator();
    
    public void testFromVersion() throws Exception {
        Version version = new Version();
        version.setDuration(Duration.standardSeconds(2));
        version.setRating("rating");
        
        DBObject dbObject = vt.toDBObject(null, version);
        
        assertEquals(version.getDuration(), dbObject.get("duration"));
        assertEquals(version.getRating(), dbObject.get("rating"));
    }
    
    public void testToVersion() throws Exception {
        Version version = new Version();
        version.setDuration(Duration.standardSeconds(2));
        version.setRating("rating");
        version.setPublishedDuration(1);
        version.setRatingText("text");
        
        Broadcast broadcast = new Broadcast("channel", clock.now(), Duration.standardSeconds(1));
        broadcast.setCanonicalUri("uri");
        version.addBroadcast(broadcast);
        
        Encoding encoding = new Encoding();
        encoding.setAudioChannels(1);
        encoding.setBitRate(2);
        version.addManifestedAs(encoding);
        
        DBObject dbObject = vt.toDBObject(null, version);
        
        Version v = vt.fromDBObject(dbObject, null);
        assertEquals(version.getDuration(), v.getDuration());
        assertEquals(version.getRating(), v.getRating());
        assertEquals(version.getRatingText(), v.getRatingText());
        assertEquals(version.getPublishedDuration(), v.getPublishedDuration());
        
        Broadcast b = v.getBroadcasts().iterator().next();
        assertEquals(broadcast.getBroadcastDuration(), b.getBroadcastDuration());
        
        Encoding e = v.getManifestedAs().iterator().next();
        assertEquals(encoding.getAudioChannels(), e.getAudioChannels());
        assertEquals(encoding.getBitRate(), e.getBitRate());
    }
}