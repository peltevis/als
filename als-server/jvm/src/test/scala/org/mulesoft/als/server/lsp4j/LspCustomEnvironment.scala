package org.mulesoft.als.server.lsp4j

import amf.core.client.common.remote.Content
import amf.core.client.platform.resource.ClientResourceLoader
import amf.core.internal.convert.CoreClientConverters._
import amf.core.internal.unsafe.PlatformSecrets
import org.eclipse.lsp4j.{DidOpenTextDocumentParams, TextDocumentItem, TraceValue}
import org.mulesoft.als.configuration.ResourceLoaderConverter
import org.mulesoft.als.logger.EmptyLogger
import org.mulesoft.als.server.client.platform.AlsLanguageServerFactory
import org.mulesoft.als.server.lsp4j.extension.AlsInitializeParams
import org.mulesoft.als.server.modules.diagnostic.ALL_TOGETHER
import org.mulesoft.als.server.{Flaky, MockDiagnosticClientNotifier}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.CompletableFuture
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.Future
class LspCustomEnvironment extends AsyncFunSuite with Matchers with PlatformSecrets {

  test("test custom environment", Flaky) {

    var calledRL = false
    val cl = new ClientResourceLoader {
      override def fetch(resource: String): CompletableFuture[Content] = {
        calledRL = true
        CompletableFuture.completedFuture(new Content("#%RAML 1.0 DataType\ntype: string", "jar:/api.raml"))
      }

      override def accepts(resource: String): Boolean = true
    }

    val notifier = new MockDiagnosticClientNotifier(4000)
    val server = new LanguageServerImpl(
      new AlsLanguageServerFactory(notifier)
        .withNotificationKind(ALL_TOGETHER)
        .withLogger(EmptyLogger)
        .withResourceLoaders(Seq(cl).asJava)
        .build()
    )
    val api =
      """#%RAML 1.0
        |title: test
        |types:
        | A: !include jar:/api.raml
        |""".stripMargin
    val initParams = new AlsInitializeParams()
    initParams.setTrace(TraceValue.Off)
    for {
      _ <- server.initialize(initParams).toScala
      _ <- Future {
        server.getTextDocumentService.didOpen(
          new DidOpenTextDocumentParams(new TextDocumentItem("file://api.raml", "raml1.0", 1, api))
        )
      }
      r1 <- notifier.nextCall
      r2 <- notifier.nextCall
    } yield {
      r2.uri should be("jar:/api.raml")
      calledRL should be(true)
    }
  }

  test("Test client resource loader conversion") {
    val cl = new ClientResourceLoader {
      override def fetch(resource: String): CompletableFuture[Content] = {
        CompletableFuture.completedFuture(new Content("#%RAML 1.0 DataType\ntype: string", "jar:/api.raml"))
      }

      override def accepts(resource: String): Boolean = true
    }

    val int = ResourceLoaderConverter.internalResourceLoader(cl)

    ResourceLoaderMatcher.asClient(int)

    succeed
  }
}
