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

package jobs

import org.joda.time.Duration
import play.api.Logger
import repositories.Repositories
import services.{CorporationTaxRegistrationService}
import uk.gov.hmrc.lock.LockKeeper
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob
import utils.SCRSFeatureSwitches

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

trait MissingIncorporationJob extends ExclusiveScheduledJob with JobConfig {

  val lock: LockKeeper
  val ctRegService: CorporationTaxRegistrationService

  //$COVERAGE-OFF$
  override def executeInMutex(implicit ec: ExecutionContext): Future[Result] = {
    SCRSFeatureSwitches.missingIncorp.enabled match {
      case true => lock.tryLock {
        Logger.info(s"Triggered $name")
        ctRegService.locateOldHeldSubmissions(HeaderCarrier())
      } map {
        case Some(x) =>
          Logger.info(s"successfully acquired lock for $name")
          Result(s"$name")
        case None =>
          Logger.info(s"failed to acquire lock for $name")
          Result(s"$name failed")
      } recover {
        case _: Exception => Result(s"$name failed")
      }
      case false => Future.successful(Result(s"Feature is turned off"))
    }
  }
  //$COVERAGE-ON$
}

object MissingIncorporationJob extends MissingIncorporationJob {
  val name = "missing-incorporation-job"
  lazy val ctRegService = CorporationTaxRegistrationService

  override lazy val lock: LockKeeper = new LockKeeper() {
    override val lockId = s"$name-lock"
    override val forceLockReleaseAfter: Duration = lockTimeout
    override val repo = Repositories.lockRepository
  }
}
