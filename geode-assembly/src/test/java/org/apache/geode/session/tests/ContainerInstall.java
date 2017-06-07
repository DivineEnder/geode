/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.session.tests;

import org.apache.commons.io.FilenameUtils;
import org.apache.geode.management.internal.configuration.utils.ZipUtils;
import org.codehaus.cargo.container.configuration.LocalConfiguration;
import org.codehaus.cargo.container.deployable.WAR;
import org.codehaus.cargo.container.installer.Installer;
import org.codehaus.cargo.container.installer.ZipURLInstaller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public abstract class ContainerInstall {
  private final String INSTALL_PATH;
  public static final String DEFAULT_INSTALL_DIR = "/tmp/cargo_containers/";
  public static final String GEODE_BUILD_HOME = System.getenv("GEODE_HOME");

  public ContainerInstall(String installDir, String downloadURL) throws MalformedURLException {
    System.out.println("Installing container from URL " + downloadURL);
    // Optional step to install the container from a URL pointing to its distribution
    Installer installer = new ZipURLInstaller(new URL(downloadURL), "/tmp/downloads", installDir);
    installer.install();
    INSTALL_PATH = installer.getHome();
    System.out.println("Installed container into " + getInstallPath());
  }

  public String getInstallPath() {
    return INSTALL_PATH;
  }

  public abstract String getContainerId();

  public abstract String getContainerDescription();

  public abstract WAR getDeployableWAR();

  public abstract void setLocator(String address, int port) throws Exception;

  /**
   * Update the configuration of a container before it is launched, if necessary.
   */
  public void modifyConfiguration(LocalConfiguration configuration) {

  }

  protected String findSessionTestingWar() {
    // Start out searching directory above current
    String curPath = "../";

    // Looking for extensions folder
    final String warModuleDirName = "extensions";
    File warModuleDir = null;

    // While directory searching for is not found
    while (warModuleDir == null) {
      // Try to find the find the directory in the current directory
      File[] files = new File(curPath).listFiles();
      for (File file : files) {
        if (file.isDirectory() && file.getName().equals(warModuleDirName)) {
          warModuleDir = file;
          break;
        }
      }

      // Keep moving up until you find it
      curPath += "../";
    }

    // Return path to extensions plus hardcoded path from there to the WAR
    return warModuleDir.getAbsolutePath()
        + "/session-testing-war/build/libs/session-testing-war.war";
  }

  protected static String findAndExtractModule(String geodeBuildHome, String moduleName)
      throws IOException {
    String modulePath = null;
    String modulesDir = geodeBuildHome + "/tools/Modules/";

    boolean archive = false;
    System.out.println("Trying to access build dir " + modulesDir);
    // Search directory for tomcat module folder/zip
    for (File file : (new File(modulesDir)).listFiles()) {

      if (file.getName().toLowerCase().contains(moduleName)) {
        modulePath = file.getAbsolutePath();

        archive = !file.isDirectory();
        if (!archive)
          break;
      }
    }

    // Unzip if it is a zip file
    if (archive) {
      if (!FilenameUtils.getExtension(modulePath).equals("zip"))
        throw new IOException("Bad module archive " + modulePath);

      ZipUtils.unzip(modulePath, modulePath.substring(0, modulePath.length() - 4));
      System.out.println(
          "Unzipped " + modulePath + " into " + modulePath.substring(0, modulePath.length() - 4));

      modulePath = modulePath.substring(0, modulePath.length() - 4);
    }

    // No module found within directory throw IOException
    if (modulePath == null)
      throw new IOException("No module found in " + modulesDir);
    return modulePath;
  }

  protected void editXMLFile(String XMLPath, String tagId, String tagName, String parentTagName,
      HashMap<String, String> attributes) throws Exception {
    editXMLFile(XMLPath, tagId, tagName, parentTagName, attributes, false);
  }

  protected void editXMLFile(String XMLPath, String tagName, String parentTagName,
      HashMap<String, String> attributes) throws Exception {
    editXMLFile(XMLPath, null, tagName, parentTagName, attributes, false);
  }

  protected void editXMLFile(String XMLPath, String tagName, String parentTagName,
      HashMap<String, String> attributes, boolean writeOnSimilarAttributeNames) throws Exception {
    editXMLFile(XMLPath, null, tagName, parentTagName, attributes, writeOnSimilarAttributeNames);
  }

  protected void editXMLFile(String XMLPath, String tagId, String tagName, String parentTagName,
      HashMap<String, String> attributes, boolean writeOnSimilarAttributeNames) throws Exception {
    // Get XML file to edit
    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
    Document doc = docBuilder.parse(XMLPath);

    boolean hasTag = false;
    NodeList nodes = doc.getElementsByTagName(tagName);

    // If tags with name were found search to find tag with proper tagId and update its fields
    if (nodes != null) {
      for (int i = 0; i < nodes.getLength(); i++) {
        Node node = nodes.item(i);
        if (tagId != null) {
          Node idAttr = node.getAttributes().getNamedItem("id");
          // Check node for id attribute
          if (idAttr != null && idAttr.getTextContent().equals(tagId)) {
            NamedNodeMap nodeAttrs = node.getAttributes();

            // Remove previous attributes
            while (nodeAttrs.getLength() > 0) {
              nodeAttrs.removeNamedItem(nodeAttrs.item(0).getNodeName());
            }

            ((Element) node).setAttribute("id", tagId);
            // Set to new attributes
            for (String key : attributes.keySet()) {
              ((Element) node).setAttribute(key, attributes.get(key));
//              node.getAttributes().getNamedItem(key).setTextContent(attributes.get(key));
            }

            hasTag = true;
            break;
          }
        } else if (writeOnSimilarAttributeNames) {
          NamedNodeMap nodeAttrs = node.getAttributes();
          boolean updateNode = true;

          // Check to make sure has all attribute fields
          for (String key : attributes.keySet()) {
            if (nodeAttrs.getNamedItem(key) == null) {
              updateNode = false;
              break;
            }
          }
//          // Check to make sure does not have more than attribute fields
//          for (int j = 0; j < nodeAttrs.getLength(); j++)
//          {
//            if (attributes.get(nodeAttrs.item(j)) == null)
//            {
//              updateNode = false;
//              break;
//            }
//          }

          // Update node attributes
          if (updateNode) {
            for (String key : attributes.keySet())
              node.getAttributes().getNamedItem(key).setTextContent(attributes.get(key));

            hasTag = true;
            break;
          }
        }

      }
    }

    if (!hasTag) {
      Element e = doc.createElement(tagName);
      // Set id attribute
      if (tagId != null)
        e.setAttribute("id", tagId);
      // Set other attributes
      for (String key : attributes.keySet())
        e.setAttribute(key, attributes.get(key));

      // WordUtils.capitalize(FilenameUtils.getBaseName(XMLPath))
      // Add it as a child of the tag for the file
      doc.getElementsByTagName(parentTagName).item(0).appendChild(e);
    }

    // Write updated XML file
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();
    DOMSource source = new DOMSource(doc);
    StreamResult result = new StreamResult(new File(XMLPath));
    transformer.transform(source, result);

    System.out.println("Modified container XML file " + XMLPath);
  }
}
