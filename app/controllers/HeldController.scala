/*
 * Copyright 2017 HM Revenue & Customs
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
import connectors.AuthConnector
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, Result}
import repositories.{HeldSubmissionMongoRepository, Repositories}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object HeldController extends HeldController {
  val auth = AuthConnector
  val repoConn = Repositories.heldSubmissionRepository
}

trait HeldController extends BaseController with Authenticated {

  val repoConn: HeldSubmissionMongoRepository

  def fetchHeldSubmissionTime(regId: String) = Action.async {
    implicit request =>
      authenticated {
        case LoggedIn(_) => repoConn.retrieveHeldSubmissionTime(regId).map {
          case Some(time) => Ok(Json.toJson(time))
          case None => NotFound
        }
        case _ =>
          Logger.info(s"[HeldController] [fetchHeldSubmissionTime] User not logged in")
          Future.successful(Forbidden)

      }
  }
}

