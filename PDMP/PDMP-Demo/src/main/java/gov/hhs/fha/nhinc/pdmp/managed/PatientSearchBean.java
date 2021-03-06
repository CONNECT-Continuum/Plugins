/*
 * Copyright (c) 2009-2016, United States Government, as represented by the Secretary of Health and Human Services.
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
package gov.hhs.fha.nhinc.pdmp.managed;

import static gov.hhs.fha.nhinc.util.StreamUtils.closeStreamSilently;

import gov.hhs.fha.nhinc.admingui.util.ConnectionHelper;
import gov.hhs.fha.nhinc.nhinclib.NhincConstants;
import gov.hhs.fha.nhinc.nhinclib.NullChecker;
import gov.hhs.fha.nhinc.pdmp.AddressRequiredZipType;
import gov.hhs.fha.nhinc.pdmp.PatientType;
import gov.hhs.fha.nhinc.pdmp.model.Document;
import gov.hhs.fha.nhinc.pdmp.model.Patient;
import gov.hhs.fha.nhinc.pdmp.model.PdmpPatient;
import gov.hhs.fha.nhinc.pdmp.model.PrescriptionInfo;
import gov.hhs.fha.nhinc.pdmp.services.CDAParserService;
import gov.hhs.fha.nhinc.pdmp.services.CDAParserServiceImpl;
import gov.hhs.fha.nhinc.pdmp.services.GatewayService;
import gov.hhs.fha.nhinc.pdmp.services.PdmpService;
import gov.hhs.fha.nhinc.pdmp.services.PdmpServiceImpl;
import gov.hhs.fha.nhinc.pdmp.services.PrescriptionClassSearch;
import gov.hhs.fha.nhinc.pdmp.services.PrescriptionClassSearchImpl;
import gov.hhs.fha.nhinc.pdmp.util.HtmlParserUtil;
import gov.hhs.fha.nhinc.pdmp.util.XSLTransformHelper;
import gov.hhs.fha.nhinc.properties.PropertyAccessException;
import gov.hhs.fha.nhinc.properties.PropertyAccessor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import org.primefaces.model.ByteArrayContent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Managed bean to capture/render the data from/to the UI.
 *
 * @author Naresh Subramanyan
 */
@ManagedBean
@SessionScoped
@Component
public class PatientSearchBean {

    private static final Logger LOG = LoggerFactory.getLogger(PatientSearchBean.class);

    private final PdmpService pdmpService = new PdmpServiceImpl();
    private final PrescriptionClassSearch drugClassService = new PrescriptionClassSearchImpl();
    private final CDAParserService cdaParser = new CDAParserServiceImpl();
    private final XSLTransformHelper transformer = new XSLTransformHelper();
    public static final String DEFAULT_XSL_FILE = "/WEB-INF/CDA.xsl";

    // Generic Variables, can be moved to a constant file
    private static final String PATIENT_FOUND = "Patient Record Found:";
    private static final String PATIENT_NOT_FOUND = "Patient Not Found.";
    private static final String DOCUMENT_NOT_FOUND = "No Documents Found.";
    private static final String DOCUMENT_FOUND = "Documents Found:";
    private static final String PDMP_SEARCH_PAGE = "pdmpPatientSearch";

    private int activeIndex = 0;
    private String displayOrganizationName;

    // For Lookup..should be moved to a different managed bean
    private List<SelectItem> documentTypeList;
    private Map<String, String> organizationList;
    private Map<String, String> genderList;

    // Used in Patient Query Page
    private boolean patientFound;
    private String patientMessage;
    private String firstName;
    private String lastName;
    private Date dateOfBirth;
    private String gender;
    private String phone;
    private String zip;
    private String organization;
    private List<Patient> patientList;
    private int selectedPatient;

    // Used in Document Query Page
    private boolean documentFound;
    private Date documentRangeFrom;
    private Date documentRangeTo;
    private String documentMessage;
    private List<String> querySelectedDocuments;
    private int selectedDocument;
    // TODO: Temporary should be removed, should use the patient object documentList
    private List<Document> documentList;

    private Date beginDrugRange;
    private Date endDrugRange;
    private boolean drugsFound;
    private List<PrescriptionInfo> prescriptionList;
    private String drugsResultUrl;
    private String prescriptionMessage;
    private boolean opioidsOnly;
    private List<PrescriptionInfo> opioidList;
    private List<PrescriptionInfo> fullPrescriptionList;

    private List<PrescriptionInfo> selectedDrugs;
    private String selectedDocId;

    /**
     * Instantiate all the variables and load the lookup Data
     */
    public PatientSearchBean() {
        documentList = new ArrayList<>();
        querySelectedDocuments = new ArrayList<>();
        patientList = new ArrayList<>();
        documentTypeList = new ArrayList<>();
        prescriptionList = new ArrayList<>();
        fullPrescriptionList = new ArrayList<>();
        opioidList = new ArrayList<>();
        drugsFound = false;
        opioidsOnly = false;
        prescriptionMessage = "";

        // Populate Organization List from UDDI
        organizationList = populateOrganizationFromConnectManagerCache();
        // populate Gender List
        genderList = populteGenderList();
        // populate document types
        documentTypeList = populateDocumentTypes();
    }

    /**
     * Action method called when user clicks the Patient Search
     *
     */
    public void searchPatient() {
        // start with a clean slate
        clearDocumentQueryTab();
        // Call the NwHIN PD to get the documents
        patientFound = GatewayService.getInstance().discoverPatient(this);
        // set the UI display message
        patientMessage = patientFound ? PATIENT_FOUND : PATIENT_NOT_FOUND;
    }

    /**
     * Action method called when user clicks the Document Query Search
     *
     */
    public void searchPatientDocument() {
        // Call the NwHIN QD to get the documents
        documentFound = GatewayService.getInstance().queryDocument(this);
        // set the UI display message
        documentMessage = documentFound ? DOCUMENT_FOUND : DOCUMENT_NOT_FOUND;
    }

    /**
     * Action method called when user clicks the Document View.
     *
     */
    public void retrieveDocument() {
        // check to make sure if the Document Retrieve is already done
        if (getDocumentList().get(getSelectedDocument()).isDocumentRetrieved()) {
            return;
        }
        // Call the NwHIN RD to get the documents
        documentFound = GatewayService.getInstance().retrieveDocument(this);
    }

    public void searchForPrescriptions() {
        PatientType patient = new PatientType();
        PatientType.Name name = new PatientType.Name();
        name.setFirst(firstName);
        name.setLast(lastName);

        gov.hhs.fha.nhinc.pdmp.ObjectFactory of = new gov.hhs.fha.nhinc.pdmp.ObjectFactory();
        patient.getContent().add(of.createPatientTypeName(name));
        patient.getContent().add(of.createPatientTypeBirthdate(pdmpService.getGregorianCalendar(dateOfBirth)));
        patient.getContent().add(of.createPatientTypeSexCode(pdmpService.getSexCodeType(gender)));
        AddressRequiredZipType address = new AddressRequiredZipType();
        address.setZipCode(zip);
        patient.getContent().add(of.createPatientTypeAddress(address));
        patient.getContent().add(of.createPatientTypePhone(new BigInteger(phone)));

        PdmpPatient patientResult = pdmpService.searchForPdmpInfo(patient, pdmpService.buildDateRange(beginDrugRange, endDrugRange));

        if (patientResult != null && NullChecker.isNotNullish(patientResult.getReportUrl())) {
            try {
                drugsResultUrl = patientResult.getReportUrl();
                prescriptionList = HtmlParserUtil.getAllPrescriptions(patientResult.getReportUrl());
                fullPrescriptionList = prescriptionList;
                for (PrescriptionInfo drug : prescriptionList) {
                    String namePart = parseDrugName(drug.getDrugName());
                    if (NullChecker.isNotNullish(namePart)) {
                        drug.setDrugClass(drugClassService.searchForDrugClass(namePart));
                    }
                    if (NullChecker.isNotNullish(drug.getDrugClass())
                            && drug.getDrugClass().toLowerCase().contains("opioid")) {
                        drug.setIsOpioid(true);
                        opioidList.add(drug);
                    } else {
                        drug.setIsOpioid(false);
                    }
                }
                prescriptionMessage = "Prescription Report URL: " + patientResult.getReportUrl();
                drugsFound = true;
            } catch (IOException ex) {
                LOG.warn("Unable to get prescriptions from report {}", patientResult.getReportUrl(), ex);
            }
        }
    }

    public void addPrescriptionsToDoc() {
        setSelectedDocument();
        retrieveDocument();
        InputStream stream = new ByteArrayInputStream(getDocumentList().get(getSelectedDocument()).getDocumentContent());

        String updatedDoc = cdaParser.addMedicationSection(stream, selectedDrugs);
        if (NullChecker.isNotNullish(updatedDoc)) {
            Document document = getDocumentList().get(getSelectedDocument());
            document.setDocumentContent(updatedDoc.getBytes());
            document.setHtmlContent(convertXmlToHtml(updatedDoc.getBytes()));
        }
    }

    private void setSelectedDocument() {
        if (NullChecker.isNotNullish(selectedDocId)) {
            for (Document doc : documentList) {
                if (selectedDocId.equalsIgnoreCase(doc.getDocumentId())) {
                    selectedDocument = doc.getDocumentIndex();
                    return;
                }
            }
        }
    }

    public byte[] convertXmlToHtml(byte[] originalDocument) {
        final InputStream xsl = FacesContext.getCurrentInstance().getExternalContext()
                .getResourceAsStream(DEFAULT_XSL_FILE);
        final InputStream xml = new ByteArrayInputStream(originalDocument);
        byte[] convertXmlToHtml = null;
        if (xsl != null) {
            convertXmlToHtml = transformer.convertXMLToHTML(xml, xsl);
            closeStreamSilently(xsl);
        }
        return convertXmlToHtml;
    }

    /**
     * Action method called when user clicks the Start Over method. This will
     * clear all the information stored in the session.
     *
     * @return
     */
    public String startOver() {
        // make it clean slate
        // go back to the Patient Search tab
        clearDocumentQueryTab();
        setActiveIndex(0);
        // reload the lookup data
        // Populate Organization List from UDDI
        organizationList = populateOrganizationFromConnectManagerCache();
        // populate Gender List
        genderList = populteGenderList();
        // populate document types
        documentTypeList = populateDocumentTypes();
        clearPatientTab();
        return PDMP_SEARCH_PAGE;
    }

    /**
     * Remove all the patient information from the Session
     *
     * @return
     */
    public void clearPatientTab() {
        dateOfBirth = null;
        firstName = null;
        lastName = null;
        organization = "";
        gender = null;
        phone = null;
        zip = null;
        patientFound = false;
        patientMessage = "";
        patientList.clear();
        setSelectedPatient(0);
        clearDocumentQueryTab();
        clearPrescriptions();
    }

    /**
     * Remove all the Document Query information
     *
     * @return
     */
    public void clearDocumentQueryTab() {
        documentFound = false;
        documentRangeFrom = null;
        documentRangeTo = null;

        querySelectedDocuments.clear();
        documentMessage = "";

        // Reset the list
        documentList.clear();

        getSelectedCurrentPatient().getDocumentList().clear();
        setSelectedDocument(0);
    }

    public void clearPrescriptions() {
        drugsFound = false;
        beginDrugRange = null;
        endDrugRange = null;
        prescriptionMessage = "";
        opioidsOnly = false;
        prescriptionList.clear();
        fullPrescriptionList.clear();
        opioidList.clear();
        selectedDocId = "";
    }

    /**
     * @return the firstName
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * @param firstName the firstName to set
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * @return the lastName
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * @param lastName the lastName to set
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * @return the dateOfBirth
     */
    public Date getDateOfBirth() {
        return dateOfBirth;
    }

    /**
     * @param dateOfBirth the dateOfBirth to set
     */
    public void setDateOfBirth(Date dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    /**
     * @return the gender
     */
    public String getGender() {
        return gender;
    }

    /**
     * @param gender the gender to set
     */
    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public Date getBeginDrugRange() {
        return beginDrugRange;
    }

    public void setBeginDrugRange(Date beginDrugRange) {
        this.beginDrugRange = beginDrugRange;
    }

    public Date getEndDrugRange() {
        return endDrugRange;
    }

    public void setEndDrugRange(Date endDrugRange) {
        this.endDrugRange = endDrugRange;
    }

    public boolean isDrugsFound() {
        return drugsFound;
    }

    public void setDrugsFound(boolean drugsFound) {
        this.drugsFound = drugsFound;
    }

    public boolean isDrugsAndDocumentFound() {
        return drugsFound && documentFound;
    }

    public List<PrescriptionInfo> getPrescriptionList() {
        return prescriptionList;
    }

    public void setPrescriptionList(List<PrescriptionInfo> prescriptionList) {
        this.prescriptionList = prescriptionList;
    }

    public List<String> getDocumentIdList() {
        List<String> documentIds = new ArrayList<>();
        List<Document> docList = getDocumentList();
        if (docList != null) {
            for (Document doc : docList) {
                documentIds.add(doc.getDocumentId());
            }
        }
        return documentIds;
    }

    public String getSelectedDocId() {
        return selectedDocId;
    }

    public void setSelectedDocId(String selectedDocId) {
        this.selectedDocId = selectedDocId;
    }

    public String getDrugsResultUrl() {
        return drugsResultUrl;
    }

    public void setDrugsResultUrl(String drugsResultUrl) {
        this.drugsResultUrl = drugsResultUrl;
    }

    public boolean isOpioidsOnly() {
        return opioidsOnly;
    }

    public void setOpioidsOnly(boolean opioidsOnly) {
        this.opioidsOnly = opioidsOnly;
    }

    public void changeTableForOpioidValues() {
        if (opioidsOnly) {
            prescriptionList = opioidList;
        } else {
            prescriptionList = fullPrescriptionList;
        }
    }

    public List<PrescriptionInfo> getSelectedDrugs() {
        return selectedDrugs;
    }

    public void setSelectedDrugs(List<PrescriptionInfo> selectedDrugs) {
        this.selectedDrugs = selectedDrugs;
    }

    /**
     * @return the organization
     */
    public String getOrganization() {
        return organization;
    }

    /**
     * @param organization the organization to set
     */
    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public boolean enablePatientqueryTab() {
        if (isPatientFound()) {
            return false;
        }
        return true;
    }

    /**
     * @return the activeIndex
     */
    public int getActiveIndex() {
        return activeIndex;
    }

    /**
     * @param activeIndex the activeIndex to set
     */
    public void setActiveIndex(int activeIndex) {
        this.activeIndex = activeIndex;
    }

    /**
     * @return the queryDocuments
     */
    public List<String> getQueryDocuments() {
        return querySelectedDocuments;
    }

    /**
     * @param queryDocuments the queryDocuments to set
     */
    public void setQueryDocuments(List<String> queryDocuments) {
        querySelectedDocuments = queryDocuments;
    }

    /**
     * @return the documentTypeList
     */
    public List<SelectItem> getDocumentTypeList() {
        return documentTypeList;
    }

    /**
     * @param documentTypeList the documentTypeList to set
     */
    public void setDocumentTypeList(List<SelectItem> documentTypeList) {
        this.documentTypeList = documentTypeList;
    }

    /**
     * @return the patientList
     */
    public List<Patient> getPatientList() {
        return patientList;
    }

    /**
     * @return the patientMessage
     */
    public String getPatientMessage() {
        return patientMessage;
    }

    public boolean isPatientFound() {
        return patientFound;
    }

    public String switchTab() {
        activeIndex = 1;
        return PDMP_SEARCH_PAGE;
    }

    /**
     * @return the documentFound
     */
    public boolean isDocumentFound() {
        return documentFound;
    }

    /**
     * @return the documentList
     */
    public List<Document> getDocumentList() {
        // always return the list from the patient object
        if (getSelectedCurrentPatient() != null) {
            return getSelectedCurrentPatient().getDocumentList();
        }
        // return the empty list if the patient object is empty
        return documentList;
    }

    /**
     * @return the documentMessage
     */
    public String getDocumentMessage() {
        return documentMessage;
    }

    /**
     * @return the documentRangeFrom
     */
    public Date getDocumentRangeFrom() {
        return documentRangeFrom;
    }

    /**
     * @param documentRangeFrom the documentRangeFrom to set
     */
    public void setDocumentRangeFrom(Date documentRangeFrom) {
        this.documentRangeFrom = documentRangeFrom;
    }

    /**
     * @return the documentRangeTo
     */
    public Date getDocumentRangeTo() {
        return documentRangeTo;
    }

    /**
     * @param documentRangeTo the documentRangeTo to set
     */
    public void setDocumentRangeTo(Date documentRangeTo) {
        this.documentRangeTo = documentRangeTo;
    }

    /**
     * @return the organizationList
     */
    public Map<String, String> getOrganizationList() {
        return organizationList;
    }

    /**
     * @return the genderList
     */
    public Map<String, String> getGenderList() {
        return genderList;
    }

    /**
     * @return the selectedDocument
     */
    public int getSelectedDocument() {
        return selectedDocument;
    }

    /**
     * @param selectedDocument the selectedDocument to set
     */
    public void setSelectedDocument(int selectedDocument) {
        this.selectedDocument = selectedDocument;
    }

    public String getPrescriptionMessage() {
        return prescriptionMessage;
    }

    public void setPrescriptionMessage(String prescriptionMessage) {
        this.prescriptionMessage = prescriptionMessage;
    }

    /**
     * Populate the Organization lookup data list from the UDDI. This logic
     * needs to be moved to a Utility or to the application bean.
     *
     */
    private Map<String, String> populateOrganizationFromConnectManagerCache() {
        return new ConnectionHelper().getOrgNameAndRemoteHcidMap();
    }

    /**
     * Populate the gender lookup data list. This logic needs to be moved to a
     * Utility or to the application bean.
     *
     */
    private Map<String, String> populteGenderList() {
        Map<String, String> localGenderList = new HashMap<>();
        localGenderList.put("Male", "M");
        localGenderList.put("Female", "F");
        localGenderList.put("Undifferentiated", "U");
        return localGenderList;
    }

    /**
     * Populate the Document Types List from the property file
     * documentType.properties file. This logic needs to be moved to a Utility
     * or to the application bean.
     *
     */
    private List<SelectItem> populateDocumentTypes() {
        List<SelectItem> localDocumentTypeList = new ArrayList<>();

        try {
            // Load the documentType.properties file
            Properties localDocumentTypeProperties = PropertyAccessor.getInstance()
                    .getProperties(NhincConstants.DOCUMENT_TYPE_PROPERTY_FILE);
            Iterator<Entry<Object, Object>> it = localDocumentTypeProperties.entrySet().iterator();
            while (it.hasNext()) {
                Entry<Object, Object> property = it.next();
                localDocumentTypeList.add(new SelectItem(property.getKey(), (String) property.getValue()));
            }
        } catch (PropertyAccessException ex) {
            LOG.error("Not able to load the document types from the property file: {}", ex.getLocalizedMessage(), ex);
        }
        return localDocumentTypeList;
    }

    /**
     *
     * @return
     */
    public String getCreationTimeUiDisplay() {
        String formattedDate = null;
        if (!getDocumentList().isEmpty()) {
            if (getDocumentList().get(selectedDocument).getCreationTime() != null) {
                Date currentDate = getDocumentList().get(selectedDocument).getCreationTime();
                SimpleDateFormat dateformat = new SimpleDateFormat("MM/dd/yyyy");
                formattedDate = dateformat.format(currentDate);
            }
        }
        return formattedDate;
    }

    /**
     * @return the selectedPatient
     */
    public int getSelectedPatient() {
        return selectedPatient;
    }

    /**
     * @param selectedPatient the selectedPatient to set
     */
    public void setSelectedPatient(int selectedPatient) {
        this.selectedPatient = selectedPatient;
    }

    /**
     * Returns the currently selected patient
     *
     * @return Patient
     */
    public Patient getSelectedCurrentPatient() {
        if (getPatientList().isEmpty()) {
            return new Patient();
        }
        return getPatientList().get(selectedPatient);
    }

    /**
     * Returns the currently selected document
     *
     * @return Document
     */
    public Document getSelectedCurrentDocument() {
        if (getDocumentList().isEmpty()) {
            // required for rendering the page for the first time
            return new Document();
        }
        return getDocumentList().get(selectedDocument);
    }

    public StreamedContent getDocumentImage() {
        // return the content only if its an image file
        if (getSelectedCurrentDocument().getContentType() != null && (getSelectedCurrentDocument().getContentType()
                .equals(GatewayService.CONTENT_TYPE_IMAGE_PNG)
                || getSelectedCurrentDocument().getContentType().equals(GatewayService.CONTENT_TYPE_IMAGE_GIF)
                || getSelectedCurrentDocument().getContentType().equals(GatewayService.CONTENT_TYPE_IMAGE_JPEG))) {
            byte[] imageInByteArray = getSelectedCurrentDocument().getDocumentContent();
            return new DefaultStreamedContent(new ByteArrayInputStream(imageInByteArray),
                    getSelectedCurrentDocument().getContentType());
        }
        return null;
    }

    /**
     * @return the renderDocumentimage
     */
    public boolean isRenderDocumentimage() {
        return getSelectedCurrentDocument().getContentType() != null && (getSelectedCurrentDocument().getContentType()
                .equals(GatewayService.CONTENT_TYPE_IMAGE_PNG)
                || getSelectedCurrentDocument().getContentType().equals(GatewayService.CONTENT_TYPE_IMAGE_GIF)
                || getSelectedCurrentDocument().getContentType().equals(GatewayService.CONTENT_TYPE_IMAGE_JPEG));
    }

    /**
     * @return the renderDocumentPdf
     */
    public boolean isRenderDocumentPdf() {
        return getSelectedCurrentDocument().getContentType() != null
                && getSelectedCurrentDocument().getContentType().equals(GatewayService.CONTENT_TYPE_APPLICATION_PDF);
    }

    /**
     * @return the renderDocumentText
     */
    public boolean isRenderDocumentText() {
        return getSelectedCurrentDocument().getContentType() != null
                && (getSelectedCurrentDocument().getContentType().equals(GatewayService.CONTENT_TYPE_APPLICATION_XML)
                || getSelectedCurrentDocument().getContentType().equals(GatewayService.CONTENT_TYPE_TEXT_HTML)
                || getSelectedCurrentDocument().getContentType().equals(GatewayService.CONTENT_TYPE_TEXT_PLAIN)
                || getSelectedCurrentDocument().getContentType().equals(GatewayService.CONTENT_TYPE_TEXT_XML));
    }

    /**
     * @return the documentPdf
     */
    public StreamedContent getDocumentPdf() {
        // return the content only if its an pdf file
        if (getSelectedCurrentDocument().getContentType() != null
                && getSelectedCurrentDocument().getContentType().equals(GatewayService.CONTENT_TYPE_APPLICATION_PDF)) {
            byte[] imageInByteArray = getSelectedCurrentDocument().getDocumentContent();
            return new DefaultStreamedContent(new ByteArrayInputStream(imageInByteArray),
                    getSelectedCurrentDocument().getContentType());
        }
        return null;
    }

    /**
     * @return the XML Clinical document in HTML format
     */
    public String getDocumentXml() {
        // return the content only if its an xml file
        if (getSelectedCurrentDocument().getContentType() != null && getSelectedCurrentDocument().getHtmlContent() != null
                && (getSelectedCurrentDocument().getContentType()
                        .equals(GatewayService.CONTENT_TYPE_APPLICATION_XML)
                || getSelectedCurrentDocument().getContentType().equals(GatewayService.CONTENT_TYPE_TEXT_HTML)
                || getSelectedCurrentDocument().getContentType().equals(GatewayService.CONTENT_TYPE_TEXT_PLAIN)
                || getSelectedCurrentDocument().getContentType().equals(GatewayService.CONTENT_TYPE_TEXT_XML))) {

            return new String(getSelectedCurrentDocument().getHtmlContent());
        }
        return null;
    }

    /**
     * @return the renderDcoumentNotSupported
     */
    public boolean isRenderDcoumentNotSupported() {
        return !(isRenderDocumentPdf() || isRenderDocumentText() || isRenderDocumentimage());
    }

    /**
     *
     * @return displayOrganizationName
     */
    public String getDisplayOrganizationName() {
        return displayOrganizationName;
    }

    /**
     *
     * @param displayOrganizationName the displayOrganizationName to set
     */
    public void setDisplayOrganizationName(String displayOrganizationName) {
        this.displayOrganizationName = displayOrganizationName;
    }

    /**
     * @return the documentTypeName
     */
    public String getDocumentTypeName() {
        if (getSelectedCurrentDocument().getDocumentType() != null) {
            return getDocumentTypeNameFromTheStaticList(getSelectedCurrentDocument().getDocumentType());
        }
        return null;
    }

    public String getDocumentTypeNameFromTheStaticList(String documentType) {
        for (SelectItem localDocumentTypeList : documentTypeList) {
            if (localDocumentTypeList.getValue().equals(documentType)) {
                return localDocumentTypeList.getLabel();
            }
        }
        return "Unknown Document";
    }

    /**
     * @return the documentInfoModalWindowHeader
     */
    public String getDocumentInfoModalWindowHeader() {
        String modal = getDocumentTypeName() + " for " + getSelectedCurrentPatient().getName();
        return modal;
    }

    public StreamedContent getFile() {
        return new ByteArrayContent(getSelectedCurrentDocument().getDocumentContent(), getSelectedCurrentDocument().getContentType(), 
                getDocumentName(getSelectedCurrentDocument().getContentType()));
    }
    
    private String getDocumentName(String contentType) {
        StringBuilder sBuilder = new StringBuilder();
        sBuilder.append(getPatientList().get(0).getFirstName().toLowerCase());
        sBuilder.append("_");
        sBuilder.append(getPatientList().get(0).getLastName().toLowerCase());
        sBuilder.append(new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
        sBuilder.append(".");
        
        if(contentType.equals(GatewayService.CONTENT_TYPE_APPLICATION_PDF)) {
            sBuilder.append("pdf");
        } else if (contentType.equals(GatewayService.CONTENT_TYPE_APPLICATION_XML) ||
                contentType.equals(GatewayService.CONTENT_TYPE_TEXT_XML) ||
                contentType.equals(GatewayService.CONTENT_TYPE_APPLICATION_OCTET_STREAM)) {
            sBuilder.append("xml");
        } else {
            sBuilder.append("txt");
        }
        
        return sBuilder.toString();
    }
    
    private String parseDrugName(String drugName) {
        String arr[] = drugName.split(" ");
        if (arr != null && arr.length > 0) {
            return arr[0];
        }
        return null;
    }
}
