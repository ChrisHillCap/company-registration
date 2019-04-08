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

package services

import helpers.BaseSpec
import models._
import models.des.BusinessAddress
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito._
import repositories.CorporationTaxRegistrationMongoRepository
import uk.gov.hmrc.play.test.LogCapturing

import scala.concurrent.Future

class GroupsServiceSpec extends BaseSpec with LogCapturing {

  val regId = "regIdWizz"

  val validGroupsModel = Groups(
    groupRelief = true,
    nameOfCompany = Some(GroupCompanyName("foo", GroupCompanyNameEnum.Other)),
    addressAndType = Some(GroupsAddressAndType(
      GroupAddressTypeEnum.ALF,
      BusinessAddress("1 abc","2 abc",Some("3 abc"),Some("4 abc"),Some("ZZ1 1ZZ"),Some("country A")))
    ),
    groupUTR = Some(GroupUTR(Some("1234567890"))))


  class Setup {
    val service = new GroupsService {
      reset(mockCTDataRepository)
      override val cTRegistrationRepository: CorporationTaxRegistrationMongoRepository = mockCTDataRepository
    }
  }

  "returnGroups" should {
    "return groups if they exist in db" in new Setup {
      when(mockCTDataRepository.returnGroupsBlock(eqTo(regId))).thenReturn(Future.successful(Some(validGroupsModel)))
      val res = await(service.returnGroups(regId))
      res shouldBe Some(validGroupsModel)
    }
    "return None if db returns nothing" in new Setup {
      when(mockCTDataRepository.returnGroupsBlock(eqTo(regId))).thenReturn(Future.successful(None))
      val res = await(service.returnGroups(regId))
      res shouldBe None
    }

  }

  "deleteGroups" should {
    "return true if delete was successful" in new Setup {
      when(mockCTDataRepository.deleteGroupsBlock(eqTo(regId))).thenReturn(Future.successful(true))
      val res = await(service.deleteGroups(regId))
      res shouldBe true
    }
    "return future failed if delete was unsuccessful db returned an exception" in new Setup {
      when(mockCTDataRepository.deleteGroupsBlock(eqTo(regId))).thenReturn(Future.failed(new Exception("foo")))
      intercept[Exception](await(service.deleteGroups(regId)))
    }
  }

  "updateGroups" should {
    "return success of groups when db returns a success" in new Setup {
      when(mockCTDataRepository.updateGroups(eqTo(regId), eqTo(validGroupsModel))).thenReturn(Future.successful(validGroupsModel))
      val res = await(service.updateGroups(regId, validGroupsModel))
      res shouldBe validGroupsModel
    }
    "return a future failed if db returns an exception" in new Setup {
      when(mockCTDataRepository.updateGroups(eqTo(regId), eqTo(validGroupsModel))).thenReturn(Future.failed(new Exception("")))
        intercept[Exception](await(service.updateGroups(regId,validGroupsModel)))
    }
  }
}