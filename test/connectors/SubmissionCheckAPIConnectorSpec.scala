/*
 * Copyright 2021 HM Revenue & Customs
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

import models.{IncorpUpdate, SubmissionCheckResponse}
import org.joda.time.DateTime
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.Future


class SubmissionCheckAPIConnectorSpec extends WordSpec with Matchers with MockitoSugar {

  val testProxyUrl = "testBusinessRegUrl"
  implicit val hc = HeaderCarrier()

  val mockWSHttp = mock[HttpClient]

  trait Setup {
    val connector = new IncorporationCheckAPIConnector {
      val proxyUrl = testProxyUrl
      val http = mockWSHttp
    }
  }

  val validSubmissionResponse = SubmissionCheckResponse(
    Seq(
      IncorpUpdate(
        transactionId = "transactionId",
        status = "status",
        crn = Some("crn"),
        incorpDate = Some(new DateTime(2016, 8, 10, 0, 0)),
        timepoint = "100000011")
    ),
    "testNextLink")


  "checkSubmission" should {

    val testTimepoint = UUID.randomUUID().toString

    "return a submission status response when no timepoint is provided" in new Setup {
      when(mockWSHttp.GET[SubmissionCheckResponse](ArgumentMatchers.anyString())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission()) shouldBe validSubmissionResponse
    }

    "return a submission status response when a timepoint is provided" in new Setup {
      val testTimepoint: String = UUID.randomUUID().toString

      when(mockWSHttp.GET[SubmissionCheckResponse](ArgumentMatchers.anyString())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission(Some(testTimepoint))) shouldBe validSubmissionResponse
    }

    "verify a timepoint is appended as a query string to the url when one is supplied" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?timepoint=$testTimepoint&items_per_page=1"

      val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[SubmissionCheckResponse](urlCaptor.capture())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission(Some(testTimepoint)))

      urlCaptor.getValue shouldBe url
    }

    "verify nothing is appended as a query string if a timepoint is not supplied" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[SubmissionCheckResponse](urlCaptor.capture())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission(None))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving a 400" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[SubmissionCheckResponse](urlCaptor.capture())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      intercept[SubmissionAPIFailure](await(connector.checkSubmission(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving a 404" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[SubmissionCheckResponse](urlCaptor.capture())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      intercept[SubmissionAPIFailure](await(connector.checkSubmission(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving an Upstream4xx" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[SubmissionCheckResponse](urlCaptor.capture())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("429", 429, 429)))

      intercept[SubmissionAPIFailure](await(connector.checkSubmission(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving an Upstream5xx" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[SubmissionCheckResponse](urlCaptor.capture())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(Upstream5xxResponse("502", 502, 502)))

      intercept[SubmissionAPIFailure](await(connector.checkSubmission(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving an unexpected error" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[SubmissionCheckResponse](urlCaptor.capture())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NoSuchElementException))

      intercept[SubmissionAPIFailure](await(connector.checkSubmission(None)))

      urlCaptor.getValue shouldBe url
    }
  }
}
