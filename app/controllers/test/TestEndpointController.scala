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

import helpers.DateHelper
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories.{Repositories, ThrottleMongoRepository}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

object TestEndpointController extends TestEndpointController {
  val throttleMongoRepository = Repositories.throttleRepository
}

trait TestEndpointController extends BaseController {

  val throttleMongoRepository: ThrottleMongoRepository

  def modifyThrottledUsers(usersIn: Int) = Action.async {
    implicit request =>
      val date = DateHelper.getCurrentDay
      throttleMongoRepository.modifyThrottledUsers(date, usersIn).map(x => Ok(Json.parse(s"""{"users_in" : $x}""")))
  }
}
