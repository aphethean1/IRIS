package com.temenos.interaction.springdsl;

/*
 * #%L
 * interaction-springdsl
 * %%
 * Copyright (C) 2012 - 2014 Temenos Holdings N.V.
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

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import com.temenos.interaction.core.hypermedia.Event;
import com.temenos.interaction.core.hypermedia.MethodNotAllowedException;
import com.temenos.interaction.core.hypermedia.PathTree;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.ResourceStateProvider;
import com.temenos.interaction.core.resource.ConfigLoader;

public class SpringDSLResourceStateProvider implements ResourceStateProvider, DynamicRegistrationResourceStateProvider {
    private final Logger logger = LoggerFactory.getLogger(SpringDSLResourceStateProvider.class);

	private ConcurrentMap<String, ResourceState> resources = new ConcurrentHashMap<String, ResourceState>();

	protected StateRegisteration stateRegisteration;
	
	private ConfigLoader configLoader = new ConfigLoader();

    /**
     * Map of ResourceState bean names, to paths.
     */
	protected Properties beanMap;

	protected boolean initialised = false;
	
	/**
	 * Map of paths to state names
	 */
	protected Map<String, Set<String>> resourceStatesByPath = new HashMap<String, Set<String>>();
	/**
	 * Map of request to state names
	 */
	protected Map<String, String> resourceStatesByRequest = new HashMap<String, String>();
	/**
	 * Map of resource methods where state name is the key
	 */
	protected Map<String, Set<String>> resourceMethodsByState = new HashMap<String, Set<String>>();
	/**
	 * Map to a resource path where the state name is the key
	 */
	protected Map<String, String> resourcePathsByState = new HashMap<String, String>();

	PathTree pathTree = new PathTree();
    
	public SpringDSLResourceStateProvider() {}
	public SpringDSLResourceStateProvider(Properties beanMap) {
		this.beanMap = beanMap;
	}

	public void setResourceMap(Properties beanMap) {
		this.beanMap = beanMap;
	}
	
	@Autowired(required = false)
	public void setConfigLoader(ConfigLoader configLoader) {
		this.configLoader = configLoader;
	}

	protected void initialise() {
		if (initialised)
			return;
		for (Object stateObj : beanMap.keySet()) {
			storeState(stateObj, null);
		}
				
		initialised = true;
	}

	protected void storeState(Object stateObj, String binding) {
		String stateName = stateObj.toString();
		
		// binding is [GET,PUT /thePath]
		if (binding == null){
			binding = beanMap.getProperty(stateName);
		}
		
		// split into methods and path
		String[] strs = binding.split(" ");
		String methodPart = strs[0];
		String path = strs[1];
		// methods
		String[] methodsStrs = methodPart.split(",");
		// path
		resourcePathsByState.put(stateName, path);
		// methods
		Set<String> methodSet = resourceMethodsByState.get(stateName);
		
		if (methodSet == null) {
		    methodSet = new HashSet<String>();
		}
		
        for(String methodStr: methodsStrs) {
            methodSet.add(methodStr);
            
            pathTree.put(path, methodStr, stateName);
        }
		
		resourceMethodsByState.put(stateName, methodSet);
		
		for (String method : methodSet) {
			String request = method + " " + path;
			logger.debug("Binding ["+stateName+"] to ["+request+"]");
			String found = resourceStatesByRequest.get(request);
			if (found != null) {
				logger.error("Multiple states bound to the same request ["+request+"], overriding ["+found+"] with ["+stateName+"]");
			}
			resourceStatesByRequest.put(request, stateName);
		}

		Set<String> stateNames = resourceStatesByPath.get(path);
		if (stateNames == null) {
			stateNames = new HashSet<String>();
		}
		stateNames.add(stateName);
		resourceStatesByPath.put(path, stateNames);		
	}
	
	

	public void addState(String stateObj, Properties properties) {
		if (initialised) {
			String stateName = stateObj.toString();

			// binding is [GET,PUT /thePath]
			String binding = properties.getProperty(stateName);

			// split into methods and path
			String[] strs = binding.split(" ");
			String methodPart = strs[0];
			String path = strs[1];

			// methods
			String[] methods = methodPart.split(",");

			logger.info("Attempting to register state: " + stateName + " methods: " + methods + " path: " + path);

			// preemptive loading
			ResourceState state = getResourceState(stateName);
			
			if (state != null){
				storeState(stateName, binding);
				
				Set<String> methodSet = new HashSet<String>();
				
		        for(String methodStr: methods) {
		            methodSet.add(methodStr);
		        }
			}
		}
	}
	
	public void unload(String name) {	    
	    resources.remove(name);
	}

	@Override
	public boolean isLoaded(String name) {
		return resources.containsKey(name);
	}

	@Override
	public ResourceState getResourceState(String resourceStateName) {
		ResourceState result = null;

		try {
			if (resourceStateName != null) {
				// Try to retrieve the resource state
				result = resources.get(resourceStateName);

				if (result == null) {
					// Resource state has not already been loaded so attempt to load it
					ResourceStateLoad newState = new ResourceStateLoad(resourceStateName);
					newState.load();
					if ( newState.isLoaded() ) {
						result = newState.loaded();						
					} else {
						logger.error( newState.toString() );
					}
				}
			}
		} catch (BeansException e) {
			logger.error("Failed to load ["+resourceStateName+"]", e);
		}

		return result;
	}

	@Override
	public ResourceState determineState(Event event, String resourcePath) {
		initialise();
		String request = event.getMethod() + " " + resourcePath;
		String stateName = resourceStatesByRequest.get(request);
		if (stateName != null){
			logger.debug("Found state ["+stateName+"] for ["+request+"]");
			return getResourceState(stateName);
		}else{
			logger.warn("NOT Found state ["+stateName+"] for ["+request+"]");
			return null;
		}
	}

	@Override
	public Map<String, Set<String>> getResourceStatesByPath() {
		initialise();
		return resourceStatesByPath;
	}

	public Map<String, Set<String>> getResourceMethodsByState() {
		initialise();
		return resourceMethodsByState;
	}

	public Map<String, String> getResourcePathsByState() {
		initialise();
		return resourcePathsByState;
	}

	protected Map<String, Set<String>> getResourceStatesByPath(Properties beanMap) {
		initialise();
		return resourceStatesByPath;
	}

	@Override
	public void setStateRegisteration(StateRegisteration registerState) {
		this.stateRegisteration = registerState;
	}


	/** Load a Resource State from the appropriate location (file or classpath).
	 *  There are likely to be several possibilities for where the requested
	 *  resource could be: this tracks all of them so that they can be logged
	 *  if the resource is not found.
	 */
	private class ResourceStateLoad {

		private String state;
		private List<String> attempts = new ArrayList<String>(2);
		private String foundFile;
		private ResourceState result;

		/** Define a resource state to load. Must call load() to actually load it. */
		public ResourceStateLoad( String resourceStateName ) {
			state = resourceStateName;
		}

		/** Was the load operation successful?
		 */
		public boolean isLoaded() {
			return ( result != null );
		}

		/** Get the Resource State from a successful load
		 */
		public ResourceState loaded() {
			return result;
		}

		/** Description of the state of this load, intended for logging
		 */
		public String toString() {
			if ( isLoaded() ) {
				return "Loaded Resource State " + state + " from " + foundFile;
			} else if (attempts.size()==0) {
				return "Not-loaded Resource State " + state;
			} else if (foundFile != null ) {
				return "State " + state + " not found in " + foundFile;
			}

			StringBuilder msg = new StringBuilder( "Failed to load resource state " );
			msg.append( state );
			msg.append( ". Attempted to load from " );
			for ( int i = 0 ; i < attempts.size() ; ++i ) {
				if ( i > 0 ) msg.append(", ");
				msg.append("[");
				msg.append(attempts.get(i));
				msg.append("]");
			}
			return msg.toString();
		}

		/** Load the configured resource state.
		 *  Use this method only once.
		 *  call isLoaded() to discover success or failure
		 */
		public void load() {
			// check that this has not been called before
			if ( attempts.size() > 0 )
				throw new IllegalStateException( "repeated call to load()" );

			String tmpResourceStateName = state;
			String tmpResourceName = tmpResourceStateName;

			if(tmpResourceName.contains("-")) {
				tmpResourceName = tmpResourceName.substring(0, tmpResourceName.indexOf("-"));
			}

			String beanXml = "IRIS-" + tmpResourceName + "-PRD.xml";

			// Attempt to create Spring context based on current resource filename pattern
			ApplicationContext context = createApplicationContext(beanXml);

			if (context == null) {
				// Failed to create Spring context using current resource filename pattern so use old pattern
				int pos = tmpResourceName.lastIndexOf("_");

				if (pos > 3){
					tmpResourceName = tmpResourceName.substring(0, pos);
					beanXml = "IRIS-" + tmpResourceName + "-PRD.xml";

					context = createApplicationContext(beanXml);

					if (context != null) {
						// Successfully created Spring context using old resource filename pattern

						// Convert resource state name to old resource name format
						pos = tmpResourceStateName.lastIndexOf("-");

						if (pos < 0){
							pos = tmpResourceStateName.lastIndexOf("_");

							if (pos > 0){
								tmpResourceStateName = tmpResourceStateName.substring(0, pos) + "-" + tmpResourceStateName.substring(pos+1);
							}
						}
					}
				}
			}

			if(context != null) {
				result = loadAllResourceStatesFromFile(context, tmpResourceStateName);
			}
		}

		private ResourceState loadAllResourceStatesFromFile(ApplicationContext context, String resourceState) {
			Map<String,ResourceState> tmpResources = context.getBeansOfType(ResourceState.class);

			// Save all the loaded resources into the main resource state cache
			resources.putAll(tmpResources);

			ResourceState result = null;

			if(tmpResources.containsKey(resourceState)) {
				result = tmpResources.get(resourceState);
			}

			return result;
		}


		/**
		 * @param beanXml the filename to locate
		 * @return a Spring ApplicationContext
		 */
		private ApplicationContext createApplicationContext(String beanXml) {
			ApplicationContext result = null;

			String irisResourceDirPath = configLoader.getIrisConfigDirPath();
			
			if(irisResourceDirPath == null) {
				// Try and load the resource from the classpath
				String description = "classpath:" + beanXml;
				attempts.add(description);
				result = new ClassPathXmlApplicationContext(new String[] {beanXml});
				if ( result != null ) foundFile = description;
			} else {
				// Try and load the resource from the file system as a resource directory has been specified
				File irisResourceDir = new File(irisResourceDirPath);

				if(irisResourceDir.exists() && irisResourceDir.isDirectory()) {
					File file = new File(irisResourceDir, beanXml);
					
					
					String path = "";
                    try {
                        path = file.toURL().toString();
                    } catch (MalformedURLException e) {
                        logger.error("Failed to load IRIS PRD file: " + file.getAbsolutePath(), e); 
                    }
                    
					attempts.add(path);

					if(file.exists()) {
						// Only attempt to create an application context if the file exists
						foundFile = path;
						result = new FileSystemXmlApplicationContext( new String[] { path });
					}
				} else {
					logger.error("Invalid IRIS resource directory path: " + irisResourceDir.getAbsolutePath());
				}
			}

			return result;
		}
	}


    @Override
    public ResourceState getResourceState(String httpMethod, String url) throws MethodNotAllowedException {
        String resourceStateId = getResourceStateId(httpMethod, url);
        if(resourceStateId == null) {
            if(pathTree.get(url) != null) {
                Set<String> allowedMethods = pathTree.get(url).keySet();
                throw new MethodNotAllowedException(allowedMethods);
            } else {
                return null;
            }
        }
        return getResourceState(resourceStateId);
    }
    
    public String getResourceStateId(String httpMethod, String url) throws MethodNotAllowedException {
        Map<String,String> methodToState = null;
        
        initialise();
        methodToState = pathTree.get(url);

        String resourceStateId = null;
        
        if(methodToState != null) {
            resourceStateId = methodToState.get(httpMethod);
            if(resourceStateId == null) {
                if(pathTree.get(url) != null) {
                    Set<String> allowedMethods = pathTree.get(url).keySet();
                    throw new MethodNotAllowedException(allowedMethods);
                }
            }
        } else {
            return null;
        }
        
        return resourceStateId;
    }
}