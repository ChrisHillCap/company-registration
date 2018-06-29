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

package utils

import java.text.Normalizer.Form
import java.text.Normalizer._

import models.validation.APIValidation

import scala.util.matching.Regex

object StringNormaliser {
  val specialCharacterConverts = Map('æ' -> "ae", 'Æ' -> "AE", 'œ' -> "oe", 'Œ' -> "OE", 'ß' -> "ss", 'ø' -> "o", 'Ø' -> "O")
  def normaliseString(string : String, charFilter : Regex) : String = {
    normalize(string, Form.NFKD)
      .replaceAll("\\p{M}", "")
      .trim
      .map(char => specialCharacterConverts.getOrElse(char, char))
      .mkString
      .filter(c => c.toString matches charFilter.regex)
  }
}