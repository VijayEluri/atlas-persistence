/* Copyright 2009 Meta Broadcast Ltd

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You may
obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License. */

package org.atlasapi.persistence.content.mongo;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.Item;
import org.atlasapi.persistence.content.ContentListener;
import org.atlasapi.persistence.content.RetrospectiveContentLister;
import org.springframework.beans.factory.InitializingBean;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

public class MongoDbBackedContentBootstrapper implements InitializingBean {
	
    private static final Log log = LogFactory.getLog(MongoDbBackedContentBootstrapper.class);
    private static final int BATCH_SIZE = 500;

    private final ContentListener contentListener;
    private final RetrospectiveContentLister contentStore;
    private int batchSize = BATCH_SIZE;

    public MongoDbBackedContentBootstrapper(ContentListener contentListener, RetrospectiveContentLister contentLister) {
        this.contentListener = contentListener;
        this.contentStore = contentLister;
    }

    @Override
    public void afterPropertiesSet() {
        new Thread() {
            public void run() {
                loadAll();
            };
        }.start();
    }

    void loadAll() {
        if (log.isInfoEnabled()) {
            log.info("Bootstrapping Brands");
        }
        loadAllBrands();

        if (log.isInfoEnabled()) {
            log.info("Bootstrapping Brands");
        }
        loadAllItems();
    }

    public void loadAllItems() {
    	load(contentStore.listAllRoots(), new Function<List<Content>, Void>() {

    		@Override
			@SuppressWarnings("unchecked")
			public Void apply(List<Content> batch) {
				contentListener.itemChanged(Iterables.filter(batch, Item.class), ContentListener.ChangeType.BOOTSTRAP);
				contentListener.brandChanged((Iterable<? extends Container<?>>) Iterables.filter(batch, Container.class), ContentListener.ChangeType.BOOTSTRAP);
				return null;
			}
    	});
    }

	private <T extends Content> void load(Iterator<T> content, final Function<List<T>, Void> handler) {
    	List<T> contentInBatch = Lists.newArrayList();
    	
    	while (content.hasNext()) {
    		contentInBatch.add(content.next());
    		
    		if (contentInBatch.size() >= batchSize) {
    			inform(handler, contentInBatch);
    			contentInBatch.clear();
    		}
    	}
    	if (!contentInBatch.isEmpty()) {
			inform(handler, contentInBatch);
    	}
	}

	private <T> void inform(final Function<List<T>, Void> handler, List<T> contentInBatch) {
		final ImmutableList<T> batch = ImmutableList.copyOf(contentInBatch);
		handler.apply(batch);
	}

    public void loadAllBrands() {
    	load(Iterators.filter(contentStore.listAllRoots(), Brand.class), new Function<List<Brand>, Void>() {
			@Override
			public Void apply(List<Brand> batch) {
				contentListener.brandChanged(batch, ContentListener.ChangeType.BOOTSTRAP);
				return null;
			}
    	});
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
