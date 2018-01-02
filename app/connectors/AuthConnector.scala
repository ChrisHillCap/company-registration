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

package connectors

import config.WSHttp
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

case class Authority(
                      uri: String,
                      gatewayId: String,
                      userDetailsLink: String,
                      ids: UserIds
                    )

case class UserIds(internalId : String,
                   externalId : String)

case class UserDetailsModel(name: String,
                            email: String,
                            affinityGroup: String,
                            description: Option[String] = None,
                            lastName: Option[String] = None,
                            dateOfBirth: Option[String] = None,
                            postcode: Option[String] = None,
                            authProviderId: String,
                            authProviderType: String)

object UserDetailsModel{
  implicit val format = Json.format[UserDetailsModel]
}


object UserIds {
  implicit val format = Json.format[UserIds]
}

trait AuthConnector extends ServicesConfig with RawResponseReads {

  def serviceUrl: String

  def authorityUri: String

  def http: CoreGet with CorePost

  def getCurrentAuthority()(implicit headerCarrier: HeaderCarrier): Future[Option[Authority]] = {
    val getUrl = s"""$serviceUrl/$authorityUri"""
    Logger.debug(s"[AuthConnector][getCurrentAuthority] - GET $getUrl")
    http.GET[HttpResponse](getUrl) flatMap {
      response =>
        Logger.debug(s"[AuthConnector][getCurrentAuthority] - RESPONSE status: ${response.status}, body: ${response.body}")
        response.status match {
          case OK => {
            val uri = (response.json \ "uri").as[String]
            val gatewayId = (response.json \ "credentials" \ "gatewayId").as[String]
            val userDetails = (response.json \ "userDetailsLink").as[String]
            val idsLink = (response.json \ "ids").as[String]

            http.GET[HttpResponse](s"$serviceUrl$idsLink") map {
              response =>
                val ids = response.json.as[UserIds]
                Some(Authority(uri, gatewayId, userDetails, ids))
            }
          }
          case status => Future.successful(None)
        }
    }
  }

  def getUserDetails(implicit headerCarrier: HeaderCarrier): Future[Option[UserDetailsModel]] = {
    getCurrentAuthority.flatMap {
      case Some(authority) => http.GET[UserDetailsModel](authority.userDetailsLink).map(Some(_))
      case _ => Future.successful(None)
    }
  }
}

object AuthConnector extends AuthConnector {
  // $COVERAGE-OFF$
  lazy val serviceUrl = baseUrl("auth")
  // $COVERAGE-OFF$
  val authorityUri = "auth/authority"
  val http: CoreGet with CorePost = WSHttp
}
