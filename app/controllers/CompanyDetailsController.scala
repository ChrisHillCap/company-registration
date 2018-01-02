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

package controllers

import javax.inject.Inject

import auth._
import connectors.AuthConnector
import models.{CompanyDetails, ErrorResponse, TradingDetails}
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Action
import repositories.Repositories
import services.{CompanyDetailsService, MetricsService}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class CompanyDetailsControllerImp @Inject() (metrics: MetricsService,
                                             companyDetailsServ: CompanyDetailsService) extends CompanyDetailsController {
  override val auth: AuthConnector = AuthConnector
  val resourceConn = Repositories.cTRepository
  override val companyDetailsService = companyDetailsServ
  override val metricsService: MetricsService = metrics
}

trait CompanyDetailsController extends BaseController with Authenticated with Authorisation[String]{

  val companyDetailsService: CompanyDetailsService
  val metricsService: MetricsService

  private def mapToResponse(registrationID: String, res: CompanyDetails)= {
    Json.toJson(res).as[JsObject] ++
      Json.obj("tradingDetails" -> TradingDetails()) ++
      Json.obj(
        "links" -> Json.obj(
          "self" -> routes.CompanyDetailsController.retrieveCompanyDetails(registrationID).url,
          "registration" -> routes.CorporationTaxRegistrationController.retrieveCorporationTaxRegistration(registrationID).url
        )
      )
  }

  def retrieveCompanyDetails(registrationID: String) = Action.async {
    implicit request =>
      authorised(registrationID){
        case Authorised(_) =>
                     val timer = metricsService.retrieveCompanyDetailsCRTimer.time()
                     companyDetailsService.retrieveCompanyDetails(registrationID).map {
          case Some(details) => {
            timer.stop()
            Ok(mapToResponse(registrationID, details))
          }
          case _ => NotFound(ErrorResponse.companyDetailsNotFound)
        }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[CompanyDetailsController] [retrieveCompanyDetails] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[CompanyDetailsController] [retrieveCompanyDetails] User logged in but not authorised for resource $registrationID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }

  def updateCompanyDetails(registrationID: String): Action[JsValue] = Action.async[JsValue](parse.json) {
    implicit request =>
      authorised(registrationID) {
        case Authorised(_) =>
          val timer = metricsService.updateCompanyDetailsCRTimer.time()
          withJsonBody[CompanyDetails] {
            companyDetails => companyDetailsService.updateCompanyDetails(registrationID, companyDetails).map{
              case Some(details) => timer.stop
                                    Ok(mapToResponse(registrationID, details))
              case None => NotFound(ErrorResponse.companyDetailsNotFound)
            }
          }
        case NotLoggedInOrAuthorised =>
          Logger.info(s"[CompanyDetailsController] [updateCompanyDetails] User not logged in")
          Future.successful(Forbidden)
        case NotAuthorised(_) =>
          Logger.info(s"[CompanyDetailsController] [updateCompanyDetails] User logged in but not authorised for resource $registrationID")
          Future.successful(Forbidden)
        case AuthResourceNotFound(_) => Future.successful(NotFound)
      }
  }
}
