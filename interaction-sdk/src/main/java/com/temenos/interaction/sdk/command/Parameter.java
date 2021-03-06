package com.temenos.interaction.sdk.command;

/*
 * #%L
 * interaction-sdk
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


/**
 * This class holds information about an IRIS command
 */
public class Parameter {
	private String value;
	private boolean isByRef;
	private String refId;

	public Parameter(String value, boolean isByRef, String refId) {
		this.value = value;
		this.isByRef = isByRef;
		this.refId = refId;
	}
	
	public String getValue() {
		return value;
	}
	
	public boolean isByReference() {
		return isByRef;
	}
	
	public String getRefId() {
		return refId;
	}
}
