package org.atlasapi.persistence.lookup.mongo;

import static com.metabroadcast.common.persistence.mongo.MongoConstants.ID;

import java.util.Set;

import org.atlasapi.media.entity.Alias;
import org.atlasapi.media.entity.LookupRef;
import org.atlasapi.persistence.lookup.entry.LookupEntry;
import org.atlasapi.persistence.media.entity.AliasTranslator;
import org.atlasapi.persistence.media.entity.LookupRefTranslator;
import org.joda.time.DateTime;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.metabroadcast.common.persistence.translator.TranslatorUtils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class LookupEntryTranslator {

    private static final String EXPLICIT = "explicit";
    private static final String DIRECT = "direct";
    private static final String EQUIVS = "equivs";
    private static final String LAST_UPDATED = "updated";
    private static final String FIRST_CREATED = "created";
    public static final String ACTIVELY_PUBLISHED = "activelyPublished";
    public static final String ALIASES = "aliases";
    public static final String IDS = "ids";
    public static final String OPAQUE_ID = "aid";
    public static final String SELF = "self";
    
    private final AliasTranslator aliasTranslator = new AliasTranslator();
    private static final LookupRefTranslator lookupRefTranslator = new LookupRefTranslator();
    
    public Function<LookupEntry, DBObject> TO_DBO = new Function<LookupEntry, DBObject>() {
        @Override
        public DBObject apply(LookupEntry input) {
            return toDbo(input);
        }
    };
    public Function<DBObject, LookupEntry> FROM_DBO = new Function<DBObject, LookupEntry>() {

        @Override
        public LookupEntry apply(DBObject dbo) {
            return fromDbo(dbo);
        }
    };
    
    public DBObject toDbo(LookupEntry entry) {
        BasicDBObject dbo = new BasicDBObject();
        
        TranslatorUtils.from(dbo, ID, entry.uri());
        TranslatorUtils.from(dbo, OPAQUE_ID, entry.id());
        TranslatorUtils.from(dbo, SELF, refToDbo.apply(entry.lookupRef()));
        
        Set<String> aliases = Sets.newHashSet(entry.uri());
        aliases.addAll(entry.aliasUrls());
        TranslatorUtils.fromSet(dbo, aliases, ALIASES);
        
        TranslatorUtils.from(dbo, IDS, aliasTranslator.toDBList(entry.aliases()));
        
        translateRefsInToField(dbo, EQUIVS, entry.equivalents());
        translateRefsInToField(dbo, DIRECT, entry.directEquivalents());
        translateRefsInToField(dbo, EXPLICIT, entry.explicitEquivalents());
        
        TranslatorUtils.fromDateTime(dbo, FIRST_CREATED, entry.created());
        TranslatorUtils.fromDateTime(dbo, LAST_UPDATED, entry.updated());
        
        if (!entry.activelyPublished()) {
            TranslatorUtils.from(dbo, ACTIVELY_PUBLISHED, entry.activelyPublished());
        }
        
        return dbo;
    }

    private void translateRefsInToField(BasicDBObject dbo, String field, Set<LookupRef> refs) {
        BasicDBList refDbos = new BasicDBList();
        refDbos.addAll(ImmutableSet.copyOf(Iterables.transform(refs, refToDbo)));
        TranslatorUtils.from(dbo, field, refDbos);
    }
    
    private static Function<LookupRef, DBObject> refToDbo = new Function<LookupRef, DBObject>() {
        @Override
        public DBObject apply(LookupRef input) {
            return lookupRefTranslator.toDBObject(null, input);
        }
    };
    
    public LookupEntry fromDbo(DBObject dbo) {
        if(dbo == null) {
            return null;
        }
        
        String uri = TranslatorUtils.toString(dbo, ID);
        Long id = TranslatorUtils.toLong(dbo, OPAQUE_ID);
        
        Set<String> aliasUris = TranslatorUtils.toSet(dbo, ALIASES);
        aliasUris.add(uri);
        
        Set<Alias> aliases = aliasTranslator.fromDBObjects(TranslatorUtils.toDBObjectList(dbo, IDS));
        
        LookupRef self = lookupRefTranslator.fromDBObject(TranslatorUtils.toDBObject(dbo, SELF), null);
        Set<LookupRef> equivs = translateRefs(self, dbo, EQUIVS);
        Set<LookupRef> directEquivalents = translateRefs(self, dbo, DIRECT);
        Set<LookupRef> explicitEquivalents = translateRefs(self, dbo, EXPLICIT);
        
        DateTime created = TranslatorUtils.toDateTime(dbo, FIRST_CREATED);
        DateTime updated = TranslatorUtils.toDateTime(dbo, LAST_UPDATED);
        
        boolean activelyPublished = true;
        if (dbo.containsField(ACTIVELY_PUBLISHED)) {
            activelyPublished = TranslatorUtils.toBoolean(dbo, ACTIVELY_PUBLISHED);
        }
        return new LookupEntry(uri, id, self, aliasUris, aliases, directEquivalents, explicitEquivalents, equivs, created, updated, activelyPublished);
    }
    
    public DBObject removeFieldsForHash(DBObject dbo) {
        if (dbo == null) {
            return null;
        }
        dbo.removeField(LAST_UPDATED);
        dbo.removeField(FIRST_CREATED);
        return dbo;
    }

    private ImmutableSet<LookupRef> translateRefs(LookupRef seflRef, DBObject dbo, String field) {
        return ImmutableSet.<LookupRef>builder()
            .add(seflRef)
            .addAll(Iterables.transform(TranslatorUtils.toDBObjectList(dbo, field), refFromDbo))
            .build();
    }

    private static final Function<DBObject, LookupRef> refFromDbo = new Function<DBObject, LookupRef>() {
        @Override
        public LookupRef apply(DBObject input) {
            return lookupRefTranslator.fromDBObject(input, null);
        }
    };
}
