package org.atlasapi.persistence.lookup.mongo;

import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.metabroadcast.common.persistence.mongo.MongoBuilders.select;
import static com.metabroadcast.common.persistence.mongo.MongoBuilders.where;
import static com.metabroadcast.common.persistence.mongo.MongoBuilders.sort;
import static com.metabroadcast.common.persistence.mongo.MongoConstants.ID;
import static com.metabroadcast.common.persistence.mongo.MongoConstants.IN;
import static com.metabroadcast.common.persistence.mongo.MongoConstants.SINGLE;
import static com.metabroadcast.common.persistence.mongo.MongoConstants.UPSERT;
import static org.atlasapi.media.entity.LookupRef.TO_URI;
import static org.atlasapi.persistence.lookup.entry.LookupEntry.lookupEntryFrom;
import static org.atlasapi.persistence.lookup.mongo.LookupEntryTranslator.ALIASES;
import static org.atlasapi.persistence.lookup.mongo.LookupEntryTranslator.IDS;
import static org.atlasapi.persistence.lookup.mongo.LookupEntryTranslator.OPAQUE_ID;
import static org.atlasapi.persistence.lookup.mongo.LookupEntryTranslator.SELF;
import static org.atlasapi.persistence.media.entity.AliasTranslator.NAMESPACE;
import static org.atlasapi.persistence.media.entity.AliasTranslator.VALUE;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.LookupRef;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.lookup.NewLookupWriter;
import org.atlasapi.persistence.lookup.entry.LookupEntry;
import org.atlasapi.persistence.lookup.entry.LookupEntryStore;
import org.atlasapi.persistence.media.entity.IdentifiedTranslator;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.persistence.mongo.MongoBuilders;
import com.metabroadcast.common.persistence.mongo.MongoConstants;
import com.metabroadcast.common.persistence.translator.TranslatorUtils;
import com.metabroadcast.common.properties.Configurer;
import com.metabroadcast.common.properties.Parameter;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.ReadPreference;

public class MongoLookupEntryStore implements LookupEntryStore, NewLookupWriter {

    private final Parameter processingConfig = Configurer.get("processing.config");
    private static final String PUBLISHER = SELF + "." + IdentifiedTranslator.PUBLISHER;
    private static final Pattern ANYTHING = Pattern.compile("^.*");
    private DBCollection lookup;
    private LookupEntryTranslator translator;
    private final ReadPreference readPreference;

    public MongoLookupEntryStore(DBCollection lookup) {
        this.lookup = lookup;
        if(processingConfig == null || !processingConfig.toBoolean()) {
            readPreference = ReadPreference.secondaryPreferred();
        } else {
            readPreference = ReadPreference.primary();
        }
    }
    
    public MongoLookupEntryStore(DBCollection lookup, ReadPreference readPreference) {
        this.lookup = lookup;
        this.readPreference = readPreference;
        this.translator = new LookupEntryTranslator();
    }
    
    @Override
    public void store(LookupEntry entry) {
        lookup.update(MongoBuilders.where().idEquals(entry.uri()).build(), translator.toDbo(entry), UPSERT, SINGLE);
    }
    
    @Override
    public Iterable<LookupEntry> entriesForCanonicalUris(Iterable<String> uris) {
        DBCursor found = lookup.find(where().idIn(uris).build()).setReadPreference(readPreference);
        if (found == null) {
            return ImmutableList.of();
        }
        return Iterables.transform(found, translator.FROM_DBO);
    }

    @Override
    public Iterable<LookupEntry> entriesForIds(Iterable<Long> ids) {
        DBObject queryDbo = new BasicDBObject(OPAQUE_ID, new BasicDBObject(IN, ids));
        DBCursor found = lookup.find(queryDbo).setReadPreference(readPreference);
        if (found == null) {
            return ImmutableList.of();
        }
        return Iterables.transform(found, translator.FROM_DBO);
    }
    
    @Override
    public void ensureLookup(Content content) {
        LookupEntry newEntry = lookupEntryFrom(content);
        // Since most content will already have a lookup entry we read first to avoid locking the database
        LookupEntry existing = translator.fromDbo(lookup.findOne(new BasicDBObject(MongoConstants.ID, content.getCanonicalUri()), null, readPreference));
        if (existing == null) {
            store(newEntry);
        } else if(!newEntry.lookupRef().category().equals(existing.lookupRef().category())) {
            updateEntry(content, newEntry, existing);
        } else if (!newEntry.aliasUrls().equals(existing.aliasUrls())) {
            store(merge(content, newEntry, existing));
        } else if (!newEntry.aliases().equals(existing.aliases())) {
            store(merge(content, newEntry, existing));
        } 
    }

    private void updateEntry(Content content, LookupEntry newEntry, LookupEntry existing) {
        LookupEntry merged = merge(content, newEntry, existing);
        LookupRef ref = merged.lookupRef();

        store(merged);
        
        for (LookupEntry entry : entriesForCanonicalUris(transform(filter(merged.equivalents(), not(equalTo(ref))), TO_URI))) {
            if(entry.directEquivalents().contains(ref)) {
                entry = entry.copyWithDirectEquivalents(ImmutableSet.<LookupRef>builder().add(ref).addAll(entry.directEquivalents()).build());
            }
            entry = entry.copyWithEquivalents(ImmutableSet.<LookupRef>builder().add(ref).addAll(existing.equivalents()).build());
            store(entry);
        }
    }

    private LookupEntry merge(Content content, LookupEntry newEntry, LookupEntry existing) {
        LookupRef ref = LookupRef.from(content);
        Set<LookupRef> directEquivs = ImmutableSet.<LookupRef>builder().add(ref).addAll(existing.directEquivalents()).build();
        Set<LookupRef> explicit = ImmutableSet.<LookupRef>builder().add(ref).addAll(existing.explicitEquivalents()).build();
        Set<LookupRef> transitiveEquivs = ImmutableSet.<LookupRef>builder().add(ref).addAll(existing.equivalents()).build();
        LookupEntry merged = new LookupEntry(newEntry.uri(), existing.id(), ref, newEntry.aliasUrls(), newEntry.aliases(), directEquivs, explicit, transitiveEquivs, existing.created(), newEntry.updated());
        return merged;
    }

    @Override
    public Iterable<LookupEntry> entriesForIdentifiers(Iterable<String> identifiers, boolean useAliases) {
        return Iterables.transform(find(identifiers), translator.FROM_DBO);
    }

    private Iterable<DBObject> find(Iterable<String> identifiers) {
        return lookup.find(where().fieldIn(ALIASES, identifiers).build()).setReadPreference(readPreference);
    }

    @Override
    public Iterable<LookupEntry> entriesForAliases(Optional<String> namespace, Iterable<String> values) {
        return Iterables.transform(find(namespace, values), translator.FROM_DBO);
    }

    @Override
    public Map<String, Long> idsForCanonicalUris(Iterable<String> uris) {
        Builder<String, Long> results = ImmutableMap.builder();
        DBCursor cursor = lookup.find(
                            where().idIn(uris).build(), 
                            select().field(OPAQUE_ID).field(ID).build()
                          )
                          .setReadPreference(readPreference);
        for (DBObject dbo : cursor) {
            Long id = TranslatorUtils.toLong(dbo, OPAQUE_ID);
            if (id != null) {
                results.put(TranslatorUtils.toString(dbo, ID), id);
            }
        }
        return results.build();
    }
    
    private Iterable<DBObject> find(Optional<String> namespace, Iterable<String> values) {
        if (namespace.isPresent()) {
            return lookup.find(where().elemMatch(IDS, where().fieldEquals(NAMESPACE, namespace.get()).fieldIn(VALUE, values)).build())
                    .setReadPreference(readPreference);        
        } else {
            return lookup.find(where().elemMatch(IDS, where().fieldEquals(NAMESPACE, ANYTHING).fieldIn(VALUE, values)).build())
                    .setReadPreference(readPreference);
        }
    }

    @Override
    public Iterable<LookupEntry> entriesForPublishers(Iterable<Publisher> publishers) {
        return Iterables.transform(
                lookup.find(where().fieldIn(PUBLISHER, Iterables.transform(publishers, Publisher.TO_KEY))
                                   .build())
                      .setReadPreference(readPreference)
                      .sort(sort().ascending(OPAQUE_ID).build()),
                translator.FROM_DBO);
    }

}
