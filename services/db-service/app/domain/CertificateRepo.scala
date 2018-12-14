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

package domain

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}

import com.hortonworks.dataplane.commons.domain.Entities.{Certificate, Error, WrappedErrorException}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CertificateRepo @Inject()(protected val dbConfigProvider: DatabaseConfigProvider,
                                protected val userRepo: UserRepo) extends HasDatabaseConfigProvider[DpPgProfile] {

  import profile.api._

  val Certificates = TableQuery[CertificatesTable]

  def list(active: Option[Boolean], name: Option[String]): Future[List[Certificate]] = db.run {
    (active, name) match {
      case (Some(active), Some(name)) => Certificates.filter(cCertificate => cCertificate.active === active && cCertificate.name === name).to[List].result
      case (Some(active), None) => Certificates.filter(_.active === active).to[List].result
      case (None, Some(name)) => Certificates.filter(_.name === name).to[List].result
      case (None, None) => Certificates.to[List].result
    }
  }

  def create(certificate: Certificate): Future[Certificate] =
    db.run { Certificates returning Certificates += certificate.copy(created = Some(LocalDateTime.now())) }

  def retrieve(id: String): Future[Certificate] =
    db.run(Certificates.filter(_.id === id).result.headOption)
      .map{
        case Some(certificate) => certificate
        case None => throw WrappedErrorException(Error(404, "Unable to find certificate with supplied ID", "database.certificate.not-found"))
      }

  def update(certificate: Certificate): Future[Certificate] =
    db.run {
      (for {
        updated <- Certificates
          .filter(_.id === certificate.id)
          .map(r => (r.name, r.data, r.format, r.active))
          .update(certificate.name, certificate.data, certificate.format, certificate.active)

        certificate <-  updated match {
          case 0 => DBIO.failed(WrappedErrorException(Error(404, "Unable to find certificate", "database.not-found")))
          case _ => Certificates.filter(_.id === certificate.id).result.head
        }
      } yield certificate).transactionally
    }

  def delete(id: String): Future[Int] =
    db.run { Certificates.filter(_.id === id).delete }

  final class CertificatesTable(tag: Tag) extends Table[Certificate](tag, Some("dataplane"), "certificates") {
    def id = column[Option[String]]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name")

    def format = column[String]("format")

    def data = column[String]("data")

    def active = column[Boolean]("active")

    def createdBy = column[Option[Long]]("created_by")

    def created = column[Option[LocalDateTime]]("created")

    def * = (id, name, format, data, active, createdBy, created) <> ((Certificate.apply _).tupled, Certificate.unapply)
  }

}
