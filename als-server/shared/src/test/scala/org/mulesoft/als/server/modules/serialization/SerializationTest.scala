package org.mulesoft.als.server.modules.serialization

import amf.apicontract.client.scala.WebAPIConfiguration
import amf.apicontract.client.scala.model.domain.api.WebApi
import amf.core.client.common.remote.Content
import amf.core.client.scala.AMFGraphConfiguration
import amf.core.client.scala.errorhandling.IgnoringErrorHandler
import amf.core.client.scala.model.document.{BaseUnit, Document}
import amf.core.client.scala.model.domain.Shape
import amf.core.client.scala.resource.ResourceLoader
import amf.core.internal.plugins.syntax.SyamlAMFErrorHandler
import amf.graphql.client.scala.GraphQLConfiguration
import org.mulesoft.als.common.diff.Diff.makeString
import org.mulesoft.als.common.diff.{Diff, FileAssertionTest, Tests}
import org.mulesoft.als.server._
import org.mulesoft.als.server.client.platform.ClientNotifier
import org.mulesoft.als.server.client.scala.LanguageServerBuilder
import org.mulesoft.als.server.feature.serialization.SerializationClientCapabilities
import org.mulesoft.als.server.modules.{WorkspaceManagerFactory, WorkspaceManagerFactoryBuilder}
import org.mulesoft.als.server.protocol.LanguageServer
import org.mulesoft.als.server.protocol.configuration.{AlsClientCapabilities, AlsInitializeParams}
import org.mulesoft.als.server.protocol.textsync.DidFocusParams
import org.mulesoft.als.server.workspace.ChangesWorkspaceConfiguration
import org.mulesoft.amfintegration.amfconfiguration.EditorConfiguration
import org.mulesoft.lsp.configuration.TraceKind
import org.mulesoft.lsp.feature.common.TextDocumentItem
import org.mulesoft.lsp.textsync.DidOpenTextDocumentParams
import org.scalatest.compatible.Assertion
import org.yaml.builder.{DocBuilder, JsonOutputBuilder}
import org.yaml.model.{YDocument, YMap, YSequence}
import org.yaml.parser.YamlParser

import java.io.StringWriter
import scala.concurrent.{ExecutionContext, Future}

class SerializationTest extends LanguageServerBaseTest with ChangesWorkspaceConfiguration with FileAssertionTest {

  override implicit val executionContext: ExecutionContext =
    ExecutionContext.Implicits.global

  override val initializeParams: AlsInitializeParams = AlsInitializeParams(
    Some(AlsClientCapabilities(serialization = Some(SerializationClientCapabilities(true)))),
    Some(TraceKind.Off)
  )

  test("Parse Model and check serialized json ld notification") {
    val alsClient: MockAlsClientNotifier = new MockAlsClientNotifier
    val serializationProps: SerializationProps[StringWriter] =
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    withServer(buildServer(serializationProps)) { server =>
      val content =
        """#%RAML 1.0
          |title: test
          |description: missing title
          |""".stripMargin

      val api = "file://api.raml"

      for {
        _ <- openFile(server)(api, content)
        s <- alsClient.nextCall.map(_.model.toString)
        parsed <- {
          val rl = new ResourceLoader {

            /** Fetch specified resource and return associated content. Resource should have benn previously accepted.
              */
            override def fetch(resource: String): Future[Content] =
              Future.successful(new Content(s, api))

            /** Accepts specified resource. */
            override def accepts(resource: String): Boolean = resource == api
          }
          WebAPIConfiguration
            .WebAPI()
            .withResourceLoader(rl)
            .baseUnitClient()
            .parse(api)
        }
      } yield {
        assertSimpleApi(parsed.baseUnit)
      }
    }
  }

  test("Request serialized model") {
    val alsClient: MockAlsClientNotifier = new MockAlsClientNotifier
    val serializationProps: SerializationProps[StringWriter] =
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    withServer(buildServer(serializationProps)) { server =>
      val content =
        """#%RAML 1.0
          |title: test
          |description: missing title
          |""".stripMargin

      val api = "file://api.raml"
      openFile(server)(api, content)

      for {
        _ <- alsClient.nextCall.map(_.model.toString)
        s <- serialize(server, api, serializationProps)
        parsed <- {
          val rl = new ResourceLoader {

            /** Fetch specified resource and return associated content. Resource should have benn previously accepted.
              */
            override def fetch(resource: String): Future[Content] =
              Future.successful(new Content(s, api))

            /** Accepts specified resource. */
            override def accepts(resource: String): Boolean = resource == api
          }
          WebAPIConfiguration
            .WebAPI()
            .withResourceLoader(rl)
            .baseUnitClient()
            .parse(api)
        }
      } yield {
        assertSimpleApi(parsed.baseUnit)
      }
    }
  }

  test("Request serialized model twice and change") {
    val alsClient: MockAlsClientNotifier = new MockAlsClientNotifier
    val serializationProps: SerializationProps[StringWriter] =
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    withServer(buildServer(serializationProps)) { server =>
      val content =
        """#%RAML 1.0
          |title: test
          |description: missing title
          |""".stripMargin

      val api = "file://api.raml"
      openFile(server)(api, content)

      for {
        _                    <- alsClient.nextCall.map(_.model.toString)
        s                    <- serialize(server, api, serializationProps)
        parsed               <- parsedApi(api, s)
        s2                   <- serialize(server, api, serializationProps)
        fromSerialization    <- parsedApi(api, s2)
        _                    <- changeFile(server)(api, "", 1)
        s3                   <- serialize(server, api, serializationProps)
        fromJsonLD           <- parsedApi(api, s3)
        _                    <- changeFile(server)(api, content, 2)
        s4                   <- serialize(server, api, serializationProps)
        serializedSecondTime <- parsedApi(api, s4)
      } yield {
        assertSimpleApi(parsed.baseUnit)
        assertSimpleApi(fromSerialization.baseUnit)
        fromJsonLD.baseUnit.isInstanceOf[Document] should be(false)
        assertSimpleApi(serializedSecondTime.baseUnit)

      }
    }
  }

  test("Request serialized model in json schema en draft-04", Flaky) {
    val alsClient: MockAlsClientNotifier = new MockAlsClientNotifier
    val serializationProps: SerializationProps[StringWriter] =
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    withServer(buildServer(serializationProps)) { server =>
      val api: String = mockJsonSchemaContent(server)

      for {
        _      <- alsClient.nextCall.map(_.model.toString)
        s      <- serialize(server, api, serializationProps)
        parsed <- parsedApi(api, s)
      } yield {
        assertSimpleJsonSchema(parsed.baseUnit)
      }
    }
  }

  test("Request serialized, parsed and re-serialized model in json schema en draft-04") {
    val alsClient: MockAlsClientNotifier = new MockAlsClientNotifier
    val serializationProps: SerializationProps[StringWriter] =
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    withServer(buildServer(serializationProps)) { server =>
      val api: String = mockJsonSchemaContent(server)

      for {
        _                 <- alsClient.nextCall.map(_.model.toString)
        s                 <- serialize(server, api, serializationProps)
        parsed            <- parsedApi(api, s)
        s2                <- serialize(server, api, serializationProps)
        fromSerialization <- parsedApi(api, s2)
      } yield {
        assertSimpleJsonSchema(parsed.baseUnit)
        assertSimpleJsonSchema(fromSerialization.baseUnit)
      }
    }
  }

  private def assertSimpleApi(bu: BaseUnit): Assertion = {
    bu.isInstanceOf[Document] shouldBe true
    bu.asInstanceOf[Document].encodes.asInstanceOf[WebApi].name.value() shouldBe "test"
  }

  private def assertSimpleJsonSchema(bu: BaseUnit): Assertion = {
    bu.isInstanceOf[Document] shouldBe true
    bu.asInstanceOf[Document].encodes.asInstanceOf[Shape].name.value() shouldBe "schema"
  }

  private def mockJsonSchemaContent(server: LanguageServer) = {
    val content =
      """{
        |  "type" : "object",
        |  "$schema" : "http://json-schema.org/draft-04/schema",
        |  "required" : ["object2"],
        |  "properties" : {
        |    "object" : {
        |      "type" : "object",
        |      "required" : ["a", "c"],
        |      "properties" : {
        |        "a" : {
        |          "type" : "string"
        |        },
        |        "c" : {
        |          "type" : "string"
        |        }
        |      }
        |    },
        |    "object2" : {
        |      "type" : "string"
        |    }
        |  }
        |}""".stripMargin

    val api = "file://api.json"
    openFile(server)(api, content)
    api
  }

  private def parsedApi(api: String, s: String) = {

    val rl = new ResourceLoader {

      /** Fetch specified resource and return associated content. Resource should have benn previously accepted. */
      override def fetch(resource: String): Future[Content] =
        Future.successful(new Content(s, api))

      /** Accepts specified resource. */
      override def accepts(resource: String): Boolean = resource == api
    }
    WebAPIConfiguration
      .WebAPI()
      .withResourceLoader(rl)
      .baseUnitClient()
      .parse(api)

  }

  test("basic test", Flaky) {
    val alsClient: MockAlsClientNotifier = new MockAlsClientNotifier
    val serializationProps: SerializationProps[StringWriter] =
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    withServer(buildServer(serializationProps)) { server =>
      val url = filePath("raml-endpoint-sorting.raml")

      for {
        _ <- platform.fetchContent(url, AMFGraphConfiguration.predefined()).flatMap { c =>
          server.textDocumentSyncConsumer.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(url, "RAML", 0, c.stream.toString))
          ) // why clean empty lines was necessary?
        }
        s <- serialize(server, url, serializationProps)
        parsed <- {
          val rl = new ResourceLoader {

            /** Fetch specified resource and return associated content. Resource should have benn previously accepted.
              */
            override def fetch(resource: String): Future[Content] =
              Future.successful(new Content(s, url))

            /** Accepts specified resource. */
            override def accepts(resource: String): Boolean = resource == url
          }
          WebAPIConfiguration
            .WebAPI()
            .withResourceLoader(rl)
            .baseUnitClient()
            .parse(url)
        }
      } yield {
        parsed.baseUnit.asInstanceOf[Document].encodes.id should be("amf://id#2")
      }
    }
  }

  test("GraphQL test", Flaky) {
    val alsClient: MockAlsClientNotifier = new MockAlsClientNotifier
    val serializationProps: SerializationProps[StringWriter] =
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    withServer(buildServer(serializationProps)) { server =>
      val url = filePath("api.graphql")

      for {
        _ <- platform.fetchContent(url, AMFGraphConfiguration.predefined()).flatMap { c =>
          server.textDocumentSyncConsumer.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(url, "GraphQL", 0, c.stream.toString))
          ) // why clean empty lines was necessary?
        }
        s <- serialize(server, url, serializationProps)
        parsed <- {
          val rl = new ResourceLoader {

            /** Fetch specified resource and return associated content. Resource should have benn previously accepted.
              */
            override def fetch(resource: String): Future[Content] =
              Future.successful(new Content(s, url))

            /** Accepts specified resource. */
            override def accepts(resource: String): Boolean = resource == url
          }
          GraphQLConfiguration
            .GraphQL()
            .withResourceLoader(rl)
            .baseUnitClient()
            .parse(url)
        }
      } yield {
        parsed.baseUnit.asInstanceOf[Document].encodes.id should be("amf://id#18")
        assert(s.contains("\"core:name\": \"Query.allPersons\""))
      }
    }
  }

  test("two requests") {
    val alsClient: MockAlsClientNotifier = new MockAlsClientNotifier
    val serializationProps: SerializationProps[StringWriter] =
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    withServer(buildServer(serializationProps)) { server =>
      val url = filePath("raml-endpoint-sorting.raml")

      for {
        c <- platform.fetchContent(url, AMFGraphConfiguration.predefined())
        _ <- server.textDocumentSyncConsumer.didOpen(
          DidOpenTextDocumentParams(TextDocumentItem(url, "RAML", 0, c.stream.toString))
        ) // why clean empty lines was necessary?
        s <- serialize(server, url, serializationProps)
        parsed <- {
          val rl = new ResourceLoader {

            /** Fetch specified resource and return associated content. Resource should have benn previously accepted.
              */
            override def fetch(resource: String): Future[Content] =
              Future.successful(new Content(s, url))

            /** Accepts specified resource. */
            override def accepts(resource: String): Boolean = resource == url
          }
          WebAPIConfiguration
            .WebAPI()
            .withResourceLoader(rl)
            .baseUnitClient()
            .parse(url)
        }
        s2 <- serialize(server, url, serializationProps)
        parsed2 <- {
          val rl = new ResourceLoader {

            /** Fetch specified resource and return associated content. Resource should have benn previously accepted.
              */
            override def fetch(resource: String): Future[Content] =
              Future.successful(new Content(s2, url))

            /** Accepts specified resource. */
            override def accepts(resource: String): Boolean = resource == url
          }
          WebAPIConfiguration
            .WebAPI()
            .withResourceLoader(rl)
            .baseUnitClient()
            .parse(url)
        }
      } yield {
        parsed.baseUnit.asInstanceOf[Document].encodes.id should be("amf://id#2")
        parsed2.baseUnit.asInstanceOf[Document].encodes.id should be("amf://id#2")
      }
    }
  }

  test("Files outside tree shouldn't overwrite main file cache") {
    val alsClient: MockAlsClientNotifier = new MockAlsClientNotifier
    val serializationProps: SerializationProps[StringWriter] =
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    withServer(buildServer(serializationProps)) { server =>
      val mainUrl      = filePath("project/librarybooks.raml")
      val extensionUrl = filePath("project/extension.raml")
      val overlayUrl   = filePath("project/overlay.raml")

      for {
        _ <- server.testInitialize(
          AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(s"${filePath("project")}"))
        )
        _ <- platform
          .fetchContent(mainUrl, AMFGraphConfiguration.predefined())
          .flatMap(c =>
            server.textDocumentSyncConsumer
              .didOpen(DidOpenTextDocumentParams(TextDocumentItem(mainUrl, "RAML", 0, c.stream.toString)))
          )
        mainSerialized1 <- serialize(server, mainUrl, serializationProps)
        _ <- platform
          .fetchContent(extensionUrl, AMFGraphConfiguration.predefined())
          .flatMap(c => {
            server.textDocumentSyncConsumer
              .didOpen(DidOpenTextDocumentParams(TextDocumentItem(extensionUrl, "RAML", 0, c.stream.toString)))
              .flatMap(_ => server.textDocumentSyncConsumer.didFocus(DidFocusParams(extensionUrl, 0)))
          })
        extensionSerialized <- serialize(server, extensionUrl, serializationProps)
        _                   <- server.textDocumentSyncConsumer.didFocus(DidFocusParams(mainUrl, 0))
        mainSerialized2     <- serialize(server, mainUrl, serializationProps)
        _ <- platform
          .fetchContent(overlayUrl, AMFGraphConfiguration.predefined())
          .flatMap(c => {
            server.textDocumentSyncConsumer
              .didOpen(DidOpenTextDocumentParams(TextDocumentItem(overlayUrl, "RAML", 0, c.stream.toString)))
              .flatMap(_ => server.textDocumentSyncConsumer.didFocus(DidFocusParams(overlayUrl, 0)))
          })
        overlaySerialized <- serialize(server, overlayUrl, serializationProps)
        _                 <- server.textDocumentSyncConsumer.didFocus(DidFocusParams(mainUrl, 0))
        mainSerialized3   <- serialize(server, mainUrl, serializationProps)
      } yield {
        (mainSerialized1 == mainSerialized2) should be(true)
        (mainSerialized2 == mainSerialized3) should be(true)
        (extensionSerialized != mainSerialized1) should be(true)
        (extensionSerialized != overlaySerialized) should be(true)
        (overlaySerialized != mainSerialized1) should be(true)
      }
    }
  }

  test("Files inside tree should respond subset") {
    val alsClient: MockAlsClientNotifier = new MockAlsClientNotifier
    val serializationProps: SerializationProps[StringWriter] =
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    val mainUrl     = filePath("tree/main.raml")
    val library     = filePath("tree/library.raml")
    val datatype    = filePath("tree/type.raml")
    val folder      = filePath("tree")
    val initialArgs = changeConfigArgs(Some("main.raml"), folder)
    withServer(
      buildServer(serializationProps),
      AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(folder))
    ) { server =>
      for {
        _                  <- changeWorkspaceConfiguration(server)(initialArgs)
        mainSerialized     <- serialize(server, mainUrl, serializationProps)
        librarySerialized  <- serialize(server, library, serializationProps)
        datatypeSerialized <- serialize(server, datatype, serializationProps)
      } yield {
        assert(mainSerialized.contains("\"Main API\""))
        assert(datatypeSerialized.contains("\"doc:Fragment\""))
        assert(!datatypeSerialized.contains("doc:declares"))
        assert(!datatypeSerialized.contains("\"Main API\""))
        assert(librarySerialized.contains("\"doc:Fragment\""))
        assert(librarySerialized.contains("doc:declares"))
        assert(!librarySerialized.contains("\"Main API\""))
      }
    }
  }

  test("Files outside tree shouldn't overwrite main file cache - overlay main file") {
    val alsClient: MockAlsClientNotifier = new MockAlsClientNotifier
    val serializationProps: SerializationProps[StringWriter] =
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    withServer(buildServer(serializationProps)) { server =>
      val mainFile     = "overlay.raml"
      val mainUrl      = filePath("project-overlay-mf/librarybooks.raml")
      val extensionUrl = filePath("project-overlay-mf/extension.raml")
      val overlayUrl   = filePath("project-overlay-mf/overlay.raml")

      for {
        _ <- server.testInitialize(
          AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(s"${filePath("project-overlay-mf")}"))
        )
        _ <- setMainFile(server)(filePath("project-overlay-mf"), mainFile)
        _ <- platform
          .fetchContent(overlayUrl, AMFGraphConfiguration.predefined())
          .flatMap(c => {
            server.textDocumentSyncConsumer
              .didOpen(DidOpenTextDocumentParams(TextDocumentItem(overlayUrl, "RAML", 0, c.stream.toString)))
              .flatMap(_ => server.textDocumentSyncConsumer.didFocus(DidFocusParams(overlayUrl, 0)))
          })
        overlaySerialized <- serialize(server, overlayUrl, serializationProps)
        _                 <- Future(server.textDocumentSyncConsumer.didFocus(DidFocusParams(mainUrl, 0)))
        _ <- platform
          .fetchContent(mainUrl, AMFGraphConfiguration.predefined())
          .flatMap(c =>
            server.textDocumentSyncConsumer
              .didOpen(DidOpenTextDocumentParams(TextDocumentItem(mainUrl, "RAML", 0, c.stream.toString)))
          )
        mainSerialized1 <- serialize(server, mainUrl, serializationProps)
        _ <- platform
          .fetchContent(extensionUrl, AMFGraphConfiguration.predefined())
          .flatMap(c => {
            server.textDocumentSyncConsumer
              .didOpen(DidOpenTextDocumentParams(TextDocumentItem(extensionUrl, "RAML", 0, c.stream.toString)))
              .flatMap(_ => server.textDocumentSyncConsumer.didFocus(DidFocusParams(extensionUrl, 0)))
          })
        extensionSerialized <- serialize(server, extensionUrl, serializationProps)
        _                   <- server.textDocumentSyncConsumer.didFocus(DidFocusParams(mainUrl, 0))
        mainSerialized2     <- serialize(server, mainUrl, serializationProps)
        _                   <- server.textDocumentSyncConsumer.didFocus(DidFocusParams(overlayUrl, 0))
        overlaySerialized2  <- serialize(server, overlayUrl, serializationProps)
      } yield {
        Tests.checkDiff(mainSerialized2, mainSerialized1)
        val diffs = Diff.trimming.ignoreEmptyLines.diff(overlaySerialized, mainSerialized1)
        checkDiffsAreIrrelevant(diffs)
        Tests.checkDiff(overlaySerialized2, mainSerialized1)
        (extensionSerialized != overlaySerialized) should be(true)
      }
    }
  }

  /** ALS-1378 Checks if differences in the serialization result are irrelevant. This differences are generated by the
    * fact that the API is parsed from different contexts, and in some cases adds or removes empty fields. Even though
    * the resulting serialization are different when looked at as literal strings, the model still represents the same
    * model if the only fields missing or being added are empty, an thus should not fail.
    * @param diffs
    *   list of Diff.Delta containing the differences between the serializations
    */
  private def checkDiffsAreIrrelevant(diffs: List[Diff.Delta[String]]): Unit = {
    if (diffs.nonEmpty) {
      diffs.foreach(delta => {
        if (
          delta.t == Diff.Delete && delta.aLines.forall(line => {
            isEmptySequence(line)
          })
        ) {
          // "doc:declares": [],
          logger.warning("Missing empty sequence in serialization", "SerializationTest", "overlay main file")
        } else {
          logger.debug("Non-empty sequence in serialization", "SerializationTest", "overlay main file")
          fail(s"\n ${delta.t}" + makeString(diffs))
        }
      })
    }
  }

  private def isEmptySequence(line: String): Boolean = {
    val syamlEH = new SyamlAMFErrorHandler(IgnoringErrorHandler)
    YamlParser(line.replace(",", ""))(syamlEH)
      .parse(false)
      .collectFirst({ case d: YDocument => d })
      .map(_.as[YMap].entries.head.value.value)
      .exists({
        case seq: YSequence => seq.isEmpty
        case _              => false
      })
  }

  val main: String            = "api.raml"
  val goldenUrl: String       = filePath("custom-validation/profile-serialized-golden.jsonld")
  val editedGoldenUrl: String = filePath("custom-validation/profile-serialized-edited-golden.jsonld")
  val profileUrl: String      = filePath("custom-validation/profile.yaml")
  val workspace               = s"${filePath("custom-validation")}"

  test("Serialize registered validation profile", Flaky) {
    val alsClient: MockAlsClientNotifier       = new MockAlsClientNotifier
    val notifier: MockDiagnosticClientNotifier = new MockDiagnosticClientNotifier(3000)
    val initialArgs: String                    = changeConfigArgs(Some(main), workspace, Set.empty, Set(profileUrl))
    implicit val serializationProps: SerializationProps[StringWriter] = {
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    }
    val server = buildServer(serializationProps, notifier, withDiagnostics = true)
    withServer(server) { server =>
      for {
        _ <- server.testInitialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(workspace)))
        _ <- changeWorkspaceConfiguration(server)(initialArgs)
        _ <- notifier.nextCall
        r <- assertSerialization(server, profileUrl, goldenUrl)
      } yield r
    }
  }

  test("Serialize unregistered validation profile", Flaky) {
    val workspace                              = s"${filePath("custom-validation")}"
    val alsClient: MockAlsClientNotifier       = new MockAlsClientNotifier
    val notifier: MockDiagnosticClientNotifier = new MockDiagnosticClientNotifier(3000)
    implicit val serializationProps: SerializationProps[StringWriter] = {
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    }
    val server = buildServer(serializationProps, notifier, withDiagnostics = true)
    withServer(server) { server =>
      for {
        _       <- server.testInitialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(workspace)))
        content <- platform.fetchContent(profileUrl, AMFGraphConfiguration.predefined())
        _       <- openFile(server)(profileUrl, content.stream.toString)
        _       <- notifier.nextCall
        r       <- assertSerialization(server, profileUrl, goldenUrl)
      } yield r
    }
  }

  test("Serialize validation profile (editing workflow)") {
    val workspace                              = s"${filePath("custom-validation")}"
    val alsClient: MockAlsClientNotifier       = new MockAlsClientNotifier
    val notifier: MockDiagnosticClientNotifier = new MockDiagnosticClientNotifier(6000)
    val initialArgs: String                    = changeConfigArgs(Some(main), workspace, Set.empty, Set(profileUrl))
    implicit val serializationProps: SerializationProps[StringWriter] = {
      new SerializationProps[StringWriter](alsClient) {
        override def newDocBuilder(prettyPrint: Boolean): DocBuilder[StringWriter] =
          JsonOutputBuilder(prettyPrint)
      }
    }
    val server = buildServer(serializationProps, notifier, withDiagnostics = true)
    withServer(server) { server =>
      for {
        _       <- server.testInitialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(workspace)))
        _       <- changeWorkspaceConfiguration(server)(initialArgs)
        _       <- notifier.nextCall
        _       <- assertSerialization(server, profileUrl, goldenUrl)       // Registered profile
        content <- platform.fetchContent(profileUrl, AMFGraphConfiguration.predefined()).map(_.stream.toString)
        _       <- openFile(server)(profileUrl, content)
        _       <- changeFile(server)(profileUrl, content.replace("warning:\n  - ab", ""), 1)
        _       <- notifier.nextCall
        _       <- assertSerialization(server, profileUrl, editedGoldenUrl) // Edited profile
        _       <- closeFile(server)(profileUrl)
        r <- assertSerialization(server, profileUrl, goldenUrl) // Closed profile (back to the registered one)
      } yield r
    }
  }

  def assertSerialization(server: LanguageServer, url: String, golden: String)(implicit
      serializationProps: SerializationProps[StringWriter]
  ): Future[Assertion] =
    for {
      serialized <- serialize(server, url, serializationProps)
      tmp        <- writeTemporaryFile(golden)(serialized)
      r          <- assertDifferences(tmp, golden)
    } yield r

  def buildServer(
      serializationProps: SerializationProps[StringWriter],
      notifier: ClientNotifier = new MockDiagnosticClientNotifier,
      withDiagnostics: Boolean = false
  ): LanguageServer = {
    val factoryBuilder: WorkspaceManagerFactoryBuilder =
      new WorkspaceManagerFactoryBuilder(notifier, EditorConfiguration())
    val dm = factoryBuilder.buildDiagnosticManagers(Some(DummyProfileValidator))
    val serializationManager: SerializationManager[StringWriter] =
      factoryBuilder.serializationManager(serializationProps)
    val factory: WorkspaceManagerFactory = factoryBuilder.buildWorkspaceManagerFactory()

    val builder =
      new LanguageServerBuilder(
        factory.documentManager,
        factory.workspaceManager,
        factory.configurationManager,
        factory.resolutionTaskManager
      )
    builder.addInitializableModule(serializationManager)
    if (withDiagnostics) dm.foreach(m => builder.addInitializableModule(m))
    builder.addRequestModule(serializationManager)
    builder.build()
  }

  override def rootPath: String = "serialization"
}
