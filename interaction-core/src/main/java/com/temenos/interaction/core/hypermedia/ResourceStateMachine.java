package com.temenos.interaction.core.hypermedia;

/*
 * #%L
 * interaction-core
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.Status.Family;

import org.odata4j.core.OEntity;
import org.odata4j.core.OEntityKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.temenos.interaction.core.MapWithReadWriteLock;
import com.temenos.interaction.core.MultivaluedMapImpl;
import com.temenos.interaction.core.cache.Cache;
import com.temenos.interaction.core.command.CommandController;
import com.temenos.interaction.core.command.CommonAttributes;
import com.temenos.interaction.core.command.InteractionCommand;
import com.temenos.interaction.core.command.InteractionContext;
import com.temenos.interaction.core.command.InteractionException;
import com.temenos.interaction.core.entity.Entity;
import com.temenos.interaction.core.entity.EntityMetadata;
import com.temenos.interaction.core.entity.EntityProperty;
import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.hypermedia.expression.Expression;
import com.temenos.interaction.core.resource.CollectionResource;
import com.temenos.interaction.core.resource.EntityResource;
import com.temenos.interaction.core.resource.MetaDataResource;
import com.temenos.interaction.core.resource.RESTResource;
import com.temenos.interaction.core.rim.HTTPHypermediaRIM;
import com.temenos.interaction.core.rim.ResourceRequestConfig;
import com.temenos.interaction.core.rim.ResourceRequestHandler;
import com.temenos.interaction.core.rim.ResourceRequestResult;
import com.temenos.interaction.core.rim.SequentialResourceRequestHandler;
import com.temenos.interaction.core.workflow.AbortOnErrorWorkflowStrategyCommand;

/**
 * A state machine that is responsible for creating the links (hypermedia) to
 * other valid application states.
 * 
 * @author aphethean
 * 
 */
public class ResourceStateMachine {
	private static final Logger LOGGER = LoggerFactory.getLogger(ResourceStateMachine.class);

	// members
	ResourceState initial;
	ResourceState exception;
	Transformer transformer;
	CommandController commandController;
	Cache responseCache;
	ResourceStateProvider resourceStateProvider;
	ResourceLocatorProvider resourceLocatorProvider;
	ResourceParameterResolverProvider parameterResolverProvider;
	
	// optimised access
	private Map<String, Transition> transitionsById = new MapWithReadWriteLock<String, Transition>();
	private Map<String, Transition> transitionsByRel = new MapWithReadWriteLock<String, Transition>();
	private Map<String, Set<String>> interactionsByPath = new MapWithReadWriteLock<String, Set<String>>();
	private Map<String, Set<String>> interactionsByState = new MapWithReadWriteLock<String, Set<String>>();
    private Map<String, Set<String>> resourceStateNamesByPath = new MapWithReadWriteLock<String, Set<String>>();
	private Map<String, ResourceState> resourceStatesByName = new MapWithReadWriteLock<String, ResourceState>();

	public ResourceStateMachine(ResourceState initialState) {
		this(initialState, null, null, null);
	}

	public ResourceStateMachine(ResourceState initialState, ResourceLocatorProvider resourceLocatorProvider) {
		this(initialState, null, null, resourceLocatorProvider, null);
	}

	public ResourceStateMachine(ResourceState initialState, ResourceState exceptionState) {
		this(initialState, exceptionState, null, null);
	}

	public ResourceStateMachine(ResourceState initialState, ResourceState exceptionState,
			ResourceLocatorProvider resourceLocatorProvider) {
		this(initialState, exceptionState, null, resourceLocatorProvider, null);
	}

	public CommandController getCommandController() {
		return commandController;
	}

	public void setCommandController(CommandController commandController) {
		this.commandController = commandController;
	}

	public Cache getCache() {
		return responseCache;
	}

	public void setCache(Cache cache) {
		responseCache = cache;
	}

	// TODO support Event
	public InteractionCommand determineAction(Event event, String resourcePath) {
		List<Action> actions = new ArrayList<Action>();
		Set<ResourceState> resourceStates = getResourceStatesByPath().get(resourcePath);
		for (ResourceState s : resourceStates) {
			actions.addAll(determineActions(event, s));
		}
		return buildWorkflow(event, actions);
	}

	public List<Action> determineActions(Event event, ResourceState state) {
		List<Action> actions = new ArrayList<Action>();
		Set<String> interactions = getInteractionByState().get(state.getName());
		// TODO turn interactions into Events
		if (interactions.contains(event.getMethod())) {
			for (Action a : state.getActions()) {
				if (event.isSafe() && a.getType().equals(Action.TYPE.VIEW)) {
                    // Add action to list. Since we now support command chains,
                    // with more than one GET command, it is possible
					// to have more than one VIEW in the action list.
					actions.add(a);
				} else if (event.isUnSafe() && a.getType().equals(Action.TYPE.ENTRY)
						&& (a.getMethod() == null || event.getMethod().equals(a.getMethod()))) {
					actions.add(a);
				}
			}
		}
		return actions;
	}

	public InteractionCommand buildWorkflow(Event event, List<Action> actions) {
		if (actions.size() > 0) {
			AbortOnErrorWorkflowStrategyCommand workflow = new AbortOnErrorWorkflowStrategyCommand();
			for (Action action : actions) {
				assert (action != null && event != null);
				workflow.addCommand(getCommandController().fetchCommand(action.getName()));
			}
			return workflow;
		}
		return null;
	}

	public ResourceState determineState(Event event, String resourcePath) {
		ResourceState state = null;
		Set<ResourceState> resourceStates = getResourceStatesByPath().get(resourcePath);
		if (resourceStates != null) {
			for (ResourceState s : resourceStates) {
				Set<String> interactions = getInteractionByState().get(s.getName());
				if (interactions.contains(event.getMethod())) {
					if (state == null || interactions.size() == 1 || !event.getMethod().equals("GET")) { // Avoid
																											// overriding
																											// existing
																											// view
																											// actions
						if (state != null && state.getViewAction() != null) {
							LOGGER.error("Multiple matching resource states for [{}] event on [{}], [{}] and [{}]", 
							        event, resourcePath, state, s );
						}
						state = s;
					}
				}
			}
		}

		return state;
	}

	/**
	 * 
	 * @invariant initial state not null
	 * @param initialState
	 * @param transformer
	 */
	public ResourceStateMachine(ResourceState initialState, Transformer transformer) {
		this(initialState, null, transformer, null);
	}

	public ResourceStateMachine(ResourceState initialState, Transformer transformer,
			ResourceStateProvider resourceStateProvider) {
		this(initialState, null, transformer, null, resourceStateProvider);
	}

	/**
	 * 
	 * @invariant initial state not null
	 * @param initialState
	 * @param transformer
	 */
	public ResourceStateMachine(ResourceState initialState, Transformer transformer,
			ResourceLocatorProvider resourceLocatorProvider) {
		this(initialState, null, transformer, resourceLocatorProvider, null);
	}

	public ResourceStateMachine(ResourceState initialState, ResourceState exceptionState, Transformer transformer) {
		this(initialState, exceptionState, transformer, null, null);
	}

	public ResourceStateMachine(ResourceState initialState, ResourceState exceptionState, Transformer transformer,
			ResourceStateProvider resourceStateProvider) {
		this(initialState, exceptionState, transformer, null, resourceStateProvider);
	}

	public ResourceStateMachine(ResourceState initialState, ResourceState exceptionState, Transformer transformer,
			ResourceLocatorProvider resourceLocatorProvider, ResourceStateProvider resourceStateProvider) {
		if (initialState == null)
			throw new RuntimeException("Initial state must be supplied");
		LOGGER.info("Constructing ResourceStateMachine with initial state [{}]", initialState);
		assert (exceptionState == null || exceptionState.isException());
		this.initial = initialState;
		this.initial.setInitial(true);
		this.exception = exceptionState;
		this.transformer = transformer;
		this.resourceLocatorProvider = resourceLocatorProvider;
		this.resourceStateProvider = resourceStateProvider;
		build();
	}

	/**
     * This method is called during resource state machine construction and
     * builds the resource state machine's internal state graph starting from
     * the initial state.
	 */
	private synchronized void build() {
	    registerAllStartingFromState(initial, HttpMethod.GET);
	}

    /**
     * Starting from the given state / method pair, fully initialises the machine's
     * internal state graph. Already registered states will not be processed, as well
	 * as its children states.
     * 
     * This method serves as a replacement for all the collect*By* methods, which
     * purpose was to initialise the optimised access maps.
     * 
     * @precondition The pair state / method to start with should NOT be already registered,
	 * 				  as well as none of its children, and the state should not be null
     * @invariant Given state not null
     * @postcondition All reachable states from the given state should be registered,
     *                regardless of the method
     * @param state
     *            The starting resource state from where to register
     * @param method
     *            The HTTP method associated with the state, usually the default GET
     *            method
     */
    public synchronized void registerAllStartingFromState(ResourceState state, String method) {

		checkAndResolve(state);
        if (state == null) return;
        populateAccessMaps(state, method);

		// don't register any further if the current state was already processed
		if(resourceStatesByName.containsKey(state.getName())) return;

		resourceStatesByName.put(state.getName(), state);

        // Register all target resources from this resource
        for (Transition tmpTransition : state.getTransitions()) {
            if(tmpTransition.getTarget() != null) {
               	registerAllStartingFromState(tmpTransition.getTarget(), tmpTransition.getCommand().getMethod());
            }
        }
    }

	/**
     * Registers the given state / method pair, and any states required to
     * process the given state, with the resource state machine's internal state
     * graph
	 *  
	 * @precondition The pair state / method to register, where the state should not be null
     * @invariant Given state not null
     * @postcondition All target states from the given state with a transition that is either
     *                EMBEDDED, FOR_EACH or FOR_EACH_EMBEDDED should be registered,
     *                regardless of the method
     * @param state
     *            The resource state to register
     * @param method
     *            The HTTP method associated with the state, this is important
     *            as the state to handle a request is determined by the duo of
     *            the state's path and HTTP method; path alone is not sufficient
     *            as multiple states can share the same path
	 */
	public synchronized void register(ResourceState state, String method) {

		checkAndResolve(state);
        if (state == null) return;
		populateAccessMaps(state, method);

		// don't register any further if the current state was already processed
		if(resourceStatesByName.containsKey(state.getName())) return;

		resourceStatesByName.put(state.getName(), state);

		// Register any embedded / foreach resources linked to this resource
        for (Transition tmpTransition : state.getTransitions()) {
            if(tmpTransition.getTarget() != null) {
                if (tmpTransition.isAnyOfTypes(Transition.EMBEDDED, Transition.FOR_EACH, Transition.FOR_EACH_EMBEDDED)) {
                    register(tmpTransition.getTarget(), tmpTransition.getCommand().getMethod());
                }
            }
        }
    }

	/**
	 * Maps should be populated for a state / method pair, even if the state was already
	 * processed, since we can reach a state by different methods.
	 */
	private void populateAccessMaps(ResourceState state, String method) {
        collectTransitionsByIdForState(state);
        collectTransitionsByRelForState(state);
        collectInteractionsByPathForState(state, method);
        collectInteractionsByStateForState(state, method);
        collectResourceStatesByPathForState(state);
	}

	/**
	 * @param state
	 */
	private void collectResourceStatesByPathForState(ResourceState state) {
		Set<String> resourceStateNames = resourceStateNamesByPath.get(state.getResourcePath());
		if (resourceStateNames == null) {
		    resourceStateNames = new HashSet<String>();
		    resourceStateNamesByPath.put(state.getResourcePath(), resourceStateNames);
		}

		resourceStateNames.add(state.getName());
	}

	/**
	 * @param state
	 * @param method
	 */
	private void collectInteractionsByStateForState(ResourceState state, String method) {
		Set<String> stateInteractions = interactionsByState.get(state.getName());
		if (stateInteractions == null) {
			stateInteractions = new HashSet<String>();
			interactionsByState.put(state.getName(), stateInteractions);
		}

		if (!state.isPseudoState()) {
			if (method != null) {
				stateInteractions.add(method);
			} else {
				stateInteractions.add(HttpMethod.GET);
			}
		}
		if (state.getActions() != null) {
			for (Action action : state.getActions()) {
				if (action.getMethod() != null) {
					stateInteractions.add(action.getMethod());
				}
			}
		}

		for (ResourceState next : state.getAllTargets()) {
			List<Transition> transitions = state.getTransitions(next);
			for (Transition t : transitions) {
				TransitionCommandSpec command = t.getCommand();

				Set<String> tmpStateInteractions = interactionsByState.get(next.getName());

				if (tmpStateInteractions == null) {
					tmpStateInteractions = new HashSet<String>();
                    interactionsByState.put(next.getName(), tmpStateInteractions);
				}

				if (command.getMethod() != null && !command.isAutoTransition())
                    tmpStateInteractions.add(command.getMethod());
			}
		}
	}

	/**
	 * @param state
	 * @param method
	 */
	private void collectInteractionsByPathForState(ResourceState state, String method) {
		Set<String> pathInteractions = interactionsByPath.get(state.getPath());
		if (pathInteractions == null) {
			pathInteractions = new HashSet<String>();
			interactionsByPath.put(state.getPath(), pathInteractions);
		}

		if (method != null) {
			pathInteractions.add(method);
		} else {
			pathInteractions.add(HttpMethod.GET);
		}
	}

	/**
	 * @param state
	 */
	private void collectTransitionsByRelForState(ResourceState state) {
		for (Transition transition : state.getTransitions()) {
			if (transition == null) {
				LOGGER.debug("collectTransitionsByRel : null transition detected");
			} else if (transition.getTarget() == null) {
				LOGGER.debug("collectTransitionsByRel : null target detected");
			} else if (transition.getTarget().getRel() == null) {
				LOGGER.debug("collectTransitionsByRel : null relation detected");
			} else {
				transitionsByRel.put(transition.getTarget().getRel(), transition);
			}
		}
	}

	/**
	 * @param state
	 */
	private void collectTransitionsByIdForState(ResourceState state) {
		for (Transition transition : state.getTransitions()) {
			transitionsById.put(transition.getId(), transition);
		}
	}

	/**
     * Unregisters the given state / method pair from the resource state
     * machine's internal state graph
     *
	 * @precondition A registered pair state / method, where the state should not be null
	 * @invariant Given state not null
	 * @postcondition The state is not reachable by the unregistered method
	 * @param state
     *            The resource state to unregister
     * @param method
     *            The HTTP method associated with the state, this is important
     *            as the state to handle a request is determined by the duo of
     *            the state's path and HTTP method; path alone is not sufficient
     *            as multiple states can share the same path
     */
	public synchronized void unregister(ResourceState state, String method) {

	     if(state == null) return;

		// don't do anything if the state is not registered
		if(!resourceStatesByName.containsKey(state.getName())) return;

        for (Transition transition : state.getTransitions()) {

			// remove transitions originating in state for this method only
            if(transition.getCommand().getMethod() == method)
                transitionsById.remove(transition.getId());

	        // remove transitions originating in state for this method only
            if (transition.getTarget() != null) {
                if(transition.getCommand().getMethod() == method)
                    transitionsByRel.remove(transition.getTarget().getRel());
            }
        }

        // Process interactions by path
        final Set<String> pathInteractions = interactionsByPath.get(state.getPath());
        if (pathInteractions != null)
            pathInteractions.remove(method);

		// Process interactions by state
        final Set<String> stateInteractions = interactionsByState.get(state.getName());
        if (stateInteractions != null)
            stateInteractions.remove(method);

		// only remove resources by path and by name if there are no methods associated with it
		if(stateInteractions != null)
			if(stateInteractions.isEmpty()) {
		        // Process resource states by path
		        final Set<String> pathStateNames = resourceStateNamesByPath.get(state.getResourcePath());
		        if (pathStateNames != null) {
		            pathStateNames.remove(state.getName());
		        }
		        resourceStatesByName.remove(state.getName());
            }
	}

	public void setParameterResolverProvider(ResourceParameterResolverProvider parameterResolverProvider) {
		this.parameterResolverProvider = parameterResolverProvider;
	}

	public ResourceState getInitial() {
		return initial;
	}

	public ResourceState getException() {
		return exception;
	}

	public Transformer getTransformer() {
		return transformer;
	}

    public synchronized Collection<ResourceState> getStates() {
		return Collections.unmodifiableCollection(resourceStatesByName.values());
	}

	/**
	 * Return a map of all the paths, and interactions with those states mapped
	 * to that path
	 * 
	 * @return
	 */
	public Map<String, Set<String>> getInteractionByPath() {
		return interactionsByPath;
	}

	/**
	 * Return a map of all the ResourceState's, and interactions with those
	 * states.
	 * 
	 * @return
	 */
	public Map<String, Set<String>> getInteractionByState() {
		return interactionsByState;
	}

	/**
	 * For a given resource state, get the valid interactions.
	 * 
	 * @param state
	 * @return
	 */
	public Set<String> getInteractions(ResourceState state) {
		Set<String> interactions = null;
		if (state != null) {
		    assert (getStates().contains(state));
			Map<String, Set<String>> interactionMap = getInteractionByPath();
			interactions = interactionMap.get(state.getPath());
		}
		return interactions;
	}

	/**
	 * For a given path, return the resource states.
	 * 
	 * @param path
	 * @return
	 */
	public Set<ResourceState> getResourceStatesForPath(String path) {
		if (path == null) {
			path = initial.getPath();
		}
		return getResourceStatesByPath().get(path);
	}

	/**
	 * For a given path regular expression, return the resource states.
	 * 
	 * @param pathRegex
	 * @return
	 */
	public Set<ResourceState> getResourceStatesForPathRegex(String pathRegex) {
		if (pathRegex == null) {
			pathRegex = initial.getPath();
		}
		return getResourceStatesForPathRegex(Pattern.compile(pathRegex));
	}

	/**
	 * Return a set of resources based on a pattern, without
     * ensuring consistency between the returned set and the state
     * of the internal maps.
     * 
	 * @see {@link ResourceStateMachine#getResourceStatesForPathRegex(String)}
	 */
	public Set<ResourceState> getResourceStatesForPathRegex(Pattern pattern) {
		Set<ResourceState> matchingStates = new HashSet<ResourceState>();
		for (String path : resourceStateNamesByPath.keySet()) {
            Matcher m = pattern.matcher(path);
            if (m.matches()) {
                matchingStates.addAll(getResourceStatesForPath(path));
            }
        }
		return matchingStates;
	}

	/**
	 * Return a map of all the paths to the various resources, without
	 * ensuring consistency between the returned map and the state
	 * of the internal maps.
	 * 
	 * @invariant initial state not null
	 */
	public Map<String, Set<ResourceState>> getResourceStatesByPath() {
        Map<String, Set<ResourceState>> stateMap = new HashMap<String, Set<ResourceState>>();
        for (Entry<String, Set<String>> entry : resourceStateNamesByPath.entrySet()) {
            Set<ResourceState> resourceStateSet = new HashSet<ResourceState>();
            for(String resourceStateName : entry.getValue()) {
                ResourceState state = resourceStatesByName.get(resourceStateName);
                if(state != null) resourceStateSet.add(state);
            }
            stateMap.put(entry.getKey(), resourceStateSet);
        }
        return stateMap;
	}

	/**
	 * Return a map of all the paths to the sub states from the supplied
	 * ResourceState.
	 * 
	 * @precondition begin state not null
	 * @invariant initial state not null
	 */
	public Map<String, Set<ResourceState>> getResourceStatesByPath(ResourceState begin) {
		assert (begin != null);
		
		collectResourceStatesByPath(resourceStateNamesByPath, begin);
		return getResourceStatesByPath();
	}

	private void collectResourceStatesByPath(Map<String, Set<String>> result, ResourceState begin) {
		List<ResourceState> states = new ArrayList<ResourceState>();
		collectResourceStatesByPath(result, states, begin);
	}

	private void collectResourceStatesByPath(Map<String, Set<String>> result, Collection<ResourceState> states,
			ResourceState currentState) {

		if (currentState == null) {
			return;
		}

        for (ResourceState tmpState : states) {
            if (tmpState == currentState)
				return;
		}

		states.add(currentState);
		// add current state to results
		Set<String> thisStateSet = result.get(currentState.getResourcePath());
		if (thisStateSet == null)
			thisStateSet = new HashSet<String>();
		thisStateSet.add(currentState.getName());
		result.put(currentState.getResourcePath(), thisStateSet);
		for (ResourceState next : currentState.getAllTargets()) {
			if (next != null && next != currentState) {
				String path = next.getResourcePath();
				if (result.get(path) != null) {
					if (!result.get(path).contains(next.getName())) {
						LOGGER.debug("Adding to existing ResourceState[{}] set ({}): {}", path, result.get(path), next);
						result.get(path).add(next.getName());
					}
				} else {
					LOGGER.debug("Putting a ResourceState[{}]: {}", path, next);
					Set<String> set = new HashSet<String>();
					set.add(next.getName());
					result.put(path, set);
				}
			}
			collectResourceStatesByPath(result, states, next);
		}
	}

	/**
	 * For a given state name, return the resource state.
	 * 
	 * @param name
	 * @return
	 */
	public ResourceState getResourceStateByName(String name) {
		if (name == null)
			throw new IllegalArgumentException("State name not supplied");
		return getResourceStateByName().get(name);
	}

	/**
	 * Return a map of all the state names to ResourceState
	 * 
	 * @invariant initial state not null
	 * @return
	 */
	public Map<String, ResourceState> getResourceStateByName() {
		return resourceStatesByName;
	}

	/**
	 * Return a map of all the state names to the sub states from the supplied
	 * ResourceState.
	 * 
	 * @precondition begin state not null
	 * @invariant initial state not null
	 */
	public Map<String, ResourceState> getResourceStateByName(ResourceState begin) {
		assert (begin != null);
		Map<String, ResourceState> stateMap = new HashMap<String, ResourceState>();
		collectResourceStatesByName(stateMap, begin);
		return stateMap;
	}

	private void collectResourceStatesByName(Map<String, ResourceState> result, ResourceState begin) {
		List<ResourceState> states = new ArrayList<ResourceState>();
		collectResourceStatesByName(result, states, begin);
	}

	private void collectResourceStatesByName(Map<String, ResourceState> result, Collection<ResourceState> states,
			ResourceState currentState) {
		if (currentState == null)
			return;

        for (ResourceState tmpState : states) {
            if (tmpState == currentState)
				return;
		}

		states.add(currentState);
		// add current state to results
		result.put(currentState.getName(), currentState);
		for (ResourceState next : currentState.getAllTargets()) {
			if (next != null && next != currentState) {
				String name = next.getName();
				LOGGER.debug("Putting a ResourceState[{}]: {}", name, next);
				result.put(name, next);
			}
			collectResourceStatesByName(result, states, next);
		}
	}

	/**
	 * Evaluate and return all the valid links (target states) from this
	 * resource state.
	 * 
	 * @param rimHandler
	 * @param ctx
	 * @param resourceEntity
	 * @return
	 */
	public Collection<Link> injectLinks(HTTPHypermediaRIM rimHandler, InteractionContext ctx,
			RESTResource resourceEntity, HttpHeaders headers, Metadata metadata) {
		return injectLinks(rimHandler, ctx, resourceEntity, null, headers, metadata);
	}

	/**
	 * Evaluate and return all the valid links (target states) from the current
	 * resource state (@see {@link InteractionContext#getCurrentState()}).
	 * 
	 * @param rimHander
	 * @param ctx
	 * @param resourceEntity
	 * @param selfTransition
	 *            if we are injecting links into a resource that has resulted
	 *            from a transition from another resource (e.g an auto
	 *            transition or an embedded transition) then we need to use the
	 *            transition parameters as there are no path parameters
	 *            available. i.e. because we've not made a request for this
	 *            resource through the whole jax-rs stack
	 * @return
	 */
	public Collection<Link> injectLinks(HTTPHypermediaRIM rimHander, InteractionContext ctx,
			RESTResource resourceEntity, Transition selfTransition, HttpHeaders headers, Metadata metadata) {
		// Add path and query parameters to the list of resource properties
		MultivaluedMap<String, String> resourceProperties = new MultivaluedMapImpl<String>();
		resourceProperties.putAll(ctx.getPathParameters());
		
		if (null != ctx.getAttribute(CommonAttributes.O_DATA_ENTITY_ATTRIBUTE)) {
		    resourceProperties.putSingle("profileOEntity", (String) ctx.getAttribute(CommonAttributes.O_DATA_ENTITY_ATTRIBUTE));
		}
		
		ResourceState state = ctx.getCurrentState();
		List<Link> links = new ArrayList<Link>();
		if (resourceEntity == null)
			return links;

		Object entity = null;
		CollectionResource<?> collectionResource = null;
		if (resourceEntity instanceof EntityResource) {
			entity = ((EntityResource<?>) resourceEntity).getEntity();
		} else if (resourceEntity instanceof CollectionResource) {
			collectionResource = (CollectionResource<?>) resourceEntity;
			// TODO add support for properties on collections
			LOGGER.info("Injecting links into a collection, only support simple, non template, links as there are no properties on the collection at the moment");
		} else if (resourceEntity instanceof MetaDataResource) {
			// TODO deprecate all resource types apart from item
			// (EntityResource) and collection (CollectionResource)
		    LOGGER.debug("Returning from the call to getLinks for a MetaDataResource without doing anything");
		    
			return links;
		} else {
			throw new RuntimeException("Unable to get links, an error occurred");
		}

		// add link to GET 'self'
		if (selfTransition == null)
			selfTransition = state.getSelfTransition();
		LinkGenerator selfLinkGenerator = new LinkGeneratorImpl(this, selfTransition, null);
		links.addAll(selfLinkGenerator.createLink(resourceProperties, ctx.getQueryParameters(), entity));

		/*
		 * Add links to other application states (resources)
		 */
		List<Transition> transitions = state.getTransitions();
		for (Transition transition : transitions) {
            if (transition.getTarget() == null) {
                LOGGER.warn("Skipping invalid transition: {}", transition);
                
				continue;
			}

			TransitionCommandSpec cs = transition.getCommand();

            if (cs.isAutoTransition()) {
                // Au revoir - Auto transitions should not be seen by user
                // agents
				continue;
			}

			/*
			 * build link and add to list of links
			 */
			if (cs.isForEach() || cs.isEmbeddedForEach()) {
				if (collectionResource != null) {
					for (EntityResource<?> er : collectionResource.getEntities()) {
						Collection<Link> eLinks = er.getLinks();
						if (eLinks == null) {
							eLinks = new ArrayList<Link>();
						}
						LinkGenerator linkGenerator = new LinkGeneratorImpl(this, transition, ctx);
						Collection<Link> generatedLinks = linkGenerator.createLink(resourceProperties, ctx.getQueryParameters(), er.getEntity());

						if (addLink(transition, ctx, er, rimHander)) {
							eLinks.addAll(generatedLinks);
						}

						er.setLinks(eLinks);

                        if (cs.isEmbeddedForEach()) {
                            // Embedded resource
				            MultivaluedMap<String, String> newPathParameters = new MultivaluedMapImpl<String>();
				            newPathParameters.putAll(ctx.getPathParameters());

                            EntityMetadata entityMetadata = metadata.getEntityMetadata(collectionResource
                                    .getEntityName());
				            List<String> ids = new ArrayList<String>();

				            Object tmpObj = er.getEntity();

                            if (tmpObj instanceof Entity) {
                                EntityProperty prop = ((Entity) tmpObj).getProperties().getProperty(ids.get(0));
				                ids.add(prop.getValue().toString());
                            } else if (tmpObj instanceof OEntity) {
                                OEntityKey entityKey = ((OEntity) tmpObj).getEntityKey();
				                ids.add(entityKey.toKeyStringWithoutParentheses().replaceAll("'", ""));
                            } else {
                                try {
    				                String fieldName = entityMetadata.getIdFields().get(0);
                                    String methodName = "get" + fieldName.substring(0, 1).toUpperCase()
                                            + fieldName.substring(1);
                                    Method method = tmpObj.getClass().getMethod(methodName);
                                    ids.add(method.invoke(tmpObj).toString());
                                } catch (Exception e) {
                                    LOGGER.warn("Failed to add record id while trying to embed current collection resource", e);
                                }
				            }

                            newPathParameters.put("id", ids);

                            InteractionContext tmpCtx = new InteractionContext(ctx, headers, newPathParameters,
                                    ctx.getQueryParameters(), transition.getTarget());
                            embedResources(rimHander, headers, tmpCtx, er);
						}
					}
				}
			} else {
				EntityResource<?> entityResource = null;
				if (ctx.getResource() instanceof EntityResource<?>) {
					entityResource = ((EntityResource<?>) ctx.getResource());
				}
				if (addLink(transition, ctx, entityResource, rimHander)) {
					LinkGenerator linkGenerator = new LinkGeneratorImpl(this, transition, ctx);
					links.addAll(linkGenerator.createLink(resourceProperties, ctx.getQueryParameters(), entity));
				}
			}
		}
		resourceEntity.setLinks(links);
		return links;
	}

	/**
	 * Execute and return all the valid embedded links (target states) from the
	 * supplied resource. Should be identical to
	 * {@link InteractionContext#getResource()}.
	 * 
	 * @param rimHandler
	 * @param headers
	 * @param ctx
	 * @param resource
	 * @return
	 */
	public Map<Transition, RESTResource> embedResources(HTTPHypermediaRIM rimHandler, HttpHeaders headers,
			InteractionContext ctx, RESTResource resource) {
		ResourceRequestHandler resourceRequestHandler = rimHandler.getResourceRequestHandler();
		assert (resourceRequestHandler != null);
		try {
			ResourceRequestConfig.Builder configBuilder = new ResourceRequestConfig.Builder();
			Collection<Link> links = resource.getLinks();
			if (links != null) {
				for (Link link : links) {
					if (link == null) {
						LOGGER.warn("embedResources : null Link detected.");
					} else {
						Transition t = link.getTransition();
						/*
						 * when embedding resources we don't want to embed
						 * ourselves we only want to embed the 'EMBEDDED'
						 * transitions
						 */
						if (t.getSource() != t.getTarget()) {
                            if ((t.getCommand().getFlags() & Transition.EMBEDDED) == Transition.EMBEDDED) {
							    configBuilder.transition(t);
							}

                            if ((t.getCommand().getFlags() & Transition.FOR_EACH_EMBEDDED) == Transition.FOR_EACH_EMBEDDED) {
                                configBuilder.transition(t);
						}
					}
				}
			}
            }

			ResourceRequestConfig config = configBuilder.build();

			Map<Transition, ResourceRequestResult> results = null;

            if (resource instanceof EntityResource<?>
                    && resourceRequestHandler instanceof SequentialResourceRequestHandler) {
                /*
                 * Handle cases where we may be embedding a resource that has
                 * filter criteria whose values are contained in the current
                 * resource's entity properties.
				 */
                Object tmpEntity = ((EntityResource) resource).getEntity();

                results = ((SequentialResourceRequestHandler) resourceRequestHandler).getResources(rimHandler, headers,
                        ctx, null, tmpEntity, config);

			} else {
				results = resourceRequestHandler.getResources(rimHandler, headers, ctx, null, config);
			}

			if (config.getTransitions() != null && !config.getTransitions().isEmpty()
					&& new HashSet(config.getTransitions()).size() != results.keySet().size()) {
				throw new InteractionException(Status.INTERNAL_SERVER_ERROR, "Resource state ["
						+ ctx.getCurrentState().getId() + "] did not return correct number of embedded resources.");
			}
			// don't replace any embedded resources added within the commands
			Map<Transition, RESTResource> resourceResults = null;
			if (ctx.getResource() != null && ctx.getResource().getEmbedded() != null) {
				resourceResults = ctx.getResource().getEmbedded();
			} else {
				resourceResults = new HashMap<Transition, RESTResource>();
			}
			for (Transition transition : results.keySet()) {
				ResourceRequestResult result = results.get(transition);
				if (Family.SUCCESSFUL.equals(Status.fromStatusCode(result.getStatus()).getFamily())) {
					resourceResults.put(transition, result.getResource());
				} else {
					LOGGER.error("Failed to embed resource for transition [{}]", transition.getId());
				}
			}
			resource.setEmbedded(resourceResults);
			return resourceResults;
		} catch (InteractionException ie) {
            LOGGER.error(
                    "Failed to embed resources [{}] with error [{} - {}]: ", ctx.getCurrentState().getId(), ie.getHttpStatus(), ie.getHttpStatus().getReasonPhrase(), ie);
			throw new RuntimeException(ie);
		}
	}

	/**
	 * Find the transition that was used by evaluating the LinkHeader and create
	 * a a Link for that transition.
	 * 
	 * @param pathParameters
	 * @param resourceEntity
	 * @param linkHeader
	 * @return
	 */
	public Link getLinkFromRelations(MultivaluedMap<String, String> pathParameters, RESTResource resourceEntity,
			LinkHeader linkHeader) {
		Link target = null;
		// Was a custom link relation supplied, informing us which link was
		// used?
		if (linkHeader != null) {
			Set<String> relationships = linkHeader.getLinksByRelationship().keySet();
			for (String related : relationships) {
				Transition transition = getTransitionsById().get(related);
				if (transition != null) {
					LinkGenerator linkGenerator = new LinkGeneratorImpl(this, transition, null);
					Collection<Link> links = linkGenerator.createLink(pathParameters, null, resourceEntity);
					target = (!links.isEmpty()) ? links.iterator().next() : null;
				}
			}
		}
		return target;
	}

	public Map<String, Transition> getTransitionsById() {
		return transitionsById;
	}

	public Map<String, Transition> getTransitionsByRel() {
		return transitionsByRel;
	}

	/**
	 * Find the transition that was used by assuming the HTTP method was applied
	 * to this state; create a a Link for that transition.
	 * 
	 * @param pathParameters
	 * @param resourceEntity
	 * @param currentState
	 * @param method
	 * @return
	 * @invariant method != null
	 */
	public Link getLinkFromMethod(MultivaluedMap<String, String> pathParameters, RESTResource resourceEntity,
			ResourceState currentState, String method) {
		assert (method != null);
		Link target = null;
		for (ResourceState nextState : currentState.getAllTargets()) {
			Transition transition = currentState.getTransition(nextState);
			if (method.contains(transition.getCommand().getMethod())) {
				// do not create link if this a pseudo state, effectively no
				// state
				if (!transition.getTarget().isPseudoState()) {
					LinkGenerator linkGenerator = new LinkGeneratorImpl(this, transition, null);
					Collection<Link> links = linkGenerator.createLink(pathParameters, null, resourceEntity);
					target = (!links.isEmpty()) ? links.iterator().next() : null;
				}
			}
		}
		return target;
	}

    public ResourceStateAndParameters resolveDynamicState(DynamicResourceState dynamicResourceState,
            Map<String, Object> transitionProperties, InteractionContext ctx) {
		Object[] aliases = getResourceAliases(transitionProperties, dynamicResourceState, ctx).toArray();

		// Use resource locator to resolve dynamic target
		String locatorName = dynamicResourceState.getResourceLocatorName();

		ResourceLocator locator = resourceLocatorProvider.get(locatorName);

		ResourceStateAndParameters result = new ResourceStateAndParameters();

		ResourceState tmpState = locator.resolve(aliases);

        if (tmpState == null) {
			// A dead link, target could not be found
            LOGGER.error("Dead link - Failed to resolve resource using {} resource locator", dynamicResourceState.getResourceLocatorName());
		} else {
			boolean registrationRequired = false;

            for (Transition transition : tmpState.getTransitions()) {
				ResourceState target = transition.getTarget();

                if (target instanceof LazyResourceState || target instanceof LazyCollectionResourceState) {
					registrationRequired = true;
				}
			}

            if (registrationRequired) {
				register(tmpState, HttpMethod.GET);
			}

            result.setState(tmpState);

            if (parameterResolverProvider != null) {
				try {
					// Add query parameters
					ResourceParameterResolver parameterResolver = parameterResolverProvider.get(locatorName);
					ResourceParameterResolverContext context = new ResourceParameterResolverContext(dynamicResourceState.getEntityName());					
					ParameterAndValue[] paramsAndValues = resolveParameterValues(parameterResolver.resolve(aliases, context), transitionProperties);
					result.setParams(paramsAndValues);
				} catch (IllegalArgumentException e) {
				    LOGGER.warn("Failed to find parameter resolver for: {}", locatorName, e);
				}
			}
		}

		return result;

	}

	private ParameterAndValue[] resolveParameterValues(ParameterAndValue[] parameterAndValues, Map<String, Object> transitionProperties) {
		if (parameterAndValues == null || parameterAndValues.length == 0) {
			return parameterAndValues;
		}
		ParameterAndValue[] result = new ParameterAndValue[parameterAndValues.length];
		for (int i = 0; i < parameterAndValues.length; i++) {
			String value = HypermediaTemplateHelper.templateReplace(parameterAndValues[i].getValue(), transitionProperties);
			result[i] = new ParameterAndValue(parameterAndValues[i].getParameter(), value);
		}
		return result;
	}

	/**
	 * @param transitionProperties
	 * @param dynamicResourceState
     * @param ctx
	 * @return
	 */
	private List<Object> getResourceAliases(Map<String, Object> transitionProperties,
			DynamicResourceState dynamicResourceState, InteractionContext ctx) {
		List<Object> aliases = new ArrayList<Object>();
		
		final Pattern pattern = Pattern.compile("\\{*([a-zA-Z0-9.]+)\\}*");

		for (String resourceLocatorArg : dynamicResourceState.getResourceLocatorArgs()) {
            Matcher matcher = pattern.matcher(resourceLocatorArg);
            matcher.find();
            String key = matcher.group(1);

            if (transitionProperties.containsKey(key)) {
                aliases.add(transitionProperties.get(key));
            } else if (ctx != null && ctx.getAttribute(key)!=null) {
                aliases.add(ctx.getAttribute(key));                
            } else { //Keep all params
            	aliases.add(key);
            }
        }
		
		return aliases;
	}

	/**
	 * Obtain transition properties. Transition properties are a list of entity
	 * properties, path parameters, and query parameters.
	 * 
	 * @param transition
	 *            transition
	 * @param entity
	 *            usually an entity of the source state
	 * @param pathParameters
	 *            path parameters
	 * @param pathParameters
	 *            path parameters
	 * @return map of transition properties
	 */
	public Map<String, Object> getTransitionProperties(Transition transition, Object entity,
			MultivaluedMap<String, String> pathParameters, MultivaluedMap<String, String> queryParameters) {
		Map<String, Object> transitionProps = new HashMap<String, Object>();

		// Obtain query parameters
		if (queryParameters != null) {
			for (String key : queryParameters.keySet()) {
				transitionProps.put(key, queryParameters.getFirst(key));
			}
		}

		// Obtain path parameters
		if (pathParameters != null) {
			for (String key : pathParameters.keySet()) {
				transitionProps.put(key, pathParameters.getFirst(key));
			}
		}

		// Obtain entity properties
		Map<String, Object> entityProperties = null;
		if (entity != null && transformer != null) {
			LOGGER.debug("Using transformer [{}] to build properties for link [{}]", transformer, transition);
			entityProperties = transformer.transform(entity);
			if (entityProperties != null) {
				transitionProps.putAll(entityProperties);
			}
		}

		// Obtain linkage properties
		Map<String, String> linkParameters = transition.getCommand().getUriParameters();
		if (linkParameters != null) {
			for (String key : linkParameters.keySet()) {
				String value = linkParameters.get(key);
				value = HypermediaTemplateHelper.templateReplace(value, transitionProps);
				transitionProps.put(key, value);
			}
		}

		return transitionProps;
	}

	public InteractionCommand determinAction(String event, String path) {
		return null;
	}

	public ResourceStateProvider getResourceStateProvider() {
		return resourceStateProvider;
	}

	public void setResourceStateProvider(ResourceStateProvider resourceStateProvider) {
		this.resourceStateProvider = resourceStateProvider;
	}

	public ResourceState checkAndResolve(ResourceState targetState) {
		if (targetState instanceof LazyResourceState || targetState instanceof LazyCollectionResourceState) {
			targetState = resourceStateProvider.getResourceState(targetState.getName());
		}
		if (targetState != null) {
			for (Transition transition : targetState.getTransitions()) {
				if (transition.getTarget() instanceof LazyResourceState
						|| transition.getTarget() instanceof LazyCollectionResourceState) {
					if (transition.getTarget() != null) {
						ResourceState tt = resourceStateProvider.getResourceState(transition.getTarget().getName());
						if (tt == null) {
							LOGGER.error("Invalid transition [{}]", transition.getId());
						}
						transition.setTarget(tt);
					}
				}
			}
            // Target can have errorState which is not a normal transition, so
            // resolve and add it here
			if (targetState.getErrorState() != null) {
				ResourceState errorState = targetState.getErrorState();
                if ((errorState instanceof LazyResourceState || errorState instanceof LazyCollectionResourceState)
                        && errorState.getId().startsWith(".")) {
                    // We should resolve and overwrite the one already there
					errorState = resourceStateProvider.getResourceState(errorState.getName());
					targetState.setErrorState(errorState);
				}
			}
		}
		return targetState;
	}

	// Generated builder pattern from here

	public static class Builder {
		private ResourceState initial;
		private ResourceState exception;
		private Transformer transformer;
		private CommandController commandController;
		private ResourceStateProvider resourceStateProvider;
		private ResourceLocatorProvider resourceLocatorProvider;
		private ResourceParameterResolverProvider parameterResolverProvider;
		private Cache responseCache;

		public Builder initial(ResourceState initial) {
			this.initial = initial;
			return this;
		}

		public Builder exception(ResourceState exception) {
			this.exception = exception;
			return this;
		}

		public Builder transformer(Transformer transformer) {
			this.transformer = transformer;
			return this;
		}

		public Builder commandController(CommandController commandController) {
			this.commandController = commandController;
			return this;
		}

		public Builder resourceStateProvider(ResourceStateProvider resourceStateProvider) {
			this.resourceStateProvider = resourceStateProvider;
			return this;
		}

		public Builder resourceLocatorProvider(ResourceLocatorProvider resourceLocatorProvider) {
			this.resourceLocatorProvider = resourceLocatorProvider;
			return this;
		}

		public Builder parameterResolverProvider(ResourceParameterResolverProvider parameterResolverProvider) {
			this.parameterResolverProvider = parameterResolverProvider;
			return this;
		}

		public Builder responseCache(Cache cache) {
			this.responseCache = cache;
			return this;
		}

		public ResourceStateMachine build() {
			return new ResourceStateMachine(this);
		}
	}

	private ResourceStateMachine(Builder builder) {
		this.initial = builder.initial;
		this.exception = builder.exception;
		this.transformer = builder.transformer;
		this.commandController = builder.commandController;
		this.resourceStateProvider = builder.resourceStateProvider;
		this.resourceLocatorProvider = builder.resourceLocatorProvider;
		this.parameterResolverProvider = builder.parameterResolverProvider;
		this.responseCache = builder.responseCache;
		build();
	}   
    
	private boolean addLink(Transition transition, InteractionContext ctx, EntityResource<?> er,
			HTTPHypermediaRIM rimHander) {
		boolean addLink = true;
		// evaluate the conditional expression
		Expression conditionalExp = transition.getCommand().getEvaluation();
		if (conditionalExp != null) {
			try {
				addLink = conditionalExp.evaluate(rimHander, ctx, (er != null) ? er.clone() : null);
			} catch(CloneNotSupportedException cnse){ //not thrown, but added to support clone design contract
				throw new RuntimeException("Failed to clone EntityResource", cnse);
			}
		}
		return addLink;
	}


}
