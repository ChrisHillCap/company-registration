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

package connectors

import java.util.UUID

import mocks.WSHttpMock
import models.SubmissionCheckResponse
import org.mockito.Matchers
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ShouldMatchers, WordSpecLike}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.Mockito._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future


class SubmissionCheckAPIConnectorSpec extends WordSpecLike with ShouldMatchers with WSHttpMock with MockitoSugar with WithFakeApplication with UnitSpec {

  val testProxyUrl = "testBusinessRegUrl"
  implicit val hc = HeaderCarrier()

  trait Setup {
    val connector = new SubmissionCheckAPIConnector {
      override val proxyUrl = testProxyUrl
      override val http = mockWSHttp
    }
  }

  val validSubmissionResponse = SubmissionCheckResponse(
                                  "transactionId",
                                  "status",
                                  "crn",
                                  "incorpDate",
                                  "timepoint"
                                )

  "checkSubmission" should {
    "return a submission status response when no timepoint is provided" in new Setup {
      when(mockWSHttp.GET[SubmissionCheckResponse](Matchers.anyString())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission()) shouldBe validSubmissionResponse
    }
    "return a submission status response when a timepoint is provided" in new Setup {
      val testTimepoint = UUID.randomUUID().toString
      when(mockWSHttp.GET[SubmissionCheckResponse](Matchers.anyString())(Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission(Some(testTimepoint))) shouldBe validSubmissionResponse
    }
  }

}
