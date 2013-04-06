package org.atlasapi.persistence.content;

import java.util.Set;

import org.atlasapi.media.common.Id;
import org.atlasapi.media.entity.LookupRef;
import org.atlasapi.persistence.lookup.entry.LookupEntry;
import org.atlasapi.persistence.lookup.entry.LookupEntryStore;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class LookupResolvingContentResolver implements ContentResolver {

    private final KnownTypeContentResolver knownTypeResolver;
    private final LookupEntryStore lookupResolver;

    public LookupResolvingContentResolver(KnownTypeContentResolver knownTypeResolver, LookupEntryStore mongoLookupEntryStore) {
        this.knownTypeResolver = knownTypeResolver;
        this.lookupResolver = mongoLookupEntryStore;
    }
    
    @Override
    public ResolvedContent findByCanonicalUris(Iterable<String> canonicalUris) {
        Set<String> dedupedUris = Sets.newHashSet(canonicalUris);
        Iterable<LookupEntry> lookupEntries = lookupResolver.entriesForCanonicalUris(dedupedUris);
        Iterable<LookupRef> lookupRefs = Iterables.transform(lookupEntries, LookupEntry.TO_SELF);
        ImmutableSet<LookupRef> resolvedLookups = ImmutableSet.copyOf(Iterables.filter(lookupRefs, Predicates.notNull()));
        ResolvedContent resolvedContent = knownTypeResolver.findByLookupRefs(resolvedLookups);
        return resolvedContent;//.copyWithAllRequestedUris(canonicalUris);
    }

    @Override
    public ResolvedContent findByIds(Iterable<Id> ids) {
        Set<Id> dedupedIds = Sets.newHashSet(ids);
        Iterable<LookupEntry> lookupEntries = lookupResolver.entriesForIds(Iterables.transform(dedupedIds,Id.toLongValue()));
        Iterable<LookupRef> lookupRefs = Iterables.transform(lookupEntries, LookupEntry.TO_SELF);
        ImmutableSet<LookupRef> resolvedLookups = ImmutableSet.copyOf(Iterables.filter(lookupRefs, Predicates.notNull()));
        ResolvedContent resolvedContent = knownTypeResolver.findByLookupRefs(resolvedLookups);
        return resolvedContent;
    }
}

