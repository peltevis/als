package org.mulesoft.als.suggestions.test.raml10

import amf.aml.client.scala.AMLConfiguration
import amf.aml.client.scala.model.document.Dialect
import amf.apicontract.client.scala.RAMLConfiguration
import amf.core.client.scala.AMFGraphConfiguration
import amf.core.client.scala.model.document.{BaseUnit, Module}
import org.mulesoft.als.configuration.ProjectConfiguration
import org.mulesoft.als.suggestions.test.{BaseSuggestionsForTest, SuggestionsTest}
import org.mulesoft.amfintegration.amfconfiguration.{
  ALSConfigurationState,
  EditorConfigurationState,
  ProjectConfigurationState
}
import org.mulesoft.lsp.feature.completion.CompletionItem
import org.scalatest.{AsyncFunSuite, Matchers}

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
    extends ProjectConfigurationState(Seq(d), Nil, config, Nil, Nil, Nil) {
  override def cache: Seq[BaseUnit] = Seq(lib.cloneUnit())
}
