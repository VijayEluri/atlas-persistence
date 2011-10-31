package org.atlasapi.persistence.content;

import static org.hamcrest.Matchers.hasItem;

import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.lookup.entry.LookupEntry;
import org.atlasapi.persistence.lookup.entry.LookupEntryStore;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;
import org.junit.internal.matchers.TypeSafeMatcher;

import com.google.common.collect.ImmutableList;
import com.metabroadcast.common.ids.IdGenerator;

public class IdSettingContentWriterTest {

    private final Mockery context = new Mockery();
    private final LookupEntryStore lookupStore = context.mock(LookupEntryStore.class);
    private final ContentWriter delegate = context.mock(ContentWriter.class);
    private final IdGenerator idGenerator = context.mock(IdGenerator.class);
    
    private final IdSettingContentWriter writer = new IdSettingContentWriter(lookupStore, idGenerator, delegate);
    
    @Test
    public void testCreatingItemGeneratesNewId() {
        
        final Item item = new Item("itemUri", "itemCurie", Publisher.BBC);
            
        final String newId = "newId";
        
        context.checking(new Expectations(){{
            oneOf(lookupStore).entriesForUris(with(hasItem(item.getCanonicalUri()))); will(returnValue(ImmutableList.of()));
            oneOf(idGenerator).generate();will(returnValue(newId));
            oneOf(delegate).createOrUpdate(with(itemWithId(newId)));
        }});
        
        writer.createOrUpdate(item);
        
        context.assertIsSatisfied();
    }
    
    @Test
    public void testUpdatingItemDoesntGenerateNewId() {
        
        final Item item = new Item("itemUri", "itemCurie", Publisher.BBC);
        final String oldId = "oldId";
        item.setId(oldId);
        
        context.checking(new Expectations(){{
            oneOf(lookupStore).entriesForUris(with(hasItem(item.getCanonicalUri()))); will(returnValue(ImmutableList.of(LookupEntry.lookupEntryFrom(item))));
            never(idGenerator).generate();
            oneOf(delegate).createOrUpdate(with(itemWithId(oldId)));
        }});
        
        item.setId("anotherId");
        
        writer.createOrUpdate(item);
        
        context.assertIsSatisfied();
        
    }

    private Matcher<Item> itemWithId(final String id) {
        return new TypeSafeMatcher<Item>() {

            @Override
            public void describeTo(Description desc) {
                desc.appendValue("Item with id" + id);
            }

            @Override
            public boolean matchesSafely(Item item) {
                return item.getId().equals(id);
            }
        };
    }

    @Test
    public void testCreatingContainerGeneratesNewId() {
        
        final Container container = new Container("containerUri", "containerCurie", Publisher.BBC);
            
        final String newId = "newId";
        
        context.checking(new Expectations(){{
            oneOf(lookupStore).entriesForUris(with(hasItem(container.getCanonicalUri()))); will(returnValue(ImmutableList.of()));
            oneOf(idGenerator).generate();will(returnValue(newId));
            oneOf(delegate).createOrUpdate(with(containerWithId(newId)));
        }});
        
        writer.createOrUpdate(container);
        
        context.assertIsSatisfied();
    }
    
    @Test
    public void testUpdatingContainerDoesntGenerateNewId() {
        
        final Container container = new Container("containerUri", "containerCurie", Publisher.BBC);
        final String oldId = "oldId";
        container.setId(oldId);
        
        context.checking(new Expectations(){{
            oneOf(lookupStore).entriesForUris(with(hasItem(container.getCanonicalUri()))); will(returnValue(ImmutableList.of(LookupEntry.lookupEntryFrom(container))));
            never(idGenerator).generate();
            oneOf(delegate).createOrUpdate(with(containerWithId(oldId)));
        }});
        
        writer.createOrUpdate(container);
        
        context.assertIsSatisfied();
        
    }
    
    private Matcher<Container> containerWithId(final String id) {
        return new TypeSafeMatcher<Container>() {

            @Override
            public void describeTo(Description desc) {
                desc.appendValue("Container with id" + id);
            }

            @Override
            public boolean matchesSafely(Container item) {
                return item.getId().equals(id);
            }
        };
    }
}
