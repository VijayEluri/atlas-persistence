package org.atlasapi.persistence.lookup;

import java.util.Map;

import org.atlasapi.persistence.lookup.entry.LookupEntry;
import org.atlasapi.persistence.lookup.entry.LookupEntryStore;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

public class InMemoryLookupEntryStore implements LookupEntryStore {

    private Map<String,LookupEntry> store;

    public InMemoryLookupEntryStore() {
        store = Maps.newConcurrentMap();
    }
    
    @Override
    public void store(LookupEntry entry) {
        store.putAll(Maps.uniqueIndex(entry.entriesForIdentifiers(), new Function<LookupEntry, String>() {
            @Override
            public String apply(LookupEntry input) {
                return input.id();
            }
        }));
    }
    
    @Override
    public LookupEntry entryFor(String identifier) {
        return store.get(identifier);
    }


}
