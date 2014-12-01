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

import grails.converters.JSON

import java.text.SimpleDateFormat

import org.annotopia.grails.connectors.converters.BaseTextMiningConversionService
import org.annotopia.grails.connectors.vocabularies.IOAccessRestrictions
import org.annotopia.grails.connectors.vocabularies.IODomeo
import org.annotopia.grails.connectors.vocabularies.IODublinCoreTerms
import org.annotopia.grails.connectors.vocabularies.IOJsonLd
import org.annotopia.grails.connectors.vocabularies.IOPav
import org.annotopia.grails.connectors.vocabularies.IORdfs
import org.annotopia.grails.connectors.utils.UUID
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFLanguages
import org.codehaus.groovy.grails.web.json.JSONArray
import org.codehaus.groovy.grails.web.json.JSONObject

import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import com.github.jsonldjava.utils.JsonUtils
import com.hp.hpl.jena.rdf.model.Model
import com.hp.hpl.jena.rdf.model.ModelFactory
import com.hp.hpl.jena.rdf.model.Resource
import com.hp.hpl.jena.rdf.model.ResourceFactory
import com.hp.hpl.jena.rdf.model.Statement
import com.hp.hpl.jena.rdf.model.StmtIterator

/**
 * @author Paolo Ciccarese <paolo.ciccarese@gmail.com>
 */
class EbiTextMiningDomeoConversionService extends BaseTextMiningConversionService {

	public static final String RETURN_FORMAT = "domeo";
	
	def configAccessService
	def grailsApplication
	
	
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
	
	public JSONObject convert(def apiKey, List<Model> models, def url) {
		String snippetUrn = URN_SNIPPET_PREFIX + UUID.uuid( );
		
		JSONObject result = new JSONObject( );
		result.put(IOJsonLd.jsonLdId, URN_ANNOTATION_SET_PREFIX + UUID.uuid( ));
		result.put(IOJsonLd.jsonLdType, "ao:AnnotationSet");
		result.put(IORdfs.label, "EBI Annotator Results");
		result.put(IODublinCoreTerms.description, "EBI Annotator Results");
		
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
		// Domeo
		JSONObject domeo = getDomeo( );
		agents.add(agents.size( ), domeo);
		result.put(IODomeo.agents, agents);
		
		// permissions
		result.put(IOAccessRestrictions.permissions, getPublicPermissions( ));	
		
		// annotations
		JSONArray annotations = new JSONArray( );
		
		Object contextJson = JsonUtils.fromInputStream(callExternalUrl(apiKey,
			configAccessService.getAsString("annotopia.jsonld.openannotation.framing")));
		
		for(int i=0; i<models.size(); i++) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			RDFDataMgr.write(baos, models.get(i).getGraph(), RDFLanguages.JSONLD);
			
			Object framed =  JsonLdProcessor.frame(JsonUtils.fromString(baos.toString().replace('"@id" : "urn:x-arq:DefaultGraphNode",','')), contextJson, new JsonLdOptions());
			def annotationInJsonString = JsonUtils.toPrettyString(framed)	
			
			JSONObject annotationJson = JSON.parse(annotationInJsonString);
			println "************** " + annotationJson;
			
			JSONObject annotation = new JSONObject( );
			annotation.put(IOJsonLd.jsonLdId, URN_ANNOTATION_PREFIX + UUID.uuid( ));
			annotation.put(IOJsonLd.jsonLdType, "ao:Qualifier");
			annotation.put(IORdfs.label, "Qualifier");
			annotation.put("pav:createdWith", "urn:domeo:software:id:Domeo-2.0alpha-040");
			annotation.put("pav:importedBy", "urn:domeo:software:id:EbiConnector-0.1-001");
			annotation.put("pav:createdBy", "http://wwwdev.ebi.ac.uk/webservices/europepmc/");
			annotation.put("pav:importedFrom","http://wwwdev.ebi.ac.uk/webservices/europepmc/");
			annotation.put("pav:lastSavedOn", dateFormat.format(new Date( )));
			annotation.put("pav:versionNumber", "");
			
			// body
			JSONObject body = new JSONObject( );
			body.put(IORdfs.label, annotationJson["@graph"][0]["hasBody"]["@id"]);
			body.put(IOJsonLd.jsonLdId, annotationJson["@graph"][0]["hasBody"]["@id"]);

			// source
			JSONObject source = new JSONObject( );
			source.put(IORdfs.label, "EBI Annotator");
			source.put(IOJsonLd.jsonLdId, "http://example.org/EBI_Annotator");
			body.put("dct:source", source);
			
			// bodies
			JSONArray bodies = new JSONArray( );
			bodies.add(body);
			annotation.put("ao:hasTopic", bodies);
			
			annotation.put("pav:previousVersion", "");
			annotation.put("pav:createdOn", dateFormat.format(new Date( )));
			
			JSONObject selector = new JSONObject();
			selector.put(IOJsonLd.jsonLdId, URN_SELECTOR_PREFIX + UUID.uuid());
			selector.put(IOJsonLd.jsonLdType, "ao:PrefixSuffixTextSelector");
			selector.put(IOPav.createdOn, dateFormat.format(new Date( )));
			selector.put("ao:prefix", annotationJson["@graph"][0]["hasTarget"]["hasSelector"].prefix);
			selector.put("ao:exact", annotationJson["@graph"][0]["hasTarget"]["hasSelector"].exact);
			selector.put("ao:suffix", annotationJson["@graph"][0]["hasTarget"]["hasSelector"].suffix);
			
			JSONObject target = new JSONObject( );
			target.put(IOJsonLd.jsonLdId, URN_SPECIFIC_RESOURCE_PREFIX + UUID.uuid( ));
			target.put(IOJsonLd.jsonLdType, "ao:SpecificResource");
			target.put("ao:hasSource", snippetUrn);
			target.put("ao:hasSelector", selector);
			JSONArray context = new JSONArray( );
			context.add(target);
			annotation.put("ao:context", context);
			
			annotations.add(annotations.size( ), annotation);
		}
		
		result.put("ao:item", annotations);
		return result;
	}
	
	/** @return Create the connector agent content. */
	private JSONObject getConnectorAgent( ) {
		JSONObject result = getSoftwareAgent(
			"http://wwwdev.ebi.ac.uk/webservices/europepmc/",
			"EBI Annotator Web Service",
			"EBI Annotator Web Service",
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
				"http://wwwdev.ebi.ac.uk/webservices/europepmc/",
				"EBI Annotator Web Service",
				"EBI Annotator Web Service",
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
	
	/**
	 * Method for calling external URLs with or without proxy.
	 * @param agentKey 	The agent key for logging
	 * @param URL		The external URL to call
	 * @return The InputStream of the external URL.
	 */
	private InputStream callExternalUrl(def agentKey, String URL) {
		Proxy httpProxy = null;
		if(grailsApplication.config.annotopia.server.proxy.host && grailsApplication.config.annotopia.server.proxy.port) {
			String proxyHost = configAccessService.getAsString("annotopia.server.proxy.host"); //replace with your proxy server name or IP
			int proxyPort = configAccessService.getAsString("annotopia.server.proxy.port").toInteger(); //your proxy server port
			SocketAddress addr = new InetSocketAddress(proxyHost, proxyPort);
			httpProxy = new Proxy(Proxy.Type.HTTP, addr);
		}
		
		if(httpProxy!=null) {
			long startTime = System.currentTimeMillis();
			logInfo(agentKey, "Proxy request: " + URL);
			URL url = new URL(URL);
			URLConnection urlConn = url.openConnection(httpProxy);
			urlConn.connect();
			logInfo(agentKey, "Proxy resolved in (" + (System.currentTimeMillis()-startTime) + "ms)");
			return urlConn.getInputStream();
		} else {
			logInfo(agentKey, "No proxy request: " + URL);
			return new URL(URL).openStream();
		}
	}
	
	private def logInfo(def userId, message) {
		log.info(":" + userId + ": " + message);
	}
}
