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



import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.odata4j.core.ImmutableList;
import org.odata4j.core.OEntity;
import org.odata4j.core.OEntityKey;
import org.odata4j.edm.EdmDataServices;
import org.odata4j.edm.EdmEntitySet;
import org.odata4j.edm.EdmEntityType;
import org.odata4j.edm.EdmProperty;
import org.odata4j.edm.EdmSchema;
import org.odata4j.edm.EdmType;
import org.odata4j.producer.EntitiesResponse;
import org.odata4j.producer.EntityQueryInfo;
import org.odata4j.producer.EntityResponse;
import org.odata4j.producer.ODataProducer;
import org.odata4j.producer.QueryInfo;

import com.temenos.interaction.core.MultivaluedMapImpl;
import com.temenos.interaction.core.command.InteractionContext;
import com.temenos.interaction.core.command.InteractionException;
import com.temenos.interaction.core.command.InteractionProducerException;
import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.resource.EntityResource;

public class TestGETEntitiesCommand {

	@Test
	public void testExecuteForQueryOptionParsingErrors() {
		// invalid value for $top
		MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl<String>();
		queryParams.add("$top", "foo");
		InteractionContext mockContext = createInteractionContext("MyEntity", queryParams);
		ODataProducer mockProducer = createMockODataProducer("MyEntity", "Edm.String");
		GETEntitiesCommand command = new GETEntitiesCommand(mockProducer);
		
		try {
			command.execute(mockContext);
			fail("InteractionException must be thrown");
		} catch (InteractionException e) {
			assertEquals(Status.BAD_REQUEST,e.getHttpStatus());
		}
		
		// invalid value for $skip
		queryParams = new MultivaluedMapImpl<String>();
		queryParams.add("$skip", "foo");
		mockContext = createInteractionContext("MyEntity", queryParams);
		command = new GETEntitiesCommand(mockProducer);
		
		try {
			command.execute(mockContext);
			fail("InteractionException must be thrown");
		} catch (InteractionException e) {
			assertEquals(Status.BAD_REQUEST,e.getHttpStatus());
		}
	}
	
	@Test	
	public void testExecuteWithQueryParams() throws InteractionException {
		MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl<String>();
		queryParams.add("$filter", "a eq 'b'");
		queryParams.add("$select", "a");
		queryParams.add("MYPARAM", "MYVALUE");
		
		InteractionContext mockContext = createInteractionContext("MyEntity", queryParams);
		ODataProducer mockProducer = createMockODataProducer("MyEntity", "Edm.String");
		GETEntitiesCommand command = new GETEntitiesCommand(mockProducer);
			
		command.execute(mockContext);
		
		verify(mockProducer).getEntities(any(String.class), argThat(new ArgumentMatcher<QueryInfo>() {
		
			@Override
			public boolean matches(Object argument) {
				boolean result = false;
				
				if(argument instanceof QueryInfo) {
					QueryInfo queryInfo = (QueryInfo)argument;
																	
					Map<String,String> customOptions = queryInfo.customOptions;
					
					if("a eq 'b'".equals(customOptions.get("$filter"))) {
						if("a".equals(customOptions.get("$select"))) {
						    if("MYVALUE".equals(customOptions.get("MYPARAM"))) {
	                            result = true;
	                        }
						}   
					}
				}
				
				return result;
			}			
		}));	
	}
	
	@Test
	public void testExecuteQueryNoMandatoryInputs() {
		MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl<String>();
		InteractionContext mockContext = createInteractionContext("MyEntity", queryParams);
		ODataProducer mockProducer = createMockODataProducerException("MyEntity", "Edm.String");
		GETEntitiesCommand command = new GETEntitiesCommand(mockProducer);

		try {
			command.execute(mockContext);
			fail("InteractionException must be thrown");
		} catch (InteractionException e) {
			assertEquals(Status.BAD_REQUEST, e.getHttpStatus());
			assertNotNull(mockContext.getResource());
			assertEquals(mockContext.getResource().getEntityName(), "Errors");
		}
	}

	private ODataProducer createMockODataProducerException(String entityName, String keyTypeName) {
		ODataProducer mockProducer = mock(ODataProducer.class);
		List<String> keys = new ArrayList<String>();
		keys.add("MyId");
		List<EdmProperty.Builder> properties = new ArrayList<EdmProperty.Builder>();
		EdmProperty.Builder ep = EdmProperty.newBuilder("MyId").setType(new MyEdmType(keyTypeName));
		properties.add(ep);
		EdmEntityType.Builder eet = EdmEntityType.newBuilder().setNamespace("MyNamespace").setAlias("MyAlias").setName(entityName).addKeys(keys).addProperties(properties);
		EdmEntitySet.Builder ees = EdmEntitySet.newBuilder().setName(entityName).setEntityType(eet);
		EdmSchema.Builder es = EdmSchema.newBuilder().setNamespace("MyNamespace");
		List<EdmEntityType> mockEntityTypes = new ArrayList<EdmEntityType>();
		mockEntityTypes.add(eet.build());
		List<EdmSchema> mockSchemas = new ArrayList<EdmSchema>();
		mockSchemas.add(es.build());
		ImmutableList<EdmSchema> mockSchemaList = ImmutableList.copyOf(mockSchemas);
		EdmDataServices mockEDS = mock(EdmDataServices.class);
		when(mockEDS.getEdmEntitySet((EdmEntityType) any())).thenReturn(ees.build());
		when(mockEDS.getEntityTypes()).thenReturn(mockEntityTypes);
		when(mockEDS.findEdmEntityType(anyString())).thenReturn(eet.build());
		when(mockEDS.getSchemas()).thenReturn(mockSchemaList);
		when(mockProducer.getMetadata()).thenReturn(mockEDS);
		EntityResponse mockEntityResponse = mock(EntityResponse.class);
		OEntity oe = mock(OEntity.class);
		when(oe.getEntityType()).thenReturn(eet.build());
		when(oe.getEntitySetName()).thenReturn(ees.build().getName());
		when(mockEntityResponse.getEntity()).thenReturn(oe);
		when(mockProducer.getEntity(anyString(), any(OEntityKey.class), any(EntityQueryInfo.class))).thenReturn(mockEntityResponse);
		InteractionProducerException mockErrorProducer = mock(InteractionProducerException.class);
		EntityResource<?> entityResource1 = mock(EntityResource.class);
		doReturn(entityResource1).when(mockErrorProducer).getEntityResource();
		doReturn("Errors").when(entityResource1).getEntityName();
		when(mockErrorProducer.getHttpStatus()).thenReturn(Status.BAD_REQUEST);
		when(mockProducer.getEntities(any(String.class), any(QueryInfo.class))).thenThrow(mockErrorProducer);
		return mockProducer;
	}

	private ODataProducer createMockODataProducer(String entityName, String keyTypeName) {
		ODataProducer mockProducer = mock(ODataProducer.class);
		List<String> keys = new ArrayList<String>();
		keys.add("MyId");
		List<EdmProperty.Builder> properties = new ArrayList<EdmProperty.Builder>();
		EdmProperty.Builder ep = EdmProperty.newBuilder("MyId").setType(new MyEdmType(keyTypeName));
		properties.add(ep);
		EdmEntityType.Builder eet = EdmEntityType.newBuilder().setNamespace("MyNamespace").setAlias("MyAlias").setName(entityName).addKeys(keys).addProperties(properties);
		EdmEntitySet.Builder ees = EdmEntitySet.newBuilder().setName(entityName).setEntityType(eet);
		EdmSchema.Builder es = EdmSchema.newBuilder().setNamespace("MyNamespace");

		List<EdmEntityType> mockEntityTypes = new ArrayList<EdmEntityType>();
		mockEntityTypes.add(eet.build());
		List<EdmSchema> mockSchemas = new ArrayList<EdmSchema>();
		mockSchemas.add(es.build());
		ImmutableList<EdmSchema> mockSchemaList = ImmutableList.copyOf(mockSchemas);

		EdmDataServices mockEDS = mock(EdmDataServices.class);
		when(mockEDS.getEdmEntitySet((EdmEntityType) any())).thenReturn(ees.build());
		when(mockEDS.getEntityTypes()).thenReturn(mockEntityTypes);
		when(mockEDS.findEdmEntityType(anyString())).thenReturn(eet.build());
		when(mockEDS.getSchemas()).thenReturn(mockSchemaList);
		when(mockProducer.getMetadata()).thenReturn(mockEDS);
		
		EntityResponse mockEntityResponse = mock(EntityResponse.class);
		OEntity oe = mock(OEntity.class);
		when(oe.getEntityType()).thenReturn(eet.build());
		when(oe.getEntitySetName()).thenReturn(ees.build().getName());
		when(mockEntityResponse.getEntity()).thenReturn(oe);
		when(mockProducer.getEntity(anyString(), any(OEntityKey.class), any(EntityQueryInfo.class))).thenReturn(mockEntityResponse);
		
		EntitiesResponse mockEntityResponse2 = mock(EntitiesResponse.class);
		
		when(mockProducer.getEntities(any(String.class), any(QueryInfo.class))).thenReturn(mockEntityResponse2);
		
        return mockProducer;
	}
	
	@SuppressWarnings("unchecked")
	private InteractionContext createInteractionContext(String entity, MultivaluedMap<String, String> queryParams) {
		ResourceState resourceState = mock(ResourceState.class);
		when(resourceState.getEntityName()).thenReturn(entity);
        InteractionContext ctx = new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), mock(MultivaluedMap.class), queryParams ,resourceState, mock(Metadata.class));
        return ctx;
	}

	class MyEdmType extends EdmType {
		public MyEdmType(String name) {
			super(name);
		}
		public boolean isSimple() { return false; }
	}
}
