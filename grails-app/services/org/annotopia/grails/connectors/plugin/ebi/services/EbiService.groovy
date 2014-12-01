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
package org.annotopia.grails.connectors.plugin.ebi.services

import java.io.InputStream;

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method

import org.annotopia.grails.connectors.BaseConnectorService
import org.annotopia.grails.connectors.ConnectorHttpResponseException
import org.annotopia.grails.connectors.ITextMiningService
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFLanguages
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
 * 
 * 
here goes some info on how to access the annotation triple store.

- end point: http://wwwdev.ebi.ac.uk/webservices/europepmc/openrdf-sesame
- example: http://wwwdev.ebi.ac.uk/webservices/europepmc/openrdf-sesame/repositories/europepmc/statements?contexts=_:PMC1240580
- documentation on sesame HTTP access: http://openrdf.callimachus.net/sesame/2.7/docs/system.docbook?view#The_Sesame_REST_HTTP_Protocol

In this store, there are 10 PMC articles:

PMC1240576
PMC1240579
PMC1240582
PMC1266030
PMC1240577
PMC1240580
PMC1240583
PMC1240578
PMC1240581
PMC1242150

 * @author Paolo Ciccarese <paolo.ciccarese@gmail.com>
 */
class EbiService extends BaseConnectorService {

	/** The URL to access the Term Search API. */
	private final static String EBI_TM_ERVICE_URL = "http://wwwdev.ebi.ac.uk/webservices/europepmc/openrdf-sesame/repositories/europepmc/statements";
	
	/** The configuration options for this service. */
	def grailsApplication;
	def configAccessService;
	def connectorsConfigAccessService;
	def ebiTextMiningDomeoConversionService;
	
	public String textmine(def response, String resourceUri, String content, HashMap parameters) {
		
		// perform the query
		long startTime = System.currentTimeMillis( );
		
		try {
			def format = parameters.get("format");
			List<Model> models = (List<Model>) retrieve("", parameters);
			if(format=="domeo") {
				JSONObject results = ebiTextMiningDomeoConversionService.convert("", models, "");
				response.outputStream << results.toString()
			} else {
				Object contextJson = JsonUtils.fromInputStream(callExternalUrl(apiKey,
					configAccessService.getAsString("annotopia.jsonld.openannotation.framing")));
				
				def summaryPrefix = '"total":"' + models.size() + '", ' +
					'"duration": "' + (System.currentTimeMillis()-startTime) + 'ms", ' +
					'"items":[';
				
				response.outputStream << '{"status":"results", "result": {' + summaryPrefix;
				for(int i=0; i<models.size(); i++) {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					RDFDataMgr.write(baos, models.get(i).getGraph(), RDFLanguages.JSONLD);
					
					Object framed =  JsonLdProcessor.frame(JsonUtils.fromString(baos.toString().replace('"@id" : "urn:x-arq:DefaultGraphNode",','')), contextJson, new JsonLdOptions());
					response.outputStream << JsonUtils.toPrettyString(framed)
					if(i<models.size()-1) response.outputStream << ','
				}
				response.outputStream << ']}}';
			}
		} catch(Exception e) {
			JSONObject returnMessage = new JSONObject();
			returnMessage.put("error", e.getMessage());
			log.error("Exception: " + e.getMessage() + " " + e.getClass().getName());
			return returnMessage;
		}
	}
	
	public List<Model> retrieve(String resourceUri, HashMap parametrization) {
		log.info 'textmine:Resource: ' + resourceUri + ' Parametrization: ' + parametrization
		
		try {
			String pmcid = parametrization.get("pmcid");
			def url = EBI_TM_ERVICE_URL;
			if(pmcid != null) {
				url += "?context=_:" + pmcid;
				
				log.info 'url ' + url
				
				// perform the query
				long startTime = System.currentTimeMillis( );
				try {
					def http = new HTTPBuilder(url);
					evaluateProxy(http, url);
		
					http.request(Method.GET, "application/rdf+xml;charset=UTF-8") {
						requestContentType = ContentType.URLENC
						
						response.success = { resp, xml ->
							long duration = System.currentTimeMillis( ) - startTime;

							List<Model> models = new ArrayList<Model>();
								
							 final Model model = ModelFactory.createDefaultModel();
							 model.read(new ByteArrayInputStream(xml.getBytes()), null);
							 
							 log.error model.size();

							 List<Resource> annotations = new ArrayList<Resource>();
							 List<Resource> specificTargets = new ArrayList<Resource>();
							 StmtIterator specificTargetsIterator = model.listStatements(
								 null,
								 ResourceFactory.createProperty("http://www.w3.org/ns/oa#hasSource"),
								 ResourceFactory.createResource("http://europepmc.org/articles/" + pmcid));
							 while(specificTargetsIterator.hasNext()) {
								 Model annotationModel = ModelFactory.createDefaultModel();
								 
								 Statement specificTargetStatement = specificTargetsIterator.next();
								 Resource CURRENT_SPECIFIC_TARGET = specificTargetStatement.getSubject();
								 
								 StmtIterator selectorResourceIterator = model.listStatements(
									 CURRENT_SPECIFIC_TARGET,
									 ResourceFactory.createProperty("http://www.w3.org/ns/oa#hasSelector"),
									 null);
								 while(selectorResourceIterator.hasNext()) {
									 Statement selectorStatement = selectorResourceIterator.next();
									 Resource CURRENT_SELECTOR = selectorStatement.getObject();
									 
									 StmtIterator selectorStatementIterator = model.listStatements(
										 CURRENT_SELECTOR,
										 null,
										 null);
									 while(selectorStatementIterator.hasNext()) {
										 Statement s = selectorStatementIterator.next();
										 if(s.getPredicate().toString()=="http://www.w3.org/ns/oa#postfix") {
											 annotationModel.add(s.getSubject(), ResourceFactory.createProperty("http://www.w3.org/ns/oa#suffix"), s.getObject());
										 } else {
										 	 annotationModel.add(s);
										 }
									 }
								 }
								 
								 StmtIterator annotationsIterator = model.listStatements(
									 null,
									 ResourceFactory.createProperty("http://www.w3.org/ns/oa#hasTarget"),
									 specificTargetStatement.getSubject());
								 while(annotationsIterator.hasNext()) {
									 Statement annotationStatement = annotationsIterator.next();
									 Resource CURRENT_ANNOTATION = annotationStatement.getSubject();
									 annotations.add(CURRENT_ANNOTATION);

									 // Annotations statements
									 StmtIterator annotationStatementsIterator = model.listStatements(
										CURRENT_ANNOTATION,
										null,
										null);
									 while(annotationStatementsIterator.hasNext()) {
										 Statement annStatement = annotationStatementsIterator.next();
										
										 String tagLabel = null;
										 
										 if(annStatement.getPredicate().toString()=="http://www.w3.org/2000/01/rdf-schema#label") {
											 tagLabel = annStatement.getLiteral().getString();
										 } else {
											annotationModel.add(annStatement);
										 }
										 
										 // Handling of Semantic Tags
										 StmtIterator annotationBodyStatementIterator = model.listStatements(
											 CURRENT_ANNOTATION,
											 ResourceFactory.createProperty("http://www.w3.org/ns/oa#hasBody"),
											 null);
										 while(annotationBodyStatementIterator.hasNext()) {
											 Statement sBody = annotationBodyStatementIterator.next()
											 StmtIterator annotationBodyStatementsIterator = model.listStatements(
												 sBody.getObject(),
												 null,
												 null);
											 while(annotationBodyStatementsIterator.hasNext()) {
												 annotationModel.add(annotationBodyStatementsIterator.next());
											 }
											 if(tagLabel!=null)
											 annotationModel.add(sBody.getObject(),  ResourceFactory.createProperty("http://www.w3.org/2000/01/rdf-schema#label"), ResourceFactory.createPlainLiteral(tagLabel));
										 }									 
										 
										 // Add annotatedBy statements	
										 def annotatedBy = ResourceFactory.createResource("http://wwwdev.ebi.ac.uk/webservices/europepmc/");					 
										 annotationModel.add(
											 annotatedBy,
											 ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
											 ResourceFactory.createResource("http://xmlns.com/foaf/0.1/Software"));
										 annotationModel.add(
											 annotatedBy,
											 ResourceFactory.createProperty("http://www.w3.org/2000/01/rdf-schema#label"),
											 ResourceFactory.createPlainLiteral("EBI Pre-computed text mining"));
										 
										 annotationModel.add(
											 CURRENT_ANNOTATION, 
											 ResourceFactory.createProperty("http://www.w3.org/ns/oa#annotatedBy"), 
											 annotatedBy);
										 
										 // Add serializedBy statements
										 def serializedBy = ResourceFactory.createResource("http://annotopia.org");
										 annotationModel.add(
											 serializedBy,
											 ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
											 ResourceFactory.createResource("http://xmlns.com/foaf/0.1/Software"));
										 annotationModel.add(
											 serializedBy,
											 ResourceFactory.createProperty("http://www.w3.org/2000/01/rdf-schema#label"),
											 ResourceFactory.createPlainLiteral("Annotopia"));
										 
										 annotationModel.add(
											 CURRENT_ANNOTATION,
											 ResourceFactory.createProperty("http://www.w3.org/ns/oa#serializedBy"),
											 serializedBy);
									 }
									 
									 // Specific targets statements
									 StmtIterator annotationSpecificTargetIterator = model.listStatements(
										 CURRENT_SPECIFIC_TARGET,
										 null,
										 null);
									 while(annotationSpecificTargetIterator.hasNext()) {
										 annotationModel.add(annotationSpecificTargetIterator.next());
									 }
									 
									 // Selectors statements
									 
								 }
								 
								 models.add(annotationModel);
								 
								 //ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
								 //RDFDataMgr.write(outputStream, annotationModel, RDFLanguages.JSONLD);
								 //log.info outputStream.toString();
								 //log.info '------------'
							 }
							 return models;
						}
						
						response.'404' = { resp ->
							log.error('Not found: ' + resp.getStatusLine())
							throw new ConnectorHttpResponseException(resp, 404, 'Service not found. The problem has been reported')
						}
					 
						response.'503' = { resp ->
							log.error('Not available: ' + resp.getStatusLine())
							throw new ConnectorHttpResponseException(resp, 503, 'Service temporarily not available. Try again later.')
						}
						
						response.failure = { resp, xml ->
							log.error('failure: ' + resp.getStatusLine())
							throw new ConnectorHttpResponseException(resp, resp.getStatusLine())
						}
					}
				} catch (groovyx.net.http.HttpResponseException ex) {
					log.error("HttpResponseException: Service " + ex.getMessage())
					throw new RuntimeException(ex);
				} catch (java.net.ConnectException ex) {
					log.error("ConnectException: " + ex.getMessage())
					throw new RuntimeException(ex);
				}
				
			} else {
				log.error('Not pmcid specified')
				//throw new ConnectorHttpResponseException(resp, 400, 'Missing required parameter: pmc')
			}
		} catch(Exception e) {
			JSONObject returnMessage = new JSONObject();
			returnMessage.put("error", e.getMessage());
			log.error("Exception: " + e.getMessage() + " " + e.getClass().getName());
			return returnMessage;
		}
		
		//TODO Logic 
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
