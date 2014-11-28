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

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method

import org.annotopia.grails.connectors.BaseConnectorService
import org.annotopia.grails.connectors.ConnectorHttpResponseException
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
	def connectorsConfigAccessService;
	
	@Override
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
	
							//log.info  "XML was ${xml}"
							
							List<Model> models = new ArrayList<Model>();
							
							 final Model model = ModelFactory.createDefaultModel();
							 model.read(new ByteArrayInputStream(xml.getBytes()), null);
							 
							 log.error model.size();
							 
//							 List<Resource> annotations = new ArrayList<Resource>();
//							 StmtIterator iter = model.listStatements(
//								 null,  
//								 ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), 
//								 ResourceFactory.createResource("http://www.w3.org/ns/oa#Annotation"));
//							 while(iter.hasNext()) {
//								 Statement s = iter.next();
//								 annotations.add(s.getSubject());
//							 }
							 
//							 log.error annotations.size();
							 
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
										 annotationModel.add(selectorStatementIterator.next());
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
									 
									 log.error '-=========== ' + CURRENT_ANNOTATION
									 
									 // Annotations statements
									 StmtIterator annotationStatementsIterator = model.listStatements(
										CURRENT_ANNOTATION,
										null,
										null);
									 while(annotationStatementsIterator.hasNext()) {
										 annotationModel.add(annotationStatementsIterator.next());
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
								 
								 ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
								 RDFDataMgr.write(outputStream, annotationModel, RDFLanguages.JSONLD);
								 log.info outputStream.toString();
								 log.info '------------'
							 }
							 
							 return models;
							 //ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
							 //RDFDataMgr.write(outputStream, model, RDFLanguages.JSONLD);
							 //outputStream.toString();
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
}
