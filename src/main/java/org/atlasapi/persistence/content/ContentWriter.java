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

package org.atlasapi.persistence.content;

import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.ContentGroup;
import org.atlasapi.media.entity.Item;

/**
 * Simple interface to the store of available content.
 *
 * @author Robert Chatley (robert@metabroadcast.com)
 * @author John Ayres (john@metabroadcast.com)
 */
public interface ContentWriter {

	void createOrUpdate(Item item);
	
	void createOrUpdate(Container<?> container, boolean markMissingItemsAsUnavailable);

	/**
	 * Saves the ContentGroup but does not attempt to persist the sub-content
	 * within the ContentGroup.  Will throw an {@link IllegalArgumentException} if the
	 * group contains a sub-element that is not already in the database
	 */
	void createOrUpdateSkeleton(ContentGroup playlist);

}
