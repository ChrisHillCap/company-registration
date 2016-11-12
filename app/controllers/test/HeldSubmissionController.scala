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

package controllers.test

import play.api.libs.json.Json
import play.api.mvc.{Result, Action}
import repositories.{HeldSubmissionMongoRepository, Repositories}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object HeldSubmissionController extends HeldSubmissionController {
  val heldSubmissionRepo = Repositories.heldSubmissionRepository
}

trait HeldSubmissionController extends BaseController {

  val heldSubmissionRepo: HeldSubmissionMongoRepository

  def fetchHeldSubmission(registrationId: String) = Action.async {
    implicit request =>
      heldSubmissionRepo.retrieveSubmissionByRegId(registrationId).map(_.fold[Result](NotFound){
        heldSub => Ok(Json.toJson(heldSub))
      })
  }
}