/**
  * Copyright (C) 2015-2016 Data61, Commonwealth Scientific and Industrial Research Organisation (CSIRO).
  * See the LICENCE.txt file distributed with this work for additional
  * information regarding copyright ownership.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package au.csiro.data61.core.storage

import java.io._
import java.nio.file.{Path, Paths}

import au.csiro.data61.types.SSDTypes.{Owl, OwlID}
import au.csiro.data61.types._
import au.csiro.data61.core.Serene
import org.json4s.Formats
import org.json4s.jackson.JsonMethods._

import scala.language.postfixOps

/**
  * Object to store ontologies
  */
object OwlStorage extends Storage[OwlID, Owl] {
  implicit val keyReader: Readable[Int] = Readable.ReadableInt

  def rootDir: String = new File(Serene.config.ontologyStorageDir).getAbsolutePath

  def extract(stream: FileInputStream): Owl = {
    parse(stream).extract[Owl]
  }

}
