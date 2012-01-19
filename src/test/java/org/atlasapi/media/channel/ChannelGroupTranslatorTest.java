package org.atlasapi.media.channel;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import org.atlasapi.media.entity.Publisher;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.metabroadcast.common.intl.Countries;
import com.mongodb.DBObject;

public class ChannelGroupTranslatorTest {

    private final ChannelGroupTranslator channelGroupTranslator = new ChannelGroupTranslator();
    
    @Test
    public void testEncodesAndDecodedChannelGroup() {
        
        ChannelGroup channelGroup = new ChannelGroup();
        channelGroup.setCountries(ImmutableSet.of(Countries.US,Countries.GB));
        channelGroup.setTitle("Title");
        channelGroup.setPublisher(Publisher.BBC);
        channelGroup.setChannels(ImmutableList.of(1234L, 1235L, 1236L));
        
        DBObject encoded = channelGroupTranslator.toDBObject(null, channelGroup);
        
        ChannelGroup decoded = channelGroupTranslator.fromDBObject(encoded, null);
        
        assertThat(decoded.getCountries(), is(equalTo(channelGroup.getCountries())));
        assertThat(decoded.getPublisher(), is(equalTo(channelGroup.getPublisher())));
        assertThat(decoded.getTitle(), is(equalTo(channelGroup.getTitle())));
        assertThat(decoded.getChannels(), is(equalTo(channelGroup.getChannels())));
        
    }

}