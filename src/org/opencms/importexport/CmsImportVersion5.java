/*
 * File   : $Source: /alkacon/cvs/opencms/src/org/opencms/importexport/CmsImportVersion5.java,v $
 * Date   : $Date: 2006/11/27 16:02:34 $
 * Version: $Revision: 1.1.2.3 $
 *
 * This library is part of OpenCms -
 * the Open Source Content Mananagement System
 *
 * Copyright (c) 2005 Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software GmbH, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.importexport;

import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsResource;
import org.opencms.file.types.CmsResourceTypePlain;
import org.opencms.file.types.I_CmsResourceType;
import org.opencms.i18n.CmsMessageContainer;
import org.opencms.loader.CmsLoaderException;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.relations.I_CmsLinkParseable;
import org.opencms.report.I_CmsReport;
import org.opencms.security.CmsRole;
import org.opencms.security.I_CmsPasswordHandler;
import org.opencms.security.I_CmsPrincipal;
import org.opencms.util.CmsDateUtil;
import org.opencms.util.CmsUUID;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import org.apache.commons.logging.Log;

import org.dom4j.Document;
import org.dom4j.Element;

/**
 * Implementation of the OpenCms Import Interface ({@link org.opencms.importexport.I_CmsImport}) for 
 * the import version 5.<p>
 * 
 * This import format is used in OpenCms since 7 dev branch.<p>
 *
 * @author Michael Moossen 
 * 
 * @version $Revision: 1.1.2.3 $ 
 * 
 * @since 6.3.0 
 * 
 * @see org.opencms.importexport.A_CmsImport
 */
public class CmsImportVersion5 extends A_CmsImport {

    /** The version number of this import implementation.<p> */
    public static final int IMPORT_VERSION = 5;

    /** The log object for this class. */
    private static final Log LOG = CmsLog.getLog(CmsImportVersion5.class);

    /** Stores all resource of type that implements the {@link I_CmsLinkParseable} interface. */
    private List m_parseables;

    /**
     * Creates a new CmsImportVerion7 object.<p>
     */
    public CmsImportVersion5() {

        m_convertToXmlPage = true;
    }

    /**
     * @see org.opencms.importexport.I_CmsImport#getVersion()
     */
    public int getVersion() {

        return CmsImportVersion5.IMPORT_VERSION;
    }

    /**
     * @see org.opencms.importexport.I_CmsImport#importResources(org.opencms.file.CmsObject, java.lang.String, org.opencms.report.I_CmsReport, java.io.File, java.util.zip.ZipFile, org.dom4j.Document)
     */
    public void importResources(
        CmsObject cms,
        String importPath,
        I_CmsReport report,
        File importResource,
        ZipFile importZip,
        Document docXml) throws CmsImportExportException {

        // initialize the import       
        initialize();
        m_cms = cms;
        m_importPath = importPath;
        m_report = report;
        m_importResource = importResource;
        m_importZip = importZip;
        m_docXml = docXml;
        m_importingChannelData = false;
        m_linkStorage = new HashMap();
        m_linkPropertyStorage = new HashMap();
        m_parseables = new ArrayList();

        try {
            // first import the user information
            if (cms.hasRole(CmsRole.ACCOUNT_MANAGER)) {
                importGroups();
                importUsers();
            }
            // now import the VFS resources
            readResourcesFromManifest();
            convertPointerToSiblings();
            rewriteParseables();
        } finally {
            cleanUp();
        }
    }

    /**
     * Imports a single user.<p>
     * @param name user name
     * @param description user description
     * @param flags user flags
     * @param password user password 
     * @param firstname firstname of the user
     * @param lastname lastname of the user
     * @param email user email
     * @param address user address 
     * @param type user type
     * @param userInfo user info
     * @param userGroups user groups
     * 
     * @throws CmsImportExportException in case something goes wrong
     */
    protected void importUser(
        String name,
        String description,
        String flags,
        String password,
        String firstname,
        String lastname,
        String email,
        String address,
        String type,
        Map userInfo,
        List userGroups) throws CmsImportExportException {

        boolean convert = false;

        Map config = OpenCms.getPasswordHandler().getConfiguration();
        if ((config != null) && config.containsKey(I_CmsPasswordHandler.CONVERT_DIGEST_ENCODING)) {
            convert = Boolean.valueOf((String)config.get(I_CmsPasswordHandler.CONVERT_DIGEST_ENCODING)).booleanValue();
        }

        if (convert) {
            password = convertDigestEncoding(password);
        }

        super.importUser(
            name,
            description,
            flags,
            password,
            firstname,
            lastname,
            email,
            address,
            type,
            userInfo,
            userGroups);
    }

    /**
     * Convert a given timestamp from a String format to a long value.<p>
     * 
     * The timestamp is either the string representation of a long value (old export format)
     * or a user-readable string format.
     * 
     * @param timestamp timestamp to convert
     * @return long value of the timestamp
     */
    private long convertTimestamp(String timestamp) {

        long value = 0;
        // try to parse the timestamp string
        // if it successes, its an old style long value
        try {
            value = Long.parseLong(timestamp);

        } catch (NumberFormatException e) {
            // the timestamp was in in a user-readable string format, create the long value form it
            try {
                value = CmsDateUtil.parseHeaderDate(timestamp);
            } catch (ParseException pe) {
                value = System.currentTimeMillis();
            }
        }
        return value;
    }

    /**
     * Imports a resource (file or folder) into the cms.<p>
     * 
     * @param source the path to the source-file
     * @param destination the path to the destination-file in the cms
     * @param type the resource type name of the file
     * @param uuidstructure the structure uuid of the resource
     * @param uuidresource the resource uuid of the resource
     * @param datelastmodified the last modification date of the resource
     * @param userlastmodified the user who made the last modifications to the resource
     * @param datecreated the creation date of the resource
     * @param usercreated the user who created 
     * @param datereleased the release date of the resource
     * @param dateexpired the expire date of the resource
     * @param flags the flags of the resource     
     * @param properties a list with properties for this resource
     * 
     * @return imported resource
     */
    private CmsResource importResource(
        String source,
        String destination,
        I_CmsResourceType type,
        String uuidstructure,
        String uuidresource,
        long datelastmodified,
        String userlastmodified,
        long datecreated,
        String usercreated,
        long datereleased,
        long dateexpired,
        String flags,
        List properties) {

        byte[] content = null;
        CmsResource result = null;

        try {

            // get the file content
            if (source != null) {
                content = getFileBytes(source);
            }
            int size = 0;
            if (content != null) {
                size = content.length;
            }

            // get UUIDs for the user   
            CmsUUID newUserlastmodified;
            CmsUUID newUsercreated;
            // check if user created and user lastmodified are valid users in this system.
            // if not, use the current user
            try {
                newUserlastmodified = m_cms.readUser(userlastmodified).getId();
            } catch (CmsException e) {
                newUserlastmodified = m_cms.getRequestContext().currentUser().getId();
                // datelastmodified = System.currentTimeMillis();
            }

            try {
                newUsercreated = m_cms.readUser(usercreated).getId();
            } catch (CmsException e) {
                newUsercreated = m_cms.getRequestContext().currentUser().getId();
                // datecreated = System.currentTimeMillis();
            }

            // get UUID for the structure
            CmsUUID newUuidstructure = null;
            if (uuidstructure != null) {
                // create a UUID from the provided string
                newUuidstructure = new CmsUUID(uuidstructure);
            } else {
                // if null generate a new structure id
                newUuidstructure = new CmsUUID();
            }

            // get UUIDs for the resource and content        
            CmsUUID newUuidresource = null;
            if ((uuidresource != null) && (!type.isFolder())) {
                // create a UUID from the provided string
                newUuidresource = new CmsUUID(uuidresource);
            } else {
                // folders get always a new resource record UUID
                newUuidresource = new CmsUUID();
            }

            // create a new CmsResource                         
            CmsResource resource = new CmsResource(
                newUuidstructure,
                newUuidresource,
                destination,
                type.getTypeId(),
                type.isFolder(),
                new Integer(flags).intValue(),
                m_cms.getRequestContext().currentProject().getId(),
                CmsResource.STATE_NEW,
                datecreated,
                newUsercreated,
                datelastmodified,
                newUserlastmodified,
                datereleased,
                dateexpired,
                1,
                size);

            // import this resource in the VFS
            result = m_cms.importResource(destination, resource, content, properties);

            if (result != null) {
                m_report.println(
                    org.opencms.report.Messages.get().container(org.opencms.report.Messages.RPT_OK_0),
                    I_CmsReport.FORMAT_OK);
            }
        } catch (Exception exc) {
            // an error while importing the file
            m_report.println(exc);
            try {
                // Sleep some time after an error so that the report output has a chance to keep up
                Thread.sleep(1000);
            } catch (Exception e) {
                // 
            }
        }
        return result;
    }

    /**
     * Reads all file nodes plus their meta-information (properties, ACL) 
     * from the <code>manifest.xml</code> and imports them as Cms resources to the VFS.<p>
     * 
     * @throws CmsImportExportException if something goes wrong
     */
    private void readResourcesFromManifest() throws CmsImportExportException {

        String source = null, destination = null, uuidstructure = null, uuidresource = null, userlastmodified = null, usercreated = null, flags = null, timestamp = null;
        long datelastmodified = 0, datecreated = 0, datereleased = 0, dateexpired = 0;

        List fileNodes = null, acentryNodes = null;
        Element currentElement = null, currentEntry = null;
        List properties = null;
        String storedSiteRoot = null;

        if (m_importingChannelData) {
            storedSiteRoot = m_cms.getRequestContext().getSiteRoot();
            m_cms.getRequestContext().setSiteRoot(CmsResource.VFS_FOLDER_CHANNELS);
        }

        // get list of immutable resources
        List immutableResources = OpenCms.getImportExportManager().getImmutableResources();
        if (immutableResources == null) {
            immutableResources = Collections.EMPTY_LIST;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(Messages.get().getBundle().key(
                Messages.LOG_IMPORTEXPORT_IMMUTABLE_RESOURCES_SIZE_1,
                Integer.toString(immutableResources.size())));
        }
        // get list of ignored properties
        List ignoredProperties = OpenCms.getImportExportManager().getIgnoredProperties();
        if (ignoredProperties == null) {
            ignoredProperties = Collections.EMPTY_LIST;
        }

        // get the desired page type for imported pages
        m_convertToXmlPage = OpenCms.getImportExportManager().convertToXmlPage();

        try {
            // get all file-nodes
            fileNodes = m_docXml.selectNodes("//" + CmsImportExportManager.N_FILE);
            int importSize = fileNodes.size();

            // walk through all files in manifest
            for (int i = 0; i < fileNodes.size(); i++) {
                m_report.print(org.opencms.report.Messages.get().container(
                    org.opencms.report.Messages.RPT_SUCCESSION_2,
                    String.valueOf(i + 1),
                    String.valueOf(importSize)), I_CmsReport.FORMAT_NOTE);
                currentElement = (Element)fileNodes.get(i);

                // <source>
                source = CmsImport.getChildElementTextValue(currentElement, CmsImportExportManager.N_SOURCE);
                // <destination>

                destination = CmsImport.getChildElementTextValue(currentElement, CmsImportExportManager.N_DESTINATION);

                // <type>
                String typeName = CmsImport.getChildElementTextValue(currentElement, CmsImportExportManager.N_TYPE);
                I_CmsResourceType type;
                try {
                    type = OpenCms.getResourceManager().getResourceType(typeName);
                } catch (CmsLoaderException e) {
                    type = OpenCms.getResourceManager().getResourceType(CmsResourceTypePlain.getStaticTypeId());
                }

                // <uuidstructure>
                uuidstructure = CmsImport.getChildElementTextValue(
                    currentElement,
                    CmsImportExportManager.N_UUIDSTRUCTURE);

                // <uuidresource>
                if (!type.isFolder()) {
                    uuidresource = CmsImport.getChildElementTextValue(
                        currentElement,
                        CmsImportExportManager.N_UUIDRESOURCE);
                } else {
                    uuidresource = null;
                }

                // <datelastmodified>
                timestamp = CmsImport.getChildElementTextValue(
                    currentElement,
                    CmsImportExportManager.N_DATELASTMODIFIED);
                if (timestamp != null) {
                    datelastmodified = convertTimestamp(timestamp);
                } else {
                    datelastmodified = System.currentTimeMillis();
                }

                // <userlastmodified>
                userlastmodified = CmsImport.getChildElementTextValue(
                    currentElement,
                    CmsImportExportManager.N_USERLASTMODIFIED);
                userlastmodified = OpenCms.getImportExportManager().translateUser(userlastmodified);

                // <datecreated>
                timestamp = CmsImport.getChildElementTextValue(currentElement, CmsImportExportManager.N_DATECREATED);
                if (timestamp != null) {
                    datecreated = convertTimestamp(timestamp);
                } else {
                    datecreated = System.currentTimeMillis();
                }

                // <usercreated>
                usercreated = CmsImport.getChildElementTextValue(currentElement, CmsImportExportManager.N_USERCREATED);
                usercreated = OpenCms.getImportExportManager().translateUser(usercreated);

                // <datereleased>
                timestamp = CmsImport.getChildElementTextValue(currentElement, CmsImportExportManager.N_DATERELEASED);
                if (timestamp != null) {
                    datereleased = convertTimestamp(timestamp);
                } else {
                    datereleased = CmsResource.DATE_RELEASED_DEFAULT;
                }

                // <dateexpired>
                timestamp = CmsImport.getChildElementTextValue(currentElement, CmsImportExportManager.N_DATEEXPIRED);
                if (timestamp != null) {
                    dateexpired = convertTimestamp(timestamp);
                } else {
                    dateexpired = CmsResource.DATE_EXPIRED_DEFAULT;
                }

                // <flags>              
                flags = CmsImport.getChildElementTextValue(currentElement, CmsImportExportManager.N_FLAGS);

                // apply name translation and import path         
                String translatedName = m_cms.getRequestContext().addSiteRoot(m_importPath + destination);
                if (type.isFolder()) {
                    // ensure folders end with a "/"
                    if (!CmsResource.isFolder(translatedName)) {
                        translatedName += "/";
                    }
                }

                // check if this resource is immutable
                boolean resourceNotImmutable = checkImmutable(translatedName, immutableResources);
                translatedName = m_cms.getRequestContext().removeSiteRoot(translatedName);
                // if the resource is not immutable and not on the exclude list, import it
                if (resourceNotImmutable) {
                    // print out the information to the report
                    m_report.print(Messages.get().container(Messages.RPT_IMPORTING_0), I_CmsReport.FORMAT_NOTE);
                    m_report.print(org.opencms.report.Messages.get().container(
                        org.opencms.report.Messages.RPT_ARGUMENT_1,
                        translatedName));
                    m_report.print(org.opencms.report.Messages.get().container(org.opencms.report.Messages.RPT_DOTS_0));
                    // get all properties
                    properties = readPropertiesFromManifest(currentElement, ignoredProperties);

                    // import the resource               
                    CmsResource res = importResource(
                        source,
                        translatedName,
                        type,
                        uuidstructure,
                        uuidresource,
                        datelastmodified,
                        userlastmodified,
                        datecreated,
                        usercreated,
                        datereleased,
                        dateexpired,
                        flags,
                        properties);

                    // if the resource was imported add the access control entrys if available
                    if (res != null) {

                        List aceList = new ArrayList();

                        // write all imported access control entries for this file
                        acentryNodes = currentElement.selectNodes("*/" + CmsImportExportManager.N_ACCESSCONTROL_ENTRY);

                        // collect all access control entries
                        for (int j = 0; j < acentryNodes.size(); j++) {
                            currentEntry = (Element)acentryNodes.get(j);

                            // get the data of the access control entry
                            String id = CmsImport.getChildElementTextValue(
                                currentEntry,
                                CmsImportExportManager.N_ACCESSCONTROL_PRINCIPAL);
                            String principalId = new CmsUUID().toString();
                            String principal = id.substring(id.indexOf('.') + 1, id.length());

                            try {
                                if (id.startsWith(I_CmsPrincipal.PRINCIPAL_GROUP)) {
                                    principal = OpenCms.getImportExportManager().translateGroup(principal);
                                    principalId = m_cms.readGroup(principal).getId().toString();
                                } else {
                                    principal = OpenCms.getImportExportManager().translateUser(principal);
                                    principalId = m_cms.readUser(principal).getId().toString();
                                }

                                String acflags = CmsImport.getChildElementTextValue(
                                    currentEntry,
                                    CmsImportExportManager.N_FLAGS);

                                String allowed = ((Element)currentEntry.selectNodes(
                                    "./"
                                        + CmsImportExportManager.N_ACCESSCONTROL_PERMISSIONSET
                                        + "/"
                                        + CmsImportExportManager.N_ACCESSCONTROL_ALLOWEDPERMISSIONS).get(0)).getTextTrim();

                                String denied = ((Element)currentEntry.selectNodes(
                                    "./"
                                        + CmsImportExportManager.N_ACCESSCONTROL_PERMISSIONSET
                                        + "/"
                                        + CmsImportExportManager.N_ACCESSCONTROL_DENIEDPERMISSIONS).get(0)).getTextTrim();

                                // add the entry to the list
                                aceList.add(getImportAccessControlEntry(res, principalId, allowed, denied, acflags));
                            } catch (CmsException e) {
                                // user or group of ACE might not exist in target system, ignore ACE
                                if (LOG.isWarnEnabled()) {
                                    LOG.warn(Messages.get().getBundle().key(
                                        Messages.LOG_IMPORTEXPORT_ERROR_IMPORTING_ACE_1,
                                        translatedName), e);
                                }
                                m_report.println(e);
                            }
                        }

                        importAccessControlEntries(res, aceList);
                        if (OpenCms.getResourceManager().getResourceType(res.getTypeId()) instanceof I_CmsLinkParseable) {
                            // store for later use
                            m_parseables.add(res);
                        }

                        if (LOG.isInfoEnabled()) {
                            LOG.info(Messages.get().getBundle().key(
                                Messages.LOG_IMPORTING_4,
                                new Object[] {
                                    String.valueOf(i + 1),
                                    String.valueOf(importSize),
                                    translatedName,
                                    destination}));
                        }
                    } else {
                        // resource import failed, since no CmsResource was created
                        m_report.print(Messages.get().container(Messages.RPT_SKIPPING_0), I_CmsReport.FORMAT_NOTE);
                        m_report.println(org.opencms.report.Messages.get().container(
                            org.opencms.report.Messages.RPT_ARGUMENT_1,
                            translatedName));

                        if (LOG.isInfoEnabled()) {
                            LOG.info(Messages.get().getBundle().key(
                                Messages.LOG_SKIPPING_3,
                                String.valueOf(i + 1),
                                String.valueOf(importSize),
                                translatedName));
                        }
                    }

                } else {
                    // skip the file import, just print out the information to the report

                    m_report.print(Messages.get().container(Messages.RPT_SKIPPING_0), I_CmsReport.FORMAT_NOTE);
                    m_report.println(org.opencms.report.Messages.get().container(
                        org.opencms.report.Messages.RPT_ARGUMENT_1,
                        translatedName));

                    if (LOG.isInfoEnabled()) {
                        LOG.info(Messages.get().getBundle().key(
                            Messages.LOG_SKIPPING_3,
                            String.valueOf(i + 1),
                            String.valueOf(importSize),
                            translatedName));
                    }
                }
            }
        } catch (Exception e) {

            m_report.println(e);

            CmsMessageContainer message = Messages.get().container(
                Messages.ERR_IMPORTEXPORT_ERROR_IMPORTING_RESOURCES_0);
            if (LOG.isDebugEnabled()) {
                LOG.debug(message.key(), e);
            }

            throw new CmsImportExportException(message, e);
        } finally {
            if (storedSiteRoot != null) {
                m_cms.getRequestContext().setSiteRoot(storedSiteRoot);
            }
        }
    }

    /**
     * Rewrites all parseable files, to assure link check.<p>
     */
    private void rewriteParseables() {

        Iterator it = m_parseables.iterator();
        while (it.hasNext()) {
            CmsResource res = (CmsResource)it.next();
            try {
                m_cms.writeFile(CmsFile.upgrade(res, m_cms));
            } catch (CmsException e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn(Messages.get().getBundle().key(Messages.LOG_IMPORTEXPORT_REWRITING_1, res.getRootPath()));
                }
            }
        }

    }
}