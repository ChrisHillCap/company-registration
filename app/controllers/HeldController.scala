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

package controllers

import auth._
import javax.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import repositories.{CorporationTaxRegistrationMongoRepository, Repositories}
import services.ProcessIncorporationService
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

class HeldControllerImpl @Inject()(
                                    val authConnector: AuthClientConnector,
                                    val service: ProcessIncorporationService,
                                    val repositories: Repositories
      ) extends HeldController {
  val resource: CorporationTaxRegistrationMongoRepository = repositories.cTRepository
}

trait HeldController extends BaseController with AuthorisedActions {

  val resource: CorporationTaxRegistrationMongoRepository
  val service: ProcessIncorporationService

  def fetchHeldSubmissionTime(regId: String): Action[AnyContent] = AuthenticatedAction.async {
    implicit request =>
      resource.getExistingRegistration(regId) map { doc =>
          Ok(Json.toJson(doc.heldTimestamp))
      }
  }

  def deleteSubmissionData(regId: String): Action[AnyContent] = AuthorisedAction(regId).async {
    implicit request =>
      service.deleteRejectedSubmissionData(regId).map {
        if(_) Ok else NotFound
      }
  }
}
