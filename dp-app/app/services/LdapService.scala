/*
 *   HORTONWORKS DATAPLANE SERVICE AND ITS CONSTITUENT SERVICES
 *
 *   (c) 2016-2018 Hortonworks, Inc. All rights reserved.
 *
 *   This code is provided to you pursuant to your written agreement with Hortonworks, which may be the terms of the
 *   Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
 *   to distribute this code.  If you do not have a written agreement with Hortonworks or with an authorized and
 *   properly licensed third party, you do not have any rights to this code.
 *
 *   If this code is provided to you under the terms of the AGPLv3:
 *   (A) HORTONWORKS PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
 *   (B) HORTONWORKS DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
 *     LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
 *   (C) HORTONWORKS IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
 *     FROM OR RELATED TO THE CODE; AND
 *   (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, HORTONWORKS IS NOT LIABLE FOR ANY
 *     DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
 *     DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
 *     OR LOSS OR CORRUPTION OF DATA.
 */

package services
import java.util
import javax.inject.Singleton
import javax.naming.directory.{DirContext, SearchControls, SearchResult}
import javax.naming._
import javax.naming.ldap.InitialLdapContext
import javax.net.ssl.SSLException

import com.google.inject.Inject
import com.google.inject.name.Named
import play.api.Configuration

import scala.concurrent.ExecutionContext.Implicits.global
import com.hortonworks.dataplane.commons.domain.Entities.{Error, Errors, LdapConfiguration, WrappedErrorException}
import com.hortonworks.dataplane.commons.domain.Ldap.{LdapGroup, LdapSearchResult, LdapUser}
import com.hortonworks.dataplane.db.Webservice.{ConfigService, LdapConfigService}
import com.typesafe.scalalogging.Logger
import com.typesafe.sslconfig.ssl.CompositeCertificateException
import internal.GeneratedSocketFactory
import models.{CredentialEntry, KnoxConfigInfo, KnoxConfigUpdateInfo}
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future, Promise}
import scala.util.Left

@Singleton
class LdapService @Inject()(
    @Named("ldapConfigService") val ldapConfigService: LdapConfigService,
    @Named("configService") val configService: ConfigService,
    private val sslContextManager: SslContextManager,
    private val ldapKeyStore: DpKeyStore,
    private val configuration: Configuration) {
  private val logger = Logger(classOf[LdapService])

  private val LDAP_CREDENTIAL_KEY = "DPSPlatform.credential.ldap"

  def configure(knoxConf: KnoxConfigInfo,
                requestHost: String): Future[Either[Errors, Boolean]] = {
    if (knoxConf.bindDn.isEmpty || knoxConf.password.isEmpty) {
      Future.successful(
        Left(Errors(Seq(Error(400, "username and password mandatory")))))
    } else
      validate(knoxConf) match {
        case Left(errors) => Future.successful(Left(errors))
        case Right(isValid) =>
          if (!isValid) {
            Future.successful(
              Left(Errors(Seq(Error(400, "invalid knox configuration")))))
          } else
            validateBindDn(knoxConf.ldapUrl, knoxConf.bindDn, knoxConf.password)
              .flatMap {
                case Left(errors) => Future.successful(Left(errors))
                case Right(isBound) =>
                  val tryToWrite = ldapKeyStore.createCredentialEntry(
                    LDAP_CREDENTIAL_KEY,
                    knoxConf.bindDn.get,
                    knoxConf.password.get
                  )
                  Future
                    .fromTry(tryToWrite)
                    .map { _ =>
                      LdapConfiguration(
                        id = None,
                        ldapUrl = Some(knoxConf.ldapUrl),
                        bindDn = knoxConf.bindDn,
                        userSearchBase = knoxConf.userSearchBase,
                        userSearchAttributeName =
                          knoxConf.userSearchAttributeName,
                        userObjectClass = knoxConf.userObjectClass,
                        groupSearchBase = knoxConf.groupSearchBase,
                        groupSearchAttributeName =
                          knoxConf.groupSearchAttributeName,
                        groupObjectClass = knoxConf.groupObjectClass,
                        groupMemberAttributeName =
                          knoxConf.groupMemberAttributeName
                      )
                    }
                    .flatMap { config =>
                      ldapConfigService
                        .create(config)
                        .map {
                          case Left(errors) => Left(errors)
                          case Right(createdLdapConfig) =>
                            configService.setConfig("dp.knox.whitelist",
                                                    requestHost)
                            Right(true)
                        }
                    }
                    .recover {
                      case ex: Exception => Right(false)
                    }
              }
      }
  }

  def updateKnoxConfig(
      knoxConfig: KnoxConfigUpdateInfo): Future[Either[Errors, Boolean]] = {
    validateBindDn(knoxConfig.ldapUrl, knoxConfig.bindDn, knoxConfig.password)
      .flatMap {
        case Left(errors) => Future.successful(Left(errors))
        case Right(isBound) => {
          val tryToWrite = ldapKeyStore.createCredentialEntry(
            LDAP_CREDENTIAL_KEY,
            knoxConfig.bindDn.get,
            knoxConfig.password.get
          )
          Future
            .fromTry(tryToWrite)
            .map { _ =>
              LdapConfiguration(id = Some(knoxConfig.id),
                                ldapUrl = Some(knoxConfig.ldapUrl),
                                bindDn = knoxConfig.bindDn)
            }
            .flatMap { ldapConfig =>
              ldapConfigService.update(ldapConfig).map {
                case Left(errors)  => Left(errors)
                case Right(result) => Right(result)
              }
            }
        }
      }
  }

  def validate(knoxConf: KnoxConfigInfo): Either[Errors, Boolean] = {
    if (knoxConf.userSearchBase.isEmpty || knoxConf.userSearchAttributeName.isEmpty) {
      Left(Errors(Seq(Error(500, "user dn template mandatory"))))
    } else {
      Right(true)
    }
  }
  def validateBindDn(
      ldapUrl: String,
      bindDn: Option[String],
      password: Option[String]): Future[Either[Errors, Boolean]] = {
    getLdapContext(ldapUrl, bindDn.get, password.get)
      .map { ctx =>
        ctx.close()
        Right(true)
      }
      .recoverWith {
        case e: WrappedErrorException => Future.successful(Left(Errors(Seq(e.error))))
      }
  }

  def doWithEither[T, A](
      either: Either[Errors, T],
      f: T => Future[Either[Errors, A]]): Future[Either[Errors, A]] = {
    either match {
      case Left(errors) => Future.successful(Left(errors))
      case Right(t)     => f(t)
    }
  }

  def search(
      userName: String,
      searchType: Option[String],
      fuzzyMatch: Boolean): Future[Either[Errors, Seq[LdapSearchResult]]] =
    for {
      configuredLdap <- getConfiguredLdap
      dirContext <- doWithEither[Seq[LdapConfiguration], DirContext](
        configuredLdap,
        validateAndGetLdapContext)
      search <- doWithEither[DirContext, Seq[LdapSearchResult]](
        dirContext,
        context => {
          val searchResult = ldapSearch(context,
                                        configuredLdap.right.get,
                                        userName,
                                        searchType,
                                        fuzzyMatch)
          searchResult.onComplete { res =>
            context.close()
          }
          searchResult
        }
      )

    } yield search

  private def getUserDetailFromLdap(dirContext: DirContext,
                                    ldapConfs: Seq[LdapConfiguration],
                                    userName: String) = {
    ldapSearch(dirContext,
               ldapConfs,
               userName,
               Some("user"),
               fuzzyMatch = false)
  }

  private def validateAndGetLdapContext(configuredLdap: Seq[LdapConfiguration])
    : Future[Either[Errors, DirContext]] = {
    //TODO bind dn validate.
    configuredLdap.headOption match {
      case Some(l) => {
        val cred: Option[CredentialEntry] =
          ldapKeyStore.getCredentialEntry(LDAP_CREDENTIAL_KEY).toOption
        cred match {
          case Some(cred) =>
            getLdapContext(l.ldapUrl.get, l.bindDn.get, cred.password)
              .map { ctx =>
                Right(ctx)
              }
              .recoverWith {
                case e: WrappedErrorException =>
                  Future.successful(Left(Errors(Seq(e.error))))
              }
          case None => {
            Future.successful(Left(Errors(Seq(Error(500, "no password ")))))
          }
        }
      }
      case None => {
        Future.successful(Left(Errors(Seq(Error(409, "LDAP is not yet configured.")))))
      }
    }
  }

  def getPassword(): Option[String] = {
    val cred: Option[CredentialEntry] =
      ldapKeyStore.getCredentialEntry(LDAP_CREDENTIAL_KEY).toOption
    if (cred.isDefined) Some(cred.get.password) else None
  }

  private def ldapSearch(
      dirContext: DirContext,
      ldapConfs: Seq[LdapConfiguration],
      userName: String,
      searchType: Option[String],
      fuzzyMatch: Boolean): Future[Either[Errors, Seq[LdapSearchResult]]] = {
    val groupSerch =
      if (searchType.isDefined && searchType.get == "group") true else false
    if (groupSerch) {
      ldapGroupSearch(dirContext, ldapConfs, userName)
    } else {
      val searchControls: SearchControls = new SearchControls()
      searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE)
      try {
        if (ldapConfs.head.userSearchBase.isEmpty || ldapConfs.head.userSearchAttributeName.isEmpty) {
          Future.successful(Left(Errors(Seq(Error(500, "User search base and user search attribute must be configured.")))))
        } else {
          val userSearchBase = ldapConfs.head.userSearchBase.get
          val userSearchAttributeName =
            ldapConfs.head.userSearchAttributeName.get
          val searchParam = s"$userSearchAttributeName=$userName*"
          val res: NamingEnumeration[SearchResult] =
            dirContext.search(userSearchBase, searchParam, searchControls)
          val ldapSearchResults: ArrayBuffer[LdapSearchResult] =
            new ArrayBuffer()
          while (res.hasMore) {
            val sr: SearchResult = res.next()
            val ldaprs = new LdapSearchResult(
              sr.getAttributes.get(userSearchAttributeName).get().toString,
              sr.getClassName,
              sr.getNameInNamespace)
            ldapSearchResults += ldaprs
          }
          Future.successful(Right(ldapSearchResults))
        }
      } catch {
        case e: Exception =>
          logger.error("exception", e)
          Future.successful(Left(Errors(Seq(Error(500, e.getMessage)))))
      }
    }
  }

  private def ldapGroupSearch(
      dirContext: DirContext,
      ldapConfs: Seq[LdapConfiguration],
      groupName: String): Future[Either[Errors, Seq[LdapSearchResult]]] = {
    val groupSearchBase = ldapConfs.head.groupSearchBase
    if (groupSearchBase.isEmpty || ldapConfs.head.groupSearchAttributeName.isEmpty) {
      Future.successful(
        Left(
          Errors(
            Seq(Error(500, "Group search base must be configured")))))
    } else {
      val searchControls: SearchControls = new SearchControls()
      searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE)
      val groupSearchBase = ldapConfs.head.groupSearchBase.get
      val groupSearchAttributeName = ldapConfs.head.groupSearchAttributeName.get
      val searchParam = s"$groupSearchAttributeName=$groupName*"
      val res: NamingEnumeration[SearchResult] =
        dirContext.search(groupSearchBase, searchParam, searchControls)
      val ldapSearchResults: ArrayBuffer[LdapSearchResult] = new ArrayBuffer()
      while (res.hasMore) {
        val sr: SearchResult = res.next()
        val ldaprs = new LdapSearchResult(
          sr.getAttributes.get(groupSearchAttributeName).get().toString,
          sr.getClassName,
          sr.getNameInNamespace)
        ldapSearchResults += ldaprs
      }
      Future.successful(Right(ldapSearchResults))
    }
  }
  def getUserGroups(userName: String): Future[Either[Errors, LdapUser]] = {
    for {
      configuredLdap <- getConfiguredLdap
      dirContext <- doWithEither[Seq[LdapConfiguration], DirContext](
        configuredLdap,
        validateAndGetLdapContext)
      search <- doWithEither[DirContext, LdapUser](dirContext, context => {
        val groups =
          getUserAndGroups(context, configuredLdap.right.get, userName)
        groups.onComplete { res =>
          context.close()
        }
        groups
      })
    } yield search

  }

  private def getUserAndGroups(
      dirContext: DirContext,
      ldapConfs: Seq[LdapConfiguration],
      userName: String): Future[Either[Errors, LdapUser]] = {
    var ldapConf = ldapConfs.head
    validateGroupSettings(ldapConf) match {
      case Some(errors) => Future.successful(Left(errors))
      case _ => {
        val groupSearchBase = ldapConf.groupSearchBase
        getUserDetailFromLdap(dirContext, ldapConfs, userName).map {
          case Left(errors) => Left(errors)
          case Right(userSearchResults) => {
            userSearchResults.headOption match {
              case None =>
                Left(Errors(Seq(Error(500, "There is no such user"))))
              case Some(userRes) =>
                val groupSearchControls = new SearchControls
                groupSearchControls.setSearchScope(SearchControls.SUBTREE_SCOPE)
                val groupObjectClass = ldapConf.groupObjectClass.get; //ex "groupofnames"
                val groupMemberAttributeName =
                  ldapConf.groupMemberAttributeName.get; //ex "member"
                val extendedGroupSearchFilter =
                  s"(objectclass=$groupObjectClass)"
                val fullUserdn = userRes.nameInNameSpace
                var groupSearchFilter =
                  s"(&$extendedGroupSearchFilter($groupMemberAttributeName=$fullUserdn))"
                val res: NamingEnumeration[SearchResult] =
                  dirContext.search(groupSearchBase.get,
                                    groupSearchFilter,
                                    groupSearchControls)
                val ldapGroups: ArrayBuffer[LdapGroup] = new ArrayBuffer
                while (res.hasMore) {
                  val sr: SearchResult = res.next()
                  val ldaprs = LdapGroup(
                    sr.getAttributes
                      .get(ldapConfs.head.groupSearchAttributeName.get)
                      .get()
                      .toString,
                    sr.getClassName,
                    sr.getNameInNamespace)
                  ldapGroups += ldaprs
                }
                val ldapUser = LdapUser(userRes.name,
                                        userRes.className,
                                        userRes.nameInNameSpace,
                                        ldapGroups)
                Right(ldapUser)
            }
          }
        }
      }
    }
  }

  private def validateGroupSettings(
      ldapConf: LdapConfiguration): Option[Errors] = {
    if (!(ldapConf.groupSearchBase.isEmpty || ldapConf.groupObjectClass.isEmpty || ldapConf.groupMemberAttributeName.isEmpty))
      None
    else {
      val errors = ArrayBuffer.empty[Error]
      if (ldapConf.groupSearchBase.isEmpty) {
        errors += Error(500, "Group Search base not configured")
      }
      if (ldapConf.groupObjectClass.isEmpty) {
        errors += Error(500, "Group Search base not configured")
      }
      if (ldapConf.groupMemberAttributeName.isEmpty) {
        errors += Error(500, "Group Member AttributeName not configured")
      }
      Some(Errors(errors.seq))
    }
  }

  def getConfiguredLdap: Future[Either[Errors, Seq[LdapConfiguration]]] =
    ldapConfigService.get()

  private def getLdapContext(url: String, bindDn: String, pass: String): Future[DirContext] = {
    val p = Promise[DirContext]()

    val env = new util.Hashtable[String, String]()
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
    env.put(Context.SECURITY_AUTHENTICATION, "simple") //TODO configure for other types.
    env.put(Context.PROVIDER_URL, url)
    env.put(Context.SECURITY_PRINCIPAL, bindDn)
    env.put(Context.SECURITY_CREDENTIALS, pass)
    env.put("com.sun.jndi.ldap.connect.pool", "true")
    if(url.startsWith("ldaps://")) {
      GeneratedSocketFactory.set(sslContextManager.getSocketFactory())
      env.put("java.naming.ldap.factory.socket", "internal.GeneratedSocketFactory")
    }

    try {
      val ctx: DirContext = new InitialLdapContext(env, null)
      p.trySuccess(ctx)
    } catch {
      case ex: CommunicationException if(ExceptionUtils.indexOfThrowable(ex, classOf[SSLException]) >= 0) => {
        logger.error("ssl error while getting ldapContext", ex)
        p.tryFailure(WrappedErrorException(Error(500, "Could not communicate with LDAP server. You might need to add the certificate for your secure LDAP server.")))
      }
      case ex: CommunicationException if(ExceptionUtils.indexOfThrowable(ex, classOf[CompositeCertificateException]) >= 0) => {
        logger.error("ssl error related to certificate validation while getting ldapContext", ex)
        p.tryFailure(WrappedErrorException(Error(500, "Could not communicate with LDAP server. Please confirm that you have added the correct certificate for your secure LDAP server.")))
      }
      case ex: CommunicationException => {
        logger.error("error while getting ldapContext", ex)
        p.tryFailure(WrappedErrorException(Error(500, "Could not communicate with LDAP server. Check connectivity.")))
      }
      case e: AuthenticationException => {
        logger.error("error while getting ldapContext", e)
        p.tryFailure(WrappedErrorException(Error(500, "Some credentials are incorrect for LDAP")))
      }
      case e: NamingException => {
        logger.error("error while getting ldapContext", e)
        p.tryFailure(WrappedErrorException(Error(500, e.getMessage)))
      }
    }

    p.future
  }
}
