/*
 * Copyright 2016 HM Revenue & Customs
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

package services

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import models.{CorporationTaxRegistrationResponse, CorporationTaxRegistration}
import org.joda.time.DateTime
import repositories.{SequenceRepository, CorporationTaxRegistrationRepository, Repositories}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CorporationTaxRegistrationService extends CorporationTaxRegistrationService {
  override val CorporationTaxRegistrationRepository = Repositories.cTRepository
  override val sequenceRepository = Repositories.sequenceRepository
}

trait CorporationTaxRegistrationService {

  val CorporationTaxRegistrationRepository: CorporationTaxRegistrationRepository
  val sequenceRepository: SequenceRepository

  def createCorporationTaxRegistrationRecord(OID: String, registrationId: String, language: String): Future[CorporationTaxRegistrationResponse] = {
    val record = CorporationTaxRegistration.empty.copy(
      None,
      OID,
      registrationId,
      generateTimestamp(new DateTime()),
      language)

    CorporationTaxRegistrationRepository.createCorporationTaxRegistration(record).map(_.toCTRegistrationResponse)
  }

  def retrieveCorporationTaxRegistrationRecord(rID: String): Future[Option[CorporationTaxRegistrationResponse]] = {
    CorporationTaxRegistrationRepository.retrieveCorporationTaxRegistration(rID).map{
      case Some(details) => Some(details.toCTRegistrationResponse)
      case None => None
    }
  }

  def updateAcknowledgementReference(rID: String): Future[Option[String]] = {
    for{
      ref <- generateAcknowledgementReference
      updatedRef <- CorporationTaxRegistrationRepository.updateAcknowledgementRef(rID, ref)
    } yield {
      updatedRef
    }
  }

  def retrieveAcknowledgementReference(rID: String): Future[Option[String]] = {
    CorporationTaxRegistrationRepository.retrieveAcknowledgementRef(rID)
  }

  private def generateTimestamp(timeStamp: DateTime) : String = {
    val timeStampFormat = "yyyy-MM-dd'T'HH:mm:ssXXX"
    val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    val format: SimpleDateFormat = new SimpleDateFormat(timeStampFormat)
    format.setTimeZone(UTC)
    format.format(new Date(timeStamp.getMillis))
  }

  private def generateAcknowledgementReference: Future[String] = {

    val sequenceID = "AcknowledgementID"
    sequenceRepository.getNext(sequenceID)
      .map {
        ref =>
      f"BRCT$ref%011d"
    }
  }
}