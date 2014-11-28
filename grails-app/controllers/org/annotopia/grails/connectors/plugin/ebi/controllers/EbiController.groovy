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
package org.annotopia.grails.connectors.plugin.ebi.controllers

import org.annotopia.grails.connectors.BaseConnectorController
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFLanguages

import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import com.github.jsonldjava.utils.JsonUtils
import com.hp.hpl.jena.rdf.model.Model


/**
 * @author Paolo Ciccarese <paolo.ciccarese@gmail.com>
 */
class EbiController extends BaseConnectorController {

	def ebiService
	def configAccessService
	def apiKeyAuthenticationService
	
	/**
	 * curl -i -X POST http://localhost:8090/cn/ebi/textmine -H "Content-Type: application/json" -d'{"apiKey":"164bb0e0-248f-11e4-8c21-0800200c9a66","pmcid":"PMC1240580"}'
	 */
	def textmine = {
		long startTime = System.currentTimeMillis( );
		
		// retrieve the API key
		def apiKey = retrieveApiKey(startTime);
		if(!apiKey) {
			return;
		}
		
		// retrieve the resource
		def pmcid = retrieveValue(request.JSON.pmcid, params.pmcid,
			"pmcid", startTime);
		if(!pmcid) {
			return;
		}
		
		HashMap parameters = new HashMap( );
		parameters.put("pmcid", pmcid);
		
		List<Model> models = ebiService.retrieve("", parameters);

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
