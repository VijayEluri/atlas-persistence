package org.atlasapi.persistence.lookup;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.metabroadcast.common.base.MorePredicates;
import com.metabroadcast.common.collect.MoreSets;
import com.metabroadcast.common.stream.MoreCollectors;
import org.atlasapi.equiv.ContentRef;
import org.atlasapi.media.entity.LookupRef;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.lookup.entry.LookupEntry;
import org.atlasapi.persistence.lookup.entry.LookupEntryStore;
import org.atlasapi.util.GroupLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.collect.Iterables.transform;

public class TransitiveLookupWriter implements LookupWriter {
    
    private static final GroupLock<String> lock = GroupLock.<String>natural();
    
    private static final Logger log = LoggerFactory.getLogger(TransitiveLookupWriter.class);
    private static final Logger timerLog = LoggerFactory.getLogger("TIMER");
    private static final int maxSetSize = 150;
    
    private final LookupEntryStore entryStore;
    private final boolean explicit;
    
    public static TransitiveLookupWriter explicitTransitiveLookupWriter(LookupEntryStore entryStore) {
        return new TransitiveLookupWriter(entryStore, true);
    }
    
    public static TransitiveLookupWriter generatedTransitiveLookupWriter(LookupEntryStore entryStore) {
        return new TransitiveLookupWriter(entryStore, false);
    }

    private TransitiveLookupWriter(LookupEntryStore entryStore, boolean explicit) {
        this.entryStore = entryStore;
        this.explicit = explicit;
    }
    
    private static final Function<ContentRef, String> TO_URI = new Function<ContentRef, String>() {
        @Override
        public String apply(@Nullable ContentRef input) {
            return input.getCanonicalUri();
        }
    };

    @Override
    public Optional<Set<LookupEntry>> writeLookup(ContentRef subject, Iterable<ContentRef> equivalents, Set<Publisher> sources) {

        long startTime = System.nanoTime();
        timerLog.debug("TIMER L TW 1 entered. {}", Thread.currentThread().getName());

        Iterable<String> neighbourUris = Iterables.transform(filterContentsources(equivalents, sources), TO_URI);
        Optional<Set<LookupEntry>> setOptional = writeLookup(
                subject.getCanonicalUri(),
                ImmutableSet.copyOf(neighbourUris),
                sources
        );
        timerLog.debug("TIMER L TW 1 wrote lookup {}ms. {}", Long.toString((System.nanoTime() - startTime)/1000000), Thread.currentThread().getName());
        if((System.nanoTime() - startTime)/1000000 > 1000){
            timerLog.debug("TIMER L TW SLOW");
        }
        return setOptional;
    }
    
    private Iterable<ContentRef> filterContentsources(Iterable<ContentRef> content, final Set<Publisher> sources) {
        return Iterables.filter(content, new Predicate<ContentRef>() {
            @Override
            public boolean apply(ContentRef input) {
                return sources.contains(input.getPublisher());
            }
        });
    }

    public Optional<Set<LookupEntry>> writeLookup(final String subjectUri, Iterable<String> equivalentUris, final Set<Publisher> sources) {
        LookupEntry subjectEntry = entryFor(subjectUri);
        return writeLookup(subjectUri, subjectEntry, equivalentUris, sources);
    }

    public Optional<Set<LookupEntry>> writeLookup(
            final String subjectUri,
            LookupEntry subjectEntry,
            Iterable<String> equivalentUris,
            final Set<Publisher> sources
    ) {

        long startTime = System.nanoTime();
        long lastTime = System.nanoTime();
        timerLog.debug("TIMER L TW 2 doing write lookup. {}", Thread.currentThread().getName());
        Preconditions.checkNotNull(emptyToNull(subjectUri), "null subject");
        
        ImmutableSet<String> newNeighboursUris = ImmutableSet.copyOf(equivalentUris);
        Set<String> subjectAndNeighbours = MoreSets.add(newNeighboursUris, subjectUri);
        Set<String> transitiveSetsUris = null;

        Set<String> existingSubjectDirectUris = subjectEntry.directEquivalents().stream()
                .map(LookupRef::uri)
                .collect(MoreCollectors.toImmutableSet());
        Set<String> directUriIntersection = Sets.intersection(subjectAndNeighbours, existingSubjectDirectUris);
        boolean strictSubset = !directUriIntersection.equals(existingSubjectDirectUris);
        //If we break some existing direct equivalences, update these first so we can
        //reduce the size of the transitive equiv set
        if(strictSubset
                && !directUriIntersection.equals(subjectAndNeighbours) //if equal we only need to update once
                ) {
            writeLookup(subjectUri, subjectEntry, directUriIntersection, sources);
            strictSubset = false; //for the entire set of neighbours
        }
        //Carry on with the entire set of neighbours

        try {
            synchronized (lock) {
                timerLog.debug("TIMER L TW 2 acquired the lock object. {}ms. {}", Long.toString((System.nanoTime() - lastTime)/1000000), Thread.currentThread().getName());
                lastTime = System.nanoTime();
                int loop = 0;
                while((transitiveSetsUris = tryLockAllIds(subjectAndNeighbours, strictSubset)) == null) {
                    timerLog.debug("TIMER L TW 2 failed to lock ids (loop "+loop++ +"). {}ms. {}", Long.toString((System.nanoTime() - lastTime)/1000000), Thread.currentThread().getName());
                    lastTime = System.nanoTime();
                    lock.unlock(subjectAndNeighbours);
                    timerLog.debug("TIMER L TW 2 unlocked "+subjectAndNeighbours.size()+" (loop "+loop++ +"). {}ms. {}",Long.toString((System.nanoTime() - lastTime)/1000000), Thread.currentThread().getName());
                    lastTime = System.nanoTime();
                    lock.wait();
                    timerLog.debug("TIMER L TW 2 wait finished (loop "+loop++ +"). {}ms. {}",Long.toString((System.nanoTime() - lastTime)/1000000), Thread.currentThread().getName());
                    lastTime = System.nanoTime();
                }
                timerLog.debug("TIMER L TW 2 all ids locked (loop "+loop +"). {}ms. {}",Long.toString((System.nanoTime() - lastTime)/1000000), Thread.currentThread().getName());
                lastTime = System.nanoTime();
            }
            
            return updateEntries(subjectUri, newNeighboursUris, transitiveSetsUris, sources);
            
        } catch(OversizeTransitiveSetException otse) {
            log.info(String.format("Oversize set for %s : %s",
                    subjectUri, otse.getMessage()));
            return Optional.absent();
        } catch(InterruptedException e) {
            log.error(String.format("%s: %s", subjectUri, newNeighboursUris), e);
            return Optional.absent();
        } finally {
            timerLog.debug("TIMER L TW Finally block reached. {}ms. {}", Long.toString((System.nanoTime() - lastTime)/1000000), Thread.currentThread().getName());
            lastTime = System.nanoTime();
            synchronized (lock) {
                timerLog.debug("TIMER L TW Finally acquired the lock object.{}ms. {}", Long.toString((System.nanoTime() - lastTime)/1000000), Thread.currentThread().getName());
                lastTime = System.nanoTime();
                lock.unlock(subjectAndNeighbours);
                timerLog.debug("TIMER L TW Finally unlocked subjet and neighbours ("+subjectAndNeighbours.size()+").{}ms. {}",Long.toString((System.nanoTime() - lastTime)/1000000), Thread.currentThread().getName());
                lastTime = System.nanoTime();
                if (transitiveSetsUris != null) {
                    lock.unlock(transitiveSetsUris);
                    timerLog.debug("TIMER L TW Finally unlocked transitive uris ("+transitiveSetsUris.size()+").{}ms. {}", Long.toString((System.nanoTime() - lastTime)/1000000), Thread.currentThread().getName());
                }
                lock.notifyAll();
            }
        }
    }

    private Optional<Set<LookupEntry>> updateEntries(String subjectUri, ImmutableSet<String> newNeighboursUris,
            Set<String> transitiveSetsUris, Set<Publisher> sources) {
        long startTime = System.nanoTime();
        long lastTime = System.nanoTime();
        timerLog.debug("TIMER L TW 4 updating all entries {}", Thread.currentThread().getName());
        LookupEntry subject = entryFor(subjectUri);

        timerLog.debug("TIMER L TW 4 got lookup for main entry. {}ms. {}", Long.toString((System.nanoTime() - lastTime)/1000000), Thread.currentThread().getName());
        lastTime = System.nanoTime();

        checkNotNull(subject, "No entry for %s", subjectUri);
        if(noChangeInNeighbours(subject, newNeighboursUris, sources)) {
            log.debug("{}: no change in neighbours: {}", subjectUri, newNeighboursUris);
            return Optional.absent();
        }
        
        // entries for all members in all transitive sets involved
        Map<String, LookupEntry> entryIndex = resolveTransitiveSets(transitiveSetsUris);

        timerLog.debug("TIMER L TW 4 Resolved transitive sets ("+entryIndex.size()+"). {}ms. {}",Long.toString((System.nanoTime() - lastTime)/1000000), Thread.currentThread().getName());
        lastTime = System.nanoTime();
        Set<LookupEntry> newNeighbours = newSubjectNeighbours(newNeighboursUris, entryIndex);
        
        for (LookupEntry entry : entryIndex.values()) {
            entryIndex.put(entry.uri(), 
                updateEntryNeighbours(entry, subject, newNeighbours, sources)
            );
        }
   
        Set<LookupEntry> newLookups = recomputeTransitiveClosures(entryIndex);
        for (LookupEntry entry : newLookups) {
            entryStore.store(entry);
        }

        timerLog.debug("TIMER L TW 4 Saved entries to db. {}ms. {}", Long.toString((System.nanoTime() - lastTime)/1000000),Thread.currentThread().getName());
        
        return Optional.of(newLookups);
    }

    private ImmutableSet<LookupEntry> newSubjectNeighbours(Set<String> neighboursUris,
            Map<String, LookupEntry> entryIndex) {
        return ImmutableSet.copyOf(Iterables.transform(neighboursUris, Functions.forMap(entryIndex)));
    }

    private Map<String, LookupEntry> resolveTransitiveSets(Set<String> transitiveSetUris) {
        return Maps.newHashMap(Maps.uniqueIndex(entriesFor(transitiveSetUris), LookupEntry.TO_ID));
    }

    /*
     * Attempts to lock the URIs of the directly affected entries before
     * resolving the entries and then attempting to lock the full equivalence
     * sets.
     * 
     * The initial lock need to be attempted since between resolving the entries
     * and locking the full equivalence sets another thread could potentially
     * have changed those entries.
     * 
     * A return value of null means either of the lock attempts failed an
     * locking needs to re-attempted. Non-null return is a set containing all
     * URIs in all transitive sets relevant to this update.
     */
    private Set<String> tryLockAllIds(Set<String> neighboursUris, boolean strictSubset) throws InterruptedException {
        long startTime = System.nanoTime();
        long lastTime = System.nanoTime();
        timerLog.debug("TIMER L TW 3 Trying to lock all ids. {}", Thread.currentThread().getName());
        if (!lock.tryLock(neighboursUris)) {
            timerLog.debug("TIMER L TW 3 Failed. {}ms. {}", Long.toString((System.nanoTime() - lastTime)/1000000), Thread.currentThread().getName());
            lastTime = System.nanoTime();
            return null;
        }

        timerLog.debug("TIMER L TW 3 all ids locked. {}ms. {}",Long.toString((System.nanoTime() - lastTime)/1000000),  Thread.currentThread().getName());
        lastTime = System.nanoTime();
        Set<LookupEntry> entries = entriesFor(neighboursUris);

        timerLog.debug("TIMER L TW 3 got all entries from the DB ("+entries.size()+"). {}ms. {}",Long.toString((System.nanoTime() - lastTime)/1000000), Thread.currentThread().getName());

        Iterable<LookupRef> transitiveSetRefs = Iterables.concat(Iterables.transform(entries, LookupEntry.TO_EQUIVS));
        Set<String> transitiveSetUris = ImmutableSet.copyOf(Iterables.transform(transitiveSetRefs, LookupRef.TO_URI));
        // We allow oversize sets if this is being written as an explicit equivalence, 
        // since a user has explicitly asked us to make the assertion, so we must
        // honour it
        // If we will shrink the direct equivalences then we allow this as well
        if (!explicit
                && transitiveSetUris.size() > maxSetSize
                && !strictSubset
                ) {
            throw new OversizeTransitiveSetException(transitiveSetUris.size());
        }
        Iterable<String> urisToLock = Iterables.filter(transitiveSetUris, not(in(neighboursUris)));
        return lock.tryLock(ImmutableSet.copyOf(urisToLock)) ? transitiveSetUris
                                                             : null;
    }

    private LookupEntry updateEntryNeighbours(LookupEntry entry, LookupEntry subject,
            Set<LookupEntry> subjectNeighbours, Set<Publisher> sources) {
        if (entry.equals(subject)) {
            return updateSubjectNeighbours(subject, subjectNeighbours, sources);
        } 
        if (sources.contains(entry.lookupRef().publisher())) {
            return updateEntrysNeighbours(entry, subject, subjectNeighbours);
        }
        return entry;
    }

    private LookupEntry updateSubjectNeighbours(LookupEntry subject,
            Set<LookupEntry> neighbours, Set<Publisher> sources) {
        Predicate<LookupRef> unaffectedSources = MorePredicates.transformingPredicate(LookupRef.TO_SOURCE, not(in(sources)));
        return updateRelevantNeighbours(subject, Iterables.concat(
            Sets.filter(getRelevantNeighbours(subject), unaffectedSources), 
            Iterables.transform(neighbours, LookupEntry.TO_SELF)
        ));
    }

    private LookupEntry updateEntrysNeighbours(LookupEntry entry,
            LookupEntry subject, Set<LookupEntry> subjectNeighbours) {
        ImmutableSet<LookupRef> subjectRef = ImmutableSet.of(subject.lookupRef());
        Set<LookupRef> entryNeighbours = getRelevantNeighbours(entry);
        if (subjectNeighbours.contains(entry)) {
            entryNeighbours = Sets.union(entryNeighbours, subjectRef);
        } else {
            entryNeighbours = Sets.difference(entryNeighbours, subjectRef);
        }
        return updateRelevantNeighbours(entry, entryNeighbours);
    }

    private LookupEntry updateRelevantNeighbours(LookupEntry equivalent,
            Iterable<LookupRef> updatedNeighbours) {
        return explicit ? equivalent.copyWithExplicitEquivalents(updatedNeighbours)
                        : equivalent.copyWithDirectEquivalents(updatedNeighbours);
    }

    private boolean noChangeInNeighbours(LookupEntry subject, ImmutableSet<String> newNeighbours,
            Set<Publisher> sources) {
        Set<LookupRef> currentNeighbours = Sets.filter(
            getRelevantNeighbours(subject), 
            MorePredicates.transformingPredicate(LookupRef.TO_SOURCE, in(sources))
        );
        Set<String> subjectAndNeighbours = MoreSets.add(newNeighbours, subject.uri());
        Set<String> currentNeighbourUris = ImmutableSet.copyOf(transform(currentNeighbours, LookupRef.TO_URI));
        boolean noChange = currentNeighbourUris.equals(subjectAndNeighbours);
        if (!noChange) {
            log.debug("Equivalence change: {} -> {}", currentNeighbourUris, subjectAndNeighbours);
        }
        return noChange;
    }

    private Set<LookupRef> getRelevantNeighbours(LookupEntry subjectEntry) {
        return explicit ? subjectEntry.explicitEquivalents() 
                        : subjectEntry.directEquivalents();
    }

    private Set<LookupEntry> recomputeTransitiveClosures(Map<String, LookupEntry> entries) {
        
        Set<LookupEntry> updatedEntries = Sets.newHashSet();
        for (LookupEntry entry : Iterables.filter(entries.values(), not(in(updatedEntries)))) {

            Set<LookupRef> transitiveSet = Sets.newHashSet();
            
            Queue<LookupRef> direct = Lists.newLinkedList(neighbours(entry));
            //Traverse equivalence graph breadth-first
            Set<LookupRef> seen = Sets.newHashSet();
            while(!direct.isEmpty()) {
                LookupRef current = direct.poll();
                if (!seen.contains(current)) {
                    transitiveSet.add(current);
                    if (entries.get(current.uri())!= null) {
                        LookupEntry currentEntry = entries.get(current.uri());
                        Iterables.addAll(direct, Iterables.filter(neighbours(currentEntry), not(in(transitiveSet))));
                    }
                    seen.add(current);
                }
            }
            
            // Because all entries in the same transitive set should have
            // the same equivalents their entries can be updated here,
            // short-circuiting the top-level loop.
            for (LookupRef lookupRef : transitiveSet) {
                LookupEntry lookupEntry = entries.get(lookupRef.uri());
                if(lookupEntry != null ) {
                    updatedEntries.add(lookupEntry.copyWithEquivalents(transitiveSet));
                }
            }
        }
        return updatedEntries;
    }

    private Iterable<LookupRef> neighbours(LookupEntry current) {
        return Iterables.concat(current.directEquivalents(), current.explicitEquivalents());
    }
    
    private Set<LookupEntry> entriesFor(Iterable<String> equivalents) {
        return ImmutableSet.copyOf(entryStore.entriesForCanonicalUris(equivalents));
    }

    private LookupEntry entryFor(String subject) {
        return Iterables.getOnlyElement(entryStore.entriesForCanonicalUris(ImmutableList.of(subject)), null);
    }

    private static class OversizeTransitiveSetException extends RuntimeException {
        
        private int size;

        public OversizeTransitiveSetException(int size)  {
            this.size = size;
        }

        @Override
        public String getMessage() {
            return String.valueOf(size);
        }
        
    }
    
}
