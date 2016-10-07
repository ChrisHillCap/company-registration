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

package fixtures

import models._

trait CompanyDetailsFixture {

  lazy val validCompanyDetails = CompanyDetails(
    "testCompanyName",
    CHROAddress(
      "Premises",
      "Line 1",
      Some("Line 2"),
      "Country",
      "Locality",
      Some("PO box"),
      Some("Post code"),
      Some("Region")
    ),
    ROAddress(
      "10",
      "test street",
      "test town",
      "test area",
      "test county",
      "XX1 1ZZ",
      "test country"
    ),
    PPOBAddress(
      "10",
      "test street",
      "test town",
      "test area",
      "test county",
      "XX1 1ZZ",
      "test country"
    ),
    "testJurisdiction"
  )

  lazy val validCompanyDetailsResponse = validCompanyDetails.toCompanyDetailsResponse("12345")
}
