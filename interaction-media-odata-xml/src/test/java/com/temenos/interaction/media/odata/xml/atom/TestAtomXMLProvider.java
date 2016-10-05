package com.temenos.interaction.media.odata.xml.atom;

/*
 * #%L
 * interaction-media-odata-xml
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



import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.DifferenceListener;
import org.custommonkey.xmlunit.IgnoreTextAndAttributeValuesDifferenceListener;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Test;
import org.odata4j.core.ImmutableList;
import org.odata4j.core.OCollection;
import org.odata4j.core.OComplexObject;
import org.odata4j.core.OEntities;
import org.odata4j.core.OEntity;
import org.odata4j.core.OEntityKey;
import org.odata4j.core.OLink;
import org.odata4j.core.OLinks;
import org.odata4j.core.OProperties;
import org.odata4j.core.OProperty;
import org.odata4j.edm.EdmDataServices;
import org.odata4j.edm.EdmEntityContainer;
import org.odata4j.edm.EdmEntitySet;
import org.odata4j.edm.EdmEntityType;
import org.odata4j.edm.EdmProperty;
import org.odata4j.edm.EdmSchema;
import org.odata4j.edm.EdmSimpleType;
import org.odata4j.exceptions.NotFoundException;

import com.temenos.interaction.commands.odata.OEntityTransformer;
import com.temenos.interaction.core.ExtendedMediaTypes;
import com.temenos.interaction.core.MultivaluedMapImpl;
import com.temenos.interaction.core.command.CommandHelper;
import com.temenos.interaction.core.entity.Entity;
import com.temenos.interaction.core.entity.EntityMetadata;
import com.temenos.interaction.core.entity.EntityProperties;
import com.temenos.interaction.core.entity.EntityProperty;
import com.temenos.interaction.core.entity.GenericError;
import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.entity.vocabulary.Vocabulary;
import com.temenos.interaction.core.entity.vocabulary.terms.TermComplexGroup;
import com.temenos.interaction.core.entity.vocabulary.terms.TermComplexType;
import com.temenos.interaction.core.entity.vocabulary.terms.TermIdField;
import com.temenos.interaction.core.entity.vocabulary.terms.TermValueType;
import com.temenos.interaction.core.hypermedia.Action;
import com.temenos.interaction.core.hypermedia.CollectionResourceState;
import com.temenos.interaction.core.hypermedia.EntityTransformer;
import com.temenos.interaction.core.hypermedia.Link;
import com.temenos.interaction.core.hypermedia.LinkGenerator;
import com.temenos.interaction.core.hypermedia.LinkGeneratorImpl;
import com.temenos.interaction.core.hypermedia.MethodNotAllowedException;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.ResourceStateMachine;
import com.temenos.interaction.core.hypermedia.Transformer;
import com.temenos.interaction.core.hypermedia.Transition;
import com.temenos.interaction.core.hypermedia.UriSpecification;
import com.temenos.interaction.core.resource.CollectionResource;
import com.temenos.interaction.core.resource.EntityResource;
import com.temenos.interaction.core.resource.MetaDataResource;
import com.temenos.interaction.core.resource.RESTResource;
import com.temenos.interaction.core.web.RequestContext;
import com.temenos.interaction.media.odata.xml.CustomError;
import com.temenos.interaction.media.odata.xml.Flight;
import com.temenos.interaction.media.odata.xml.IgnoreNamedElementsXMLDifferenceListener;
import com.temenos.interaction.odataext.entity.MetadataOData4j;

public class TestAtomXMLProvider {

	private final static String EXPECTED_XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?><entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\" xml:base=\"http://localhost:8080/responder/rest\"><id>http://localhost:8080/responder/restFlight('123')</id><title type=\"text\"></title><updated>2012-03-14T11:29:19Z</updated><author><name></name></author><category term=\"InteractionTest.Flight\" scheme=\"http://schemas.microsoft.com/ado/2007/08/dataservices/scheme\"></category><content type=\"application/xml\"><m:properties><d:id>1</d:id><d:flight>EI218</d:flight></m:properties></content></entry>";
	public final static String FLIGHT_ENTRY_XML = "FlightEntry.xml";
	public final static String FLIGHT_ENTRY_SIMPLE_XML = "FlightEntrySimple.xml";
	public final static String FLIGHT_COLLECTION_XML = "FlightsFeed.xml";
	public final static String ATOM_GENERIC_ERROR_ENTRY_XML = "AtomGenericErrorEntry.xml";
	public final static String ATOM_CUSTOM_ERROR_ENTRY_XML = "AtomCustomErrorEntry.xml";
	public final static String EMPTY_FUNDS_TRANSFERS_FEED_XML = "EmptyFundsTransfersFeed.xml";
	
	public class MockAtomXMLProvider extends AtomXMLProvider {
		public MockAtomXMLProvider(MetadataOData4j metadataOData4j) {
			this(metadataOData4j, mock(Metadata.class));
		}
		public MockAtomXMLProvider(MetadataOData4j metadataOData4j, Metadata metadata) {
			//super(null, metadata, new EntityTransformer());
			super(metadataOData4j, 
					metadata, 
					new ResourceStateMachine(new ResourceState("ServiceDocument", "ServiceDocument", new ArrayList<Action>(), "/")), 
					null);
		}
		public void setUriInfo(UriInfo uriInfo) {
			super.setUriInfo(uriInfo);
		}
	};

	private MetadataOData4j createMockMetadataOData4j(EdmDataServices mockEDS) {
		MetadataOData4j mockMetadataOData4j = mock(MetadataOData4j.class);
		when(mockMetadataOData4j.getMetadata()).thenReturn(mockEDS);
		return mockMetadataOData4j;
	}
	
	@Test
	public void testReadEntityResourceOEntity_XML() throws Exception {				
		List<Action> initialActions = new ArrayList<Action>();
		
		Action action = mock(Action.class);
		when(action.getMethod()).thenReturn("GET");
		
		initialActions.add(action);
		
		ResourceState initial = new ResourceState("initial_state", "initial_state", initialActions, "/", null, new UriSpecification("Initial", "/initial"));
		
		List<Action> serviceDocActions = new ArrayList<Action>();
		serviceDocActions.add(action);
		
		ResourceState serviceDoc = new ResourceState("SD", "ServiceDocument", serviceDocActions, "/");
		
		initial.addTransition(new Transition.Builder().method("GET").target(serviceDoc).build());
		
		List<Action> ftCommissionTypeActions = new ArrayList<Action>();
		ftCommissionTypeActions.add(action);
		
		CollectionResourceState resourceType = new CollectionResourceState("FtCommissionType", "FtCommissionTypes", ftCommissionTypeActions, "/oentitys", null, null);
		initial.addTransition(new Transition.Builder().method("GET").target(resourceType).build());
		ResourceStateMachine hypermediaEngine = new ResourceStateMachine(initial);
				
		Metadata metadata = new Metadata("hothouse-models");
		
		MetadataOData4j metadataOData4j = new MetadataOData4j(metadata, new ResourceStateMachine(serviceDoc));
		
		AtomXMLProvider ap = new AtomXMLProvider(metadataOData4j, metadata, hypermediaEngine, new OEntityTransformer());
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getPathParameters(false)).thenReturn(mock(MultivaluedMap.class));
		ap.setUriInfo(uriInfo);
		
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getAbsolutePath()).thenReturn(new URI("http://www.temenos.com/rest.svc/oentitys"));		
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc"));		
		ap.setUriInfo(mockUriInfo);
		
		Request request = mock(Request.class);
		when(request.getMethod()).thenReturn("GET");
		ap.setRequestContext(request);
				
  		InputStream in = null;
  		
  		EntityResource<OEntity> entity;
		try {
			in = getClass().getClassLoader().getResourceAsStream("issue193_entry_with_Bag.xml");
			
			GenericEntity<EntityResource<OEntity>> ge = new GenericEntity<EntityResource<OEntity>>(new EntityResource<OEntity>(null)) {
			};
			entity = ap.readFrom(RESTResource.class, ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, in);
		} finally {
			if(in != null) {
				in.close();	
			}
				
		}
		
		
		
		assertNotNull(entity);
		
		OEntity oEntity = entity.getEntity();
		assertNotNull(oEntity);
		
		assertEquals("FtCommissionTypes", oEntity.getEntitySetName());
		
		List<OProperty<?>> properties = oEntity.getProperties();
		assertNotNull(properties);
		
		boolean ftCommissionType_ShortDescrMvGroupFound = false;
		boolean ftCommissionType_CurrencyMvGroupFound = false;
		boolean txnCodeDrFound = false;
		boolean currencyFound = false;
		boolean upToAmtFound = false;
		
		for(OProperty<?> property: properties) {
			if("TxnCodeDr".equals(property.getName())) {
				assertEquals("639", property.getValue());				
				txnCodeDrFound = true;
			}
			
			if("FtCommissionType_ShortDescrMvGroup".equals(property.getName())) {
				OCollection<OComplexObject> tmpComplexType = (OCollection<OComplexObject>)property.getValue(); 
				assertNotNull(tmpComplexType.getType());
				OComplexObject tmpOComplexObj = tmpComplexType.iterator().next();
				assertEquals("Sec Default", tmpOComplexObj.getProperty("ShortDescr").getValue());
				
				ftCommissionType_ShortDescrMvGroupFound = true; 
			}
			
			if("FtCommissionType_CurrencyMvGroup".equals(property.getName())) {
				OCollection<OComplexObject> tmpComplexType = (OCollection<OComplexObject>)property.getValue();
				Iterator<OComplexObject> tmpComplexTypeIter = tmpComplexType.iterator();
				
				while(tmpComplexTypeIter.hasNext()) {
					OComplexObject tmpOComplexObj = tmpComplexTypeIter.next();
					List<OProperty<?>> tmpProperties = tmpOComplexObj.getProperties();
					
					for(OProperty<?> tmpProperty: tmpProperties) {
						if("Currency".equals(tmpProperty.getName()) && !currencyFound) {
							assertEquals("USD", tmpProperty.getValue());
							currencyFound = true;	
						}
						
						if("FtCommissionType_CalcTypeSvGroup".equals(tmpProperty.getName())) {
							ftCommissionType_CurrencyMvGroupFound = true;
							
							OCollection<OComplexObject> tmpComplexType2 = (OCollection<OComplexObject>)tmpProperty.getValue();
							Iterator<OComplexObject> tmpComplexTypeIter2 = tmpComplexType2.iterator();
							
							while(tmpComplexTypeIter2.hasNext() && !upToAmtFound) {
								OComplexObject tmpOComplexObj2 = tmpComplexTypeIter2.next();
								List<OProperty<?>> tmpProperties2 = tmpOComplexObj2.getProperties();
								
								for(OProperty<?> tmpProperty2: tmpProperties2) {
									if("UptoAmt".equals(tmpProperty2.getName())) {
										upToAmtFound = true;
										
										assertEquals("500000.00", tmpProperty2.getValue());
									}
								}
								
							}
						}						
					}
				}
			}
		}
		
		assertTrue(ftCommissionType_ShortDescrMvGroupFound);
		assertTrue(ftCommissionType_CurrencyMvGroupFound);
		assertTrue(txnCodeDrFound);
		assertTrue(currencyFound);
		assertTrue(upToAmtFound);
	}
	
	@Test
	public void testWriteEntityResourceOEntity_XML() throws Exception {
		EdmEntitySet ees = createMockEdmEntitySet();
		EdmDataServices mockEDS = createMockFlightEdmDataServices();
		EntityResource<OEntity> er = createMockEntityResourceOEntity(ees);
		
		when(mockEDS.getEdmEntitySet(any(EdmEntityType.class))).thenReturn(ees);
		
        //Wrap entity resource into a JAX-RS GenericEntity instance
		GenericEntity<EntityResource<OEntity>> ge = new GenericEntity<EntityResource<OEntity>>(er) {};

		//Create provider
		MockAtomXMLProvider p = new MockAtomXMLProvider(createMockMetadataOData4j(mockEDS));
		UriInfo uriInfo = mock(UriInfo.class);
		URI uri = new URI("http://localhost:8080/responder/rest");
		when(uriInfo.getBaseUri()).thenReturn(uri);
		p.setUriInfo(uriInfo);
		
		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");
		System.out.println(responseString);

		//Assert xml string but ignore text and attribute values
	    DifferenceListener myDifferenceListener = new IgnoreTextAndAttributeValuesDifferenceListener();
	    Diff myDiff = new Diff(responseString, EXPECTED_XML);
	    myDiff.overrideDifferenceListener(myDifferenceListener);
	    assertTrue(myDiff.similar());		
	}

	@Test
	public void testWriteEntityResourceOEntity_AtomXML() throws Exception {
		EdmEntitySet ees = createMockEdmEntitySet();
		EdmDataServices mockEDS = createMockFlightEdmDataServices();
		EntityResource<OEntity> er = createMockEntityResourceOEntity(ees);
		
		when(mockEDS.getEdmEntitySet(any(EdmEntityType.class))).thenReturn(ees);

        //Wrap entity resource into a JAX-RS GenericEntity instance
		GenericEntity<EntityResource<OEntity>> ge = new GenericEntity<EntityResource<OEntity>>(er) {};

		//Create provider
		MockAtomXMLProvider p = new MockAtomXMLProvider(createMockMetadataOData4j(mockEDS));
		UriInfo uriInfo = mock(UriInfo.class);
		URI uri = new URI("http://localhost:8080/responder/rest");
		when(uriInfo.getBaseUri()).thenReturn(uri);
		p.setUriInfo(uriInfo);
		
		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");

		//Assert xml string but ignore text and attribute values
	    DifferenceListener myDifferenceListener = new IgnoreTextAndAttributeValuesDifferenceListener();
	    Diff myDiff = new Diff(responseString, EXPECTED_XML);
	    myDiff.overrideDifferenceListener(myDifferenceListener);
	    assertTrue(myDiff.similar());		
	}
	
	private EdmEntitySet createMockEdmEntitySet() {
		// Create an entity set
		List<EdmProperty.Builder> eprops = new ArrayList<EdmProperty.Builder>();
		EdmProperty.Builder ep = EdmProperty.newBuilder("id").setType(EdmSimpleType.STRING);
		eprops.add(ep);
		EdmEntityType.Builder eet = EdmEntityType.newBuilder().setNamespace("InteractionTest").setName("Flight").addKeys(Arrays.asList("id")).addProperties(eprops);
		EdmEntitySet.Builder eesb = EdmEntitySet.newBuilder().setName("Flight").setEntityType(eet);
		return eesb.build();
	}
	
	private EdmDataServices createMockFlightEdmDataServices() {
		EdmDataServices mockEDS = mock(EdmDataServices.class);

		//Mock EdmDataServices
		List<String> keys = new ArrayList<String>();
		keys.add("MyId");
		List<EdmProperty.Builder> properties = new ArrayList<EdmProperty.Builder>();
		EdmProperty.Builder ep = EdmProperty.newBuilder("MyId").setType(EdmSimpleType.STRING);
		properties.add(ep);
		EdmEntityType.Builder eet = EdmEntityType.newBuilder().setNamespace("FlightModelModel").setAlias("MyAlias").setName("Flight").addKeys(keys).addProperties(properties);
		EdmEntitySet.Builder ees = EdmEntitySet.newBuilder().setName("Flight").setEntityType(eet);
		List<EdmEntityType.Builder> mockEntityTypes = new ArrayList<EdmEntityType.Builder>();
		mockEntityTypes.add(eet);
		List<EdmEntitySet.Builder> mockEntitySets = new ArrayList<EdmEntitySet.Builder>();
		mockEntitySets.add(ees);
		EdmEntityContainer.Builder eec = EdmEntityContainer.newBuilder().setName("MyEntityContainer").addEntitySets(mockEntitySets);
		List<EdmEntityContainer.Builder> mockEntityContainers = new ArrayList<EdmEntityContainer.Builder>();
		mockEntityContainers.add(eec);
		EdmSchema.Builder es = EdmSchema.newBuilder().setNamespace("MyNamespace").setAlias("MyAlias").addEntityTypes(mockEntityTypes).addEntityContainers(mockEntityContainers);
		List<EdmSchema> mockSchemas = new ArrayList<EdmSchema>();
		mockSchemas.add(es.build());
		when(mockEDS.getSchemas()).thenReturn(ImmutableList.copyOf(mockSchemas));
		when(mockEDS.findEdmEntitySet(anyString())).thenReturn(ees.build());
		when(mockEDS.getEdmEntitySet(any(EdmEntityType.class))).thenReturn(ees.build());
		
		return mockEDS;
	}
	
	private EdmDataServices createMockEdmDataServices(String entitySetName) {
		EdmDataServices edmDataServices = mock(EdmDataServices.class);
		EdmEntitySet entitySet = mock(EdmEntitySet.class);
		when(entitySet.getName()).thenReturn(entitySetName);
		when(edmDataServices.getEdmEntitySet(any(EdmEntityType.class))).thenReturn(entitySet);
		EdmEntityType entityType = mock(EdmEntityType.class);
		when(edmDataServices.findEdmEntityType(anyString())).thenReturn(entityType);
		return edmDataServices;
	}
	
	private EntityResource<Entity> createMockEntityResourceEntity(String name) {
		//Create an Entity
		EntityProperties properties = new EntityProperties();
		properties.setProperty(new EntityProperty("id", "1"));
		properties.setProperty(new EntityProperty("flight", "EI218"));
		Entity entity = new Entity(name, properties);
		return new EntityResource<Entity>(name, entity);
	}
	
	private EntityResource<OEntity> createMockEntityResourceOEntity(EdmEntitySet ees) {
	    return createMockEntityResourceOEntity(ees, null);
	}

	private EntityResource<OEntity> createMockEntityResourceOEntity(EdmEntitySet ees, String etag) {
		//Create an OEntity
		OEntityKey entityKey = OEntityKey.create("1");
		List<OProperty<?>> properties = new ArrayList<OProperty<?>>();
		properties.add(OProperties.string("id", "1"));
		properties.add(OProperties.string("flight", "EI218"));
		OEntity entity = OEntities.create(ees, null, entityKey, etag, properties, new ArrayList<OLink>());
		return new EntityResource<OEntity>(entity);
	}
	
	private Metadata createMockFlightMetadata() {
		Metadata mockMetadata = new Metadata("FlightModel");
		
		// Define vocabulary for this entity - minimum fields for an input
		EntityMetadata entityMetadata = new EntityMetadata("Flight");
		
		Vocabulary voc_id = new Vocabulary();
		voc_id.setTerm(new TermValueType(TermValueType.TEXT));
		voc_id.setTerm(new TermIdField(true));
		entityMetadata.setPropertyVocabulary("id", voc_id);
		
		Vocabulary voc_flight = new Vocabulary();
		voc_flight.setTerm(new TermValueType(TermValueType.TEXT));
		entityMetadata.setPropertyVocabulary("flight", voc_flight);
			
		// MvSv Group
		Vocabulary voc_MvSvGroup = new Vocabulary();
		voc_MvSvGroup.setTerm(new TermComplexType(true));
		entityMetadata.setPropertyVocabulary("MvSvGroup", voc_MvSvGroup);

		Stack<String> mvParentProperties = new Stack<String>();
		mvParentProperties.push("MvSvGroup");
		
		Vocabulary voc_MvSv = new Vocabulary();
		voc_MvSv.setTerm(new TermComplexGroup("MvSvGroup"));
		voc_MvSv.setTerm(new TermValueType(TermValueType.TEXT));
		entityMetadata.setPropertyVocabulary("MvSv", voc_MvSv, mvParentProperties.elements());
		
		Vocabulary voc_MvSvStartGroup = new Vocabulary();
		voc_MvSvStartGroup.setTerm(new TermComplexType(true));
		voc_MvSvStartGroup.setTerm(new TermComplexGroup("MvSvGroup"));
		entityMetadata.setPropertyVocabulary("MvSvStartGroup", voc_MvSvStartGroup, mvParentProperties.elements());
	
		Stack<String> svParentProperties = new Stack<String>();
		svParentProperties.push("MvSvGroup");
		svParentProperties.push("MvSvStartGroup");
		
		Vocabulary voc_MvSvStart = new Vocabulary();
		voc_MvSvStart.setTerm(new TermComplexGroup("MvSvStartGroup"));
		voc_MvSvStart.setTerm(new TermValueType(TermValueType.TEXT));
		entityMetadata.setPropertyVocabulary("MvSvStart", voc_MvSvStart, svParentProperties.elements());
		
		Vocabulary voc_MvSvEnd = new Vocabulary();
		voc_MvSvEnd.setTerm(new TermComplexGroup("MvSvStartGroup"));
		voc_MvSvEnd.setTerm(new TermValueType(TermValueType.TEXT));
		entityMetadata.setPropertyVocabulary("MvSvEnd", voc_MvSvEnd, svParentProperties.elements());

		mockMetadata.setEntityMetadata(entityMetadata);

		//Define a custom error entity
		EntityMetadata mdCustomError = new EntityMetadata("CustomError");
		Vocabulary vocCustomError = new Vocabulary();
		vocCustomError.setTerm(new TermValueType(TermValueType.TEXT));
		mdCustomError.setPropertyVocabulary("mycustomerror", vocCustomError);
		mockMetadata.setEntityMetadata(mdCustomError);
		
		return mockMetadata;
	}

	private Metadata createMockMetadata(String modelName) {
		Metadata metadata = mock(Metadata.class);
		when(metadata.getModelName()).thenReturn(modelName);
		return metadata;
	}
	
	private CollectionResource<Entity> createMockCollectionResourceEntity() {
		List<EntityResource<Entity>> erList = new ArrayList<EntityResource<Entity>>(); 
		erList.add(createMockEntityResourceEntity());
		
		CollectionResource<Entity> cr = new CollectionResource<Entity>("Flights", erList);
		cr.setLinks(new ArrayList<Link>());
		cr.setEntityName("Flight");
		return cr;
	}
	
	@SuppressWarnings("unchecked")
	private EntityResource<Entity> createMockEntityResourceEntity() {
		EntityResource<Entity> er = mock(EntityResource.class);

		// Create entity
		EntityProperties properties = new EntityProperties();
		properties.setProperty(new EntityProperty("id", "123"));
		properties.setProperty(new EntityProperty("flight", "EI218"));
		
		// Multi-value group with sub-value group
		List<EntityProperties> mvSvGroup = new ArrayList<EntityProperties>();
		EntityProperties mvSvGroup1 = new EntityProperties();
			mvSvGroup1.setProperty(new EntityProperty("MvSv", "mv sv 1:1"));
			List<EntityProperties> mvSvStartGroup1 = new ArrayList<EntityProperties>();
			// Sub-value group
				EntityProperties mvSvStartGroup1_1 = new EntityProperties();
					mvSvStartGroup1_1.setProperty(new EntityProperty("MvSvStart", "mv sv start 1:1"));
					mvSvStartGroup1_1.setProperty(new EntityProperty("MvSvEnd", "mv sv end 1:1"));
					mvSvStartGroup1.add(mvSvStartGroup1_1);
				EntityProperties mvSvStartGroup1_2 = new EntityProperties();
					mvSvStartGroup1_2.setProperty(new EntityProperty("MvSvStart", "mv sv start 1:2"));
					mvSvStartGroup1_2.setProperty(new EntityProperty("MvSvEnd", "mv sv end 1:2"));
					mvSvStartGroup1.add(mvSvStartGroup1_2);
			mvSvGroup1.setProperty(new EntityProperty("MvSvStartGroup", mvSvStartGroup1));
		mvSvGroup.add(mvSvGroup1);

		// Multi-value group
		EntityProperties mvSvGroup2 = new EntityProperties();
			mvSvGroup2.setProperty(new EntityProperty("MvSv", "mv sv 2:1"));
			List<EntityProperties> mvSvStartGroup2 = new ArrayList<EntityProperties>();
				// Sub-value group
				EntityProperties mvSvStartGroup2_1 = new EntityProperties();
					mvSvStartGroup2_1.setProperty(new EntityProperty("MvSvStart", "mv sv start 2:1"));
					mvSvStartGroup2_1.setProperty(new EntityProperty("MvSvEnd", "mv sv end 2:1"));
					mvSvStartGroup2.add(mvSvStartGroup2_1);
				EntityProperties mvSvStartGroup2_2 = new EntityProperties();
					mvSvStartGroup2_2.setProperty(new EntityProperty("MvSvStart", "mv sv start 2:2"));
					mvSvStartGroup2_2.setProperty(new EntityProperty("MvSvEnd", "mv sv end 2:2"));
					mvSvStartGroup2.add(mvSvStartGroup2_2);
			mvSvGroup2.setProperty(new EntityProperty("MvSvStartGroup", mvSvStartGroup2));
		mvSvGroup.add(mvSvGroup2);
				
		properties.setProperty(new EntityProperty("MvSvGroup", mvSvGroup));
		// End Multi-value group
	
		Entity entity = new Entity("Flight", properties);
		when(er.getEntity()).thenReturn(entity);
		when(er.getEntityName()).thenReturn("Flight");
		return er;
	}
	
	@SuppressWarnings("unchecked")
	private EntityResource<Flight> createMockEntityResourceObject() throws Exception {
		String testXMLString = "<resource><Flight><id>123</id><flight>EI218</flight></Flight></resource>";
		JAXBContext jc = JAXBContext.newInstance(EntityResource.class, Flight.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        EntityResource<Flight> er = (EntityResource<Flight>) unmarshaller.unmarshal(new ByteArrayInputStream(testXMLString.getBytes()));
        er.setEntityName("Flight");
        return er;
	}
	
	@Test (expected = AssertionError.class)
	public void testUnhandledRawType() throws IOException, URISyntaxException {
		EdmDataServices metadata = mock(EdmDataServices.class);
		MetadataOData4j metadataOData4j = createMockMetadataOData4j(metadata);
		EdmEntitySet edmEntitySet = mock(EdmEntitySet.class);
		when(metadataOData4j.getEdmEntitySetByEntityName(eq("ServiceDocument"))).thenReturn(edmEntitySet);
		AtomXMLProvider ap = new AtomXMLProvider(metadataOData4j, mock(Metadata.class), mockResourceStateMachine(), new OEntityTransformer());
		
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI("/myapp.svc"));
		when(uriInfo.getPath()).thenReturn("/");
		ap.setUriInfo(uriInfo);
		
		Request request = mock(Request.class);
		when(request.getMethod()).thenReturn("GET");
		ap.setRequestContext(request);

        // Wrap an unsupported resource into a JAX-RS GenericEntity instance
		GenericEntity<MetaDataResource<String>> ge = new GenericEntity<MetaDataResource<String>>(new MetaDataResource<String>("")) {};
		// will throw exception if we check the class properly
		Annotation[] annotations = null;
		MediaType mediaType = MediaType.APPLICATION_ATOM_XML_TYPE;
		MultivaluedMap<String, String> headers = null;
		InputStream content = null;
		ap.readFrom(RESTResource.class, ge.getType(), annotations, mediaType, headers, content);
	}
		
	@Test
	public void testWriteCollectionResourceEntity_AtomXML() throws Exception {
		EdmEntitySet ees = createMockEdmEntitySet();
		EdmDataServices mockEDS = createMockFlightEdmDataServices();		
		when(mockEDS.getEdmEntitySet(anyString())).thenReturn(ees);
		
		Metadata mockMetadata = createMockFlightMetadata();
		CollectionResource<Entity> cr = createMockCollectionResourceEntity();
		
        //Wrap entity resource into a JAX-RS GenericEntity instance
		GenericEntity<CollectionResource<Entity>> ge = new GenericEntity<CollectionResource<Entity>>(cr) {};

		//Create provider
		MockAtomXMLProvider p = new MockAtomXMLProvider(createMockMetadataOData4j(mockEDS), mockMetadata);
		UriInfo uriInfo = mock(UriInfo.class);
		URI uri = new URI("http://localhost:8080/responder/rest/");
		when(uriInfo.getBaseUri()).thenReturn(uri);
		when(uriInfo.getPath()).thenReturn("Flight()");
		p.setUriInfo(uriInfo);

		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");

		//Check response
		XMLUnit.setIgnoreWhitespace(true);
		Diff myDiff = XMLUnit.compareXML(readTextFile(FLIGHT_COLLECTION_XML), responseString);
	    myDiff.overrideDifferenceListener(new IgnoreNamedElementsXMLDifferenceListener("updated"));
	    if(!myDiff.similar()) {
	    	fail(myDiff.toString());
	    }
	}
	
	@Test
	public void testWriteEntityResourceEntity_AtomXML() throws Exception {
		EdmEntitySet ees = createMockEdmEntitySet();
		EdmDataServices mockEDS = createMockFlightEdmDataServices();		
		when(mockEDS.getEdmEntitySet(anyString())).thenReturn(ees);
		
		Metadata mockMetadata = createMockFlightMetadata();
		EntityResource<Entity> er = createMockEntityResourceEntity();
		
        //Wrap entity resource into a JAX-RS GenericEntity instance
		GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(er) {};

		//Create provider
		MockAtomXMLProvider p = new MockAtomXMLProvider(createMockMetadataOData4j(mockEDS), mockMetadata);
		UriInfo uriInfo = mock(UriInfo.class);
		URI uri = new URI("http://localhost:8080/responder/rest/");
		when(uriInfo.getBaseUri()).thenReturn(uri);
		when(uriInfo.getPath()).thenReturn("Flight('123')");
		p.setUriInfo(uriInfo);

		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");

		//Check response
		XMLUnit.setIgnoreWhitespace(true);
		Diff myDiff = XMLUnit.compareXML(readTextFile(FLIGHT_ENTRY_XML), responseString);
	    myDiff.overrideDifferenceListener(new IgnoreNamedElementsXMLDifferenceListener("updated"));
	    if(!myDiff.similar()) {
	    	fail(myDiff.toString());
	    }
	}

	@Test
	public void testWriteEntityResourceObject_AtomXML() throws Exception {
		EdmEntitySet ees = createMockEdmEntitySet();
		EdmDataServices mockEDS = createMockFlightEdmDataServices();		
		when(mockEDS.getEdmEntitySet(anyString())).thenReturn(ees);
		
		Metadata mockMetadata = createMockFlightMetadata();
		// change id to integer for this test
		EntityMetadata entityMetadata = mockMetadata.getEntityMetadata("Flight");
		Vocabulary voc_id = new Vocabulary();
		voc_id.setTerm(new TermValueType(TermValueType.INTEGER_NUMBER));
		voc_id.setTerm(new TermIdField(true));
		entityMetadata.setPropertyVocabulary("id", voc_id);

		EntityResource<Flight> er = createMockEntityResourceObject();
		
        //Wrap entity resource into a JAX-RS GenericEntity instance
		GenericEntity<EntityResource<Flight>> ge = new GenericEntity<EntityResource<Flight>>(er) {};

		//Create provider
		MockAtomXMLProvider p = new MockAtomXMLProvider(createMockMetadataOData4j(mockEDS), mockMetadata);
		UriInfo uriInfo = mock(UriInfo.class);
		URI uri = new URI("http://localhost:8080/responder/rest/");
		when(uriInfo.getBaseUri()).thenReturn(uri);
		when(uriInfo.getPath()).thenReturn("Flight(123)");
		p.setUriInfo(uriInfo);

		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");

		//Check response
		XMLUnit.setIgnoreWhitespace(true);
		Diff myDiff = XMLUnit.compareXML(readTextFile(FLIGHT_ENTRY_SIMPLE_XML), responseString);
	    myDiff.overrideDifferenceListener(new IgnoreNamedElementsXMLDifferenceListener("updated"));
	    if(!myDiff.similar()) {
	    	fail(myDiff.toString());
	    }
	}
	
	@Test
	public void testSkipSelfIfEditExists() {
		/*
		 *  Is this in the OData spec?  It seems that if a 'self' and 'edit' link relation exists
		 *  then ODataExplorer barfs
		 */
		
		EdmDataServices edmDataServices = createMockEdmDataServices("FundsTransfers");
		Metadata metadata = createMockMetadata("MyModel");
		Transition transition = createMockTransition(
				createMockResourceState("FundsTransfers", "FundsTransfer", true), 
				createMockResourceState("fundstransfer", "FundsTransfer", false));

		
		AtomXMLProvider provider = 
				new AtomXMLProvider(createMockMetadataOData4j(edmDataServices), 
						metadata, mockResourceStateMachine(), mock(Transformer.class));
		Collection<Link> processedLinks = null;
		List<Link> links = new ArrayList<Link>();
		links.add(new Link.Builder()
					.transition(transition)
					.title("title")
					.rel("self")
					.href("href")
					.id("id")
					.build());
		EntityResource<OEntity> entityResource = new EntityResource<OEntity>(mock(OEntity.class));
		entityResource.setLinks(links);
		provider.processLinks(entityResource);
		processedLinks = entityResource.getLinks();
		assertEquals(1, processedLinks.size());
		
		// now add the 'edit' link, it should replace the 'self' link
		links.add(new Link.Builder()
					.transition(transition)
					.title("title")
					.rel("edit")
					.href("href")
					.id("id")
					.build());
		entityResource.setLinks(links);
		provider.processLinks(entityResource);
		processedLinks = entityResource.getLinks();
		assertEquals(1, processedLinks.size());
	}

	@Test
	public void testSingleLinkEntryToCollection() {
		ResourceState account = new ResourceState("Account", "customerAccount", new ArrayList<Action>(), "/CustomerAccounts('{id}')");
		ResourceState fundsTransfers = new CollectionResourceState("FundsTransfer", "FundsTransfers", new ArrayList<Action>(), "/FundsTransfers");
		Map<String, String> uriLinkageMap = new HashMap<String, String>();
		uriLinkageMap.put("DebitAcctNo", "{Acc}");
		account.addTransition(new Transition.Builder().method(HttpMethod.GET).target(fundsTransfers).uriParameters(uriLinkageMap).label("Debit funds transfers").build());
		
		EdmDataServices mockEDS = createMockEdmDataServices("FundsTransfers");
		
		AtomXMLProvider provider = new AtomXMLProvider(createMockMetadataOData4j(mockEDS), createMockMetadata("MyModel"), mockResourceStateMachine(), mock(Transformer.class));
		Transition t = account.getTransition(fundsTransfers);
		List<Link> links = new ArrayList<Link>();
		links.add(new Link(t, t.getTarget().getRel(), "/FundsTransfers()?$filter=DebitAcctNo eq '123'", HttpMethod.GET));
		EntityResource<OEntity> entityResource = new EntityResource<OEntity>(mock(OEntity.class));
		entityResource.setLinks(links);
		provider.processLinks(entityResource);
		Collection<Link> processedLinks = entityResource.getLinks();
		assertEquals(1, processedLinks.size());
		Link theLink = processedLinks.iterator().next();
		
		//Link relation should contain MS-DATA base uri + /related/ + navigation property. The nav. property in this case is the EntitySet name
		assertEquals("http://schemas.microsoft.com/ado/2007/08/dataservices/related/FundsTransfers", theLink.getRel());
		
		assertEquals("Debit funds transfers", theLink.getTitle());
		
		assertEquals("/FundsTransfers()?$filter=DebitAcctNo eq '123'", theLink.getHref());
	}

	@Test
	public void testMultipleLinksEntryToCollection() {
		ResourceState account = new ResourceState("Account", "customerAccount", new ArrayList<Action>(), "/CustomerAccounts('{id}')");
		ResourceState fundsTransfers = new CollectionResourceState("FundsTransfer", "FundsTransfers", new ArrayList<Action>(), "/FundsTransfers");
		Map<String, String> uriLinkageMap = new HashMap<String, String>();
		uriLinkageMap.put("DebitAcctNo", "{Acc}");
		account.addTransition(new Transition.Builder().method(HttpMethod.GET).target(fundsTransfers).uriParameters(uriLinkageMap).label("Debit funds transfers").build());
		uriLinkageMap.clear();
		uriLinkageMap.put("CreditAcctNo", "{Acc}");
		account.addTransition(new Transition.Builder().method(HttpMethod.GET).target(fundsTransfers).uriParameters(uriLinkageMap).label("Credit funds transfers").build());
		
		AtomXMLProvider provider = 
				new AtomXMLProvider(createMockMetadataOData4j(createMockEdmDataServices("FundsTransfers")), 
						createMockMetadata("MyModel"), mockResourceStateMachine(), mock(Transformer.class));
		List<Transition> transitions = account.getTransitions(fundsTransfers);
		assertEquals(2, transitions.size());

		List<Link> links = new ArrayList<Link>();
		links.add(new Link(transitions.get(0), transitions.get(0).getTarget().getRel(), transitions.get(0).getLabel().contains("Debit") ? "/FundsTransfers()?$filter=DebitAcctNo eq '123'" : "/FundsTransfers()?$filter=CreditAcctNo eq '123'", HttpMethod.GET));
		links.add(new Link(transitions.get(1), transitions.get(1).getTarget().getRel(), transitions.get(1).getLabel().contains("Debit") ? "/FundsTransfers()?$filter=DebitAcctNo eq '123'" : "/FundsTransfers()?$filter=CreditAcctNo eq '123'", HttpMethod.GET));
		EntityResource<OEntity> entityResource = new EntityResource<OEntity>(mock(OEntity.class));
		entityResource.setLinks(links);
		provider.processLinks(entityResource);
		Collection<Link> processedLinks = entityResource.getLinks();
		assertEquals(2, processedLinks.size());
		Iterator<Link> iterator = processedLinks.iterator();
		Link debitLink = iterator.next();
		Link creditLink = iterator.next();
		
		//Link relation should contain MS-DATA base uri + /related/ + navigation property. However, the nav. property in this case is NOT the EntitySet name
		//but a transition ID identifying the link (the link title at the moment). It does not fully comply with OData but this one does not cater for multiple links to the same target.  
		assertEquals("Credit funds transfers", creditLink.getTitle());
		assertEquals("/FundsTransfers()?$filter=CreditAcctNo eq '123'", creditLink.getHref());
		assertEquals("http://schemas.microsoft.com/ado/2007/08/dataservices/related/FundsTransfers", creditLink.getRel());
		
		assertEquals("Debit funds transfers", debitLink.getTitle());
		assertEquals("/FundsTransfers()?$filter=DebitAcctNo eq '123'", debitLink.getHref());
		assertEquals("http://schemas.microsoft.com/ado/2007/08/dataservices/related/FundsTransfers", debitLink.getRel());

	}

	@Test
	public void testSingleLinkFeedToCollectionSameEntity() throws Exception {
		// initialise the thread local request context with requestUri and baseUri
        RequestContext ctx = new RequestContext("http://localhost:8080/responder/rest", "/FundsTransfers", null);
        RequestContext.setRequestContext(ctx);

        //Create rsm
		ResourceState initial = new ResourceState("ServiceDocument", "ServiceDocument", new ArrayList<Action>(), "/");
		ResourceState fundsTransfers = new CollectionResourceState("FundsTransfer", "FundsTransfers", new ArrayList<Action>(), "/FundsTransfers");
		ResourceState fundsTransfersIAuth = new CollectionResourceState("FundsTransfer", "FundsTransfersIAuth", new ArrayList<Action>(), "/FundsTransfersIAuth");
		fundsTransfers.addTransition(new Transition.Builder().method(HttpMethod.GET).target(fundsTransfersIAuth).label("Unauthorised input records").build());
		initial.addTransition(new Transition.Builder().method(HttpMethod.GET)
				.target(fundsTransfers)
				.build());
		ResourceStateMachine rsm = new ResourceStateMachine(initial);

		//Create collection resource
		CollectionResource<Entity> cr = new CollectionResource<Entity>("FundsTransfers", new ArrayList<EntityResource<Entity>>());
		List<Link> links = new ArrayList<Link>();
		links.add(createLink(rsm, fundsTransfers.getTransition(fundsTransfersIAuth), null, null, null));
		cr.setLinks(links);
		GenericEntity<CollectionResource<Entity>> ge = new GenericEntity<CollectionResource<Entity>>(cr) {};

		//Create provider
		AtomXMLProvider p = 
				new AtomXMLProvider(createMockMetadataOData4j(createMockEdmDataServices("FundsTransfers")), 
						mock(Metadata.class), rsm, null);
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI(ctx.getBasePath()));
		when(uriInfo.getPath()).thenReturn(ctx.getRequestUri());
		p.setUriInfo(uriInfo);

		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");
		System.out.println(responseString);

		//Check response
		XMLUnit.setIgnoreWhitespace(true);
		Diff myDiff = XMLUnit.compareXML(readTextFile(EMPTY_FUNDS_TRANSFERS_FEED_XML), responseString);
	    myDiff.overrideDifferenceListener(new IgnoreNamedElementsXMLDifferenceListener("updated"));
	    if(!myDiff.similar()) {
	    	fail(myDiff.toString());
	    }
	}
	
	private final static String COMPANY_COLLECTION_LINK_COLLECTION = "<?xml version='1.0' encoding='UTF-8'?>"
			+ "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\" xml:base=\"http://localhost:8080/responder/rest/MockCompany001/\">"
				+ "<title type=\"text\">FundsTransfers</title>"
				+ "<id>http://localhost:8080/responder/rest/MockCompany001/FundsTransfers</id>"
				+ "<updated>2014-02-20T09:27:18Z</updated>"
				+ "<link rel=\"http://schemas.microsoft.com/ado/2007/08/dataservices/related/FundsTransfers\" title=\"Unauthorised input records\" href=\"FundsTransfersIAuth\">"
				+ "</link>"
				+ "</feed>";

	private ResourceStateMachine createCollectionToCollectionRSM() {
		ResourceState initial = new ResourceState("ServiceDocument", "ServiceDocument", new ArrayList<Action>(), "/{companyid}");
		ResourceState fundsTransfers = new CollectionResourceState("FundsTransfer", "FundsTransfers", new ArrayList<Action>(), "/{companyid}/FundsTransfers");
		ResourceState fundsTransfersIAuth = new CollectionResourceState("FundsTransfer", "FundsTransfersIAuth", new ArrayList<Action>(), "/{companyid}/FundsTransfersIAuth");
		fundsTransfers.addTransition(new Transition.Builder().method(HttpMethod.GET).target(fundsTransfersIAuth).label("Unauthorised input records").build());
		initial.addTransition(new Transition.Builder().method(HttpMethod.GET)
				.target(fundsTransfers)
				.build());
		ResourceStateMachine rsm = new ResourceStateMachine(initial);
		return rsm;
	}

	@Test
	public void testCollectionToCollectionEntityWithCompanyBasePath() throws Exception {
		// initialise the thread local request context with requestUri and baseUri
        RequestContext ctx = 
        		new RequestContext("http://localhost:8080/responder/rest", "/MockCompany001/FundsTransfers", null);
        RequestContext.setRequestContext(ctx);

        //Create rsm
        ResourceStateMachine rsm = createCollectionToCollectionRSM();
        ResourceState fundsTransfers = rsm.getResourceStateByName("FundsTransfers");
        ResourceState fundsTransfersIAuth = rsm.getResourceStateByName("FundsTransfersIAuth");

		//Create collection resource
		CollectionResource<Entity> cr = 
				new CollectionResource<Entity>("FundsTransfers", new ArrayList<EntityResource<Entity>>());
		List<Link> links = new ArrayList<Link>();
		MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String>();
		pathParameters.add("companyid", "MockCompany001");
		links.add(createLink(rsm, fundsTransfers.getTransition(fundsTransfersIAuth), null, pathParameters, null));
		cr.setLinks(links);
		GenericEntity<CollectionResource<Entity>> ge = new GenericEntity<CollectionResource<Entity>>(cr) {};

		//Create provider
		AtomXMLProvider p = 
				new AtomXMLProvider(createMockMetadataOData4j(createMockEdmDataServices("FundsTransfers")),
						createMockMetadata("MyModel"), rsm, mock(Transformer.class));
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI(ctx.getBasePath()));
		when(uriInfo.getPath()).thenReturn(ctx.getRequestUri());
		p.setUriInfo(uriInfo);

		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");
		System.out.println(responseString);

		//Check response
		assertTrue(responseString.contains("href=\"FundsTransfersIAuth\""));
		//Check response
		XMLUnit.setIgnoreWhitespace(true);
		Diff myDiff = XMLUnit.compareXML(COMPANY_COLLECTION_LINK_COLLECTION, responseString);
	    myDiff.overrideDifferenceListener(new IgnoreNamedElementsXMLDifferenceListener("updated"));
	    if(!myDiff.similar()) {
	    	fail(myDiff.toString());
	    }
	}

	@Test
	public void testCollectionToCollectionOEntityWithCompanyBasePath() throws Exception {
		// initialise the thread local request context with requestUri and baseUri
        RequestContext ctx = new RequestContext("http://localhost:8080/responder/rest", "/MockCompany001/FundsTransfers", null);
        RequestContext.setRequestContext(ctx);

        //Create rsm
        ResourceStateMachine rsm = createCollectionToCollectionRSM();
        ResourceState fundsTransfers = rsm.getResourceStateByName("FundsTransfers");
        ResourceState fundsTransfersIAuth = rsm.getResourceStateByName("FundsTransfersIAuth");

		//Create collection resource
		CollectionResource<OEntity> cr = new CollectionResource<OEntity>("FundsTransfers", new ArrayList<EntityResource<OEntity>>());
		List<Link> links = new ArrayList<Link>();
		MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String>();
		pathParameters.add("companyid", "MockCompany001");
		links.add(createLink(rsm, fundsTransfers.getTransition(fundsTransfersIAuth), null, pathParameters, null));
		cr.setLinks(links);
		GenericEntity<CollectionResource<OEntity>> ge = new GenericEntity<CollectionResource<OEntity>>(cr) {};

		//Create provider
		AtomXMLProvider p = 
				new AtomXMLProvider(createMockMetadataOData4j(createMockEdmDataServices("FundsTransfers")),
						createMockMetadata("MyModel"), rsm, mock(Transformer.class));
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI(ctx.getBasePath()));
		when(uriInfo.getPath()).thenReturn(ctx.getRequestUri());
		p.setUriInfo(uriInfo);

		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");
		System.out.println(responseString);

		//Check response
		assertTrue(responseString.contains("href=\"FundsTransfersIAuth\""));
		//Check response
		XMLUnit.setIgnoreWhitespace(true);
		Diff myDiff = XMLUnit.compareXML(COMPANY_COLLECTION_LINK_COLLECTION, responseString);
	    myDiff.overrideDifferenceListener(new IgnoreNamedElementsXMLDifferenceListener("updated"));
	    if(!myDiff.similar()) {
	    	fail(myDiff.toString());
	    }
	}

	private final static String ENTITY_COLLECTION_LINK_ITEM = "<?xml version='1.0' encoding='utf-8'?>"
			+ "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\" "
				+ "xml:base=\"http://localhost:8080/responder/rest/\">"
			+ "<title type=\"text\">Flight</title>"
			+ "<id>http://localhost:8080/responder/rest/FundsTransfers</id>"
			+ "<updated>2014-02-20T08:56:32Z</updated>"
				+ "<entry><id>http://localhost:8080/responder/rest/Flight('1')</id>"
				+ "<title type=\"text\" />"
				+ "<updated>2014-02-20T08:56:32Z</updated>"
				+ "<author><name /></author>"
				+ "<link rel=\"self\" title=\"Link to entity\" href=\"Flights('1')\"  profile=\"item\" />"
				+ "<category term=\"FlightModelModel.Flight\" scheme=\"http://schemas.microsoft.com/ado/2007/08/dataservices/scheme\" />"
				+ "<content type=\"application/xml\">"
				+ "<m:properties><d:id>1</d:id><d:flight>EI218</d:flight></m:properties></content>"
				+ "</entry>"
			+ "</feed>";

	private final static String OENTITY_COLLECTION_LINK_ITEM = "<?xml version='1.0' encoding='utf-8'?>"
			+ "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\" "
				+ "xml:base=\"http://localhost:8080/responder/rest/\">"
			+ "<title type=\"text\">Flight</title>"
			+ "<id>http://localhost:8080/responder/rest/FundsTransfers</id>"
			+ "<updated>2014-02-20T08:56:32Z</updated>"
				+ "<entry m:etag=\"07u1PAxpJ7RGGblAB8FicpJF18wtzngF71WGQUMlABE=\"><id>http://localhost:8080/responder/rest/Flight('1')</id>"
				+ "<title type=\"text\" />"
				+ "<updated>2014-02-20T08:56:32Z</updated>"
				+ "<author><name /></author>"
				+ "<link rel=\"self\" title=\"Link to entity\" href=\"Flights('1')\" />"
				+ "<category term=\"FlightModelModel.Flight\" scheme=\"http://schemas.microsoft.com/ado/2007/08/dataservices/scheme\" />"
				+ "<content type=\"application/xml\">"
				+ "<m:properties><d:id>1</d:id><d:flight>EI218</d:flight></m:properties></content>"
				+ "</entry>"
			+ "</feed>";	
	private ResourceStateMachine createCollectionToItemRSM(Transformer transformer) {
		
		List<Action> serviceDocActions = new ArrayList<Action>();
		Action action = mock(Action.class);
		when(action.getMethod()).thenReturn("GET");
		serviceDocActions.add(action);
		
		List<Action> flightsActions = new ArrayList<Action>();
		flightsActions.add(action);
		
		List<Action> flightActions = new ArrayList<Action>();
		flightActions.add(action);		
		
		ResourceState initial = new ResourceState("ServiceDocument", "ServiceDocument", serviceDocActions, "/");
		ResourceState flights = new CollectionResourceState("Flight", "Flights", flightsActions, "/Flights");
		ResourceState flight = new ResourceState("Flight", "Flight", flightActions, "/Flights('{id}')");
		flights.addTransition(new Transition.Builder().method(HttpMethod.GET).target(flight).label("Link to entity").build());
		initial.addTransition(new Transition.Builder().method(HttpMethod.GET)
				.target(flights)
				.build());
		ResourceStateMachine rsm = new ResourceStateMachine(initial, transformer);
		return rsm;
	}
	
	@Test
	public void testCollectionToItemEntityBasePath() throws Exception {
		// initialise the thread local request context with requestUri and baseUri
        RequestContext ctx = new RequestContext("http://localhost:8080/responder/rest", "/FundsTransfers", null);
        RequestContext.setRequestContext(ctx);

        // metadata
        EdmDataServices edmDataServices = createMockFlightEdmDataServices();
        Metadata metadata = createMockFlightMetadata();
        // Create rsm
        ResourceStateMachine rsm = createCollectionToItemRSM(new EntityTransformer());
        ResourceState flights = rsm.getResourceStateByName("Flights");
        ResourceState flight = rsm.getResourceStateByName("Flight");
        
		// Build up some entities
		List<EntityResource<Entity>> entities = new ArrayList<EntityResource<Entity>>();
		EntityResource<Entity> er = createMockEntityResourceEntity("Flight");
		List<Link> links = new ArrayList<Link>();
		MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String>();
		links.add(createLink(rsm, flights.getTransition(flight), er.getEntity(), pathParameters, null));
		er.setLinks(links);
		entities.add(er);
		// Create collection resource
		CollectionResource<Entity> cr = new CollectionResource<Entity>("Flight", entities);
		GenericEntity<CollectionResource<Entity>> ge = new GenericEntity<CollectionResource<Entity>>(cr) {};

		//Create provider
		AtomXMLProvider p = 
				new AtomXMLProvider(createMockMetadataOData4j(edmDataServices), metadata, rsm, mock(Transformer.class));
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI(ctx.getBasePath()));
		when(uriInfo.getPath()).thenReturn(ctx.getRequestUri());
		p.setUriInfo(uriInfo);

		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");
		System.out.println(responseString);

		//Check response
		XMLUnit.setIgnoreWhitespace(true);
		Diff myDiff = XMLUnit.compareXML(ENTITY_COLLECTION_LINK_ITEM, responseString);
	    myDiff.overrideDifferenceListener(new IgnoreNamedElementsXMLDifferenceListener("updated"));
	    if(!myDiff.similar()) {
	    	fail(myDiff.toString());
	    }
	}

	@Test
	public void testCollectionToItemOEntityBasePath() throws Exception {
		// initialise the thread local request context with requestUri and baseUri
        RequestContext ctx = new RequestContext("http://localhost:8080/responder/rest", "/FundsTransfers", null);
        RequestContext.setRequestContext(ctx);

        // metadata
        EdmDataServices edmDataServices = createMockFlightEdmDataServices();
        Metadata metadata = createMockFlightMetadata();
        // Create rsm
        ResourceStateMachine rsm = createCollectionToItemRSM(new OEntityTransformer());
        ResourceState flights = rsm.getResourceStateByName("Flights");
        ResourceState flight = rsm.getResourceStateByName("Flight");

		// Build up some entities
		List<EntityResource<OEntity>> oentities = new ArrayList<EntityResource<OEntity>>();
		EntityResource<OEntity> er = createMockEntityResourceOEntity(edmDataServices.findEdmEntitySet("Flights"), 
		        "07u1PAxpJ7RGGblAB8FicpJF18wtzngF71WGQUMlABE=");
		List<Link> links = new ArrayList<Link>();
		MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String>();
		links.add(createLink(rsm, flights.getTransition(flight), er.getEntity(), pathParameters, null));
		er.setLinks(links);
		oentities.add(er);
		// Create collection resource
		CollectionResource<OEntity> cr = new CollectionResource<OEntity>("Flights", oentities);
		GenericEntity<CollectionResource<OEntity>> ge = new GenericEntity<CollectionResource<OEntity>>(cr) {};

		//Create provider
		AtomXMLProvider p = 
				new AtomXMLProvider(createMockMetadataOData4j(edmDataServices), metadata, rsm, mock(Transformer.class));
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI(ctx.getBasePath()));
		when(uriInfo.getPath()).thenReturn(ctx.getRequestUri());
		p.setUriInfo(uriInfo);

		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");
		System.out.println(responseString);

		//Check response
		XMLUnit.setIgnoreWhitespace(true);
		Diff myDiff = XMLUnit.compareXML(OENTITY_COLLECTION_LINK_ITEM, responseString);
		myDiff.overrideDifferenceListener(new IgnoreNamedElementsXMLDifferenceListener("updated"));
	    if(!myDiff.similar()) {
	    	fail(myDiff.toString());
	    }
	}

	private final static String ENTITY_COMPANY_COLLECTION_LINK_ITEM = "<?xml version='1.0' encoding='utf-8'?>"
			+ "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\" "
				+ "xml:base=\"http://localhost:8080/responder/rest/MockCompany001/\">"
			+ "<title type=\"text\">Flight</title>"
			+ "<id>http://localhost:8080/responder/rest/MockCompany001/FundsTransfers</id>"
			+ "<updated>2014-02-20T08:56:32Z</updated>"
				+ "<entry><id>http://localhost:8080/responder/rest/MockCompany001/Flight('1')</id>"
				+ "<title type=\"text\" />"
				+ "<updated>2014-02-20T08:56:32Z</updated>"
				+ "<author><name /></author>"
				+ "<link rel=\"self\" title=\"Link to entity\" href=\"Flights('1')\" profile=\"item\" />"
				+ "<category term=\"FlightModelModel.Flight\" scheme=\"http://schemas.microsoft.com/ado/2007/08/dataservices/scheme\" />"
				+ "<content type=\"application/xml\">"
				+ "<m:properties><d:id>1</d:id><d:flight>EI218</d:flight></m:properties></content>"
				+ "</entry>"
			+ "</feed>";
	
	private final static String OENTITY_COMPANY_COLLECTION_LINK_ITEM = "<?xml version='1.0' encoding='utf-8'?>"
			+ "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\" "
				+ "xml:base=\"http://localhost:8080/responder/rest/MockCompany001/\">"
			+ "<title type=\"text\">Flight</title>"
			+ "<id>http://localhost:8080/responder/rest/MockCompany001/FundsTransfers</id>"
			+ "<updated>2014-02-20T08:56:32Z</updated>"
				+ "<entry><id>http://localhost:8080/responder/rest/MockCompany001/Flight('1')</id>"
				+ "<title type=\"text\" />"
				+ "<updated>2014-02-20T08:56:32Z</updated>"
				+ "<author><name /></author>"
				+ "<link rel=\"self\" title=\"Link to entity\" href=\"Flights('1')\" />"
				+ "<category term=\"FlightModelModel.Flight\" scheme=\"http://schemas.microsoft.com/ado/2007/08/dataservices/scheme\" />"
				+ "<content type=\"application/xml\">"
				+ "<m:properties><d:id>1</d:id><d:flight>EI218</d:flight></m:properties></content>"
				+ "</entry>"
			+ "</feed>";	

	private ResourceStateMachine createCompanyCollectionToItemRSM(Transformer transformer) {
		List<Action> serviceDocActions = new ArrayList<Action>();
		Action action = mock(Action.class);
		when(action.getMethod()).thenReturn("GET");
		serviceDocActions.add(action);
		
		List<Action> flightsActions = new ArrayList<Action>();
		flightsActions.add(action);
		
		List<Action> flightActions = new ArrayList<Action>();
		flightActions.add(action);
				
		ResourceState initial = new ResourceState("ServiceDocument", "ServiceDocument", serviceDocActions, "/{companyid}");
		ResourceState flights = new CollectionResourceState("Flight", "Flights", flightsActions, "/{companyid}/Flights");
		ResourceState flight = new ResourceState("Flight", "Flight", flightActions, "/{companyid}/Flights('{id}')");
		flights.addTransition(new Transition.Builder().method(HttpMethod.GET).target(flight).label("Link to entity").build());
		initial.addTransition(new Transition.Builder().method(HttpMethod.GET)
				.target(flights)
				.build());
		ResourceStateMachine rsm = new ResourceStateMachine(initial, transformer);
		return rsm;
	}
	
	@Test
	public void testCollectionToItemEntityWithCompanyBasePath() throws Exception {
		// initialise the thread local request context with requestUri and baseUri
        RequestContext ctx = new RequestContext("http://localhost:8080/responder/rest", "/MockCompany001/FundsTransfers", null);
        RequestContext.setRequestContext(ctx);

        // metadata
        EdmDataServices edmDataServices = createMockFlightEdmDataServices();
        Metadata metadata = createMockFlightMetadata();
        // Create rsm
        ResourceStateMachine rsm = createCompanyCollectionToItemRSM(new EntityTransformer());
        ResourceState flights = rsm.getResourceStateByName("Flights");
        ResourceState flight = rsm.getResourceStateByName("Flight");
        
		// Build up some entities
		List<EntityResource<Entity>> entities = new ArrayList<EntityResource<Entity>>();
		EntityResource<Entity> er = createMockEntityResourceEntity("Flight");
		List<Link> links = new ArrayList<Link>();
		MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String>();
		pathParameters.add("companyid", "MockCompany001");
		links.add(createLink(rsm, flights.getTransition(flight), er.getEntity(), pathParameters, null));
		er.setLinks(links);
		entities.add(er);
		// Create collection resource
		CollectionResource<Entity> cr = new CollectionResource<Entity>("Flight", entities);
		GenericEntity<CollectionResource<Entity>> ge = new GenericEntity<CollectionResource<Entity>>(cr) {};

		//Create provider
		AtomXMLProvider p = 
				new AtomXMLProvider(createMockMetadataOData4j(edmDataServices), metadata, rsm, mock(Transformer.class));
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI(ctx.getBasePath()));
		when(uriInfo.getPath()).thenReturn(ctx.getRequestUri());
		p.setUriInfo(uriInfo);

		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");
		System.out.println(responseString);

		//Check response
		XMLUnit.setIgnoreWhitespace(true);
		Diff myDiff = XMLUnit.compareXML(ENTITY_COMPANY_COLLECTION_LINK_ITEM, responseString);
	    myDiff.overrideDifferenceListener(new IgnoreNamedElementsXMLDifferenceListener("updated"));
	    if(!myDiff.similar()) {
	    	fail(myDiff.toString());
	    }
	}

	@Test
	public void testCollectionToItemOEntityWithCompanyBasePath() throws Exception {
		// initialise the thread local request context with requestUri and baseUri
        RequestContext ctx = new RequestContext("http://localhost:8080/responder/rest", "/MockCompany001/FundsTransfers", null);
        RequestContext.setRequestContext(ctx);

        // metadata
        EdmDataServices edmDataServices = createMockFlightEdmDataServices();
        Metadata metadata = createMockFlightMetadata();
        // Create rsm
        ResourceStateMachine rsm = createCompanyCollectionToItemRSM(new OEntityTransformer());
        ResourceState flights = rsm.getResourceStateByName("Flights");
        ResourceState flight = rsm.getResourceStateByName("Flight");

		// Build up some entities
		List<EntityResource<OEntity>> oentities = new ArrayList<EntityResource<OEntity>>();
		EntityResource<OEntity> er = createMockEntityResourceOEntity(edmDataServices.findEdmEntitySet("Flights"));
		List<Link> links = new ArrayList<Link>();
		MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String>();
		pathParameters.add("companyid", "MockCompany001");
		links.add(createLink(rsm, flights.getTransition(flight), er.getEntity(), pathParameters, null));
		er.setLinks(links);
		oentities.add(er);
		// Create collection resource
		CollectionResource<OEntity> cr = new CollectionResource<OEntity>("Flights", oentities);
		GenericEntity<CollectionResource<OEntity>> ge = new GenericEntity<CollectionResource<OEntity>>(cr) {};

		//Create provider
		AtomXMLProvider p = 
				new AtomXMLProvider(createMockMetadataOData4j(edmDataServices), metadata, rsm, mock(Transformer.class));
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI(ctx.getBasePath()));
		when(uriInfo.getPath()).thenReturn(ctx.getRequestUri());
		p.setUriInfo(uriInfo);

		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");
		System.out.println(responseString);

		//Check response
		XMLUnit.setIgnoreWhitespace(true);
		Diff myDiff = XMLUnit.compareXML(OENTITY_COMPANY_COLLECTION_LINK_ITEM, responseString);
		myDiff.overrideDifferenceListener(new IgnoreNamedElementsXMLDifferenceListener("updated"));
	    if(!myDiff.similar()) {
	    	fail(myDiff.toString());
	    }
	}

	@Test
	public void testLinkEntryToEntity() {
		List<Action> serviceDocActions = new ArrayList<Action>();
		Action action = mock(Action.class);
		when(action.getMethod()).thenReturn("GET");
		serviceDocActions.add(action);
		
		List<Action> customerAccountActions = new ArrayList<Action>();
		customerAccountActions.add(action);

		List<Action> currnecyActions = new ArrayList<Action>();
		currnecyActions.add(action);
		
		ResourceState initial = new ResourceState("ServiceDocument", "ServiceDocument", serviceDocActions, "/");
		ResourceState account = new ResourceState("Account", "customerAccount", customerAccountActions, "/CustomerAccounts('{id}')");
		ResourceState currency = new ResourceState("Currency", "currency", currnecyActions, "/Currencys('{id}')");
		account.addTransition(new Transition.Builder().method(HttpMethod.GET).target(currency).label("currency").linkId("123456").build());
		initial.addTransition(new Transition.Builder().method(HttpMethod.GET)
				.target(account)
				.build());
		ResourceStateMachine rsm = new ResourceStateMachine(initial);
		
		AtomXMLProvider provider = 
				new AtomXMLProvider(createMockMetadataOData4j(createMockEdmDataServices("Currencys")),
						createMockMetadata("MyModel"), rsm, mock(Transformer.class));
		Transition t = account.getTransition(currency);
		List<Link> links = new ArrayList<Link>();
		links.add(new Link(t, t.getTarget().getRel(), "/Currencys('USD')", HttpMethod.GET));
		EntityResource<OEntity> entityResource = new EntityResource<OEntity>(mock(OEntity.class));
		entityResource.setLinks(links);
		provider.processLinks(entityResource);
		Collection<Link> processedLinks = entityResource.getLinks();
		assertEquals(1, processedLinks.size());
		Link theLink = processedLinks.iterator().next();
		
		//Link relation should contain MS-DATA base uri + /related/ + navigation property. The nav. property in this case is the entity (type) name
		assertEquals("http://schemas.microsoft.com/ado/2007/08/dataservices/related/Currency", theLink.getRel());
		
		assertEquals("currency", theLink.getTitle());
		
		assertEquals("/Currencys('USD')", theLink.getHref());
		
		assertEquals("123456", theLink.getLinkId());
	}

	@Test
	public void testEmbeddedEntry() throws URISyntaxException {
        // Create the RIM
		ResourceState initial = new ResourceState("ServiceDocument", "ServiceDocument", new ArrayList<Action>(), "/");
		ResourceState parent = new ResourceState("Flight", "parent", new ArrayList<Action>(), "/Flight('{id}')");
		ResourceState child = new ResourceState("Flight", "child", new ArrayList<Action>(), "/Flight('id')/child");
		parent.addTransition(new Transition.Builder().method(HttpMethod.GET).target(child).label("My Child Link").linkId("123456").build());
		initial.addTransition(new Transition.Builder().method(HttpMethod.GET)
				.target(parent)
				.build());
		ResourceStateMachine rsm = new ResourceStateMachine(initial);
		
		EdmDataServices edmDataServices = createMockFlightEdmDataServices();
		EdmEntitySet persons = edmDataServices.findEdmEntitySet("Flight");
		EntityResource<OEntity> childResource = createMockEntityResourceOEntity(persons);
		
		List<OLink> olinks = new ArrayList<OLink>();
		olinks.add(OLinks.relatedEntityInline("relation", "child", null, childResource.getEntity()));
		List<OProperty<?>> properties = new ArrayList<OProperty<?>>();
		properties.add(OProperties.string("name", "blah"));
		OEntity accountOEntity = OEntities.create(persons, OEntityKey.create("USD"), properties, olinks);
		EntityResource<OEntity> accountEntityResource = new EntityResource<OEntity>(accountOEntity);
		List<Link> links = new ArrayList<Link>();
		links.add(new Link("title", "http://schemas.microsoft.com/ado/2007/08/dataservices/related/title", "Flight('USD')/child", "type", null));
		accountEntityResource.setLinks(links);
		
		AtomXMLProvider provider =
				new AtomXMLProvider(createMockMetadataOData4j(edmDataServices),
						createMockMetadata("MyModel"), rsm, mock(Transformer.class));
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/responder/rest"));
		when(uriInfo.getPath()).thenReturn("/mock");
		provider.setUriInfo(uriInfo);
		provider.addExpandedLinks(accountEntityResource);
		
		Map<Transition,RESTResource> embeddedResources = accountEntityResource.getEmbedded();
		assertNotNull(embeddedResources);
		assertEquals(1, embeddedResources.size());
	}
	
	@Test
	public void testSingleLinkToCollectionNotEntitySet() {
		ResourceState serviceRoot = new ResourceState("SD", "ServiceDocument", new ArrayList<Action>(), "/");
		ResourceState fundsTransfer = new ResourceState("FundsTransfer", "fundsTransfer", new ArrayList<Action>(), "/FundsTransfers('{id}')");
		ResourceState fundsTransfersIAuth = new CollectionResourceState("Dummy", "FundsTransfersIAuth", new ArrayList<Action>(), "/FundsTransfersIAuth");
		serviceRoot.addTransition(new Transition.Builder().flags(Transition.AUTO).target(fundsTransfer).build());
		fundsTransfer.addTransition(new Transition.Builder().method(HttpMethod.GET).target(fundsTransfersIAuth).label("Unauthorised funds transfers").build());
		
		//FundsTransfersIAuth is not an entity set
		EdmDataServices edmDataServices = createMockEdmDataServices("FundsTransfers");
		when(edmDataServices.getEdmEntitySet(any(EdmEntityType.class))).thenThrow(new NotFoundException("EntitySet for entity type Dummy has not been found"));
		MetadataOData4j mockMetadataOData4j = createMockMetadataOData4j(edmDataServices);
		when(mockMetadataOData4j.getEdmEntitySetByEntityName(anyString())).thenThrow(new NotFoundException("EntitySet for entity type Dummy has not been found"));;
		AtomXMLProvider provider = 
				new AtomXMLProvider(mockMetadataOData4j, createMockMetadata("MyModel"), new ResourceStateMachine(serviceRoot), mock(Transformer.class));
		Transition t = fundsTransfer.getTransition(fundsTransfersIAuth);
		List<Link> links = new ArrayList<Link>();
		links.add(new Link(t, t.getTarget().getRel(), "/FundsTransfersIAuth()", HttpMethod.GET));
		EntityResource<OEntity> entityResource = new EntityResource<OEntity>(mock(OEntity.class));
		entityResource.setLinks(links);
		provider.processLinks(entityResource);
		Collection<Link> processedLinks = entityResource.getLinks();
		assertEquals(1, processedLinks.size());
		Link theLink = processedLinks.iterator().next();
		
		//Link relation should contain MS-DATA base uri + /related/ + navigation property. The nav. property in this case would be the EntitySet name, however, it is not
		//an entity set so use the name of the target state.
		assertEquals("http://schemas.microsoft.com/ado/2007/08/dataservices/related/FundsTransfersIAuth", theLink.getRel());
		
		assertEquals("Unauthorised funds transfers", theLink.getTitle());
		
		assertEquals("/FundsTransfersIAuth()", theLink.getHref());
	}
	
	@Test
	public void testSingleLinkOEntityFeedToNonEntitySet() throws Exception {
		// initialise the thread local request context with requestUri and baseUri
        RequestContext ctx = new RequestContext("http://localhost:8080/responder/rest", "/FundsTransfers", null);
        RequestContext.setRequestContext(ctx);

        //Create rsm
		ResourceState serviceRoot = new ResourceState("SD", "initial", new ArrayList<Action>(), "/");
		ResourceState fundsTransfers = new CollectionResourceState("FundsTransfer", "FundsTransfers", new ArrayList<Action>(), "/FundsTransfers");
		ResourceState fundsTransfersIAuth = new CollectionResourceState("FundsTransfer", "FundsTransfersIAuth", new ArrayList<Action>(), "/FundsTransfersIAuth");
		serviceRoot.addTransition(new Transition.Builder().flags(Transition.AUTO).target(fundsTransfers).build());
		fundsTransfers.addTransition(new Transition.Builder().method(HttpMethod.GET).target(fundsTransfersIAuth).label("Unauthorised input records").build());
		ResourceStateMachine rsm = new ResourceStateMachine(fundsTransfers);

		//Create collection resource
		CollectionResource<OEntity> cr = new CollectionResource<OEntity>("FundsTransfers", new ArrayList<EntityResource<OEntity>>());
		List<Link> links = new ArrayList<Link>();
		links.add(createLink(rsm, fundsTransfers.getTransition(fundsTransfersIAuth), null, null, null));
		cr.setLinks(links);
		GenericEntity<CollectionResource<OEntity>> ge = new GenericEntity<CollectionResource<OEntity>>(cr) {};

		//Create provider
		EdmDataServices edmDataServices = createMockEdmDataServices("FundsTransfers");
		Metadata metadata = mock(Metadata.class);
		when(metadata.getModelName()).thenReturn("MyModel");
		MockAtomXMLProvider p = new MockAtomXMLProvider(createMockMetadataOData4j(edmDataServices), metadata);
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI(ctx.getBasePath()));
		when(uriInfo.getPath()).thenReturn(ctx.getRequestUri());
		p.setUriInfo(uriInfo);

		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");
		System.out.println(responseString);

		//Check response
		XMLUnit.setIgnoreWhitespace(true);
		Diff myDiff = XMLUnit.compareXML(readTextFile(EMPTY_FUNDS_TRANSFERS_FEED_XML), responseString);
	    myDiff.overrideDifferenceListener(new IgnoreNamedElementsXMLDifferenceListener("updated"));
	    if(!myDiff.similar()) {
	    	fail(myDiff.toString());
	    }
	}
	
	@Test
	public void testWriteEntityResourceGenericError_AtomXML() throws Exception {
		EdmEntitySet ees = createMockEdmEntitySet();
		EdmDataServices mockEDS = createMockFlightEdmDataServices();		
		when(mockEDS.getEdmEntitySet(anyString())).thenReturn(ees);
		
		Metadata mockMetadata = createMockFlightMetadata();
		EntityResource<GenericError> er = createMockEntityResourceGenericError();
		
        //Wrap entity resource into a JAX-RS GenericEntity instance
		GenericEntity<EntityResource<GenericError>> ge = new GenericEntity<EntityResource<GenericError>>(er) {};
		
		
		//Create provider
		MockAtomXMLProvider p = new MockAtomXMLProvider(createMockMetadataOData4j(mockEDS), mockMetadata);
		UriInfo uriInfo = mock(UriInfo.class);
		URI uri = new URI("http://localhost:8080/responder/rest/");
		when(uriInfo.getBaseUri()).thenReturn(uri);
		when(uriInfo.getPath()).thenReturn("Flight(123)");
		p.setUriInfo(uriInfo);

		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");

		//Check response
		XMLUnit.setIgnoreWhitespace(true);
		Diff myDiff = XMLUnit.compareXML(readTextFile(ATOM_GENERIC_ERROR_ENTRY_XML), responseString);
	    myDiff.overrideDifferenceListener(new IgnoreNamedElementsXMLDifferenceListener("updated"));
	    if(!myDiff.similar()) {
	    	fail(myDiff.toString());
	    }
	}
	
	@Test
	public void testWriteEntityResourceAcceptAtomSvc() throws Exception {
		EdmEntitySet ees = createMockEdmEntitySet();
		EdmDataServices mockEDS = createMockFlightEdmDataServices();		
		when(mockEDS.getEdmEntitySet(anyString())).thenReturn(ees);
		Metadata mockMetadata = createMockFlightMetadata();
		EntityResource<GenericError> er = createMockEntityResourceGenericError();
		GenericEntity<EntityResource<GenericError>> ge = new GenericEntity<EntityResource<GenericError>>(er) {};
		
		//Create provider
		MockAtomXMLProvider p = new MockAtomXMLProvider(createMockMetadataOData4j(mockEDS), mockMetadata);
		UriInfo uriInfo = mock(UriInfo.class);
		URI uri = new URI("http://localhost:8080/responder/rest/");
		when(uriInfo.getBaseUri()).thenReturn(uri);
		when(uriInfo.getPath()).thenReturn("Flight(123)");
		p.setUriInfo(uriInfo);

		//Set accept header to atomsvc+xml
		MultivaluedMap<String, Object> httpHeaders = new MultivaluedMapImpl<Object>();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, ExtendedMediaTypes.APPLICATION_ATOMSVC_XML_TYPE, httpHeaders, new ByteArrayOutputStream());

		//Make sure the response is atom+xml
		assertEquals(MediaType.APPLICATION_ATOM_XML, httpHeaders.getFirst(HttpHeaders.CONTENT_TYPE));
		
		//content length must be greater than zero
		assertThat(Integer.parseInt((String)httpHeaders.getFirst(HttpHeaders.CONTENT_LENGTH)), greaterThan(0));
	}
		
	@Test
	public void testWriteEntityResourceCustomError_AtomXML() throws Exception {
		EdmEntitySet ees = createMockEdmEntitySet();
		EdmDataServices mockEDS = createMockFlightEdmDataServices();		
		when(mockEDS.getEdmEntitySet(anyString())).thenReturn(ees);
		
		Metadata mockMetadata = createMockFlightMetadata();
		EntityResource<CustomError> er = CommandHelper.createEntityResource("CustomError", new CustomError("My custom error message."), CustomError.class);
		
        //Wrap entity resource into a JAX-RS GenericEntity instance
		GenericEntity<EntityResource<CustomError>> ge = new GenericEntity<EntityResource<CustomError>>(er) {};
		
		
		//Create provider
		MockAtomXMLProvider p = new MockAtomXMLProvider(createMockMetadataOData4j(mockEDS), mockMetadata);
		UriInfo uriInfo = mock(UriInfo.class);
		URI uri = new URI("http://localhost:8080/responder/rest/");
		when(uriInfo.getBaseUri()).thenReturn(uri);
		when(uriInfo.getPath()).thenReturn("Flight(123)");
		p.setUriInfo(uriInfo);

		//Serialize resource
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		p.writeTo(ge.getEntity(), ge.getRawType(), ge.getType(), null, MediaType.APPLICATION_ATOM_XML_TYPE, null, bos);
		String responseString = new String(bos.toByteArray(), "UTF-8");

		//Check response
		XMLUnit.setIgnoreWhitespace(true);
		Diff myDiff = XMLUnit.compareXML(readTextFile(ATOM_CUSTOM_ERROR_ENTRY_XML), responseString);
	    myDiff.overrideDifferenceListener(new IgnoreNamedElementsXMLDifferenceListener("updated"));
	    if(!myDiff.similar()) {
	    	fail(myDiff.toString());
	    }
	}

	private ResourceState createMockResourceState(String name, String entityName, boolean isCollection) {
		ResourceState state = mock(isCollection ? CollectionResourceState.class : ResourceState.class);
		when(state.getName()).thenReturn(name);
		when(state.getEntityName()).thenReturn(entityName);
		when(state.getRel()).thenReturn(isCollection ? "collection" : "item");
		return state; 
	}

	private Transition createMockTransition(ResourceState source, ResourceState target) {
		Transition.Builder builder = new Transition.Builder();
		builder.source(source);
		builder.target(target);
		builder.method("GET");
		return builder.build();
	}

	@SuppressWarnings("unchecked")
	private EntityResource<GenericError> createMockEntityResourceGenericError() {
		EntityResource<GenericError> er = mock(EntityResource.class);
				
		GenericError error = new GenericError("UPSTREAM_SERVER_UNAVAILABLE", "Failed to connect to resource manager.");
		when(er.getEntity()).thenReturn(error);
		when(er.getEntityName()).thenReturn("Flight");
		when(er.getGenericEntity()).thenReturn(new GenericEntity<EntityResource<GenericError>>(er, er.getClass().getGenericSuperclass()));
		return er;
	}	
	
	/*
	 * Read a text file
	 */
	private String readTextFile(String textFile) {
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(textFile);
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();
		String read;
		try {
			while((read = br.readLine()) != null) {
			    sb.append(read).append(System.getProperty("line.separator"));
			}
		}
		catch(IOException ioe) {
			fail(ioe.getMessage());
		}
		return sb.toString();		
	}
	
	public ResourceStateMachine mockResourceStateMachine() {
		ResourceState initial = new ResourceState("ServiceDocument", "ServiceDocument", new ArrayList<Action>(), "/");
		ResourceStateMachine rsm = new ResourceStateMachine(initial);
		return rsm;
	}
	
	@Test
	public void testBaseUri() throws URISyntaxException {
		ResourceState initial = new ResourceState("ServiceDocument", "ServiceDocument", new ArrayList<Action>(), "/");
		
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/responder/rest"));
		when(uriInfo.getPath()).thenReturn("/test");

		assertEquals("http://localhost:8080/responder/rest/", AtomXMLProvider.getBaseUri(initial, uriInfo));
	}

	@Test
	public void testBaseUriNested() throws URISyntaxException {
		ResourceState initial = new ResourceState("ServiceDocument", "ServiceDocument", new ArrayList<Action>(), "/");
		
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/responder/rest"));
		when(uriInfo.getPath()).thenReturn("/test/blah");

		assertEquals("http://localhost:8080/responder/rest/", AtomXMLProvider.getBaseUri(initial, uriInfo));
	}

	@Test
	public void testBaseUriServiceDocumentTemplate() throws URISyntaxException {
		ResourceState initial = new ResourceState("ServiceDocument", "ServiceDocument", new ArrayList<Action>(), "/{companyid}");
		
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/responder/rest"));
		when(uriInfo.getPath()).thenReturn("/MockCompany001/test");

		assertEquals("http://localhost:8080/responder/rest/MockCompany001/", AtomXMLProvider.getBaseUri(initial, uriInfo));
	}

	@Test
	public void testBaseUriNestedServiceDocumentTemplate() throws URISyntaxException {
		ResourceState initial = new ResourceState("ServiceDocument", "ServiceDocument", new ArrayList<Action>(), "/{companyid}");
		
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI("http://localhost:8080/responder/rest"));
		when(uriInfo.getPath()).thenReturn("/MockCompany001/test/blah");

		assertEquals("http://localhost:8080/responder/rest/MockCompany001/", AtomXMLProvider.getBaseUri(initial, uriInfo));
	}

	@Test
	public void testGetCurrentState() throws URISyntaxException, MethodNotAllowedException {		
        // Create rsm
        ResourceStateMachine rsm = createCollectionToItemRSM(new EntityTransformer());
        ResourceState serviceDocument = rsm.getResourceStateByName("ServiceDocument");
        ResourceState flights = rsm.getResourceStateByName("Flights");

		//Create provider
		AtomXMLProvider p = 
				new AtomXMLProvider(createMockMetadataOData4j(mock(EdmDataServices.class)), 
						mock(Metadata.class), rsm, mock(Transformer.class));

		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getAbsolutePath()).thenReturn(new URI("www.treething.com/Flights"));
		when(uriInfo.getBaseUri()).thenReturn(new URI("www.treething.com/"));		
		p.setUriInfo(uriInfo);

        Request request = mock(Request.class);		
        when(request.getMethod()).thenReturn("GET");
        p.setRequestContext(request);       
		ResourceState result = p.getCurrentState(serviceDocument, "/Flights");
		assertEquals(flights, result);
	}

	@Test
	public void testGetCurrentStateCompany() throws URISyntaxException, MethodNotAllowedException {
        // Create rsm (creates a service document with '/{companyid})
        ResourceStateMachine rsm = createCompanyCollectionToItemRSM(new EntityTransformer());
        ResourceState serviceDocument = rsm.getResourceStateByName("ServiceDocument");
        ResourceState flights = rsm.getResourceStateByName("Flights");

		//Create provider
		AtomXMLProvider p = 
				new AtomXMLProvider(createMockMetadataOData4j(mock(EdmDataServices.class)), 
						mock(Metadata.class), rsm, mock(Transformer.class));
		
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getAbsolutePath()).thenReturn(new URI("www.treething.com/123/Flights"));
		when(uriInfo.getBaseUri()).thenReturn(new URI("www.treething.com/"));		
		p.setUriInfo(uriInfo);

		
		Request request = mock(Request.class);
		when(request.getMethod()).thenReturn("GET");
		p.setRequestContext(request);		
		ResourceState result = p.getCurrentState(serviceDocument, "/Flights");
		assertEquals(flights, result);
	}

	@Test
	public void testGetCurrentStateCompanyNoPath() throws URISyntaxException, MethodNotAllowedException {
        // Create rsm (creates a service document with '/{companyid})
        ResourceStateMachine rsm = createCompanyCollectionToItemRSM(new EntityTransformer());
        ResourceState serviceDocument = rsm.getResourceStateByName("ServiceDocument");
        ResourceState flights = rsm.getResourceStateByName("Flights");

		//Create provider
		AtomXMLProvider p = 
				new AtomXMLProvider(createMockMetadataOData4j(mock(EdmDataServices.class)), 
						mock(Metadata.class), rsm, mock(Transformer.class));
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI("www.jedssheds.com"));
		when(uriInfo.getAbsolutePath()).thenReturn(new URI("www.jedssheds.com/123/Flights"));
		p.setUriInfo(uriInfo);
        Request request = mock(Request.class);
        when(request.getMethod()).thenReturn("GET");
        p.setRequestContext(request);
		ResourceState result = p.getCurrentState(serviceDocument, "Flights");
		assertEquals(flights, result);
	}

	@Test
	public void testGetCurrentStateCompanyWithPath() throws URISyntaxException, MethodNotAllowedException {
        // Create rsm (creates a service document with '/{companyid}/)
		
		List<Action> serviceDocActions = new ArrayList<Action>();
		Action action = mock(Action.class);
		when(action.getMethod()).thenReturn("GET");
		serviceDocActions.add(action);
		
		ResourceState serviceDocument = new ResourceState("ServiceDocument", "ServiceDocument", serviceDocActions, "/{companyid}/");
		
		List<Action> flightsActions = new ArrayList<Action>();
		flightsActions.add(action);
		
		ResourceState flights = new CollectionResourceState("Flight", "Flights", flightsActions, "/{companyid}/Flights");
		serviceDocument.addTransition(new Transition.Builder().method(HttpMethod.GET)
				.target(flights)
				.build());
		ResourceStateMachine rsm = new ResourceStateMachine(serviceDocument, mock(Transformer.class));

		//Create provider
		AtomXMLProvider 
		p = new AtomXMLProvider(createMockMetadataOData4j(mock(EdmDataServices.class)),
				mock(Metadata.class), rsm, mock(Transformer.class));
		p.setUriInfo(mock(UriInfo.class));
		
		UriInfo uriInfo = mock(UriInfo.class);
		when(uriInfo.getBaseUri()).thenReturn(new URI("/myapp.svc"));
		when(uriInfo.getAbsolutePath()).thenReturn(new URI("/myapp.svc/123/Flights"));
		MultivaluedMap pathParams = mock(MultivaluedMap.class);
		List <String> pathValues = new ArrayList<String>();
		pathValues.add("123");
		when(pathParams.get(eq("companyid"))).thenReturn(pathValues);
		Set<String> pathKeys = new HashSet<String>();
		pathKeys.add("companyid");
		when(pathParams.keySet()).thenReturn(pathKeys);
		when(uriInfo.getPathParameters()).thenReturn(pathParams);
		p.setUriInfo(uriInfo);
		
		Request request = mock(Request.class);
		when(request.getMethod()).thenReturn("GET");
		p.setRequestContext(request);
		ResourceState result = p.getCurrentState(serviceDocument, "Flights");
		assertEquals(flights, result);
	}
	
	@Test
	public void testLinkId() {
		ResourceState account = 
				new ResourceState("Account", "customerAccount", new ArrayList<Action>(), "/CustomerAccounts('{id}')");
		ResourceState fundsTransfers = 
				new CollectionResourceState("FundsTransfer", "FundsTransfers", new ArrayList<Action>(), "/FundsTransfers");
		Map<String, String> uriLinkageMap = new HashMap<String, String>();
		uriLinkageMap.put("DebitAcctNo", "{Acc}");
		account.addTransition(new Transition.Builder()
									.method(HttpMethod.GET)
									.target(fundsTransfers)
									.uriParameters(uriLinkageMap)
									.label("Debit funds transfers")
									.linkId("123456")
									.build());
		uriLinkageMap.clear();
		uriLinkageMap.put("CreditAcctNo", "{Acc}");
		account.addTransition(new Transition.Builder()
									.method(HttpMethod.GET)
									.target(fundsTransfers)
									.uriParameters(uriLinkageMap)
									.label("Credit funds transfers")
									.linkId("654321")
									.build());
		
		AtomXMLProvider provider = 
				new AtomXMLProvider(createMockMetadataOData4j(createMockEdmDataServices("FundsTransfers")), 
						createMockMetadata("MyModel"), mockResourceStateMachine(), mock(Transformer.class));
		List<Transition> transitions = account.getTransitions(fundsTransfers);
		assertEquals(2, transitions.size());

		List<Link> links = new ArrayList<Link>();
		links.add(new Link(transitions.get(0), transitions.get(0).getTarget().getRel(), transitions.get(0).getLabel().contains("Debit") ? "/FundsTransfers()?$filter=DebitAcctNo eq '123'" : "/FundsTransfers()?$filter=CreditAcctNo eq '123'", HttpMethod.GET));
		links.add(new Link(transitions.get(1), transitions.get(1).getTarget().getRel(), transitions.get(1).getLabel().contains("Debit") ? "/FundsTransfers()?$filter=DebitAcctNo eq '123'" : "/FundsTransfers()?$filter=CreditAcctNo eq '123'", HttpMethod.GET));
		EntityResource<OEntity> entityResource = new EntityResource<OEntity>(mock(OEntity.class));
		entityResource.setLinks(links);
		provider.processLinks(entityResource);
		Collection<Link> processedLinks = entityResource.getLinks();
		assertEquals(2, processedLinks.size());
		Iterator<Link> iterator = processedLinks.iterator();
		Link debitLink = iterator.next();
		Link creditLink = iterator.next();
		
		assertEquals("123456", debitLink.getLinkId());
		assertEquals("654321", creditLink.getLinkId());
	}
	
	@Test
    public void testGetAbsolutePath() throws URISyntaxException, UnsupportedEncodingException {
	    
	    String pathDecoded = "resourceTest('1234://ABC')";
	    String pathEncodedUTF8 = new String(pathDecoded.getBytes("UTF-8"), "UTF-8");
	    String pathEncodedWrong = new String(pathDecoded.getBytes("UTF-8"), "x-UTF-16LE-BOM");
	    
	    String absPath = null;
	    
	    UriInfo uriInfo = mock(UriInfo.class);
	    URI uri = new URI("");
	    when(uriInfo.getBaseUri()).thenReturn(uri);
	    
	    // Test encoded string utf8
	    when(uriInfo.getPath()).thenReturn(pathEncodedUTF8);	    
	    absPath = AtomXMLProvider.getAbsolutePath(uriInfo);
	    assertNotNull(absPath);
	    assertEquals(pathDecoded, absPath);
	    
	    // Test encoded string utf16
	    when(uriInfo.getPath()).thenReturn(pathEncodedWrong);
        absPath = AtomXMLProvider.getAbsolutePath(uriInfo);
        assertNotNull(absPath);
        assertNotSame(pathDecoded, absPath);
        
        // Test encoded null string
        when(uriInfo.getPath()).thenReturn(null);
        absPath = AtomXMLProvider.getAbsolutePath(uriInfo);
        assertNotNull(absPath);
        assertEquals("null", absPath);
	}

	private Link createLink(ResourceStateMachine rsm, Transition transition, Object entity,
			MultivaluedMap<String, String> pathParameters, MultivaluedMap<String, String> queryParameters) {
		LinkGenerator linkGenerator = new LinkGeneratorImpl(rsm, transition, null);
		Collection<Link> links = linkGenerator.createLink(pathParameters, queryParameters, entity);
		return (!links.isEmpty()) ? links.iterator().next() : null;
	}
}
