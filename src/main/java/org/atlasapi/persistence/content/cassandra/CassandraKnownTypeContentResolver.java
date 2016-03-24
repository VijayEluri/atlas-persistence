package org.atlasapi.persistence.content.cassandra;

import java.util.Set;

import org.atlasapi.media.entity.LookupRef;
import org.atlasapi.output.Annotation;
import org.atlasapi.persistence.content.KnownTypeContentResolver;
import org.atlasapi.persistence.content.ResolvedContent;

import com.google.common.collect.Iterables;

/**
 */
public class CassandraKnownTypeContentResolver implements KnownTypeContentResolver {

    private final CassandraContentStore store;

    public CassandraKnownTypeContentResolver(CassandraContentStore store) {
        this.store = store;
    }

    @Override
    public ResolvedContent findByLookupRefs(Iterable<LookupRef> lookupRefs) {
        return store.findByCanonicalUris(Iterables.transform(lookupRefs, LookupRef.TO_URI));
    }

    @Override
    public ResolvedContent findByLookupRefs(Iterable<LookupRef> lookupRefs,
            Set<Annotation> activeAnnotations) {
        return findByLookupRefs(lookupRefs);
    }
}
