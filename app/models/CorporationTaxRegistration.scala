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

package models

import play.api.libs.json.Json

case class CorporationTaxRegistration(OID: String,
                    registrationID: String,
                    formCreationTimestamp: String,
                    language: String,
                    link: Links)

object CorporationTaxRegistration {
  implicit val linksFormats = Json.format[Links]
  implicit val formats = Json.format[CorporationTaxRegistration]

  def empty: CorporationTaxRegistration = {
    CorporationTaxRegistration("", "", "", "", Links(""))
  }
}
case class Links(self: String)


case class CorporationTaxRegistrationResponse(registrationID: String,
                                      formCreationTimestamp: String,
                                      language: String,
                                      link: Links)

object CorporationTaxRegistrationResponse {
  implicit val linksFormats = Json.format[Links]
  implicit val formats = Json.format[CorporationTaxRegistrationResponse]
}