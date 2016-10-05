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


import javax.ws.rs.core.Response.Status;

import org.odata4j.core.OEntity;
import org.odata4j.exceptions.ODataProducerException;
import org.odata4j.producer.EntityResponse;
import org.odata4j.producer.ODataProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.temenos.interaction.core.command.InteractionCommand;
import com.temenos.interaction.core.command.InteractionContext;
import com.temenos.interaction.core.command.InteractionException;
import com.temenos.interaction.core.entity.Entity;
import com.temenos.interaction.core.resource.EntityResource;

public class CreateEntityCommand extends AbstractODataCommand implements InteractionCommand {
	private static final Logger LOGGER = LoggerFactory.getLogger(CreateEntityCommand.class);

	public CreateEntityCommand(ODataProducer producer) {
		super(producer);
	}

	/* Implement InteractionCommand interface */
	
	@SuppressWarnings("unchecked")
	@Override
	public Result execute(InteractionContext ctx) throws InteractionException {
		assert(ctx != null);
		assert(ctx.getCurrentState() != null);
		assert(ctx.getCurrentState().getEntityName() != null && !ctx.getCurrentState().getEntityName().equals(""));
		assert(ctx.getResource() != null);
		
		// create the entity
		OEntity entity = null;
		try {
			entity = ((EntityResource<OEntity>) ctx.getResource()).getEntity();
		} catch (ClassCastException cce) {
		    if(LOGGER.isDebugEnabled()) {
		        LOGGER.debug("OEntity class not found.", cce);
		    }
			entity = CommandHelper.createOEntityFromEntity(this, producer, ((EntityResource<Entity>) ctx.getResource()).getEntity(), null);
		}
		String entityName = getEntityName(ctx);
		
		LOGGER.debug("Creating entity for " + entityName);
		OEntity oEntity;
		try {
			EntityResponse er = producer.createEntity(entityName, entity);
			oEntity = er.getEntity();
		}
		catch(ODataProducerException ope) {
		    LOGGER.debug("Failed to create entity [" + entityName + "]: ", ope);
			throw new InteractionException(ope.getHttpStatus(), ope);
		}
		catch(Exception e) {
		    LOGGER.debug("Error while creating entity [" + entityName + "]: ", e);
			throw new InteractionException(Status.INTERNAL_SERVER_ERROR, e);
		}
		
		ctx.setResource(CommandHelper.createEntityResource(oEntity));
		return Result.CREATED;
	}

}
