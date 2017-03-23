/*
 * Copyright (c) 2009-2017, United States Government, as represented by the Secretary of Health and Human Services.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above
 *       copyright notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *     * Neither the name of the United States Government nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE UNITED STATES GOVERNMENT BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package gov.hhs.fha.nhinc.documentretrieve.loadtest;

import gov.hhs.fha.nhinc.common.nhinccommon.AssertionType;
import gov.hhs.fha.nhinc.docretrieve.adapter.proxy.AdapterDocRetrieveProxy;
import gov.hhs.fha.nhinc.largefile.LargeFileUtils;
import gov.hhs.fha.nhinc.loadtest.DataManager;
import gov.hhs.fha.nhinc.loadtest.Document;
import gov.hhs.fha.nhinc.loadtest.ILoadTestData;
import gov.hhs.fha.nhinc.loadtest.LoadTestData;
import ihe.iti.xds_b._2007.ObjectFactory;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetRequestType.DocumentRequest;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType;
import ihe.iti.xds_b._2007.RetrieveDocumentSetResponseType.DocumentResponse;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author mweaver
 */
public class DocumentRetrieveBO implements AdapterDocRetrieveProxy {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentRetrieveBO.class);

    public RetrieveDocumentSetResponseType retrieveDocumentSet(RetrieveDocumentSetRequestType request,
        AssertionType assertion) {

        ObjectFactory of = new ObjectFactory();
        RetrieveDocumentSetResponseType response = of.createRetrieveDocumentSetResponseType();

        try {
            DocumentResponse dr = of.createRetrieveDocumentSetResponseTypeDocumentResponse();
            LoadTestData ltd = DataManager.getInstance().getLoadTestData();

            RegistryResponseType responseType = new RegistryResponseType();
            responseType.setStatus("urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success");
            response.setRegistryResponse(responseType);

            for (DocumentRequest req : request.getDocumentRequest()) {
                ILoadTestData data = ltd.getLoadTestData().get(req.getDocumentUniqueId());
                if (data instanceof Document) {
                    Document d = (Document) data;
                    dr.setDocument(LargeFileUtils.getInstance().convertToDataHandler(d.getDocument()));
                } else {
                    byte[] bytes = {0xa, 0xa};
                    dr.setDocument(LargeFileUtils.getInstance().convertToDataHandler(bytes));
                }
                dr.setHomeCommunityId(ltd.getEnvironment());
                dr.setDocumentUniqueId(req.getDocumentUniqueId());
                dr.setRepositoryUniqueId(req.getRepositoryUniqueId());
                dr.setMimeType("text/xml");

                response.getDocumentResponse().add(dr);
            }
        } catch (Exception e) {
            LOG.error(e.getLocalizedMessage(), e);
        }
        return response;
    }
}
