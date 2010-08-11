package org.atlasapi.persistence.media.entity;

import org.atlasapi.media.entity.Playlist;
import org.atlasapi.persistence.ModelTranslator;

import com.metabroadcast.common.persistence.translator.TranslatorUtils;
import com.mongodb.DBObject;

public class PlaylistTranslator implements ModelTranslator<Playlist> {

	private final ContentTranslator contentTranslator;

	public PlaylistTranslator() {
		this(new ContentTranslator(new DescriptionTranslator(true)));
	}
	
	public PlaylistTranslator(ContentTranslator contentTranslator) {
		this.contentTranslator = contentTranslator;
	}
	
    @Override
    public Playlist fromDBObject(DBObject dbObject, Playlist entity) {
        if (entity == null) {
            entity = new Playlist();
        }
        
        contentTranslator.fromDBObject(dbObject, entity);
        
        entity.setPlaylistUris(TranslatorUtils.toList(dbObject, "playlistUris"));
        entity.setItemUris(TranslatorUtils.toList(dbObject, "itemUris"));
        
        return entity;
    }

    @Override
    public DBObject toDBObject(DBObject dbObject, Playlist entity) {
    	
        dbObject = contentTranslator.toDBObject(dbObject, entity);
        
        TranslatorUtils.fromList(dbObject, entity.getItemUris(), "itemUris");
        TranslatorUtils.fromList(dbObject, entity.getPlaylistUris(), "playlistUris");
        
        return dbObject;
    }

}