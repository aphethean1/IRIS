package com.temenos.interaction.winkext;

/*
 * #%L
 * interaction-winkext
 * %%
 * Copyright (C) 2012 - 2015 Temenos Holdings N.V.
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
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.wink.common.model.multipart.InMultiPart;
import org.junit.Test;
import org.mockito.Mockito;

import com.temenos.interaction.core.command.CommandController;
import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.hypermedia.Event;
import com.temenos.interaction.core.hypermedia.MethodNotAllowedException;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.ResourceStateMachine;
import com.temenos.interaction.core.hypermedia.ResourceStateProvider;
import com.temenos.interaction.core.resource.EntityResource;

/**
 * @author mlambert
 *
 */
public class TestLazyResourceDelegate {
    @Test
    public void testRegularGet() throws MethodNotAllowedException {             
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        
        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(myResourceState);
        when(resourceStateProvider.getResourceState(eq("GET"), eq("/myResource"))).thenReturn(myResourceState);
        when(resourceStateProvider.getResourceStateId(eq("GET"), anyString())).thenReturn("resource");
        
        ResourceState rootState = mock(ResourceState.class);
        when(rootState.getName()).thenReturn("root");
        when(rootState.getPath()).thenReturn("/");    
        when(resourceStateProvider.getResourceState(eq("GET"), eq("/"))).thenReturn(rootState);
        
        Map<String,Set<String>> resourceMethodsByState = new HashMap<String, Set<String>>();
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("GET");
        
        resourceMethodsByState.put("resource", myResourceMethods);
        resourceMethodsByState.put("root", myResourceMethods);
        when(resourceStateProvider.getResourceMethodsByState()).thenReturn(resourceMethodsByState);
        
        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        
        Set<String> rootMethods = new HashSet<String>();
        rootMethods.add("GET");
        when(interactionsByPath.get(eq("/"))).thenReturn(rootMethods);
                
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getQueryParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));        
        when(uriInfo.getPath(eq(false))).thenReturn("myResource");
        
        lazyResourceDelegate.get(headers, id, uriInfo);
        
        verify(resourceStateProvider).getResourceStateId("GET", "/myResource");
        
        uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getQueryParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));        
        when(uriInfo.getPath(eq(false))).thenReturn("");
        
        lazyResourceDelegate.get(headers, id, uriInfo);
        
        verify(resourceStateProvider).getResourceStateId("GET", "/");
        verify(resourceStateProvider, Mockito.times(2)).getResourceState("resource");        
    }
    
    /*
     * LazyResourceDelegate assumes that a (resource,method) pair is not registered in the ResourceStateMachine
     * when the ResourceStateProvider finds the resource but the requested method is not registered with it.
     */
    @Test
    public void testGetWithUnregisteredResource() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        
        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(myResourceState);
        when(resourceStateProvider.getResourceState(eq("GET"), eq("/myResource"))).thenReturn(myResourceState);
        when(resourceStateProvider.getResourceStateId(eq("GET"), anyString())).thenReturn("resource");

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("GET");
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);

        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getQueryParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));        
        when(uriInfo.getPath(eq(false))).thenReturn("");
        
        lazyResourceDelegate.get(headers, id, uriInfo);
        
        verify(resourceStateMachine).register(myResourceState, "GET");
    }
    
    @Test
    public void testGetWhenResourceNotFound() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);

        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.getResourceStateId(eq("GET"), anyString())).thenReturn(null);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("GET");
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);

        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/",
                mock(HashSet.class));

        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);

        Response status = lazyResourceDelegate.get(headers, id, uriInfo);

        assertEquals(404, status.getStatus());
    }
    
    @Test
    public void testGetWithMethodNotAllowed() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        
        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(myResourceState);
        when(resourceStateProvider.getResourceState(eq("GET"), eq("/myResource"))).thenReturn(myResourceState);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);

        Set<String> allowedMethods = new HashSet<String>();
        allowedMethods.add("POST");
        when(resourceStateProvider.getResourceStateId(eq("GET"), anyString())).thenThrow(new MethodNotAllowedException(allowedMethods));
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("GET");
        
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);
        
        Response status = lazyResourceDelegate.get(headers, id, uriInfo);
        
        assertEquals(405, status.getStatus());
    }
    
	@Test
	public void testRegularPost() throws MethodNotAllowedException {				
		ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
		ResourceState state = mock(ResourceState.class);
        when(state.getName()).thenReturn("resource");		
		when(state.getPath()).thenReturn("/myResource");	
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(state);
        when(resourceStateProvider.getResourceState(eq("POST"), anyString())).thenReturn(state);        
        when(resourceStateProvider.getResourceStateId(eq("POST"), anyString())).thenReturn("resource");

		ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
		Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
		Set<String> methods = new HashSet<String>();
		methods.add("POST");
		
        Map<String,Set<String>> resourceMethodsByState = new HashMap<String, Set<String>>();               
        resourceMethodsByState.put("resource", methods);
        when(resourceStateProvider.getResourceMethodsByState()).thenReturn(resourceMethodsByState);
		
		when(interactionsByPath.get(anyString())).thenReturn(methods);
		when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
		
		LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
				resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));	
		
		HttpHeaders headers = mock(HttpHeaders.class);
		String id = "123";
		UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));	
        when(uriInfo.getQueryParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));        
		when(uriInfo.getPath(eq(false))).thenReturn("myResource");
		EntityResource<?> resource = mock(EntityResource.class);
	
		lazyResourceDelegate.post(headers, id, uriInfo, resource);
		
		verify(resourceStateProvider).getResourceStateId("POST", "/myResource");		
	    verify(resourceStateProvider).getResourceState("resource");
	}

    @Test
    public void testPostWhenResourceNotFound() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);

        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.getResourceStateId(eq("POST"), anyString())).thenReturn(null);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("POST");
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);

        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/",
                mock(HashSet.class));

        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);
        EntityResource<?> resource = mock(EntityResource.class);

        Response status = lazyResourceDelegate.post(headers, id, uriInfo, resource);

        assertEquals(404, status.getStatus());
    }
    
    @Test
    public void testPostWithMethodNotAllowed() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        
        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(myResourceState);
        when(resourceStateProvider.getResourceState(eq("POST"), eq("/myResource"))).thenReturn(myResourceState);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);

        Set<String> allowedMethods = new HashSet<String>();
        allowedMethods.add("GET");
        when(resourceStateProvider.getResourceStateId(eq("POST"), anyString())).thenThrow(new MethodNotAllowedException(allowedMethods));
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("POST");
        
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);
        EntityResource<?> resource = mock(EntityResource.class);
        
        Response status = lazyResourceDelegate.post(headers, id, uriInfo, resource);
        
        assertEquals(405, status.getStatus());
    }

	@Test
	public void testMultiPartPost() throws MethodNotAllowedException {				
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        ResourceState state = mock(ResourceState.class);
        when(state.getName()).thenReturn("resource");        
        when(state.getPath()).thenReturn("/myResource");
        when(state.getEntityName()).thenReturn("dummy");
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(state);
        when(resourceStateProvider.getResourceState(eq("POST"), anyString())).thenReturn(state);        
        when(resourceStateProvider.getResourceStateId(eq("POST"), anyString())).thenReturn("resource");

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        Set<String> methods = new HashSet<String>();
        methods.add("POST");
        
        Map<String,Set<String>> resourceMethodsByState = new HashMap<String, Set<String>>();        
        resourceMethodsByState.put("resource", methods);
        when(resourceStateProvider.getResourceMethodsByState()).thenReturn(resourceMethodsByState);
        
        when(interactionsByPath.get(anyString())).thenReturn(methods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);

        when(resourceStateMachine.determineState(any(Event.class), anyString())).thenReturn(state);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));     
        when(uriInfo.getQueryParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));        
        when(uriInfo.getPath(eq(false))).thenReturn("myResource");
        
        InMultiPart inMP = mock(InMultiPart.class);        
        lazyResourceDelegate.post(headers, uriInfo, inMP);
        
        verify(resourceStateProvider).getResourceStateId("POST", "/myResource");      			
        verify(resourceStateProvider).getResourceState("resource");
	}

    @Test
    public void testMultiPartPostWhenResourceNotFound() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);

        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.getResourceStateId(eq("POST"), anyString())).thenReturn(null);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("POST");
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);

        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/",
                mock(HashSet.class));

        HttpHeaders headers = mock(HttpHeaders.class);
        UriInfo uriInfo = mock(UriInfo.class);

        InMultiPart inMP = mock(InMultiPart.class);        
        Response status = lazyResourceDelegate.post(headers, uriInfo, inMP);

        assertEquals(404, status.getStatus());
    }
    
    @Test
    public void testMultiPartPostWithMethodNotAllowed() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        
        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(myResourceState);
        when(resourceStateProvider.getResourceState(eq("POST"), eq("/myResource"))).thenReturn(myResourceState);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);

        Set<String> allowedMethods = new HashSet<String>();
        allowedMethods.add("GET");
        when(resourceStateProvider.getResourceStateId(eq("POST"), anyString())).thenThrow(new MethodNotAllowedException(allowedMethods));
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("POST");
        
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        UriInfo uriInfo = mock(UriInfo.class);
        
        InMultiPart inMP = mock(InMultiPart.class);        
        Response status = lazyResourceDelegate.post(headers, uriInfo, inMP);
        
        assertEquals(405, status.getStatus());
    }

    @Test
    public void testPostWithFormParams() throws MethodNotAllowedException {                
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        ResourceState state = mock(ResourceState.class);
        when(state.getName()).thenReturn("resource");       
        when(state.getPath()).thenReturn("/myResource");    
        when(state.getEntityName()).thenReturn("resource");       
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(state);
        when(resourceStateProvider.getResourceState(eq("POST"), anyString())).thenReturn(state);        
        when(resourceStateProvider.getResourceStateId(eq("POST"), anyString())).thenReturn("resource");

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        Set<String> methods = new HashSet<String>();
        methods.add("POST");
        
        Map<String,Set<String>> resourceMethodsByState = new HashMap<String, Set<String>>();               
        resourceMethodsByState.put("resource", methods);
        when(resourceStateProvider.getResourceMethodsByState()).thenReturn(resourceMethodsByState);
        
        when(interactionsByPath.get(anyString())).thenReturn(methods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
        
        when(resourceStateMachine.determineState(any(Event.class), any(String.class))).thenReturn(state);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));  
        when(uriInfo.getQueryParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));        
        when(uriInfo.getPath(eq(false))).thenReturn("myResource");
        MultivaluedMap<String, String> formMap = mock(MultivaluedMap.class);
    
        lazyResourceDelegate.post(headers, id, uriInfo, formMap);
        
        verify(resourceStateProvider).getResourceStateId("POST", "/myResource");        
        verify(resourceStateProvider).getResourceState("resource");
    }

    @Test
    public void testPostWithFormParamsWhenResourceNotFound() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);

        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.getResourceStateId(eq("POST"), anyString())).thenReturn(null);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("GET");
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);

        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/",
                mock(HashSet.class));

        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);
        MultivaluedMap<String, String> formMap = mock(MultivaluedMap.class);
        
        Response status = lazyResourceDelegate.post(headers, id, uriInfo, formMap);

        assertEquals(404, status.getStatus());
    }
    
    @Test
    public void testPostWithFormParamsWithMethodNotAllowed() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        
        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(myResourceState);
        when(resourceStateProvider.getResourceState(eq("POST"), eq("/myResource"))).thenReturn(myResourceState);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);

        Set<String> allowedMethods = new HashSet<String>();
        allowedMethods.add("GET");
        when(resourceStateProvider.getResourceStateId(eq("POST"), anyString())).thenThrow(new MethodNotAllowedException(allowedMethods));
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("POST");
        
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);
        MultivaluedMap<String, String> formMap = mock(MultivaluedMap.class);
        
        Response status = lazyResourceDelegate.post(headers, id, uriInfo, formMap);
        
        assertEquals(405, status.getStatus());
    }

	@Test
	public void testRegularPut() throws MethodNotAllowedException {				
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        ResourceState state = mock(ResourceState.class);
        when(state.getName()).thenReturn("resource");        
        when(state.getPath()).thenReturn("/myResource");   
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(state);
        when(resourceStateProvider.getResourceState(eq("PUT"), anyString())).thenReturn(state);
        when(resourceStateProvider.getResourceStateId(eq("PUT"), anyString())).thenReturn("resource");
        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        Set<String> methods = new HashSet<String>();
        methods.add("PUT");
        
        Map<String,Set<String>> resourceMethodsByState = new HashMap<String, Set<String>>();
        
        resourceMethodsByState.put("resource", methods);
        when(resourceStateProvider.getResourceMethodsByState()).thenReturn(resourceMethodsByState);
        
        when(interactionsByPath.get(anyString())).thenReturn(methods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(eq(false))).thenReturn(mock(MultivaluedMap.class)); 
        when(uriInfo.getQueryParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));        
        when(uriInfo.getPath(eq(false))).thenReturn("myResource");
        EntityResource<?> resource = mock(EntityResource.class);
        
        lazyResourceDelegate.put(headers, id, uriInfo, resource);  
        
        verify(resourceStateProvider).getResourceStateId("PUT", "/myResource");
        verify(resourceStateProvider).getResourceState("resource");
	}

    @Test
    public void testPutWhenResourceNotFound() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);

        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.getResourceStateId(eq("GET"), anyString())).thenReturn(null);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("GET");
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);

        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/",
                mock(HashSet.class));

        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);
        EntityResource<?> resource = mock(EntityResource.class);

        Response status = lazyResourceDelegate.put(headers, id, uriInfo, resource);  

        assertEquals(404, status.getStatus());
    }
    
    @Test
    public void testPutWithMethodNotAllowed() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        
        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(myResourceState);
        when(resourceStateProvider.getResourceState(eq("PUT"), eq("/myResource"))).thenReturn(myResourceState);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);

        Set<String> allowedMethods = new HashSet<String>();
        allowedMethods.add("POST");
        when(resourceStateProvider.getResourceStateId(eq("PUT"), anyString())).thenThrow(new MethodNotAllowedException(allowedMethods));
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("GET");
        
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);
        EntityResource<?> resource = mock(EntityResource.class);

        Response status = lazyResourceDelegate.put(headers, id, uriInfo, resource);  
        
        assertEquals(405, status.getStatus());
    }
    
    @Test
    public void testMultiPartPut() throws MethodNotAllowedException {               
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        ResourceState state = mock(ResourceState.class);
        when(state.getPath()).thenReturn("/myResource");
        when(state.getName()).thenReturn("resource");        
        when(state.getEntityName()).thenReturn("dummy");
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(state);
        when(resourceStateProvider.getResourceState(eq("PUT"), anyString())).thenReturn(state);
        when(resourceStateProvider.getResourceStateId(eq("PUT"), anyString())).thenReturn("resource");

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        Set<String> methods = new HashSet<String>();
        methods.add("PUT");
        
        Map<String,Set<String>> resourceMethodsByState = new HashMap<String, Set<String>>();        
        resourceMethodsByState.put("resource", methods);
        when(resourceStateProvider.getResourceMethodsByState()).thenReturn(resourceMethodsByState);
        
        when(interactionsByPath.get(anyString())).thenReturn(methods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);

        when(resourceStateMachine.determineState(any(Event.class), anyString())).thenReturn(state);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));    
        when(uriInfo.getQueryParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));        
        when(uriInfo.getPath(eq(false))).thenReturn("myResource");
        
        InMultiPart inMP = mock(InMultiPart.class);        
        lazyResourceDelegate.put(headers, uriInfo, inMP);
        
        verify(resourceStateProvider).getResourceStateId("PUT", "/myResource");                  
        verify(resourceStateProvider).getResourceState("resource");
    }

    @Test
    public void testMultiPartPutWhenResourceNotFound() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);

        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(myResourceState.getEntityName()).thenReturn("dummy");
        when(resourceStateProvider.getResourceStateId(eq("PUT"), anyString())).thenReturn(null);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("PUT");
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);

        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/",
                mock(HashSet.class));

        HttpHeaders headers = mock(HttpHeaders.class);
        UriInfo uriInfo = mock(UriInfo.class);
        InMultiPart inMP = mock(InMultiPart.class);        

        Response status = lazyResourceDelegate.put(headers, uriInfo, inMP);  

        assertEquals(404, status.getStatus());
    }
    
    @Test
    public void testMultiPartPutWithMethodNotAllowed() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        
        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(myResourceState.getEntityName()).thenReturn("dummy");
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(myResourceState);
        when(resourceStateProvider.getResourceState(eq("PUT"), eq("/myResource"))).thenReturn(myResourceState);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);

        Set<String> allowedMethods = new HashSet<String>();
        allowedMethods.add("POST");
        when(resourceStateProvider.getResourceStateId(eq("PUT"), anyString())).thenThrow(new MethodNotAllowedException(allowedMethods));
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("PUT");
        
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        UriInfo uriInfo = mock(UriInfo.class);
        InMultiPart inMP = mock(InMultiPart.class);        

        Response status = lazyResourceDelegate.put(headers, uriInfo, inMP);  
        
        assertEquals(405, status.getStatus());
    }
 
    @Test
    public void testDelete() throws MethodNotAllowedException {              
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        ResourceState state = mock(ResourceState.class);
        when(state.getName()).thenReturn("resource");        
        when(state.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(state);
        when(resourceStateProvider.getResourceState(eq("DELETE"), anyString())).thenReturn(state);
        when(resourceStateProvider.getResourceStateId(eq("DELETE"), anyString())).thenReturn("resource");

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        Set<String> methods = new HashSet<String>();
        methods.add("DELETE");
        
        Map<String,Set<String>> resourceMethodsByState = new HashMap<String, Set<String>>();               
        resourceMethodsByState.put("resource", methods);
        when(resourceStateProvider.getResourceMethodsByState()).thenReturn(resourceMethodsByState);
        
        when(interactionsByPath.get(anyString())).thenReturn(methods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));    
        when(uriInfo.getQueryParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));        
        when(uriInfo.getPath(eq(false))).thenReturn("myResource");
        EntityResource<?> resource = mock(EntityResource.class);
        
        lazyResourceDelegate.delete(headers, id, uriInfo);  
        
        verify(resourceStateProvider).getResourceStateId("DELETE", "/myResource");
        verify(resourceStateProvider).getResourceState("resource");
    }

    @Test
    public void testDeleteWhenResourceNotFound() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);

        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.getResourceStateId(eq("DELETE"), anyString())).thenReturn(null);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("DELETE");
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);

        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/",
                mock(HashSet.class));

        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);

        Response status = lazyResourceDelegate.delete(headers, id, uriInfo);  

        assertEquals(404, status.getStatus());
    }
    
    @Test
    public void testDeleteWithMethodNotAllowed() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        
        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(myResourceState);
        when(resourceStateProvider.getResourceState(eq("DELETE"), eq("/myResource"))).thenReturn(myResourceState);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);

        Set<String> allowedMethods = new HashSet<String>();
        allowedMethods.add("GET");
        when(resourceStateProvider.getResourceStateId(eq("DELETE"), anyString())).thenThrow(new MethodNotAllowedException(allowedMethods));
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("DELETE");
        
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);

        Response status = lazyResourceDelegate.delete(headers, id, uriInfo);  
        
        assertEquals(405, status.getStatus());
    }

    @Test
    public void testOptions() throws MethodNotAllowedException {              
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        ResourceState state = mock(ResourceState.class);
        when(state.getName()).thenReturn("resource");        
        when(state.getPath()).thenReturn("/myResource");    
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(state);
        when(resourceStateProvider.getResourceState(eq("OPTIONS"), anyString())).thenReturn(state);
        when(resourceStateProvider.getResourceStateId(eq("OPTIONS"), anyString())).thenReturn("resource");

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        Set<String> methods = new HashSet<String>();
        methods.add("OPTIONS");
        
        Map<String,Set<String>> resourceMethodsByState = new HashMap<String, Set<String>>();               
        resourceMethodsByState.put("resource", methods);
        when(resourceStateProvider.getResourceMethodsByState()).thenReturn(resourceMethodsByState);
        
        when(interactionsByPath.get(anyString())).thenReturn(methods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
        when(resourceStateMachine.determineState(any(Event.class), anyString())).thenReturn(state);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));     
        when(uriInfo.getQueryParameters(eq(false))).thenReturn(mock(MultivaluedMap.class));                
        when(uriInfo.getPath(eq(false))).thenReturn("myResource");
        
        lazyResourceDelegate.options(headers, id, uriInfo);  
        
        verify(resourceStateProvider).getResourceStateId("OPTIONS", "/myResource");
        verify(resourceStateProvider).getResourceState("resource");
    }

    @Test
    public void testOptionsWhenResourceNotFound() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);

        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.getResourceStateId(eq("OPTIONS"), anyString())).thenReturn(null);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("OPTIONS");
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);

        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/",
                mock(HashSet.class));

        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);

        Response status = lazyResourceDelegate.options(headers, id, uriInfo);  

        assertEquals(404, status.getStatus());
    }
    
    @Test
    public void testOptionsWithMethodNotAllowed() throws MethodNotAllowedException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        
        ResourceState myResourceState = mock(ResourceState.class);
        when(myResourceState.getName()).thenReturn("resource");
        when(myResourceState.getPath()).thenReturn("/myResource");
        when(resourceStateProvider.isLoaded("resource")).thenReturn(true);
        when(resourceStateProvider.getResourceState("resource")).thenReturn(myResourceState);
        when(resourceStateProvider.getResourceState(eq("OPTIONS"), eq("/myResource"))).thenReturn(myResourceState);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);

        Set<String> allowedMethods = new HashSet<String>();
        allowedMethods.add("GET");
        when(resourceStateProvider.getResourceStateId(eq("OPTIONS"), anyString())).thenThrow(new MethodNotAllowedException(allowedMethods));
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("OPTIONS");
        
        Map<String, Set<String>> interactionsByPath = mock(HashMap.class);
        when(interactionsByPath.get(eq("/myResource"))).thenReturn(myResourceMethods);
        when(resourceStateMachine.getInteractionByPath()).thenReturn(interactionsByPath);
        
        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", mock(HashSet.class));  
        
        HttpHeaders headers = mock(HttpHeaders.class);
        String id = "123";
        UriInfo uriInfo = mock(UriInfo.class);

        Response status = lazyResourceDelegate.options(headers, id, uriInfo);  
        
        assertEquals(405, status.getStatus());
    }
	
	@Test
	public void testAddResource() throws MethodNotAllowedException, IOException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);
        
        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);
        
        Set<String> myResourceMethods = new HashSet<String>();
        myResourceMethods.add("GET");

        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/", myResourceMethods);  
        
        String strMapBefore = lazyResourceDelegate.getBeanName();
        Map<String, Set<String>> parsedMethodsBefore = parseStringMap(strMapBefore);
        
        for(String method : myResourceMethods) {
            assertTrue(parsedMethodsBefore.get("test").contains(method));
        }

        Set<String> newMethods = new HashSet<String>();
        newMethods.add("PUT");
        newMethods.add("POST");
        
        lazyResourceDelegate.addResource("test", newMethods);

        String strMapAfter = lazyResourceDelegate.getBeanName();
        Map<String, Set<String>> parsedMethodsAfter = parseStringMap(strMapAfter);
        
        // add the originally added method
        newMethods.add("GET");
        for(String method : newMethods) {
            assertTrue(parsedMethodsAfter.get("test").contains(method));
        }
	}

    @Test
    public void testAddResourceWithNullMethods() throws MethodNotAllowedException, IOException {
        ResourceStateProvider resourceStateProvider = mock(ResourceStateProvider.class);

        ResourceStateMachine resourceStateMachine = mock(ResourceStateMachine.class);

        LazyResourceDelegate lazyResourceDelegate = new LazyResourceDelegate(resourceStateMachine,
                resourceStateProvider, mock(CommandController.class), mock(Metadata.class), "test", "/",
                null);

        lazyResourceDelegate.getBeanName();

        Set<String> newMethods = new HashSet<String>();
        newMethods.add("PUT");
        newMethods.add("POST");

        lazyResourceDelegate.addResource("test", newMethods);

        String strMapAfter = lazyResourceDelegate.getBeanName();
        Map<String, Set<String>> parsedMethodsAfter = parseStringMap(strMapAfter);

        for (String method : newMethods) {
            assertTrue(parsedMethodsAfter.get("test").contains(method));
        }
    }

	private Map<String, Set<String>> parseStringMap(String strMap) {
	    // remove curly brackets
        strMap = strMap.substring(1, strMap.length() - 1);
        
        Map<String, Set<String>> parsedMethods = new HashMap<String, Set<String>>();
        String[] keyValues = strMap.split("=");
        assertEquals(keyValues[0], "test");
        String methods = keyValues[1].substring(1, keyValues[1].length() - 1);
        Set<String> parsedSet = new HashSet<String>();
        for(final String method : methods.split(",")) {
            parsedSet.add(method.trim());
        }
        parsedMethods.put(keyValues[0], parsedSet);
        return parsedMethods;
	}
}
