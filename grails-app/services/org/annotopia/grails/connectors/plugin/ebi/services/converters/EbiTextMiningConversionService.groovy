/*
 * Copyright 2014 Massachusetts General Hospital
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.annotopia.grails.connectors.plugin.ebi.services.converters

import java.text.SimpleDateFormat

import org.annotopia.grails.connectors.converters.BaseTextMiningConversionService;
import org.annotopia.grails.connectors.vocabularies.IOAccessRestrictions
import org.annotopia.grails.connectors.vocabularies.IODomeo
import org.annotopia.grails.connectors.vocabularies.IODublinCoreTerms
import org.annotopia.grails.connectors.vocabularies.IOJsonLd
import org.annotopia.grails.connectors.vocabularies.IOOpenAnnotation
import org.annotopia.grails.connectors.vocabularies.IOPav
import org.annotopia.grails.connectors.vocabularies.IORdfs
import org.codehaus.groovy.grails.web.json.JSONArray
import org.codehaus.groovy.grails.web.json.JSONObject

/**
 * @author Paolo Ciccarese <paolo.ciccarese@gmail.com>
 */
class EbiTextMiningConversionService extends BaseTextMiningConversionService {

	public static final String RETURN_FORMAT = "annotopia";
	
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
	
	JSONObject convert(def apiKey, def resourceURI, def text, def results) {
		
		String snippetUrn = URN_SNIPPET_PREFIX + UUID.uuid( );
		
		JSONObject result = new JSONObject( );
		result.put(IOJsonLd.jsonLdId, snippetUrn);
		result.put(IOJsonLd.jsonLdType, "ao:AnnotationSet");
		result.put(IORdfs.label, "NIF Annotator Results");
		result.put(IODublinCoreTerms.description, "NIF Annotator Results");
		result.put("ao:onResource", URN_SNIPPET_PREFIX + UUID.uuid( ));
		
		// agents
		JSONArray agents = new JSONArray( );
		// connector
		JSONObject connector = getConnectorAgent( );
		result.put(IOPav.importedOn, dateFormat.format(new Date( )));
		result.put(IOPav.importedBy, connector[IOJsonLd.jsonLdId]);
		agents.add(agents.size( ), connector);
		// annotator
		JSONObject annotator = getAnnotatorAgent( );
		result.put(IOPav.importedFrom, annotator[IOJsonLd.jsonLdId]);
		agents.add(agents.size( ), annotator);
		result.put(IODomeo.agents, agents);
		
		// permissions
		result.put(IOAccessRestrictions.permissions, getPublicPermissions( ));
		
		// resources
		JSONArray resources = new JSONArray( );
		JSONObject content = new JSONObject( );
		content.put(IOJsonLd.jsonLdId, snippetUrn);
		content.put(IOJsonLd.jsonLdType, IOOpenAnnotation.ContentAsText);
		//content.put(IOOpenAnnotation.chars, contentText);
		content.put(IOPav.derivedFrom, resourceURI);
		resources.add(resources.size( ), content);
		result.put(IODomeo.resources, resources);
		
		// annotations
		JSONArray annotations = new JSONArray( );
	}
	
	/** @return Create the connector agent content. */
	private JSONObject getConnectorAgent( ) {
		JSONObject result = getSoftwareAgent(
			"http://nif-services.neuinfo.org/servicesv1/resource_AnnotateService.html",
			"NIF Annotator Web Service",
			"NIF Annotator Web Service",
			"1.0"
		);
		result.remove(IOPav.version);
		result.put("foafx:build", "001");
		result.put("foafx:version", "0.1");
		return result;
	}
	
	/** @return Create the annotator agent content. */
	private JSONObject getAnnotatorAgent( ) {
		JSONObject annotator = getSoftwareAgent(
				"http://nif-services.neuinfo.org/servicesv1/resource_AnnotateService.html",
				"NIF Annotator Web Service",
				"NIF Annotator Web Service",
				"1.0"
		);
		annotator.remove(IOPav.version);
		annotator.put("foafx:build", "001");
		annotator.put("foafx:version", "1.0");
		return annotator;
	}
	
	/** @return The Domeo specific agent content. */
	private JSONObject getDomeo( ) {
		JSONObject domeo = getSoftwareAgent("urn:domeo:software:id:Domeo-2.0alpha-040",
				"Domeo Annotation Toolkit", "Domeo", "1.0");
		domeo.remove(IOPav.version);
		domeo.put("foafx:build", "040");
		domeo.put("foafx:version", "1.0");
		return domeo;
	}
}
