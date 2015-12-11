package com.ibm.watson.developercloud.language_translation.v2

import com.ibm.watson.developercloud.utils.{JsonUtils, Validation, WatsonService, WatsonServiceConfig}
import com.typesafe.scalalogging.LazyLogging
import spray.client.pipelining._
import spray.http._
import spray.json.{JsObject, JsString, JsValue}
import LanguageTranslationProtocol._
import spray.httpx.SprayJsonSupport._
import scala.concurrent.Future

/**
  * The IBM Watson Language Translation service translate text from one language to another and
  * identifies the language in which text is written.
  *
  * @version v2
  * @param config service config
  */
class LanguageTranslation(config: WatsonServiceConfig ) extends WatsonService(config) with LazyLogging {
  import system.dispatcher

  val MODEL_URL  = "/v2/models"
  val PATH_MODEL = "/v2/models/%s"
  val PATH_IDENTIFY: String = "/v2/identify"
  val PATH_TRANSLATE: String = "/v2/translate"
  val PATH_IDENTIFIABLE_LANGUAGES: String = "/v2/identifiable_languages"
  val PATH_MODELS: String = "/v2/models"
  def serviceType = "language_translation"

  /**
    * Retrieves a list of models
    * @return future of language models
    */
  def getModels : Future[LanguageModels] = getModels(Map())

  /**
    * Retrieves a translation models.
    * @param showDefault show default models
    * @param source the source language
    * @param target the target language
    * @return the translation models
    */
  def getModels(showDefault: Boolean, source: String, target: String) : Future[LanguageModels] = {

    val map = collection.mutable.Map[String, String]()

    Option(source) match {
      case Some(src) if src.nonEmpty => map.put(LanguageTranslation.Source, src)
      case _ =>
    }

    Option(target) match {
      case Some(trg) if trg.nonEmpty => map.put(LanguageTranslation.Target, trg)
      case _ =>
    }

    Option(showDefault) match {
      case Some(showDef) => map.put(LanguageTranslation.Default, showDef.toString)
      case _ =>
    }

    getModels(map.toMap)
  }

  /**
    * Retrieves the list of models.
    * @param params list of parameters to search for models
    * @return future
    */
  def getModels(params: Map[String,String]) : Future[LanguageModels] = {
    logger.info("Entering getModels")

    val request = Get(Uri(config.endpoint + MODEL_URL).withQuery(params))
    val response: Future[HttpResponse] = send(request)
    logger.info("Returning response")
    response.map(unmarshal[LanguageModels])
  }

  /**
    * Creates a translation models.
    *
    * @param options the create model options
    * @return the translation model
    */
  def createModel(options: CreateModelOptions) : Future[LanguageModel] = {
    var data: List[BodyPart] = List(BodyPart(options.baseModelId, formHeaders(LanguageTranslation.Name -> LanguageTranslation.BodyPart)))
    data = Option(options.forcedGlossary) match {
      case Some(glossary) => BodyPart(glossary, LanguageTranslation.ForcedGlossary) :: data
      case _ => data
    }

    data = Option(options.monlingualCorpus) match {
      case Some(corpus) => BodyPart(corpus, LanguageTranslation.MonolingualCorpus) :: data
      case _ => data
    }

    data = Option(options.parallelCorpus) match {
      case Some(parallelCorpus) => BodyPart(parallelCorpus, LanguageTranslation.ParallelCorpus) :: data
      case _ => data
    }

    data = Option(options.name) match {
      case Some(name) => BodyPart(name, LanguageTranslation.Name) :: data
      case _ => data
    }

    val formData = MultipartFormData(data)

    val request = Post(config.endpoint + PATH_MODELS, formData)
    send(request).map(unmarshal[LanguageModel])

  }

  /**
    * Deletes a model by ID
    * @param modelId identifier of model
    * @return future of HttpRequest
    */
  def deleteModel(modelId : String) : Future[HttpResponse] = {
    Validation.notEmpty(modelId, "Model ID cannot be empty")
    val request = Delete(config.endpoint + PATH_MODEL.format(modelId))
    send(request)
  }


  /**
    * Retrieves a list of identifiable languages
    * @return the list of identifiable languages
    */
  def getIdentifiableLanguages: Future[List[IdentifiableLanguage]] = {
    val request = Get(config.endpoint + PATH_IDENTIFIABLE_LANGUAGES)
    val response = send(request)
    response.map(unmarshal[List[IdentifiableLanguage]])
  }

  /**
    * Identifies language in which text is written.
    * @param text the text to identify
    * @return the identified language
    */
  def identify (text: String): Future[List[IdentifiedLanguage]] = {
    val request = Post(config.endpoint + PATH_IDENTIFY, text)
    val response = send(request)
    response.map(unmarshal[List[IdentifiedLanguage]])
  }

  /**
    * Translates text using model
    * @param text text to translate
    * @param modelId the model ID
    * @return translation result
    */
  def translate(text: String, modelId: String): Future[TranslationResult] = {
    Validation.notEmpty(modelId, "ModelId cannot be empty")
    translateRequest(text, modelId, null, null)
  }

  /**
    * Translate text using source and target languages
    * @param text text to translate
    * @param source source language
    * @param target target language
    * @return translated text
    */
  def translate(text: String, source: String, target: String) : Future[TranslationResult]= {
    Validation.notEmpty(source, "Source cannot be empty")
    Validation.notEmpty(target, "Target cannot be empty")
    translateRequest(text, null, source, target)
  }

  /**
    * Translate paragraphs of text using a model and or source and target. model_id or source and
    * target needs to be specified. If both are specified, then only model_id will be used
    *
    * @param text text to translate
    * @param modelId id of the model
    * @param source source language
    * @param target target language
    * @return translated text
    */
  def translateRequest(text: String, modelId: String, source: String, target: String) : Future[TranslationResult] = {
    Validation.notEmpty(text, "Text cannot be empty")
    var map: Map[String, JsValue] = Map(LanguageTranslation.Text -> JsString(text))
    map = JsonUtils.addIfNotEmpty(modelId, LanguageTranslation.ModelId, map)
    map = JsonUtils.addIfNotEmpty(source, LanguageTranslation.Source, map)
    map = JsonUtils.addIfNotEmpty(target, LanguageTranslation.Target, map)
    val jsonRequest = new JsObject(map)

    val response = send(Post(config.endpoint + PATH_TRANSLATE, jsonRequest.toString()))
    response.map(unmarshal[TranslationResult])
  }
}

object LanguageTranslation {
  val ModelId = "model_id"
  val Source = "source"
  val Target = "target"
  val Text = "text"
  val Name = "name"
  val ParallelCorpus = "parallel_corpus"
  val MonolingualCorpus = "monolingual_corpus"
  val ForcedGlossary = "forced_glossary"
  val BodyPart = "body_part"
  val Default = "default"
}