package org.atlasapi.persistence.content.listing;

import com.google.common.base.Optional;
import com.metabroadcast.common.persistence.mongo.DatabasedMongo;
import com.metabroadcast.common.persistence.mongo.MongoConstants;
import com.metabroadcast.common.persistence.mongo.MongoUpdateBuilder;
import com.metabroadcast.common.persistence.translator.TranslatorUtils;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.content.ContentCategory;


import static com.metabroadcast.common.persistence.mongo.MongoBuilders.where;

public class MongoProgressStore implements ProgressStore {

    private static final String PROGRESS_COLLECTION_NAME = "listerProgress";
    private static final String START = "start";
    private static final String PUBLISHER = "publisher";
    private static final String LAST_ID = "lastId";
    private static final String CATEGORY = "category";

    private final DBCollection progressCollection;

    public MongoProgressStore(DatabasedMongo mongoDb) {
        this.progressCollection = mongoDb.collection(PROGRESS_COLLECTION_NAME);
    }

    @Override
    public Optional<ContentListingProgress> progressForTask(String taskName) {
        DBObject progress = progressCollection.findOne(taskName);
        if (progress != null) {
            return Optional.of(fromDbo(progress));
        }
        return Optional.absent();
    }

    private ContentListingProgress fromDbo(DBObject progress) {
        String lastId = TranslatorUtils.toString(progress, LAST_ID);

        if(START.equals(lastId)) {
            return ContentListingProgress.START;
        }

        String tableName = TranslatorUtils.toString(progress, CATEGORY);
        ContentCategory category = tableName == null ? null : ContentCategory.valueOf(tableName);

        String pubKey = TranslatorUtils.toString(progress, PUBLISHER);
        Publisher publisher = pubKey == null ? null : Publisher.fromKey(pubKey).valueOrNull();

        return new ContentListingProgress(category, publisher, lastId);
    }

    @Override
    public void storeProgress(String taskName, ContentListingProgress progress) {
        MongoUpdateBuilder update = new MongoUpdateBuilder().setField(LAST_ID, progress.getUri() == null ? START : progress.getUri());

        if(progress.getCategory() != null) {
            update.setField(CATEGORY, progress.getCategory().toString());
        } else {
            update.unsetField(CATEGORY);
        }

        if(progress.getPublisher() != null) {
            update.setField(PUBLISHER, progress.getPublisher().key());
        } else {
            update.unsetField(PUBLISHER);
        }

        if (progress.getCount() != null) {
            update.setField("count", progress.getCount());
        }

        progressCollection.update(
                where().fieldEquals(MongoConstants.ID, taskName).build(),
                update.build(),
                MongoConstants.UPSERT,
                MongoConstants.SINGLE
        );
    }
}