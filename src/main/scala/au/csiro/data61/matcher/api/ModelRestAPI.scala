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
package au.csiro.data61.matcher.api

import au.csiro.data61.matcher.types.ModelTypes.{Status, TrainState, ModelID, Model}
import au.csiro.data61.matcher._
import io.finch._
import org.joda.time.DateTime
import org.json4s.JValue
import org.json4s.JsonAST.JNothing
import org.json4s.jackson.JsonMethods._
import types._

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}



/**
 *  Model REST endpoints...
 *
 *  GET    /v1.0/model
 *  POST   /v1.0/model              -- json model object
 *  GET    /v1.0/model/:id
 *  GET    /v1.0/model/:id/train    -- returns async status obj
 *  GET    /v1.0/model/:id/predict  -- returns async status obj
 *  POST   /v1.0/model/:id          -- update
 *  DELETE /v1.0/model/:id
 */
object ModelRestAPI extends RestAPI {

  val TestModel = Model(
    description = "This is a model description",
    id = 0,
    modelType = ModelType.RANDOM_FOREST,
    classes = List("name", "address", "phone", "flight"),
    features = FeaturesConfig(activeFeatures = Set("num-unique-vals", "prop-unique-vals", "prop-missing-vals")
      ,activeGroupFeatures = Set("stats-of-text-length", "prop-instances-per-class-in-knearestneighbours")
      ,featureExtractorParams = Map(
        "prop-instances-per-class-in-knearestneighbours" -> Map(
          "name" -> "prop-instances-per-class-in-knearestneighbours",
          "num-neighbours" -> "5")
        )),
    costMatrix = List(
      List(1,0,0,0),
      List(0,1,0,0),
      List(0,0,1,0),
      List(0,0,0,1)),
    labelData = Map.empty[Int, String],
    resamplingStrategy = SamplingStrategy.RESAMPLE_TO_MEAN,
    refDataSets = List(1, 2, 3, 4),
    state = TrainState(Status.UNTRAINED, "", DateTime.now, DateTime.now),
    dateCreated = DateTime.now,
    dateModified = DateTime.now
  )

  /**
   * Returns all model ids
   *
   * curl http://localhost:8080/v1.0/model
   */
  val modelRoot: Endpoint[List[ModelID]] = get(APIVersion :: "model") {
    Ok(MatcherInterface.modelKeys)
  }

  /**
    * Updates cache for models
    *
    * curl http://localhost:8080/v1.0/model/cache
    */
  val cacheUpdate: Endpoint[List[ModelID]] = get(APIVersion :: "model" :: "cache") {
    Ok(MatcherInterface.updateModelKeys)
  }

  /**
   * Adds a new model as specified by the json body.
   *
   * {
   *   "description": "Testing model used for identifying phone numbers only."
   *   "modelType": "randomForest",
   *   "labels": ["name", "address", "phone", "unknown"],
   *   "features": ["isAlpha", "alphaRatio", "atSigns", ...]
   *   "training": {"type": "kFold", "n": 10},
   *   "costMatrix": [[1, 2, 3], [3, 4, 5], [6, 7, 8]],
   *   "resamplingStrategy": "resampleToMean",
   * }
   *
   * Returns a JSON Model object with id.
   *
   */
  val modelCreate: Endpoint[Model] = post(APIVersion :: "model" :: body) {
    (body: String) =>
      (for {
        request <- parseModelRequest(body)
        _ <- Try {
          request.classes match {
            case Some(x) if x.nonEmpty =>
              request
            case _ =>
              throw BadRequestException("No classes found.")
          }
        }
        _ <- Try {
          if (request.features.isEmpty) {
            throw BadRequestException("No features found.")
          }
        }
        m <- Try { MatcherInterface.createModel(request) }
      } yield m)
      match {
        case Success(mod) =>
          Ok(mod)
        case Failure(err: InternalException) =>
          InternalServerError(InternalException(err.getMessage))
        case Failure(err) =>
          BadRequest(BadRequestException(err.getMessage))
      }
  }

  /**
   * Returns a JSON Model object at id
   */
  val modelGet: Endpoint[Model] = get(APIVersion :: "model" :: int) {
    (id: Int) =>
      Try { MatcherInterface.getModel(id) } match {
        case Success(Some(ds))  =>
          Ok(ds)
        case Success(None) =>
          NotFound(NotFoundException(s"Model $id does not exist."))
        case Failure(err) =>
          BadRequest(BadRequestException(err.getMessage))
      }
  }

  /**
    * Trains a model at id
    * If training has been successfully launched, it returns nothing
    */
  val modelTrain: Endpoint[Unit] = get(APIVersion :: "model" :: int :: "train") {
    (id: Int) =>
      val state = Try(MatcherInterface.trainModel(id))
      state match {
        case Success(Some(_))  =>
          Accepted[Unit]
        case Success(None) =>
          NotFound(NotFoundException(s"Model $id does not exist."))
        case Failure(err) =>
          BadRequest(BadRequestException(err.getMessage))
      }
  }

  /**
   * Patch a portion of a Model. Will destroy all cached models
   */
  val modelPatch: Endpoint[Model] = post(APIVersion :: "model" :: int :: body) {
    (id: Int, body: String) =>
      (for {
        request <- parseModelRequest(body)
        model <- Try {
          MatcherInterface.updateModel(id, request)
        }
      } yield model)
      match {
        case Success(m) =>
          Ok(m)
        case Failure(err) =>
          InternalServerError(InternalException(err.getMessage))
      }
  }

  /**
   * Deletes the model at position id.
   */
  val modelDelete: Endpoint[String] = delete(APIVersion :: "model" :: int) {
    (id: Int) =>
      Try(MatcherInterface.deleteModel(id)) match {
        case Success(Some(_)) =>
          logger.debug(s"Deleted model $id")
          Ok(s"Model $id deleted successfully.")
        case Success(None) =>
          logger.debug(s"Could not find model $id")
          NotFound(NotFoundException(s"Model $id could not be found"))
        case Failure(err) =>
          logger.debug(s"Some other problem with deleting...")
          InternalServerError(InternalException(s"Failed to delete resource: ${err.getMessage}"))
      }
  }

  /**
    * Helper function to parse json objects. This will return None if
    * nothing is present, and throw a BadRequest error if it is incorrect,
    * and Some(T) if correct
    *
    * @param label The key for the object. Must be present in jValue
    * @param jValue The Json Object
    * @tparam T The return type
    * @return
    */
  private def parseOption[T: Manifest](label: String, jValue: JValue): Try[Option[T]] = {
    val jv = jValue \ label
    if (jv == JNothing) {
      Success(None)
    } else {
      Try {
        Some(jv.extract[T])
      } recoverWith {
        case err =>
          Failure(
            BadRequestException(s"Failed to parse: $label. Error: ${err.getMessage}"))
      }
    }
  }

  /**
   * Helper function to parse a string into a ModelRequest object...
   *
   * @param str The json string with the model request information
   * @return
   */
  private def parseModelRequest(str: String): Try[ModelRequest] = {

    for {
      raw <- Try { parse(str) }

      description <- parseOption[String]("description", raw)

      modelType <- parseOption[String]("modelType", raw)
                    .map(_.map(
                      ModelType.lookup(_)
                        .getOrElse(throw BadRequestException("Bad modelType"))))

      classes <- parseOption[List[String]]("classes", raw)

      features <- parseOption[FeaturesConfig]("features", raw)

      userData <- parseOption[Map[Int, String]]("labelData", raw)

      costMatrix <- parseOption[List[List[Double]]]("costMatrix", raw)

      resamplingStrategy <- parseOption[String]("resamplingStrategy", raw)
                              .map(_.map(
                                SamplingStrategy.lookup(_)
                                  .getOrElse(throw BadRequestException("Bad resamplingStrategy"))))
    } yield {
      ModelRequest(
        description,
        modelType,
        classes,
        features,
        costMatrix,
        userData,
        resamplingStrategy
      )}
  }

  /**
   * Final endpoints for the Model endpoint...
   */
  val endpoints =
    modelRoot :+:
      modelCreate :+:
      modelGet :+:
      modelTrain :+:
      modelPatch :+:
      modelDelete :+:
      cacheUpdate
}


case class ModelRequest(description: Option[String],
                        modelType: Option[ModelType],
                        classes: Option[List[String]],
                        features: Option[FeaturesConfig],
                        costMatrix: Option[List[List[Double]]],
                        labelData: Option[Map[Int, String]],
                        resamplingStrategy: Option[SamplingStrategy])