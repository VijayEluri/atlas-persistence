package org.atlasapi.media.channel;

import static com.google.common.collect.Iterables.transform;

import java.util.List;
import java.util.Set;

import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.MediaType;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.ModelTranslator;
import org.atlasapi.persistence.media.entity.IdentifiedTranslator;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.metabroadcast.common.persistence.translator.TranslatorUtils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class ChannelTranslator implements ModelTranslator<Channel> {

    public static final String TITLE = "title";
    public static final String TITLES = "titles";
	public static final String PUBLISHER = "publisher";
	public static final String BROADCASTER = "broadcaster";
	public static final String MEDIA_TYPE = "mediaType";
	public static final String AVAILABLE_ON = "availableOn";
	public static final String HIGH_DEFINITION = "highDefinition";
	public static final String REGIONAL = "regional";
	public static final String TIMESHIFT = "timeshift";
	public static final String KEY = "key";
	public static final String IMAGE = "image";
	public static final String IMAGES = "images";
	public static final String PARENT = "parent";
	public static final String VARIATIONS = "variations";
	public static final String NUMBERINGS = "numberings";
	public static final String START_DATE = "startDate";
	public static final String END_DATE = "endDate";
	

	private ModelTranslator<Identified> identifiedTranslator;
	private ChannelNumberingTranslator channelNumberingTranslator;
	private TemporalTitleTranslator temporalTitleTranslator;
	private final TemporalImageTranslator temporalImageTranslator;

	public ChannelTranslator() {
		this.identifiedTranslator = new IdentifiedTranslator(true);
		this.channelNumberingTranslator = new ChannelNumberingTranslator();
		this.temporalTitleTranslator = new TemporalTitleTranslator();
		this.temporalImageTranslator = new TemporalImageTranslator();
	}

	@Override
	public DBObject toDBObject(DBObject dbObject, Channel model) {
		dbObject = new BasicDBObject();

		identifiedTranslator.toDBObject(dbObject, model);
		
		temporalTitleTranslator.fromTemporalTitleSet(dbObject, TITLES, model.getAllTitles());
		temporalImageTranslator.fromTemporalImageSet(dbObject, IMAGES, model.getAllImages());
		
		TranslatorUtils.from(dbObject, MEDIA_TYPE, model.getMediaType().name());
		TranslatorUtils.from(dbObject, PUBLISHER, model.getSource().key());
		TranslatorUtils.from(dbObject, HIGH_DEFINITION, model.getHighDefinition());
		TranslatorUtils.from(dbObject, REGIONAL, model.getRegional());
		TranslatorUtils.fromDuration(dbObject, TIMESHIFT, model.getTimeshift());
		TranslatorUtils.from(dbObject, BROADCASTER, model.getBroadcaster() != null ? model.getBroadcaster().key() : null);
		if (model.getAvailableFrom() != null) {
		    TranslatorUtils.fromSet(dbObject, ImmutableSet.copyOf(transform(model.getAvailableFrom(), Publisher.TO_KEY)), AVAILABLE_ON);
		}
		TranslatorUtils.from(dbObject, KEY, model.getKey());
		TranslatorUtils.from(dbObject, PARENT, model.getParent());
		if (model.getVariations() != null) {
		    TranslatorUtils.fromLongSet(dbObject, VARIATIONS, model.getVariations());
		}
		if (model.getChannelNumbers() != null) {
		    channelNumberingTranslator.fromChannelNumberingSet(dbObject, NUMBERINGS, model.getChannelNumbers());
		}
		TranslatorUtils.fromLocalDate(dbObject, START_DATE, model.getStartDate());
		TranslatorUtils.fromLocalDate(dbObject, END_DATE, model.getEndDate());
		
		return dbObject;
	}

	@Override
	public Channel fromDBObject(DBObject dbObject, Channel model) {
	    if (dbObject == null) {
	        return null;
	    }
	    
		if (model == null) {
			model = Channel.builder().build();
		}

        model.setSource(Publisher.fromKey(TranslatorUtils.toString(dbObject, PUBLISHER)).requireValue());
		model.setMediaType(MediaType.valueOf(TranslatorUtils.toString(dbObject, MEDIA_TYPE)));
        if (dbObject.containsField(TITLES)) {
            model.setTitles(temporalTitleTranslator.toTemporalTitleSet(dbObject, TITLES));
        }
        if (dbObject.containsField(TITLE)) {
            model.addTitle(TranslatorUtils.toString(dbObject, TITLE));
        }
        if (dbObject.containsField(IMAGES)) {
            model.setImages(temporalImageTranslator.toTemporalImageSet(dbObject, IMAGES));
        }
		model.setKey((String) dbObject.get(KEY));
		model.setHighDefinition(TranslatorUtils.toBoolean(dbObject, HIGH_DEFINITION));
		model.setRegional(TranslatorUtils.toBoolean(dbObject, REGIONAL));
		model.setTimeshift(TranslatorUtils.toDuration(dbObject, TIMESHIFT));
		model.setAvailableFrom(Iterables.transform(TranslatorUtils.toSet(dbObject, AVAILABLE_ON), Publisher.FROM_KEY));
		
		String broadcaster = TranslatorUtils.toString(dbObject, BROADCASTER);
		model.setBroadcaster(broadcaster != null ? Publisher.fromKey(broadcaster).valueOrNull() : null);
		model.setParent(TranslatorUtils.toLong(dbObject, PARENT));
		model.setVariationIds(TranslatorUtils.toLongSet(dbObject, VARIATIONS));
		model.setChannelNumbers(channelNumberingTranslator.toChannelNumberingSet(dbObject, NUMBERINGS));
		model.setStartDate(TranslatorUtils.toLocalDate(dbObject, START_DATE));
		model.setEndDate(TranslatorUtils.toLocalDate(dbObject, END_DATE));
		
		return (Channel) identifiedTranslator.fromDBObject(dbObject, model);
	}
}
