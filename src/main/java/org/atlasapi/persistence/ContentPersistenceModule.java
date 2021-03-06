package org.atlasapi.persistence;

import org.atlasapi.media.product.ProductResolver;
import org.atlasapi.media.product.ProductStore;
import org.atlasapi.media.segment.SegmentResolver;
import org.atlasapi.media.segment.SegmentWriter;
import org.atlasapi.persistence.content.ContentGroupResolver;
import org.atlasapi.persistence.content.ContentGroupWriter;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.content.LookupBackedContentIdGenerator;
import org.atlasapi.persistence.content.PeopleQueryResolver;
import org.atlasapi.persistence.content.people.ItemsPeopleWriter;
import org.atlasapi.persistence.shorturls.ShortUrlSaver;
import org.atlasapi.persistence.topic.TopicQueryResolver;
import org.atlasapi.persistence.topic.TopicStore;

import com.metabroadcast.common.ids.IdGenerator;

public interface ContentPersistenceModule {

    ContentGroupWriter contentGroupWriter();
    
    ContentGroupResolver contentGroupResolver();
    
	ContentWriter contentWriter();

	ContentWriter nonIdSettingContentWriter();

	ContentWriter nonIdNoLockSettingContentWriter();

	ContentWriter mongoContentWriter();
	
	ItemsPeopleWriter itemsPeopleWriter();
	
	ContentResolver contentResolver();
	
	TopicStore topicStore();
	
	TopicQueryResolver topicQueryResolver();

	ShortUrlSaver shortUrlSaver();
	
	SegmentWriter segmentWriter();
	
	SegmentResolver segmentResolver();
	
	ProductStore productStore();
	
	ProductResolver productResolver();
	
	PeopleQueryResolver peopleQueryResolver();

	IdGenerator contentIdGenerator();

	LookupBackedContentIdGenerator lookupBackedContentIdGenerator();
}
