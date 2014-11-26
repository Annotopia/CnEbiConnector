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
package org.annotopia.grails.connectors.plugin.ebi.tests

import static org.junit.Assert.*
import grails.test.mixin.TestFor

import org.annotopia.grails.connectors.plugin.ebi.services.EbiService
import org.codehaus.groovy.grails.web.json.JSONObject
import org.junit.BeforeClass
import org.junit.Test

/** 
 * JUnit test case for the term search.
 * @author Tom Wilkin 
 */
@TestFor(EbiService)
class EbiServiceTest {
	
	/** The instance of the Nif Service. */
	def static ebiService;
	
	@BeforeClass
	public static void initialise( ) {
		ebiService = new EbiService( );
	}

	@Test
	public void termSearchTest( ) {
		log.info("TEST:recallPmc1240580");
		
		HashMap parameters = new HashMap( );
		parameters.put("pmcid", "PMC1240580");

		JSONObject result = ebiService.retrieve("url", parameters);
		//log.info(result);
		assertNotNull(result);
	}
	
};
