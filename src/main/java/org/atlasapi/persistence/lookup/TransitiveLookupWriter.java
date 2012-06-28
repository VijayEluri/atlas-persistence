package org.atlasapi.persistence.lookup;

import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;
import static org.atlasapi.media.entity.Identified.TO_URI;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.Described;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.lookup.entry.LookupEntry;
import org.atlasapi.persistence.lookup.entry.LookupEntryStore;
import org.atlasapi.persistence.lookup.entry.LookupRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class TransitiveLookupWriter implements LookupWriter {

    private static final Logger log = LoggerFactory.getLogger(TransitiveLookupWriter.class);
    private static final int maxSetSize = 150; 
    private final LookupEntryStore entryStore;

    public TransitiveLookupWriter(LookupEntryStore entryStore) {
        this.entryStore = entryStore;
    }

    @Override
    public <T extends Content> void writeLookup(final T subject, Iterable<T> directEquivalents, final Set<Publisher> publishers) {
        Preconditions.checkArgument(Strings.emptyToNull(subject.getCanonicalUri()) != null, "Invalid subject URI");
        
        Set<Described> allItems = filterContentPublishers(ImmutableSet.<Described>builder().add(subject).addAll(directEquivalents).build(), publishers);
        
        //canonical URIs of subject and directEquivalents
        final Set<String> canonUris = ImmutableSet.copyOf(Iterables.transform(allItems, TO_URI));

        //entry for the subject.
        LookupEntry subjectEntry = Iterables.getOnlyElement(entryStore.entriesForUris(ImmutableList.of(subject.getCanonicalUri())), null);
        
        if (subjectEntry != null) {
            ImmutableSet<String> currentEquivalents = ImmutableSet.copyOf(Iterables.transform(subjectEntry.directEquivalents(),LookupRef.TO_ID));
            if(currentEquivalents.equals(canonUris)) {
                log.debug("Equivalence no change: {}", subject.getCanonicalUri());
                return;
            }
            log.debug("Equivalence change: {} -> {}", currentEquivalents, canonUris);
        }
        
        subjectEntry = subjectEntry != null ? subjectEntry : LookupEntry.lookupEntryFrom(subject);
        Set<LookupEntry> equivEntries = entriesFor(directEquivalents);

        //Pull the current transitive closures for the directly equivalent parameters.
        Map<LookupRef, LookupEntry> lookups = transitiveClosure(ImmutableSet.copyOf(Iterables.concat(ImmutableSet.of(subjectEntry), equivEntries)));
        
        if (lookups.size() > maxSetSize) {
            log.info("Transitive set too large: {} {}", subject, lookups.size());
            return;
        }
        
        //Update the direct equivalents for all the lookups.
        lookups = Maps.newHashMap(Maps.transformValues(lookups, new Function<LookupEntry, LookupEntry>() {
            @Override
            public LookupEntry apply(LookupEntry entry) {
                //Only modify direct equivalents of entries in the transitive closure of publishers that are argued
                if (!publishers.contains(entry.lookupRef().publisher())) {
                    return entry;
                }
                if (canonUris.contains(entry.uri())) {
                    return entry.copyWithDirectEquivalents(Sets.union(entry.directEquivalents(), ImmutableSet.of(LookupRef.from(subject))));
                } else {
                    return entry.copyWithDirectEquivalents(Sets.difference(entry.directEquivalents(), ImmutableSet.of(LookupRef.from(subject))));
                }
            }
        }));
        
        /* Update the subject content entry. Included:
         *  refs of publishers not argued.
         *  refs for all content argued as equivalent.
         */
        lookups.put(subjectEntry.lookupRef(), subjectEntry.copyWithDirectEquivalents(Iterables.concat(
                retainRefsNotInPublishers(subjectEntry.directEquivalents(), publishers), 
                Iterables.transform(allItems, LookupRef.FROM_DESCRIBED)
        )));
        
        //For each lookup, recompute its transitive closure. 
        Set<LookupEntry> newLookups = recomputeTransitiveClosures(lookups.values());

        //Write to store
        for (LookupEntry entry : newLookups) {
            entryStore.store(entry);
        }

    }

    private Iterable<LookupRef> retainRefsNotInPublishers(Set<LookupRef> directEquivalents, final Set<Publisher> publishers) {
        return Iterables.filter(directEquivalents, new Predicate<LookupRef>() {
            @Override
            public boolean apply(LookupRef input) {
                return !publishers.contains(input.publisher());
            }
        });
    }
    
    private Set<Described> filterContentPublishers(Set<Described> content, final Set<Publisher> publishers) {
        return Sets.filter(content, new Predicate<Described>() {
            @Override
            public boolean apply(Described input) {
                return publishers.contains(input.getPublisher());
            }
        });
    }

    private Set<LookupEntry> recomputeTransitiveClosures(Iterable<LookupEntry> lookups) {
        
        Map<String,LookupEntry> lookupMap = Maps.uniqueIndex(lookups, LookupEntry.TO_ID);
        
        Set<LookupEntry> newLookups = Sets.newHashSet();
        for (LookupEntry entry : lookups) {
            
            Set<LookupRef> transitiveSet = Sets.newHashSet();
            
            Set<LookupRef> seen = Sets.newHashSet();
            Queue<LookupRef> direct = new LinkedList<LookupRef>(entry.directEquivalents());
            //Traverse equivalence graph breadth-first
            while(!direct.isEmpty()) {
                LookupRef current = direct.poll();
                if(seen.contains(current)) {
                    continue;
                } else {
                    seen.add(current);
                }
                transitiveSet.add(current);
                direct.addAll(lookupMap.get(current.id()).directEquivalents());
            }
            
            newLookups.add(entry.copyWithEquivalents(transitiveSet));
        }
        return newLookups;
    }
    
    private Set<LookupEntry> entriesFor(Iterable<? extends Content> equivalents) {
        return ImmutableSet.copyOf(Iterables.transform(equivalents, new Function<Content, LookupEntry>() {
            @Override
            public LookupEntry apply(Content input) {
                return getOrCreate(input);
            }
        }));
    }

    private LookupEntry getOrCreate(Content subject) {
        LookupEntry subjectEntry = Iterables.getOnlyElement(entryStore.entriesForUris(ImmutableList.of(subject.getCanonicalUri())), null);
        return subjectEntry != null ? subjectEntry : LookupEntry.lookupEntryFrom(subject);
    }

    // Uses a work queue to pull out and map the transitive closures rooted at each entry in entries.
    private Map<LookupRef, LookupEntry> transitiveClosure(Set<LookupEntry> entries) {

        HashMap<LookupRef, LookupEntry> transitiveClosure = Maps.newHashMap();
        
        for (LookupEntry entry : entries) {
            transitiveClosure.put(entry.lookupRef(), entry);
            for (LookupEntry equivEntry : entriesForRefs(filter(entry.equivalents(), not(in(transitiveClosure.keySet()))))) {
                transitiveClosure.put(equivEntry.lookupRef(), equivEntry);
            }
        }
        
        return transitiveClosure;
        
    }

    private Set<LookupEntry> entriesForRefs(Iterable<LookupRef> refs) {
        return ImmutableSet.copyOf(entryStore.entriesForUris(Iterables.transform(refs, LookupRef.TO_ID)));
    }
}
