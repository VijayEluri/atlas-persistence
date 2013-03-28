package org.atlasapi.persistence.content;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;

import org.atlasapi.media.common.Id;
import org.atlasapi.media.content.Container;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.lookup.entry.LookupEntry;
import org.atlasapi.persistence.lookup.entry.LookupEntryStore;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.metabroadcast.common.ids.IdGenerator;

@RunWith(JMock.class)
public class LookupStoreBackedIdSettingContentWriterTest {

    private final Mockery context = new Mockery();
    private final LookupEntryStore lookupStore = context.mock(LookupEntryStore.class);
    private final ContentWriter delegate = context.mock(ContentWriter.class);
    private final IdGenerator idGenerator = context.mock(IdGenerator.class);
    
    private final AbstractIdSettingContentWriter writer = new LookupStoreBackedIdSettingContentWriter(lookupStore, idGenerator, delegate);
    
    @Test
    public void testCreatingItemGeneratesNewId() {
        
        final Item item = new Item("itemUri", "itemCurie", Publisher.BBC);
            
        final long newId = 1234l;
        
        context.checking(new Expectations(){{
            oneOf(lookupStore).entriesForCanonicalUris(with(hasItems(item.getCanonicalUri()))); will(returnValue(ImmutableList.of()));
            oneOf(idGenerator).generateRaw();will(returnValue(newId));
            oneOf(delegate).createOrUpdate(with(itemWithId(newId)));
        }});
        
        writer.createOrUpdate(item);
        
        context.assertIsSatisfied();
    }
    
    @Test
    public void testUpdatingItemDoesntGenerateNewId() {
        
        final Item item = new Item("itemUri", "itemCurie", Publisher.BBC);
        final long oldId = 1234;
        item.setId(Id.valueOf(oldId));
        
        context.checking(new Expectations(){{
            oneOf(lookupStore).entriesForCanonicalUris(with(hasItems(item.getCanonicalUri()))); will(returnValue(ImmutableList.of(LookupEntry.lookupEntryFrom(item))));
            never(idGenerator).generateRaw();
            oneOf(delegate).createOrUpdate(with(itemWithId(oldId)));
        }});
        
        item.setId(Id.valueOf(1235l));
        
        writer.createOrUpdate(item);
        
        context.assertIsSatisfied();
        
    }

    private Matcher<Item> itemWithId(final long id) {
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
        
        final Brand brand = new Brand("containerUri", "containerCurie", Publisher.BBC);
            
        final long newId = 1234;
        
        context.checking(new Expectations(){{
            oneOf(lookupStore).entriesForCanonicalUris(with(hasItems(brand.getCanonicalUri()))); will(returnValue(ImmutableList.of()));
            oneOf(idGenerator).generateRaw();will(returnValue(newId));
            oneOf(delegate).createOrUpdate(with(containerWithId(newId)));
        }});
        
        writer.createOrUpdate(brand);
        
        context.assertIsSatisfied();
    }
    
    @Test
    public void testUpdatingContainerDoesntGenerateNewId() {
        
        final Brand brand = new Brand("containerUri", "containerCurie", Publisher.BBC);
        final long oldId = 1234l;
        brand.setId(Id.valueOf(oldId));
        
        context.checking(new Expectations(){{
            oneOf(lookupStore).entriesForCanonicalUris(with(hasItems(brand.getCanonicalUri()))); will(returnValue(ImmutableList.of(LookupEntry.lookupEntryFrom(brand))));
            never(idGenerator).generateRaw();
            oneOf(delegate).createOrUpdate(with(containerWithId(oldId)));
        }});
        
        writer.createOrUpdate(brand);
        
        context.assertIsSatisfied();
        
    }
    
    private Matcher<Container> containerWithId(final long id) {
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