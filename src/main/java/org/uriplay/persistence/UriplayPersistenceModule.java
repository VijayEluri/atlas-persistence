package org.uriplay.persistence;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.uriplay.persistence.content.AggregateContentListener;
import org.uriplay.persistence.content.ContentWriter;
import org.uriplay.persistence.content.EventFiringContentWriter;
import org.uriplay.persistence.content.MongoDbBackedContentBootstrapper;
import org.uriplay.persistence.content.mongo.MongoDbBackedContentStore;
import org.uriplay.persistence.content.mongo.MongoRoughSearch;
import org.uriplay.persistence.tracking.ContentMentionStore;
import org.uriplay.persistence.tracking.MongoDBBackedContentMentionStore;

import com.mongodb.Mongo;

@Configuration
public class UriplayPersistenceModule {

	private @Autowired Mongo mongo;
	
	public @Bean ContentMentionStore contentMentionStore() {
		return new MongoDBBackedContentMentionStore(mongo, "uriplay");
	}
	
	public @Bean ContentWriter persistentWriter() {
		return new EventFiringContentWriter(mongoContentStore(), contentListener());
	}	
	
	public @Bean MongoRoughSearch mongoRoughSearch() {
		return new MongoRoughSearch(mongoContentStore());
	}
	
	@Bean(name={"mongoContentStore", "aliasWriter"}) MongoDbBackedContentStore mongoContentStore() {
		return new MongoDbBackedContentStore(mongo, "uriplay");
	}
	
	@Bean MongoDbBackedContentBootstrapper contentBootstrapper() {
		return new MongoDbBackedContentBootstrapper(contentListener(), mongoContentStore());
	}

	@Bean AggregateContentListener contentListener() {
		return new AggregateContentListener();
	}
}