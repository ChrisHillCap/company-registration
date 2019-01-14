/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services.admin

import java.util.Base64

import audit._
import config.MicroserviceAuditConnector
import connectors.{BusinessRegistrationConnector, DesConnector, IncorporationInformationConnector}
import helpers.DateFormatter
import javax.inject.Inject
import models.RegistrationStatus._
import models.admin.{AdminCTReferenceDetails, HO6Identifiers, HO6Response}
import models.{ConfirmationReferences, CorporationTaxRegistration, HO6RegistrationInformation, SessionIdData}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Request
import repositories.{CorpTaxRegistrationRepo, CorporationTaxRegistrationMongoRepository}
import services.FailedToDeleteSubmissionData
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AdminServiceImpl @Inject()(
                                 corpTaxRepo: CorpTaxRegistrationRepo,
                                 val brConnector: BusinessRegistrationConnector,
                                 val desConnector: DesConnector,
                                 val incorpInfoConnector: IncorporationInformationConnector) extends AdminService with ServicesConfig {

  val corpTaxRegRepo: CorporationTaxRegistrationMongoRepository = corpTaxRepo.repo
  lazy val staleAmount: Int = getInt("staleDocumentAmount")
  lazy val clearAfterXDays: Int = getInt("clearAfterXDays")
  lazy val ignoredDocs: Set[String] = new String(Base64.getDecoder.decode(getConfString("skipStaleDocs", "")), "UTF-8").split(",").toSet
  lazy val auditConnector = MicroserviceAuditConnector
}

trait AdminService extends DateFormatter {

  val corpTaxRegRepo: CorporationTaxRegistrationMongoRepository
  val desConnector: DesConnector
  val auditConnector: AuditConnector
  val incorpInfoConnector: IncorporationInformationConnector
  val brConnector: BusinessRegistrationConnector

  val staleAmount: Int
  val clearAfterXDays: Int
  val ignoredDocs: Set[String]

  def fetchHO6RegistrationInformation(regId: String): Future[Option[HO6RegistrationInformation]] = corpTaxRegRepo.fetchHO6Information(regId)

  def fetchSessionIdData(regId: String): Future[Option[SessionIdData]] = {
    corpTaxRegRepo.retrieveCorporationTaxRegistration(regId) map (_.map{ reg =>
      SessionIdData(
        reg.sessionIdentifiers.map(_.sessionId),
        reg.sessionIdentifiers.map(_.credId),
        reg.companyDetails.map(_.companyName),
        reg.confirmationReferences.map(_.acknowledgementReference)
      )
    })
  }

  private[services] def forceSubscription(regId: String, transactionId: String)(implicit hc: HeaderCarrier, req: Request[_]): Future[Boolean] = {
    incorpInfoConnector.registerInterest(regId, transactionId, true)
  }

  def auditAdminEvent(strideUser: String, identifiers: HO6Identifiers, response: HO6Response)(implicit hc: HeaderCarrier): Future[AuditResult] = {
    val identifiersJson = Json.toJson(identifiers)(HO6Identifiers.adminAuditWrites).as[JsObject]
    val responseJson = Json.toJson(response)(HO6Response.adminAuditWrites).as[JsObject]
    val timestamp = Json.obj("timestamp" -> Json.toJson(nowAsZonedDateTime)(zonedDateTimeWrites))
    val auditEvent = new AdminReleaseAuditEvent(timestamp, strideUser, identifiersJson, responseJson)
    auditConnector.sendExtendedEvent(auditEvent)
  }

  private[services] def fetchTransactionId(regId: String): Future[Option[String]] = corpTaxRegRepo.retrieveConfirmationReferences(regId).map(_.fold[Option[String]] {
    Logger.error(s"[Admin] [fetchTransactionId] - Held submission found but transaction Id missing for regId $regId")
    None
  }(refs => Option(refs.transactionId)))

  def ctutrCheck(id: String): Future[JsObject] = {
    corpTaxRegRepo.retrieveStatusAndExistenceOfCTUTR(id) map {
      case Some((status, ctutr)) => Json.obj("status" -> status, "ctutr" -> ctutr)
      case _ => Json.obj()
    }
  }

  def updateRegistrationWithCTReference(ackRef: String, ctUtr: String, username: String)(implicit hc: HeaderCarrier): Future[Option[JsObject]] = {
    corpTaxRegRepo.updateRegistrationWithAdminCTReference(ackRef, ctUtr) map {
      _ flatMap { cr =>
        cr.acknowledgementReferences map { acknowledgementRefs =>
          val timestamp = Json.obj("timestamp" -> Json.toJson(nowAsZonedDateTime)(zonedDateTimeWrites))
          val refDetails = AdminCTReferenceDetails(acknowledgementRefs.ctUtr, ctUtr, acknowledgementRefs.status, "04")

          auditConnector.sendExtendedEvent(
            new AdminCTReferenceEvent(timestamp, username, Json.toJson(refDetails)(AdminCTReferenceDetails.adminAuditWrites).as[JsObject])
          )

          Json.obj("status" -> "04", "ctutr" -> true)
        }
      }
    }
  }


  def adminDeleteSubmission(info: DocumentInfo, txId : Option[String])(implicit hc : HeaderCarrier): Future[Boolean] = {
    val cancelSub = txId match {
      case Some(tId) =>
        incorpInfoConnector.cancelSubscription(info.regId, tId) recover {
          case e : NotFoundException =>
            Logger.info(s"[processStaleDocument] Registration ${info.regId} - $tId does not have CTAX subscription. Now trying to delete CT sub.")
            incorpInfoConnector.cancelSubscription(info.regId, tId, useOldRegime = true) recover {
              case e : NotFoundException =>
                Logger.warn(s"[processStaleDocument] Registration ${info.regId} - $tId has no subscriptions.")
                Future.successful(true)
            }
        }
      case _         => Future.successful(true)
    }

    for {
      _               <- cancelSub
      metadataDeleted <- brConnector.adminRemoveMetadata(info.regId)
      ctDeleted       <- corpTaxRegRepo.removeTaxRegistrationById(info.regId)
    } yield {
      if (ctDeleted && metadataDeleted) {
        Logger.info(s"[processStaleDocument] Deleted stale regId: ${info.regId} timestamp: ${info.lastSignedIn}")
        true
      } else {
        throw FailedToDeleteSubmissionData
      }
    }
  }

  def updateTransactionId(updateFrom: String, updateTo: String): Future[Boolean] = {
    corpTaxRegRepo.updateTransactionId(updateFrom, updateTo) map {
      _ == updateTo
    } recover {
      case _ => false
    }
  }

  def deleteStaleDocuments(): Future[Int] = {
    for {
      documents <- corpTaxRegRepo.retrieveStaleDocuments(staleAmount, clearAfterXDays)
      _         = Logger.info(s"[deleteStaleDocuments] Mongo query found ${documents.size} stale documents. Now processing.")
      processed <- Future.sequence(documents filterNot(doc => ignoredDocs(doc.registrationID)) map processStaleDocument)
    } yield processed count (_ == true)
  }

  case class DocumentInfo(regId: String, status: String, lastSignedIn: DateTime)

  private def removeStaleDocument(documentInfo: DocumentInfo, optRefs : Option[ConfirmationReferences])(implicit hc: HeaderCarrier) = optRefs match {
    case Some(confRefs) => checkNotIncorporated(documentInfo, confRefs) flatMap { _ =>
      rejectNoneDraft(documentInfo, confRefs) flatMap { _ =>
        adminDeleteSubmission(documentInfo, Some(confRefs.transactionId))
      }
    }
    case None => adminDeleteSubmission(documentInfo, None)
  }

  private[services] def processStaleDocument(doc: CorporationTaxRegistration): Future[Boolean] = {
    implicit val hc = HeaderCarrier()
    val documentInfo = DocumentInfo(doc.registrationID, doc.status, doc.lastSignedIn)

    Logger.info(s"[processStaleDocument] Processing stale document of $documentInfo")

    ((doc.status, doc.confirmationReferences) match {
      case ((DRAFT | HELD | LOCKED), optRefs) => removeStaleDocument(documentInfo, optRefs)
      case _                                  => Future.successful(false)
    }) recover {
      case e: Throwable =>
        Logger.warn(s"[processStaleDocument] Failed to delete regId: ${documentInfo.regId} with throwable $e")
        false
    }
  }

  private def checkNotIncorporated(documentInfo: DocumentInfo, confRefs: ConfirmationReferences)(implicit hc: HeaderCarrier): Future[Boolean] = {
    incorpInfoConnector.checkCompanyIncorporated(confRefs.transactionId) map { res =>
      res.fold {
        true
      } { crn =>
        Logger.warn(s"[processStaleDocument] Could not delete document with CRN: $crn " +
          s"regId: ${documentInfo.regId} transID: ${confRefs.transactionId} lastSignIn: ${documentInfo.lastSignedIn} status: ${documentInfo.status}"
        )
        throw new RuntimeException
      }
    }
  }

  private def rejectNoneDraft(info: DocumentInfo, confRefs: ConfirmationReferences)(implicit hc: HeaderCarrier) = {
    def submission(ackRef: String) = Json.obj(
      "status" -> "Rejected",
      "acknowledgementReference" -> ackRef
    )

    if (info.status != DRAFT) {
      val ackRef = confRefs.acknowledgementReference
      val event = new DesTopUpSubmissionEvent(
        DesTopUpSubmissionEventDetail(
          info.regId,
          ackRef,
          "Rejected",
          None,
          None,
          None,
          None,
          Some(true)
        ),
        "ctRegistrationAdditionalData",
        "ctRegistrationAdditionalData"
      )
      for {
        _ <- desConnector.topUpCTSubmission(ackRef, submission(ackRef), info.regId)
        _ <- auditConnector.sendExtendedEvent(event)
      } yield true
    } else {
      Future.successful(true)
    }
  }

  def updateDocSessionID(regId: String, sessionId: String, credId: String, username : String)(implicit hc : HeaderCarrier) : Future[SessionIdData] = {
    Logger.info(s"[updateDocSessionID] Updating document session id regId $regId")

    corpTaxRegRepo.retrieveSessionIdentifiers(regId) flatMap {
      case Some(sessionIds) =>
        for {
          _             <- corpTaxRegRepo.storeSessionIdentifiers(regId, sessionId, credId)
          sessionIdData <- fetchSessionIdData(regId).map(
            _.getOrElse(throw new RuntimeException(s"Registration Document does not exist or Document does not have sessionIdInfo for regId $regId")))
          timestamp     = Json.obj("timestamp" -> Json.toJson(nowAsZonedDateTime)(zonedDateTimeWrites))
          _             = auditConnector.sendExtendedEvent(new AdminSessionIDEvent(timestamp, username, Json.toJson(sessionIdData).as[JsObject], sessionIds.sessionId))
        } yield sessionIdData
      case _ => throw new RuntimeException(s"Registration Document does not exist or Document does not have sessionIdentifiers for regId $regId")
    }
  }
}