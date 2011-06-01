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

import java.util.List;

import org.apache.commons.beanutils.PropertyUtils;
import org.atlasapi.content.criteria.AttributeQuery;
import org.atlasapi.content.criteria.BooleanAttributeQuery;
import org.atlasapi.content.criteria.ContentQuery;
import org.atlasapi.content.criteria.DateTimeAttributeQuery;
import org.atlasapi.content.criteria.EnumAttributeQuery;
import org.atlasapi.content.criteria.IntegerAttributeQuery;
import org.atlasapi.content.criteria.MatchesNothing;
import org.atlasapi.content.criteria.QueryVisitor;
import org.atlasapi.content.criteria.StringAttributeQuery;
import org.atlasapi.content.criteria.operator.BooleanOperatorVisitor;
import org.atlasapi.content.criteria.operator.DateTimeOperatorVisitor;
import org.atlasapi.content.criteria.operator.EnumOperatorVisitor;
import org.atlasapi.content.criteria.operator.IntegerOperatorVisitor;
import org.atlasapi.content.criteria.operator.Operators.After;
import org.atlasapi.content.criteria.operator.Operators.Before;
import org.atlasapi.content.criteria.operator.Operators.Beginning;
import org.atlasapi.content.criteria.operator.Operators.Equals;
import org.atlasapi.content.criteria.operator.Operators.GreaterThan;
import org.atlasapi.content.criteria.operator.Operators.LessThan;
import org.atlasapi.content.criteria.operator.StringOperatorVisitor;
import org.atlasapi.media.entity.Identified;
import org.joda.time.DateTime;

class InMemoryQueryResultChecker  {

	private final Identified target;

	public InMemoryQueryResultChecker(Identified target) {
		this.target = target;
	}
	
	public boolean check(ContentQuery query) {

		 List<Boolean> checked = query.accept(new QueryVisitor<Boolean>() {
			
			@Override
			@SuppressWarnings("unchecked")
			public Boolean visit(DateTimeAttributeQuery query) {
				if (!shouldApplyTo(query)) {
					return true;
				}
				final DateTime lhs =  (DateTime) valueOfBean(query);
				final List<DateTime> values =  (List<DateTime>) query.getValue();
				
				if (lhs == null) {
					return false;
				}
				for (final DateTime value : values) {
					boolean result =  query.accept(new DateTimeOperatorVisitor<Boolean>() {
						
						@Override
						public Boolean visit(Before before) {
							return lhs.isBefore(value);
						}
						
						@Override
						public Boolean visit(After after) {
							return lhs.isAfter(value);
						}

						@Override
						public Boolean visit(Equals equals) {
							throw new UnsupportedOperationException();
						}
					});
					if (result) {
						return true;
					}
				}
				return false;
			}
			
			@SuppressWarnings("unchecked")
			@Override
			public Boolean visit(final EnumAttributeQuery<?> query) {
				if (!shouldApplyTo(query)) {
					return true;
				}
				final Enum<?> lhs = (Enum<?>) valueOfBean(query);
				if (lhs == null) {
					return false;
				}
				
				final List<Enum<?>> values = (List<Enum<?>>) query.getValue();
				
				for (final Enum<?> value : values) {
					boolean result =  query.accept(new EnumOperatorVisitor<Boolean>() {
						
						@Override
						public Boolean visit(Equals equals) {
							return lhs.equals(value);
						}
					});
					if (result)  {
						return true;
					}
				}
				return false;
			}
			
			@Override
			@SuppressWarnings("unchecked")
			public Boolean visit(final BooleanAttributeQuery query) {
				if (!shouldApplyTo(query) || query.isUnconditionallyTrue()) {
					return true;
				}
				final Boolean lhs = (Boolean) valueOfBean(query);
				if (lhs == null) {
					return false;
				}

				final List<Boolean> values = (List<Boolean>) query.getValue();

				for (final Boolean value : values) {

					boolean result = query.accept(new BooleanOperatorVisitor<Boolean>() {
						@Override
						public Boolean visit(Equals equals) {
							return lhs.equals(value);
						}
					});
					
					if (result) {
						return true;
					}
				}
				return false;
			}
			
			@Override
			@SuppressWarnings("unchecked")
			public Boolean visit(final StringAttributeQuery query) {
				if (!shouldApplyTo(query)) {
					return true;
				}
				//don't check genres
				if (query.getAttribute().isCollectionOfValues()) {
					return true;
				}
				final String lhs = (String) valueOfBean(query);
				final List<String> values = (List<String>) query.getValue();

				if (lhs == null) {
					return false;
				}
				
				for (final String value : values) {
					boolean result =  query.accept(new StringOperatorVisitor<Boolean>() {
						
						@Override
						public Boolean visit(Beginning beginning) {
							return lhs.startsWith(value);
						}
						
						@Override
						public Boolean visit(Equals equals) {
							return lhs.equals(value);
						}
					});
					if (result) {
						return true;
					}
				}
				return false;
			}
		
			@Override
			@SuppressWarnings("unchecked")
			public Boolean visit(final IntegerAttributeQuery query) {
				if (!shouldApplyTo(query)) {
					return true;
				}
				final Integer lhs = (Integer) valueOfBean(query);
				final List<Integer> values = (List<Integer>) query.getValue();
				
				if (lhs == null) {
					return false;
				}
				
				for (final Integer value : values) {
					boolean result =  query.accept(new IntegerOperatorVisitor<Boolean>() {

						@Override
						public Boolean visit(GreaterThan greaterThan) {
							return lhs > value;
						}

						@Override
						public Boolean visit(LessThan lessThan) {
							return lhs < value;
						}

						@Override
						public Boolean visit(Equals equals) {
							return lhs.equals(value);
						}
					});
					
					if (result) {
						return true;
					}
				}
				return false;
			}				
			
			private Object valueOfBean(final AttributeQuery<?> query) {
				try {
					return PropertyUtils.getSimpleProperty(target, query.getAttribute().javaAttributeName());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			private boolean shouldApplyTo(AttributeQuery<?> query) {
				return target.getClass().isAssignableFrom(query.getAttribute().target()) || query.getAttribute().target().isAssignableFrom(target.getClass());
			}
			
			@Override
			public Boolean visit(MatchesNothing noOp) { 
				return false;
			}
		});
		 
		return !checked.contains(Boolean.FALSE);
	}
}
