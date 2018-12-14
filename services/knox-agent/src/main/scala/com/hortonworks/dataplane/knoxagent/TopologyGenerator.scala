/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.hortonworks.dataplane.knoxagent

import java.io.StringWriter

import javax.xml.parsers.{DocumentBuilder, DocumentBuilderFactory}
import javax.xml.transform.{OutputKeys, TransformerFactory}
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.{XPath, XPathConstants, XPathFactory}
import org.w3c.dom.{Document, Element, Node, NodeList}

import scala.collection.mutable
import scala.util.Try

object TopologyGenerator {


  val defaultKnoxTokenTTLMinutes = 120;

  val xPath: XPath = XPathFactory.newInstance().newXPath()
  private def findByRoleName(nodeList: NodeList,
                             roleName: String): Option[Node] = {
    var i = 0
    while (i < nodeList.getLength) {
      val node = nodeList.item(i)
      val roleNode = xPath.evaluate("role", node)
      if (roleNode.equals(roleName)) {
        return Some(node)
      }
      i = i + 1
    }
    None
  }
  private def createParamElem(doc: Document,
                              name: String,
                              value: String): Node = {
    val paramElem = doc.createElement("param")
    val nameElem = doc.createElement("name")
    nameElem.appendChild(doc.createTextNode(name))
    paramElem.appendChild(nameElem)
    val valueElem = doc.createElement("value")
    valueElem.appendChild(doc.createTextNode(value))
    paramElem.appendChild(valueElem)
    paramElem
  }

  def configure(config: KnoxConfig) = {

    val doc: Document = getSsoTemplateDoc

    val authParams = getAuthenticationParams(doc)

    replaceParamValue(authParams.get("main.ldapRealm.userDnTemplate").get, config.userDnTemplate.get)
    replaceParamValue(authParams.get("main.ldapRealm.contextFactory.url").get, config.ldapUrl.get)

    val gatewayProviders: NodeList = xPath
      .evaluate("/topology/gateway/provider",
                doc.getDocumentElement,
                XPathConstants.NODESET)
      .asInstanceOf[NodeList]
    val authenticationNode = findByRoleName(gatewayProviders, "authentication")
    if (config.userSearchBase.isDefined) {
      authenticationNode.get.appendChild(createParamElem(doc, "main.ldapRealm.userSearchBase", config.userSearchBase.get))
      authenticationNode.get.appendChild(createParamElem(doc, "main.ldapRealm.userSearchAttributeName", config.userSearchAttributeName.get))
      authenticationNode.get.appendChild(createParamElem(doc, "main.ldapRealm.userObjectClass", config.userObjectClass.get))
      if(config.useAnonymous.getOrElse(false) == false) {
        authenticationNode.get.appendChild(createParamElem(doc, "main.ldapRealm.contextFactory.systemUsername", config.bindDn.get))
        authenticationNode.get.appendChild(createParamElem(doc, "main.ldapRealm.contextFactory.systemPassword", "${ALIAS=ldcSystemPassword}"))
        authenticationNode.get.appendChild(createParamElem(doc, "main.ldapRealm.contextFactory.authenticationMechanism", "simple"))
      } else {
        authenticationNode.get.appendChild(createParamElem(doc, "main.ldapRealm.contextFactory.authenticationMechanism", "none"))
      }
      authenticationNode.get.appendChild(createParamElem(doc, "main.ldapRealm.contextFactory.referral", config.referral.getOrElse("ignore")))
    }

    val ssoServiceParams = getKnoxSsoServiceParams(doc)
    var ttlMilliSecs = config.signedTokenTtl match {
      case Some(ttlInMinutes) => ttlInMinutes * 60000
      case None               => defaultKnoxTokenTTLMinutes * 60000
    }
    replaceParamValue(ssoServiceParams("knoxsso.token.ttl"),
                      ttlMilliSecs.toString)

    val httpsOnly = Try(agentConfig.getBoolean("sso.cookie.secure.only")).getOrElse(true)
    replaceParamValue(ssoServiceParams("knoxsso.cookie.secure.only"), httpsOnly.toString)

    val whiteListDomains = config.domains match {
      case Some(domains) =>
        if (domains.size > 0) {
          domains.mkString("|").trim + "|"
        } else {
          ""
        }
      case None => ""
    }
    val whitelistRegex =
      if (Try(agentConfig.getBoolean("sso.whitelist.strict")).getOrElse(true))
        "^https?:\\/\\/(" + whiteListDomains + "localhost|127.0.0.1|0:0:0:0:0:0:0:1|::1)(:[0-9])*.*$"
      else
        "^https?:\\/\\/*.*$"
    replaceParamValue(
      ssoServiceParams("knoxsso.redirect.whitelist.regex"),
      whitelistRegex)
    val transformerFactory = TransformerFactory.newInstance
    val transformer = transformerFactory.newTransformer
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount",
                                  "4")
    val stringWriter: StringWriter = new StringWriter()
    transformer.transform(new DOMSource(doc), new StreamResult(stringWriter))
    stringWriter.toString
  }

  private def getKnoxSsoServiceParams(doc: Document) = {
    val services: NodeList = xPath
      .evaluate("/topology/service",
                doc.getDocumentElement(),
                XPathConstants.NODESET)
      .asInstanceOf[NodeList]
    val knoxSsoNode = findByRoleName(services, "KNOXSSO")
    val knoxSsoParamVals = getParamNodeMap(
      knoxSsoNode.get.asInstanceOf[Element])
    knoxSsoParamVals
  }

  private def getAuthenticationParams(doc: Document) = {
    val gatewayProviders: NodeList = xPath
      .evaluate("/topology/gateway/provider",
                doc.getDocumentElement(),
                XPathConstants.NODESET)
      .asInstanceOf[NodeList]
    val authenticationNode = findByRoleName(gatewayProviders, "authentication")
    val authParamVals = getParamNodeMap(
      authenticationNode.get.asInstanceOf[Element])
    authParamVals
  }

  private def getSsoTemplateDoc = {
    val ssoTopologyTemplateStream =
      getClass.getResourceAsStream("/knoxssotopology_template.xml")
    val docFactory: DocumentBuilderFactory =
      DocumentBuilderFactory.newInstance()
    val docBuilder: DocumentBuilder = docFactory.newDocumentBuilder()
    val doc: Document = docBuilder.parse(ssoTopologyTemplateStream)
    doc
  }

  private def replaceParamValue(node: Node, paramValue: String) {

    val paramNodeChildren = node.getChildNodes
    var i = 0
    while (i < paramNodeChildren.getLength) {
      {
        val item = paramNodeChildren.item(i)
        if (item.getNodeName.equals("value")) item.setTextContent(paramValue)
      }
      {
        i += 1; i - 1
      }
    }
  }
  private def getParamNodeMap(knoxSsoConfig: Element) = {
    val paramNodes = knoxSsoConfig.getElementsByTagName("param")
    val paramNameToNodesMap = new mutable.HashMap[String, Node]()

    var i = 0
    while (i < paramNodes.getLength) {
      {
        val paramNode = paramNodes.item(i)
        val paramProperties = paramNode.getChildNodes
        var j = 0
        while (j < paramProperties.getLength) {
          {
            val paramProperty = paramProperties.item(j)
            if (paramProperty.getNodeName.equals("name"))
              paramNameToNodesMap.put(paramProperty.getFirstChild.getNodeValue,
                                      paramNode)
          }
          {
            j += 1; j - 1
          }
        }
      }
      {
        i += 1; i - 1
      }
    }
    paramNameToNodesMap
  }

}
