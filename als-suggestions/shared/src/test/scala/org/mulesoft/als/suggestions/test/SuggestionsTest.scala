package org.mulesoft.als.suggestions.test

import amf.ProfileName
import amf.core.client.ParserConfig
import amf.core.model.document.BaseUnit
import amf.internal.environment.Environment
import amf.internal.resource.ResourceLoader
import org.mulesoft.als.common.PlatformDirectoryResolver
import org.mulesoft.als.suggestions.CompletionProvider
import org.mulesoft.als.suggestions.client.{Suggestion, Suggestions}
import org.mulesoft.high.level.CustomDialects
import org.mulesoft.high.level.amfmanager.ParserHelper
import org.mulesoft.high.level.interfaces.IProject
import org.mulesoft.high.level.{CustomDialects, InitOptions}
import org.scalatest.{Assertion, AsyncFunSuite}

import scala.concurrent.{ExecutionContext, Future}

trait SuggestionsTest extends AsyncFunSuite with BaseSuggestionsForTest {

  implicit override def executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  def matchCategory(suggestion: Suggestion): Boolean = {
    suggestion.displayText.toLowerCase() match {
      case t if t == "securityschemes" =>
        suggestion.category.toLowerCase() == "security"
      case t if t == "baseuri" =>
        suggestion.category.toLowerCase() == "root"
      case t if t == "protocols" =>
        suggestion.category.toLowerCase() == "root"
      case t if t == "uses" =>
        suggestion.category.toLowerCase() == "unknown"
      case _ => true
    }
  }

  def assertCategory(path: String, suggestions: Set[Suggestion]): Assertion = {
    if (suggestions.forall(matchCategory))
      succeed
    else fail(s"Difference in categories for $path")
  }

  def assert(path: String, actualSet: Set[String], golden: Set[String]): Assertion = {
    def replaceEOL(s: String): String = {
      s.replace("\r\n", "\n")
    }

    val diff1 = actualSet
      .map(replaceEOL)
      .diff(golden.map(replaceEOL))

    val diff2 = golden
      .map(replaceEOL)
      .diff(actualSet.map(replaceEOL))

    diff1.foreach(println)
    diff2.foreach(println)
    if (diff1.isEmpty && diff2.isEmpty) succeed
    else
      fail(s"Difference for $path: got [${actualSet
        .mkString(", ")}] while expecting [${golden.mkString(", ")}]")
  }

  /**
    * @param path   URI for the API resource
    * @param label  Pointer placeholder
    * @param cut    if true, cuts text after label
    * @param labels set of every label in the file (needed for cleaning API)
    */
  def runTestCategory(path: String,
                      label: String = "*",
                      cut: Boolean = false,
                      labels: Array[String] = Array("*")): Future[Assertion] =
    this
      .suggest(path, label, cut, labels)
      .map(r => assertCategory(path, r.toSet))

  /**
    * @param path                URI for the API resource
    * @param originalSuggestions Expected result set
    * @param label               Pointer placeholder
    * @param cut                 if true, cuts text after label
    * @param labels              set of every label in the file (needed for cleaning API)
    */
  def runSuggestionTest(path: String,
                        originalSuggestions: Set[String],
                        label: String = "*",
                        cut: Boolean = false,
                        labels: Array[String] = Array("*"),
                        customDialect: Option[CustomDialects] = None): Future[Assertion] =
    this
      .suggest(path, label, cut, labels, customDialect)
      .map(r => assert(path, r.map(_.text).toSet, originalSuggestions))

  def withDialect(path: String,
                  originalSuggestions: Set[String],
                  dialectPath: String,
                  dialectProfile: ProfileName): Future[Assertion] = {
    platform.resolve(filePath(dialectPath)).flatMap { c =>
      runSuggestionTest(path,
                        originalSuggestions,
                        cut = false,
                        customDialect = Some(CustomDialects(dialectProfile, c.url, c.stream.toString)))
    }
  }

  def format: String

  def rootPath: String

  def suggest(path: String,
              label: String,
              cutTail: Boolean,
              labels: Array[String],
              customDialect: Option[CustomDialects] = None): Future[Seq[Suggestion]] = {
    super.suggest(filePath(path), format, customDialect)
  }

  case class ModelResult(u: BaseUnit, url: String, position: Int, originalContent: Option[String])

  def init(): Future[Unit] = org.mulesoft.als.suggestions.Core.init()

  def parseAMF(path: String, env: Environment = Environment()): Future[BaseUnit] = {
    val cfg = new ParserConfig(
      Some(ParserConfig.PARSE),
      Some(path),
      Some(format),
      Some("application/yaml"),
      None,
      Some("AMF Graph"),
      Some("application/ld+json")
    )

    val helper = ParserHelper(platform)
    helper.parse(cfg, env)
  }

  def buildParserConfig(language: String, url: String): ParserConfig =
    Suggestions.buildParserConfig(language, url)

  def buildHighLevel(model: BaseUnit): Future[IProject] =
    Suggestions.buildHighLevel(model, platform)

  def buildCompletionProvider(project: IProject,
                              url: String,
                              position: Int,
                              originalContent: String): CompletionProvider =
    Suggestions.buildCompletionProvider(project, url, position, originalContent, directoryResolver, platform)

  def buildCompletionProviderNoAST(text: String, url: String, position: Int): CompletionProvider =
    Suggestions.buildCompletionProviderNoAST(text, url, position, directoryResolver, platform)

  def filePath(path: String): String = {
    var result =
      s"file://als-suggestions/shared/src/test/resources/test/$rootPath/$path"
        .replace('\\', '/')
    result = result.replace("/null", "")
    result
  }

}
