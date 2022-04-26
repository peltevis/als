package org.mulesoft.als.suggestions.test.raml10

import amf.aml.client.scala.AMLConfiguration
import amf.aml.client.scala.model.document.Dialect
import amf.apicontract.client.scala.RAMLConfiguration
import amf.core.client.scala.config.{CachedReference, UnitCache}
import amf.core.client.scala.{AMFGraphConfiguration, AMFParseResult}
import amf.core.client.scala.model.document.{BaseUnit, Module}
import amf.core.client.scala.resource.ResourceLoader
import amf.core.client.scala.validation.AMFValidationResult
import org.mulesoft.als.configuration.ProjectConfiguration
import org.mulesoft.als.suggestions.test.{BaseSuggestionsForTest, SuggestionsTest}
import org.mulesoft.amfintegration.ValidationProfile
import org.mulesoft.amfintegration.amfconfiguration.{
  ALSConfigurationState,
  EditorConfigurationState,
  ProjectConfigurationState
}
import org.mulesoft.lsp.feature.completion.CompletionItem
import org.scalatest.{AsyncFunSuite, Matchers}
import org.mulesoft.amfintegration.AmfImplicits._
import scala.concurrent.{ExecutionContext, Future}

class AliasedSemexSuggestionTest extends AsyncFunSuite with BaseSuggestionsForTest with Matchers {
  def rootPath: String                                     = "file://als-suggestions/shared/src/test/resources/test/raml10/aliased-semex/"
  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  private def suggest(api: String): Future[Seq[CompletionItem]] = {
    for {
      d <- AMLConfiguration.predefined().baseUnitClient().parseDialect(rootPath + "dialect.yaml")
      l <- RAMLConfiguration.RAML10().baseUnitClient().parseLibrary(rootPath + "companion.raml")
      c <- platform.fetchContent(rootPath + api, AMFGraphConfiguration.predefined())
      ci <- {
        l.library.withReferences(Seq(d.dialect.cloneUnit()))
        val projectState = TestProjectConfigurationState(
          d.dialect,
          new ProjectConfiguration(rootPath,
                                   Some(api),
                                   Set(rootPath + "companion.raml"),
                                   Set.empty,
                                   Set(rootPath + "dialect.yaml"),
                                   Set.empty),
          l.library
        )
        val state = ALSConfigurationState(EditorConfigurationState.empty, projectState, None)

        suggestFromFile(c.stream.toString, rootPath + api, "*", state)
      }
    } yield ci
  }

  test("test semex suggestion - empty") {
    suggest("empty-semex.raml").map { ci =>
      ci.length shouldBe (1)
      ci.head.label shouldBe ("(lib.")
    }
  }

  test("test aliased value semex suggestion") {
    suggest("aliased-value-semex.raml").map { ci =>
      ci.length shouldBe (1)
      ci.head.label shouldBe ("key)")
      ci.head.detail.get shouldBe ("extensions")
    }
  }

  test("test properties suggestion  of aliased semex") {
    suggest("semex-content.raml").map { ci =>
      ci.length shouldBe (2)
      val namePro = ci.find(_.label == "name")
      namePro.isDefined shouldBe (true)
      val keyProp = ci.find(_.label == "key")
      keyProp.isDefined shouldBe (true)
    }
  }

  test("test to not suggest local annotations overridden by semex") {
    suggest("local-annotation-other-target.raml").map { ci =>
      ci.length shouldBe (0)
    }
  }
}

case class TestProjectConfigurationState(d: Dialect, override val config: ProjectConfiguration, lib: Module)
    extends ProjectConfigurationState {
  override def cache: UnitCache = new UnitCache {
    val map: Map[String, BaseUnit] = {
      val clone = lib.cloneUnit()
      Map(clone.identifier -> clone)
    }
    override def fetch(url: String): Future[CachedReference] = map.get(url) match {
      case Some(bu) => Future.successful(CachedReference(url, bu))
      case _        => throw new Exception("Unit not found")
    }
  }

  override val extensions: Seq[Dialect]                = Seq(d)
  override val profiles: Seq[ValidationProfile]        = Nil
  override val results: Seq[AMFParseResult]            = Nil
  override val resourceLoaders: Seq[ResourceLoader]    = Nil
  override val projectErrors: Seq[AMFValidationResult] = Nil
}
