package com.temenos.interaction.media.xhtml;

/*
 * #%L
 * interaction-media-xhtml
 * %%
 * Copyright (C) 2012 - 2016 Temenos Holdings N.V.
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


import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

/**
 * Class to merge velocity apache templates with user context, also contains predefined templates
 *
 * @author kwieconkowski
 */
class VelocityTemplateEngine {
    private static final String PATH_TO_TEMPLATES = "xhtml/";
    private static final VelocityEngine velocityEngine;
    static {
        Properties properties = new Properties();
        properties.put(VelocityEngine.RESOURCE_LOADER, "classpath");
        properties.put("classpath." + VelocityEngine.RESOURCE_LOADER + ".class", ClasspathResourceLoader.class.getName());
        velocityEngine = new VelocityEngine(properties);
        velocityEngine.init();
    }

    enum VmTemplate {
        HEADER("header.vm"),
        HEADER_MINIMAL("headerMinimal.vm"),
        FOOTER("footer.vm"),
        RESOURCE_LINKS("resourceLinks.vm"),
        ENTITIES("entities.vm"),
        ENTITIES_MINIMAL("entitiesMinimal.vm"),
        ENTITY("entity.vm"),
        ENTITY_MINIMAL("entityMinimal.vm");

        private final Template template;
        private final String templateAsString;

        VmTemplate(String name) {
            String relativePath = PATH_TO_TEMPLATES + name;
            template = velocityEngine.getTemplate(relativePath, "UTF-8");
            URL url = getClass().getClassLoader().getResource(relativePath);
            try {
                templateAsString = org.apache.commons.io.IOUtils.toString(url, "UTF-8");
            } catch (IOException ex) {
                throw new IllegalStateException("[Critical] Error while loading VM templates for xhtml", ex);
            }
        }

        public Template get() {
            return template;
        }

        @Override
        public String toString() {
            return templateAsString;
        }
    }

    public void merge(Writer writer, Map<String, Object> parameters, Template template) {
        VelocityContext context = new VelocityContext(parameters);
        template.merge(context, writer);
    }
}
