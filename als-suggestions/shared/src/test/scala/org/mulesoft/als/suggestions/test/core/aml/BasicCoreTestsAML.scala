package org.mulesoft.als.suggestions.test.core.aml

import amf.core.remote.Aml
import org.mulesoft.als.suggestions.CompletionPluginsRegistryAML
import org.mulesoft.als.suggestions.client.Suggestions
import org.mulesoft.als.suggestions.test.core.{CoreTest, DummyPlugins}
import org.mulesoft.high.level.InitOptions
import org.mulesoft.typesystem.definition.system.{OasCommonMediaTypes, OasResponseCodes}

import scala.concurrent.Future

class BasicCoreTestsAML extends CoreTest with DummyPlugins {

  def rootPath: String = "AML/demo"

  def format: String = Aml.toString

  test("full root structure") {
    this.runTestForCustomDialect("visit01.yaml", "dialect.yaml", Set("office", "date", "meetings"))
  }

  test("some root structure") {
    this.runTestForCustomDialect("visit02.yaml", "dialect.yaml", Set("date", "meetings"))
  }

  test("root structure with prefix") {
    this.runTestForCustomDialect("visit03.yaml", "dialect.yaml", Set("office"))
  }

  test("Known Values - Response Codes") {
    this.runTestForCustomDialect("otherDialects/responseCodes.yaml",
                                 "otherDialects/knownValuesDialect.yaml",
                                 OasResponseCodes.all.toSet)
  }

  test("Known Values - Media Types") {
    this.runTestForCustomDialect("otherDialects/mediaTypes.yaml",
                                 "otherDialects/knownValuesDialect.yaml",
                                 OasCommonMediaTypes.all.toSet)
  }

  test("Custom Plugins completion Dummy") {

    for {
      _ <- parseAMF(filePath("dialect.yaml"))
      _ <- Suggestions.init(InitOptions.AllProfiles)
      _ <- Future {
        CompletionPluginsRegistryAML.cleanPlugins()
        Seq(DummyCompletionPlugin(), DummyInvalidCompletionPlugin())
          .foreach(CompletionPluginsRegistryAML.registerPlugin)
      }
      result <- suggest("visit01.yaml")
    } yield assert(result.length == 1 && result.forall(_.description == "dummy description"))
  }
}
