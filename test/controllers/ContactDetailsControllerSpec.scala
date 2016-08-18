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

package controllers

import fixtures.ContactDetailsFixture
import helpers.SCRSSpec
import models.{ContactDetailsResponse, Links}
import play.api.test.FakeRequest

class ContactDetailsControllerSpec extends SCRSSpec with ContactDetailsFixture {

  trait Setup {
    val controller = new ContactDetailsController {
      override val contactDetailsService = mockContactDetailsService
      override val resourceConn = mockCTDataRepository
      override val auth = mockAuthConnector
    }
  }

  val registrationID = "12345"

  "retrieveContactDetails" in new Setup {
    ContactDetailsServiceMocks.retrieveContactDetails(registrationID, Some(contactDetailsResponse))

    val result = controller.retrieveContactDetails(registrationID)(FakeRequest())
    status(result) shouldBe ""
  }
}
