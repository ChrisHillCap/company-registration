/*
 * Copyright 2018 HM Revenue & Customs
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

package config

import fixtures.CorporationTaxRegistrationFixture
import models.{ContactDetails, PPOBAddress, TradingDetails, _}
import models.RegistrationStatus._
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import repositories.{CorporationTaxRegistrationMongoRepository, HeldSubmissionMongoRepository, Repositories}
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.scalatest.concurrent.Eventually
import play.api.{Application, Configuration, Logger}
import play.api.libs.json.Json
import services.admin.AdminServiceImpl

import scala.concurrent.Future

class AppStartupJobsSpec extends UnitSpec with MockitoSugar with LogCapturing
  with CorporationTaxRegistrationFixture with Eventually {

  val mockApp: Application = mock[Application]
  val mockConfig: Configuration = mock[Configuration]
  val mockHeldRepo: HeldSubmissionMongoRepository = mock[HeldSubmissionMongoRepository]
  val mockCTRepository: CorporationTaxRegistrationMongoRepository = mock[CorporationTaxRegistrationMongoRepository]
  val mockAdminService: AdminServiceImpl = mock[AdminServiceImpl]
  val mockRepositories: Repositories = mock[Repositories]

  val expectedLockedReg = List()
  val expectedRegStats  = Map.empty[String,Int]

  trait Setup {
    when(mockRepositories.cTRepository)
      .thenReturn(mockCTRepository)

    when(mockRepositories.heldSubmissionRepository)
      .thenReturn(mockHeldRepo)

    when(mockCTRepository.retrieveLockedRegIDs())
      .thenReturn(Future.successful(expectedLockedReg))

    when(mockCTRepository.getRegistrationStats())
      .thenReturn(Future.successful(expectedRegStats))

    val appStartupJobs: AppStartupJobs = new AppStartupJobs(mockConfig, mockAdminService, mockRepositories)
  }

  "get Company Name" should {

    val regId1 = "reg-1"
    val companyName1 = "ACME1 ltd"
    val companyName2 = "ACME2 ltd"

    val ctDoc1 = validCTRegWithCompanyName(regId1, companyName1)
    val ctDoc2 = validCTRegWithCompanyName(regId1, companyName2)


    "log specific company name relating to reg id passed in" in new Setup {
      when(mockCTRepository.retrieveMultipleCorporationTaxRegistration(any()))
        .thenReturn(Future.successful(List(ctDoc1,ctDoc2)))

      withCaptureOfLoggingFrom(Logger){ logEvents =>
        eventually {
          await(appStartupJobs.getCTCompanyName(regId1))
          val expectedLogs = List(
            s"[CompanyName] status : held - reg Id : $regId1 - Company Name : $companyName1 - Trans ID : TX1",
            s"[CompanyName] status : held - reg Id : $regId1 - Company Name : $companyName2 - Trans ID : TX1"
          )

          expectedLogs.diff(logEvents.map(_.getMessage)) shouldBe List.empty
        }
      }
    }

  }
  "fetch Reg Ids" should {

    val dateTime: DateTime = DateTime.parse("2016-10-27T16:28:59.000")
    def corporationTaxRegistration(regId: String,
                                   status: String = SUBMITTED,
                                   transId: String = "transid-1"
                                   ): CorporationTaxRegistration = {
      CorporationTaxRegistration(
        internalId = "testID",
        registrationID = regId,
        formCreationTimestamp = dateTime.toString,
        language = "en",
        companyDetails = Some(CompanyDetails(
          "testCompanyName",
          CHROAddress("Premises", "Line 1", Some("Line 2"), "Country", "Locality", Some("PO box"), Some("Post code"), Some("Region")),
          PPOB("MANUAL", Some(PPOBAddress("10 test street", "test town", Some("test area"), Some("test county"), Some("XX1 1ZZ"), None, None, "txid"))),
          "testJurisdiction"
        )),
        contactDetails = Some(ContactDetails(
          "testFirstName",
          Some("testMiddleName"),
          "testSurname",
          Some("0123456789"),
          Some("0123456789"),
          Some("test@email.co.uk")
        )),
        tradingDetails = Some(TradingDetails("false")),
        status = status,
        confirmationReferences = Some(ConfirmationReferences(s"ACKFOR-$regId",transId,Some("PAYREF"),Some("12")))
      )
    }

    val txIds = Seq("transid-1","transid-2","transid-3")

    "log specific company name relating to reg id passed in" in new Setup {
      when(mockCTRepository.retrieveRegistrationByTransactionID("transid-1"))
        .thenReturn(Future.successful(Some(corporationTaxRegistration("1","TestStatus","transid-1"))))
      when(mockCTRepository.retrieveRegistrationByTransactionID("transid-2"))
        .thenReturn(Future.successful(Some(corporationTaxRegistration("2","TestStatus2","transid-2"))))
      when(mockCTRepository.retrieveRegistrationByTransactionID("transid-3"))
        .thenReturn(Future.successful(None))

      withCaptureOfLoggingFrom(Logger){ logEvents =>
        eventually {
          await(appStartupJobs.fetchRegIds(txIds))
        }
      }
    }
  }
  "retrieveCount of invalid rejections" in new Setup {
    when(mockCTRepository.updateInvalidRejectionCasesAndReturnCountOfModified).thenReturn(Future.successful(1))

    withCaptureOfLoggingFrom(Logger){ logEvents =>
      eventually {
        await(appStartupJobs.retrieveCountOfInvalidRejections)
        val expectedLogs = List(
          "[InvalidRejections] modified 1 document(s)"
        )

        expectedLogs.diff(logEvents.map(_.getMessage)) shouldBe List.empty
      }
    }
  }
}
