package com.temenos.interaction.commands.odata;

/*
 * #%L
 * interaction-commands-odata
 * %%
 * Copyright (C) 2012 - 2013 Temenos Holdings N.V.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */


import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;

import org.odata4j.core.OEntities;
import org.odata4j.core.OEntity;
import org.odata4j.core.OEntityKey;
import org.odata4j.core.OProperties;
import org.odata4j.core.OProperty;
import org.odata4j.edm.EdmDataServices;
import org.odata4j.edm.EdmEntitySet;
import org.odata4j.edm.EdmEntityType;
import org.odata4j.edm.EdmProperty;
import org.odata4j.edm.EdmSimpleType;
import org.odata4j.producer.ODataProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.temenos.interaction.core.command.InteractionContext;
import com.temenos.interaction.core.command.InteractionException;
import com.temenos.interaction.core.entity.Entity;
import com.temenos.interaction.core.entity.EntityProperties;
import com.temenos.interaction.core.entity.EntityProperty;
import com.temenos.interaction.core.hypermedia.ActionPropertyReference;
import com.temenos.interaction.core.resource.CollectionResource;
import com.temenos.interaction.core.resource.EntityResource;
import com.temenos.interaction.core.resource.MetaDataResource;
import com.temenos.interaction.core.resource.ResourceTypeHelper;
import com.temenos.interaction.odataext.ODataHelper;

public class CommandHelper {
	private final static Logger logger = LoggerFactory.getLogger(CommandHelper.class);
	private static Pattern parameterPattern = Pattern.compile("\\{(.*?)\\}");
	
	/**
	 * To handle generic case where user do not know the type, here we will be looking for a type
	 * and then generate EntityResource<E>
	 * @param entity
	 * @return EntityResource<E>
	 */
	public static<E> EntityResource<E> createEntityResource(E entity) {
		GenericEntity<E> ge = new GenericEntity<E>(entity) {};
		Type t = com.temenos.interaction.core.command.CommandHelper.getEffectiveGenericType(ge.getType(), entity);
		if(ResourceTypeHelper.isType(ge.getRawType(), t, OEntity.class, OEntity.class)) {
			OEntity te = (OEntity) entity;
			String entityName = te != null && te.getEntityType() != null ? te.getEntityType().getName() : null;
			return com.temenos.interaction.core.command.CommandHelper.createEntityResource(entityName, entity, OEntity.class);
		} else if(ResourceTypeHelper.isType(ge.getRawType(), t, Entity.class, Entity.class)) {
			Entity te = (Entity) entity;
			String entityName = te != null ? te.getName() : null; 
			return com.temenos.interaction.core.command.CommandHelper.createEntityResource(entityName, entity, Entity.class);
		} else {
			// Call the generic and lets see what happens
			return com.temenos.interaction.core.command.CommandHelper.createEntityResource(entity, null);
		}
	}
	
	
	/**
	 * To help converting Entity object into EntityResource as we are no longer extending the core CommandHelper
	 * @param entity
	 * @return EntityResource<Entity> EntityResource of parameter type Entity
	 */
	public static EntityResource<Entity> createEntityResource(Entity entity) {
		return com.temenos.interaction.core.command.CommandHelper.createEntityResource(entity);
	}
	
	/**
	 * Create an OData entity resource (entry)
	 * @param e OEntity
	 * @return entity resource EntityResource of parameter type OEntity
	 */
	public static EntityResource<OEntity> createEntityResource(OEntity e) {
		String entityName = e != null && e.getEntityType() != null ? e.getEntityType().getName() : null;
		return com.temenos.interaction.core.command.CommandHelper.createEntityResource(entityName, e, OEntity.class);
	}

	/**
	 * Create an OData collection resource (feed)
	 * @param entitySetName Entity set name
	 * @param entities List of OData entities
	 * @return collection resource
	 */
	public static CollectionResource<OEntity> createCollectionResource(String entitySetName, List<OEntity> entities) {
		List<EntityResource<OEntity>> subResources = new ArrayList<EntityResource<OEntity>>();
		for (OEntity entity : entities)
			subResources.add(createEntityResource(entity));
		return new CollectionResource<OEntity>(entitySetName, subResources) {};
	}

	/**
	 * Create an OData service document (atomsvc)
	 * @param metadata Edmx
	 * @return Service document
	 */
	public static EntityResource<EdmDataServices> createServiceDocumentResource(EdmDataServices metadata) {
		return new EntityResource<EdmDataServices>(metadata) {};	
	}

	/**
	 * Create an OData metadata document (edmx)
	 * @param metadata Edmx
	 * @return metadata resource
	 */
	public static MetaDataResource<EdmDataServices> createMetaDataResource(EdmDataServices metadata) {
		return new MetaDataResource<EdmDataServices>(metadata) {};	
	}
	
	/**
	 * Create an OEntityKey instance for the specified entity id
	 * @param edmDataServices edmDataServices
	 * @param entity Entity set name
	 * @param id Id
	 * @return An OEntityKey instance
	 * @throws Exception Error creating key 
	 */
	public static OEntityKey createEntityKey(EdmDataServices edmDataServices, String entitySetName, String id) throws Exception {
		return createEntityKey(edmDataServices.getEdmEntitySet(entitySetName), id);
	}	
	
	/**
	 * Create an OEntityKey instance for the specified entity id
	 * @param entitySet entitySet
	 * @param id Id
	 * @return An OEntityKey instance
	 * @throws Exception Error creating key 
	 */
	public static OEntityKey createEntityKey(EdmEntitySet entitySet, String id) throws Exception {
		//Lookup type of entity key (simple keys only)
		String keyType = null;
		if(entitySet != null) {
			EdmEntityType entityType = entitySet.getType();
			List<String> keys = entityType.getKeys();
			if(keys.size() == 1) {
				EdmProperty prop = entityType.findDeclaredProperty(keys.get(0));
				if(prop != null && prop.getType() != null) {
					keyType = prop.getType().getFullyQualifiedTypeName();
				}
			}
		}
		assert(keyType != null) : "Should not be possible to get this far and find no key type";
		
		//Create an entity key
		OEntityKey key = null;
		try {
			if (keyType.equals("Edm.Int64")) {
				key = OEntityKey.parse(id);
			} else if(keyType.equals("Edm.Int32")) {
				key = OEntityKey.parse(id);
			} else if(keyType.equals("Edm.DateTime")) {
				key = OEntityKey.parse(id);
			} else if(keyType.equals("Edm.Time")) {
				key = OEntityKey.parse(id);
			} else if(keyType.equals("Edm.String")) {
				key = OEntityKey.parse(id);
			}
		} catch (Exception e) {
			logger.debug("Entity key type " + keyType + " is not supported by CommandHelper, trying OEntityKey.parse.",e);
		}
		// could not parse the key, have one last attempt with OEntityKey create
		if (key == null) {
			try {
				if (keyType.equals("Edm.Int64")) {
					key = OEntityKey.create(Long.parseLong(id));
				} else if(keyType.equals("Edm.Int32")) {
					key = OEntityKey.create(Integer.parseInt(id));
				} else {
					key = OEntityKey.create(id);
				}					
			} catch (Exception e) {
				logger.error("OEntityKey.parse failed to parse id [" + id + "]", e);
			}
		}
		if (key == null)
			throw new Exception("Entity key type " + id + " is not supported.");
		return key;
	}
	
	/**
	 * Returns the entity set holding the specified entity (type) name
	 * @param entityName entity type name
	 * @param edmDataServices metadata
	 * @return entity set
	 * @throws Exception if entity set cannot be found
	 */
	public static EdmEntitySet getEntitySet(String entityName, EdmDataServices edmDataServices) throws Exception {
		return ODataHelper.getEntitySet(entityName, edmDataServices);
	}
	
	/**
	 * Returns a view action property
	 * This method will try to replace template parameters "{param}"
	 * with query parameters if available 
	 * @param ctx interaction context
	 * @param property action property
	 * @return property value
	 */
	public static String getViewActionProperty(InteractionContext ctx, String property) {
		String prop = null;
		if(ctx.getCurrentState().getViewAction() != null) {
			Properties properties = ctx.getCurrentState().getViewAction().getProperties();
			if(properties != null && properties.containsKey(property)) {
				//Get the specified action property
				prop = getActionProperty(property, properties, ctx.getQueryParameters());
				
				//Fill in template parameters
				if(prop != null) {
					Matcher m = parameterPattern.matcher(prop);
					while(m.find()) {
						String templateParam = m.group(1);			//e.g. code
						if(ctx.getQueryParameters().containsKey(templateParam)) {
							prop = prop.replaceAll("\\{" + templateParam + "\\}", ctx.getQueryParameters().getFirst(templateParam));
						}
						else if(ctx.getPathParameters().containsKey(templateParam)) {
							prop = prop.replaceAll("\\{" + templateParam + "\\}", ctx.getPathParameters().getFirst(templateParam));
						}
					}
				}
			}
		}
		return prop != null && !prop.equals(property) ? prop : null;
	}
	
	/**
	 * Obtain the specified action property.
	 * An action property can either contain a simple value or reference a link property.
	 * If it is the latter it will obtain the referenced value stored in the link property.
	 * e.g. GetEntities filter=myfilter and myfilter="CreditAcctNo eq '{Acc}'", Acc="CreditAcctNo"
	 * => filter=CreditAcctNo eq '{CreditAcctNo}'
	 * @param propertyName Action property name
	 * @param actionProperties Action properties
	 * @param queryParameters Query parameters (keys)
	 * @return Action property string
	 */
	protected static String getActionProperty(String propertyName, Properties actionProperties, MultivaluedMap<String, String> queryParameters) {
		Object propObj = actionProperties.get(propertyName);
		if(propObj != null && propObj instanceof ActionPropertyReference) {
			ActionPropertyReference propRef = (ActionPropertyReference) propObj;
			Set<String> queryParamKeys = queryParameters.keySet();

			if (queryParameters.containsKey(propRef.getKey())) {
				return queryParameters.getFirst(propRef.getKey());
			}
			
			String key = "_";
			for(String queryParamKey : queryParamKeys) {
				if(!queryParamKey.startsWith("$")) {		//Do not consider $filter, $select, etc.
					key += "_" + queryParamKey;
				}
			}
			return propRef.getProperty(key);
		}
		else {
			return actionProperties.get(propertyName).toString();		//e.g. fld eq '{code}'
		}		
	}
	
	/**
	 * Create a map containing the values we wish to pass as custom options to the OData producer 
	 * 
	 * @param ctx interaction context
	 * 
	 * @return Map containing the values we wish to pass as custom options to the OData producer
	 */
	static Map<String, String> populateCustomOptionsMap(InteractionContext ctx) {
		Map<String, String> customOptions = new HashMap<String,String>();
		
		// Capture all path parameters
		MultivaluedMap<String, String> pathParams = ctx.getPathParameters();
		
		if(pathParams != null) {
			for(Map.Entry<String,List<String>> entry: pathParams.entrySet()) {
				String parmName = entry.getKey();
				List<String> paramValues = entry.getValue();
				
				if(!paramValues.isEmpty()) {
					customOptions.put(parmName, paramValues.get(0));
				}
			}
		}
		
		// Capture all query parameters 
		MultivaluedMap<String, String> queryParams = ctx.getQueryParameters();
		
		if(queryParams != null) {
			for(Map.Entry<String,List<String>> entry: queryParams.entrySet()) {
				String parmName = entry.getKey();
				List<String> paramValues = entry.getValue();
				
				if(!paramValues.isEmpty()) {
					customOptions.put(parmName, paramValues.get(0));
				}
			}
		}
		
		return customOptions;
	}
	
	// TODO move this transformation up to where we have all the metadata, note the hacked hardcoded "Id"
	protected static OEntity createOEntityFromEntity(AbstractODataCommand command, ODataProducer producer, Entity entity, OEntityKey key) throws InteractionException {
		try {
			assert(entity != null);
			assert(entity.getName() != null);
			EdmEntitySet entitySet = command.getEdmEntitySet(entity.getName());
			EdmEntityType entityType = entitySet.getType();
					
			String id = null;
			EntityProperties entityProps = entity.getProperties();
			List<OProperty<?>> eProps = new ArrayList<OProperty<?>>();
			for (String propKey : entityProps.getProperties().keySet()) {
				EntityProperty prop = entityProps.getProperty(propKey);
				if (prop.getName().equals("Id")) {
					id = prop.getValue().toString();
				}
				if (entityType.findProperty(prop.getName()) != null) {
					EdmProperty eProp = entityType.findProperty(prop.getName());
					if (eProp.getType().equals(EdmSimpleType.STRING)) {
						eProps.add(OProperties.string(prop.getName(), prop.getValue().toString()));
					} else if (eProp.getType().equals(EdmSimpleType.INT32)) {
						eProps.add(OProperties.int32(prop.getName(), new Integer(prop.getValue().toString())));
					}
				}
			}
			if (id != null) {
				OEntityKey eKey = CommandHelper.createEntityKey(producer.getMetadata(), entity.getName(), id);				
				return OEntities.create(entitySet, eKey, eProps, null);
			} else if (key != null) {
				return OEntities.create(entitySet, key, eProps, null);
			} else {
				return OEntities.createRequest(entitySet, eProps, null);
			}
		} catch (Exception e) {
			throw new InteractionException(Status.INTERNAL_SERVER_ERROR, e);
		}
    }
	
}
