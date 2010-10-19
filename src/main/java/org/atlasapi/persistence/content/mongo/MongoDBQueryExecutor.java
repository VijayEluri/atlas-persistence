/* Copyright 2010 Meta Broadcast Ltd

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You may
obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License. */

package org.atlasapi.persistence.content.mongo;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.atlasapi.content.criteria.AtomicQuery;
import org.atlasapi.content.criteria.AttributeQuery;
import org.atlasapi.content.criteria.ContentQuery;
import org.atlasapi.content.criteria.MatchesNothing;
import org.atlasapi.content.criteria.attribute.Attribute;
import org.atlasapi.content.criteria.attribute.Attributes;
import org.atlasapi.content.criteria.operator.Operators;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.Description;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Playlist;
import org.atlasapi.persistence.content.query.KnownTypeQueryExecutor;
import org.atlasapi.persistence.content.query.QueryFragmentExtractor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.query.Selection;

@SuppressWarnings("unchecked")
public class MongoDBQueryExecutor implements KnownTypeQueryExecutor {

	private static final Set<Class<? extends Description>> playlistAttributes = (Set) Sets.newHashSet(Playlist.class);
	private static final Set<Class<? extends Description>> brandAndPlaylistAttributes = (Set) Sets.newHashSet(Brand.class, Playlist.class);
	
	/**
	 * If the query has brand and item constraints then we first find the 
	 * Brands that match, then search for items contained within those brands.  If the 
	 * first query (the brand query) returns too many Brands it generates a large
	 * and inefficient query.
	 */
	private static final int MAX_INTERMEDIATE_BRANDS = 2000;
	
	private final QuerySplitter splitter = new QuerySplitter();
	private final QueryResultTrimmer trimmer = new QueryResultTrimmer();
	private final MongoRoughSearch roughSearch;
	
	private boolean filterUriQueries = false;
	
	public MongoDBQueryExecutor(MongoRoughSearch roughSearch) {
		this.roughSearch = roughSearch;
	}
	
	@Override
	public List<Item> executeItemQuery(ContentQuery query) {

		if (MatchesNothing.isEquivalentTo(query)) {
			return Collections.emptyList();
		}
		
		Maybe<ContentQuery> playlistQuery = splitter.retain(query, brandAndPlaylistAttributes);

		// The query doesn't make any playlist or brand constraints: just perform a standard item query
		if (playlistQuery.isNothing()) {
			return executeItemQueryInternal(query);		
		}
		
		// Find the playlists
		List<Playlist> playlists = roughSearch.dehydratedPlaylistsMatching(playlistQuery.requireValue());
		
		if (playlists.isEmpty()) {
			return Collections.emptyList();
		}
		
		Maybe<ContentQuery> everythingElse = splitter.discard(query, brandAndPlaylistAttributes);
		
		// Execute the item query but constrain results to the playlists found
		return executeItemQueryInternal(createContainedInPlaylistQuery(everythingElse, playlists, Attributes.PLAYLIST_URI, query.getSelection()));
	}

	private List<Item> executeItemQueryInternal(ContentQuery query) {
		// Extract exact item match query fragments (item by uri or curie)
		Maybe<AttributeQuery<?>> byUriOrCurie = QueryFragmentExtractor.extract(query, Sets.<Attribute<?>>newHashSet(Attributes.ITEM_URI));
		// If the request is for an exact match query then query for the item by uri or curie and filter the result
		if (byUriOrCurie.hasValue() && !filterUriQueries) {
			// Preserve any 'contained in' constraints
			Maybe<AttributeQuery<?>> containedIn = QueryFragmentExtractor.extract(query, Sets.<Attribute<?>>newHashSet(Attributes.PLAYLIST_URI, Attributes.BRAND_URI));
			ContentQuery itemQuery = splitter.retain(query, ImmutableSet.<Class<? extends Description>>of(Item.class)).requireValue();
			ContentQuery unfilteredItemQuery = containedIn.hasValue() ? ContentQuery.joinTo(itemQuery, new ContentQuery(ImmutableList.<AtomicQuery>of(containedIn.requireValue()))) : itemQuery; 
			List<Item> filtered = filterItems(query, roughSearch.itemsMatching(unfilteredItemQuery), false);
			return sort(filtered, (List<String>) byUriOrCurie.requireValue().getValue());
		}
		return filterItems(query, roughSearch.itemsMatching(query), true);
	}
	
	private  <T extends Content> List<T> sort(List<T> content, final List<String> order) {
		
		Comparator<Content> byPositionInList = new Comparator<Content>() {

			@Override
			public int compare(Content c1, Content c2) {
				return Ints.compare(indexOf(c1), indexOf(c2));
			}

			private int indexOf(Content content) {
				for (String uri : content.getAllUris()) {
					int idx = order.indexOf(uri);
					if (idx != -1) {
						return idx;
					}
				}
				if (content.getCurie() != null) {
					return order.indexOf(content.getCurie());
				}
				return -1;
			}
		};
		
		List<T> toSort = Lists.newArrayList(content);
		Collections.sort(toSort, byPositionInList);
		return toSort;
	}
	
	@Override
	public List<Brand> executeBrandQuery(ContentQuery query) {
		
		if (MatchesNothing.isEquivalentTo(query)) {
			return Collections.emptyList();
		}
		
		Maybe<ContentQuery> playlistQuery = splitter.retain(query, playlistAttributes);
		
		if (playlistQuery.isNothing()) {
			return executeBrandQueryInternal(query);
		}
		
		List<Playlist> playlists = roughSearch.dehydratedPlaylistsMatching(playlistQuery.requireValue());
		
		if (playlists.isEmpty()) {
			return Collections.emptyList();
		}
		
		Maybe<ContentQuery> everythingElse = splitter.discard(query, playlistAttributes);

		return executeBrandQueryInternal(createContainedInPlaylistQuery(everythingElse, playlists, Attributes.PLAYLIST_URI, query.getSelection()));
	}

	private List<Brand> executeBrandQueryInternal(ContentQuery query) {
		
		Maybe<ContentQuery> brandQuery = splitter.retain(query, (Set) Sets.newHashSet(Brand.class, Playlist.class));
		Maybe<ContentQuery> itemQuery = splitter.discard(query, (Set) Sets.newHashSet(Brand.class, Playlist.class));

		if (brandQuery.isNothing() && itemQuery.isNothing()) {
			throw new IllegalArgumentException("Query is too broad");
		}
		
		List<Brand> brands = brandQuery.isNothing() ? loadBrandsFromItems(itemQuery) : loadBrandsAndFilterItems(brandQuery, itemQuery);
		
		if (brands.isEmpty()) {
			return Collections.emptyList();
		}
		
		Maybe<AttributeQuery<?>> brandUriQuery = QueryFragmentExtractor.extract(query, Sets.<Attribute<?>>newHashSet(Attributes.BRAND_URI));
		
		brands = filterBrands(query, brands, !(brandUriQuery.hasValue() && !filterUriQueries));
		
		// Filter out subplaylists that don't match if the query was not by uri or curie
		if (itemQuery.hasValue() && (brandUriQuery.isNothing() || filterUriQueries)) {
			return filterEmpty(brands);
		}
		
		if (brandUriQuery.hasValue()) {
			return sort(brands, (List<String>) brandUriQuery.requireValue().getValue());
		}
		
		return brands;
	}

	private List<Brand> loadBrandsAndFilterItems(Maybe<ContentQuery> brandQuery, Maybe<ContentQuery> itemQuery) {
		List<Brand> brands = roughSearch.dehydratedBrandsMatching(brandQuery.requireValue());
		if (brands.isEmpty()) {
			return Lists.newArrayList();
		}
		if (brands.size() > MAX_INTERMEDIATE_BRANDS) {
			throw new IllegalStateException("Query matched too many brands");
		}
		List<Item> items = executeItemQueryInternal(createContainedInPlaylistQuery(itemQuery, brands, Attributes.BRAND_URI, null));
		hydratePlaylists(brands, items, null);
		return brands;
	}

	private List<Brand> loadBrandsFromItems(Maybe<ContentQuery> itemQuery) {
		List<Item> items = executeItemQueryInternal(itemQuery.requireValue());
		if (items.isEmpty()) {
			return Lists.newArrayList();
		}
		List<String> brandUris = Lists.newArrayList();
		for (Item item : items) {
			if (item instanceof Episode) {
				Episode episode = (Episode) item;
				if (episode.getBrand() != null) {
					brandUris.add(episode.getBrand().getCanonicalUri());
				}
			}
		}
		List<Brand> brands = roughSearch.dehydratedBrandsMatching(new ContentQuery(Attributes.BRAND_URI.createQuery(Operators.EQUALS, brandUris)));
		hydratePlaylists(brands, items, null);
		return brands;
	}


	@Override
	public List<Playlist> executePlaylistQuery(ContentQuery query) {
		if (MatchesNothing.isEquivalentTo(query)) {
			return Collections.emptyList();
		}
		
		Maybe<ContentQuery> playlistQuery = splitter.retain(query, playlistAttributes);
		Maybe<ContentQuery> subElementQuery = splitter.discard(query, playlistAttributes);
		
		if (playlistQuery.isNothing()) {
			throw new IllegalArgumentException("Query is too broad");
		}
		
		List<Playlist> playlists = roughSearch.dehydratedPlaylistsMatching(playlistQuery.requireValue());

		if (playlists.isEmpty()) {
			return Collections.emptyList();
		}
		
		ContentQuery subElementsContainedIn = createContainedInPlaylistQuery(subElementQuery, playlists, Attributes.PLAYLIST_URI, null);
		
		
		List<Item> items = executeItemQueryInternal(subElementsContainedIn);
		List<Brand> brands = executeBrandQueryInternal(subElementsContainedIn);
		
		hydratePlaylists(playlists, items, brands);
		
		Maybe<AttributeQuery<?>> playlistUriQuery = QueryFragmentExtractor.extract(query, Sets.<Attribute<?>>newHashSet(Attributes.PLAYLIST_URI));
		
		return filterPlaylists(query, playlists, !(playlistUriQuery.hasValue() && !filterUriQueries));
	}

	private List<Brand> filterEmpty(List<Brand> brands) {
		List<Brand> filtered = Lists.newArrayList();
		for (Brand brand : brands) {
			if (!brand.getPlaylists().isEmpty() || !brand.getItems().isEmpty()) {
				filtered.add(brand);
			}
		}
		return filtered;
	}

	private void hydratePlaylists(List<? extends Playlist> playlists, List<Item> subItems, List<? extends Playlist> subPlaylists) {
		Map<String, Item> itemLookup = toMapByUri(subItems);
		
		Map<String, ? extends Playlist> playlistLookup = null;
		if (subPlaylists != null) {
			 playlistLookup = toMapByUri(subPlaylists);
		}
		
		for (Playlist playlist : playlists) {
			List<String> itemUris = Lists.newArrayList(playlist.getItemUris());
			playlist.setItems(Lists.<Item>newArrayList());
			for (String itemUri : itemUris) {
				Item item = itemLookup.get(itemUri);
				if (item != null) {
					playlist.addItem(item);
				}
			}
			
			if (subPlaylists != null) {
				List<String> subPlaylistUris = Lists.newArrayList(playlist.getPlaylistUris());
				playlist.setPlaylists(Lists.<Playlist>newArrayList());
				for (String subPlaylistUri : subPlaylistUris) {
					Playlist subPlaylist = playlistLookup.get(subPlaylistUri);
					if (subPlaylist != null) {
						playlist.addPlaylist(subPlaylist);
					}
				}
			}
		}
	}

	private ContentQuery createContainedInPlaylistQuery(Maybe<ContentQuery> subElementQuery, List<? extends Playlist> playlists, Attribute<String> attribute, Selection selection) {
		List<AtomicQuery> operands = Lists.newArrayListWithCapacity(playlists.size());
		Set<String> playlistUris = Sets.newHashSet();
		for (Playlist playlist : playlists) {
			playlistUris.add(playlist.getCanonicalUri());
		}
		operands.add(attribute.createQuery(Operators.EQUALS, playlistUris));
		
		if (subElementQuery.hasValue()) {
			operands.addAll(subElementQuery.requireValue().operands());
		}
		return new ContentQuery(operands, selection);
	}
	
		
	/** 
	 * Util method for converting a list of Descriptions into a lookup-by-uri map.
	 */
	private <T extends Description> Map<String, T> toMapByUri(List<T> elems) {
		Map<String, T> itemLookup = Maps.newHashMap();
		for (T elem : elems) {
			itemLookup.put(elem.getCanonicalUri(), elem);
		}
		return itemLookup;
	}
	
	private List<Item> filterItems(ContentQuery query, List<Item> items, boolean removeItemsThatDontMatch) {
		return trimmer.trimItems(items, query, removeItemsThatDontMatch);
	}
	
	private List<Brand> filterBrands(ContentQuery query, List<Brand> brands, boolean removeItemsThatDontMatch) {
		return trimmer.trimBrands(brands, query, removeItemsThatDontMatch);
	}
	
	private List<Playlist> filterPlaylists(ContentQuery query, List<Playlist> playlists, boolean removeItemsThatDontMatch) {
		return trimmer.trimPlaylists(playlists, query, removeItemsThatDontMatch);
	}
	
	public void setFilterUriQueries(boolean filterUriQueries) {
		this.filterUriQueries = filterUriQueries;
	}
}
