package org.atlasapi.persistence.lookup;

import java.util.List;
import java.util.Set;

import org.atlasapi.media.entity.Described;
import org.atlasapi.media.entity.Publisher;
import org.joda.time.DateTime;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.metabroadcast.common.time.DateTimeZones;

public class LookupEntry {

    private final String id;
    private final Set<String> aliases;
    private final Publisher publisher;
    private final String type;
    private final List<Equivalent> equivs;
    private final DateTime created;
    private final DateTime updated;

    private Set<String> directEquivalents;

    public LookupEntry(String id, Set<String> aliases, Publisher publisher, String type, List<Equivalent> equivs, DateTime created, DateTime updated) {
        this.id = id;
        this.aliases = aliases;
        this.publisher = publisher;
        this.type = type;
        this.equivs = equivs;
        this.created = created;
        this.updated = updated;
    }

    public String id() {
        return id;
    }

    public List<Equivalent> equivalents() {
        return equivs;
    }

    public DateTime created() {
        return created;
    }

    public DateTime updated() {
        return updated;
    }

    Set<String> aliases() {
        return aliases;
    }

    public Set<String> directEquivalents() {
        return directEquivalents;
    }

    public LookupEntry withDirectEquivalents(Iterable<String> directEquivalents) {
        this.directEquivalents = ImmutableSet.copyOf(directEquivalents);
        return this;
    }
    
    @Override
    public boolean equals(Object that) {
        if(this == that) {
            return true;
        }
        if(that instanceof LookupEntry) {
            LookupEntry other = (LookupEntry) that;
            return id.equals(other.id) && equivs.equals(other.equivs) && created.equals(other.created) && updated.equals(other.updated);
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(id, equivs, created, updated);
    }
    
    @Override
    public String toString() {
        return "Lookup entry for " + id;
    }
    
    public Set<String> identifiers() {
        return ImmutableSet.<String>builder().add(id).addAll(aliases).build();
    }
    
    public static Function<LookupEntry,String> TO_ID = new Function<LookupEntry, String>() {
        @Override
        public String apply(LookupEntry input) {
            return input.id();
        }
    };
    
    public static Function<LookupEntry,List<Equivalent>> TO_EQUIVS = new Function<LookupEntry, List<Equivalent>>() {
        @Override
        public List<Equivalent> apply(LookupEntry input) {
            return input.equivalents();
        }
    };

    public LookupEntry copyWithEquivalents(Iterable<Equivalent> newEquivs) {
        return new LookupEntry(id, aliases, publisher, type, ImmutableList.copyOf(newEquivs), created, updated).withDirectEquivalents(directEquivalents);
    }

    public static LookupEntry lookupEntryFrom(Described subject) {
        DateTime now = new DateTime(DateTimeZones.UTC);
        return new LookupEntry(subject.getCanonicalUri(), subject.getAliases(), subject.getPublisher(), subject.getType(), ImmutableList.of(Equivalent.from(subject)), now, now);
    }

    public List<LookupEntry> entriesForIdentifiers() {
        List<LookupEntry> entries = Lists.newArrayList(this);
        for (String alias : aliases) {
            entries.add(new LookupEntry(alias, ImmutableSet.<String>of(), this.publisher, this.type, this.equivs, created, updated).withDirectEquivalents(directEquivalents));
        }
        return ImmutableList.copyOf(entries);
    }

    public Set<String> setDirectEquivalents(Set<String> directEquivalents) {
        this.directEquivalents = directEquivalents;
        return directEquivalents;
    }

    public Publisher publisher() {
        return publisher;
    }
    
    public String type() {
        return type;
    }
    
    public Equivalent toEquivalent() {
        return new Equivalent(id, publisher, type);
    }
}
