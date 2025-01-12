package org.mulesoft.als.server.workspace

import amf.aml.client.scala.AMLConfiguration
import amf.core.client.common.remote.Content
import amf.core.client.scala.resource.ResourceLoader
import org.mulesoft.als.configuration.ProjectConfiguration
import org.mulesoft.als.server.client.platform.ClientNotifier
import org.mulesoft.als.server.client.scala.LanguageServerBuilder
import org.mulesoft.als.server.modules.WorkspaceManagerFactoryBuilder
import org.mulesoft.als.server.protocol.LanguageServer
import org.mulesoft.als.server.protocol.configuration.AlsInitializeParams
import org.mulesoft.als.server.workspace.command.Commands
import org.mulesoft.als.server.{Flaky, LanguageServerBaseTest, MockDiagnosticClientNotifier}
import org.mulesoft.amfintegration.amfconfiguration.EditorConfiguration
import org.mulesoft.lsp.configuration.{TraceKind, WorkspaceFolder}
import org.mulesoft.lsp.feature.common.{Position, Range}
import org.mulesoft.lsp.feature.diagnostic.PublishDiagnosticsParams
import org.mulesoft.lsp.feature.telemetry.TelemetryMessage
import org.mulesoft.lsp.workspace.{ExecuteCommandParams, FileChangeType}
import org.scalatest.compatible.Assertion

import scala.concurrent.{ExecutionContext, Future}

class WorkspaceManagerTest extends LanguageServerBaseTest {

  override implicit val executionContext: ExecutionContext =
    ExecutionContext.Implicits.global

  private val profileUri: String  = "file://profile.yaml"
  private val profileUri2: String = "file://profile.yaml" // todo: why are this two the same??
  val fakeRl: ResourceLoader = new ResourceLoader {
    override def fetch(resource: String): Future[Content] =
      Future.successful(new Content("#%Validation Profile 1.0\nprofile: MyProfile", resource))

    override def accepts(resource: String): Boolean = resource == profileUri || resource == profileUri2
  }
  def buildServer(diagnosticClientNotifier: ClientNotifier): LanguageServer = {
    val builder =
      new WorkspaceManagerFactoryBuilder(
        diagnosticClientNotifier,
        EditorConfiguration.withPlatformLoaders(Seq(fakeRl))
      )

    val dm      = builder.buildDiagnosticManagers()
    val factory = builder.buildWorkspaceManagerFactory()

    val b = new LanguageServerBuilder(
      factory.documentManager,
      factory.workspaceManager,
      factory.configurationManager,
      factory.resolutionTaskManager
    )
    dm.foreach(m => b.addInitializableModule(m))
    b.addRequestModule(factory.structureManager)
    b.build()
  }

  test("Workspace Manager check validations (initializing a tree should validate instantly)", Flaky) {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      for {
        _ <- server.testInitialize(
          AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(s"${filePath("ws1")}"))
        )
        _ <- setMainFile(server)(s"${filePath("ws1")}", "api.raml")
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
        c <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val allDiagnostics = Seq(a, b, c)
        assert(allDiagnostics.size == allDiagnostics.map(_.uri).distinct.size)
      }
    }
  }

  test("Workspace Manager search by location rather than uri (workspace)") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      for {
        _ <- server.testInitialize(
          AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(s"${filePath("ws3")}"))
        )
        _ <- setMainFile(server)(s"${filePath("ws3")}", "api.raml")
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val allDiagnostics = Seq(a, b)
        assert(allDiagnostics.size == allDiagnostics.map(_.uri).distinct.size)
      }
    }
  }

  test("Workspace Manager check validation Stack - Error on external fragment with indirection") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val rootFolder = s"${filePath("ws-error-stack-1")}"
      for {
        _ <- server.testInitialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(rootFolder)))
        _ <- setMainFile(server)(rootFolder, "api.raml")
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
        c <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val allDiagnostics = Seq(a, b, c)
        verifyWS1ErrorStack(rootFolder, allDiagnostics)
      }
    }
  }

  private def verifyWS1ErrorStack(rootFolder: String, allDiagnostics: Seq[PublishDiagnosticsParams]) = {
    assert(allDiagnostics.size == allDiagnostics.map(_.uri).distinct.size)
    val main   = allDiagnostics.find(_.uri == s"$rootFolder/api.raml")
    val others = allDiagnostics.filterNot(pd => main.exists(_.uri == pd.uri))
    assert(main.isDefined)
    others.size should be(2)

    main match {
      case Some(m) =>
        m.diagnostics.size should be(1)
        m.diagnostics.head.range should be(Range(Position(3, 14), Position(3, 28)))
        val information = m.diagnostics.head.relatedInformation.getOrElse(Seq())
        information.size should be(2)
        information.head.location.uri should be(s"$rootFolder/external1.yaml")
        information.head.location.range should be(Range(Position(2, 14), Position(2, 28)))
        information.tail.head.location.uri should be(s"$rootFolder/external2.yaml")
        information.tail.head.location.range should be(Range(Position(0, 6), Position(0, 16)))
      case _ => fail("No Main detected")
    }
  }

  test("Workspace Manager check validation Stack - Error on library") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val rootFolder = s"${filePath("ws-error-stack-2")}"
      for {
        _ <- server.testInitialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(rootFolder)))
        _ <- setMainFile(server)(rootFolder, "api.raml")
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val allDiagnostics = Seq(a, b)
        assert(allDiagnostics.size == allDiagnostics.map(_.uri).distinct.size)
        val library = allDiagnostics.find(_.uri == s"$rootFolder/library.raml")
        val others =
          allDiagnostics.filterNot(pd => library.exists(_.uri == pd.uri))
        assert(library.isDefined)
        others.size should be(1)

        library match {
          case Some(m) =>
            m.diagnostics.size should be(1)
            m.diagnostics.head.range should be(Range(Position(3, 0), Position(3, 6)))
            val information = m.diagnostics.head.relatedInformation.getOrElse(Seq())
            information.size should be(1)
            information.head.location.uri should be(s"$rootFolder/api.raml")
            information.head.location.range should be(Range(Position(4, 7), Position(4, 19)))
          case _ => fail("No Main detected")
        }
      }
    }
  }

  test("Workspace Manager check validation Stack - Error on typed fragment") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val rootFolder = s"${filePath("ws-error-stack-3")}"
      for {
        _ <- server.testInitialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(rootFolder)))
        _ <- setMainFile(server)(rootFolder, "api.raml")
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val allDiagnostics = Seq(a, b)
        assert(allDiagnostics.size == allDiagnostics.map(_.uri).distinct.size)
        val library = allDiagnostics.find(_.uri == s"$rootFolder/external.raml")
        val others =
          allDiagnostics.filterNot(pd => library.exists(_.uri == pd.uri))
        assert(library.isDefined)
        others.size should be(1)

        library match {
          case Some(m) =>
            m.diagnostics.size should be(1)
            m.diagnostics.head.range should be(Range(Position(2, 0), Position(2, 7)))
            val information = m.diagnostics.head.relatedInformation.getOrElse(Seq())
            information.size should be(1)
            information.head.location.uri should be(s"$rootFolder/api.raml")
            information.head.location.range should be(Range(Position(4, 14), Position(4, 27)))
          case _ => fail("No Main detected")
        }
      }
    }
  }

  test("Workspace Manager check validation Stack - Error on External with two stacks - all traces enabled") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val rootFolder = s"${filePath("ws-error-stack-4")}"
      for {
        _ <- server.testInitialize(
          AlsInitializeParams(
            None,
            Some(TraceKind.Off),
            rootUri = Some(rootFolder)
          )
        )
        _ <- setMainFile(server)(rootFolder, "api.raml")
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
        c <- diagnosticClientNotifier.nextCall
        d <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val allDiagnostics = Seq(a, b, c, d)
        assert(allDiagnostics.size == allDiagnostics.map(_.uri).distinct.size)
        val root = allDiagnostics.find(_.uri == s"$rootFolder/api.raml")
        val others =
          allDiagnostics.filterNot(pd => root.exists(_.uri == pd.uri))
        assert(root.isDefined)
        others.size should be(3)
        assert(others.forall(p => p.diagnostics.isEmpty))

        root match {
          case Some(m) =>
            m.diagnostics.size should be(2)

            m.diagnostics.exists { d =>
              val information = d.relatedInformation.getOrElse(Seq())
              d.range == Range(Position(7, 14), Position(7, 27)) &&
              information.size == 2 &&
              information.head.location.uri == s"$rootFolder/external.yaml" &&
              information.head.location.range == Range(Position(1, 21), Position(1, 36)) &&
              information.tail.head.location.uri == s"$rootFolder/external-2.yaml" &&
              information.tail.head.location.range == Range(Position(1, 3), Position(1, 13))
            } should be(true)

            m.diagnostics.exists { d =>
              d.range == Range(Position(4, 7), Position(4, 19))
              val information = d.relatedInformation.getOrElse(Seq())
              information.size == 3
              information.head.location.uri == s"$rootFolder/library.raml" &&
              information.head.location.range == Range(Position(3, 14), Position(3, 27)) &&
              information.tail.head.location.uri == s"$rootFolder/external.yaml" &&
              information.tail.head.location.range == Range(Position(1, 21), Position(1, 36)) &&
              information.tail.tail.head.location.uri == s"$rootFolder/external-2.yaml" &&
              information.tail.tail.head.location.range == Range(Position(1, 3), Position(1, 13))
            } should be(true)

            succeed
          case _ => fail("No Main detected")
        }
      }
    }
  }

  test("Workspace Manager check validation Stack - No stack in error") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val rootFolder = s"${filePath("ws-error-stack-5")}"
      for {
        _ <- server.testInitialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(rootFolder)))
        _ <- setMainFile(server)(rootFolder, "api.yaml")
        a <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val allDiagnostics = Seq(a)
        assert(allDiagnostics.size == allDiagnostics.map(_.uri).distinct.size)
        val root = allDiagnostics.find(_.uri == s"$rootFolder/api.yaml")
        assert(root.isDefined)

        root match {
          case Some(m) =>
            m.diagnostics.size should be(3)

            if (
              !m.diagnostics.exists { d => // header
                d.range == Range(Position(0, 9), Position(0, 14)) &&
                d.relatedInformation.forall(_.isEmpty)
              }
            ) fail(s"Header is not present: ${m.diagnostics}")

            if (
              !m.diagnostics.exists { d => // wrong array
                d.range == Range(Position(1, 0), Position(17, 9)) &&
                d.relatedInformation.forall(_.isEmpty)
              }
            ) fail(s"Wrong array: ${m.diagnostics}")

            succeed
          case _ => fail("No Main detected")
        }
      }
    }
  }

  test("Workspace Manager check change in Config [using Command] - Should notify validations of new tree") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val root        = s"${filePath("ws4")}"
      val apiName     = "api.raml"
      val apiRoot     = s"$root/$apiName"
      val api2Name    = s"api2.raml"
      val api2Root    = s"$root/$api2Name"
      val apiFragment = s"$root/fragment.raml"

      for {
        _ <- server.testInitialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(root)))
        content <- EditorConfiguration.platform
          .fetchContent(apiRoot, AMLConfiguration.predefined())
          .map(_.stream.toString) // Open as single file
        _ <- openFileNotification(server)(apiRoot, content)
        // api.raml, fragment.raml
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
        _ <- changeWorkspaceConfiguration(server)(changeConfigArgs(Some(api2Name), root))
        // api2.raml
        c1 <- diagnosticClientNotifier.nextCall
        _  <- changeWorkspaceConfiguration(server)(changeConfigArgs(Some(apiName), root))
        // api.raml, fragment.raml
        d1 <- diagnosticClientNotifier.nextCall
        d2 <- diagnosticClientNotifier.nextCall

      } yield {
        server.shutdown()
        val first  = Seq(a, b)
        val second = Seq(c1)
        val third  = Seq(d1, d2)

        assert(first.exists(_.uri == apiRoot))
        assert(first.exists(_.uri == apiFragment))
        assert(second.exists(c => c.uri == api2Root && c.diagnostics.nonEmpty))
        assert(third.exists(_.uri == apiRoot))
        assert(third.exists(_.uri == apiFragment))
      }
    }
  }

  test("Workspace Manager change custom validation profiles [using Command]", Flaky) {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog

    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val root     = s"${filePath("ws4")}"
      val apiRoot  = s"api.raml"
      val api2Root = s"api2.raml"
      // val apiFragment = s"fragment.raml" todo: if not used, then delete
      val wm = server.workspaceService.asInstanceOf[WorkspaceManager]

      for {
        _ <- server.testInitialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(root)))
        _ <- server.workspaceService.executeCommand(
          ExecuteCommandParams(Commands.DID_CHANGE_CONFIGURATION, List(changeConfigArgs(Some(apiRoot), root)))
        )
        // api.raml, fragment.raml
        _       <- diagnosticClientNotifier.nextCall
        _       <- diagnosticClientNotifier.nextCall
        config1 <- wm.getWorkspace(filePath("ws4")).flatMap(_.getConfigurationState)
        _ <- server.workspaceService.executeCommand(
          ExecuteCommandParams(
            Commands.DID_CHANGE_CONFIGURATION,
            List(changeConfigArgs(Some(api2Root), root, profiles = Set(profileUri)))
          )
        )
        // api2.raml
        _       <- diagnosticClientNotifier.nextCall
        config2 <- wm.getWorkspace(filePath("ws4")).flatMap(_.getConfigurationState)
        _ <- server.workspaceService.executeCommand(
          ExecuteCommandParams(
            Commands.DID_CHANGE_CONFIGURATION,
            List(changeConfigArgs(Some(api2Root), root, profiles = Set(profileUri2)))
          )
        )
        // api2.raml
        _       <- diagnosticClientNotifier.nextCall
        config3 <- wm.getWorkspace(filePath("ws4")).flatMap(_.getConfigurationState)
        _ <- server.workspaceService.executeCommand(
          ExecuteCommandParams(
            Commands.DID_CHANGE_CONFIGURATION,
            List(changeConfigArgs(Some(api2Root), root, profiles = Set(profileUri, profileUri2)))
          )
        )
        // api2.raml
        _       <- diagnosticClientNotifier.nextCall
        config4 <- wm.getWorkspace(filePath("ws4")).flatMap(_.getConfigurationState)

      } yield {
        server.shutdown()
        def assertConfig(config: ProjectConfiguration, mainFile: String, profiles: Set[String]) = {
          assert(config.mainFile.contains(mainFile))
          assert(profiles.forall(p => config.validationDependency.contains(p)))
        }
        assertConfig(config1.projectState.config, "api.raml", Set.empty)
        assertConfig(config2.projectState.config, "api2.raml", Set(profileUri))
        assertConfig(config3.projectState.config, "api2.raml", Set(profileUri))
        assertConfig(config4.projectState.config, "api2.raml", Set(profileUri, profileUri2))
      }
    }
  }

  test("Workspace Content Manager - Unit not found (when changing RAML header)") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val root     = s"${filePath("ws4")}"
      val title    = s"$root/fragment.raml"
      val content1 = "#%RAML 1.0 DataType\n"
      val content2 = "#%RAML 1.0 Library\n"

      for {
        _ <- server.testInitialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(root)))
        _ <- setMainFile(server)(root, "api.raml")
        _ <- {
          openFile(server)(title, content1)
          diagnosticClientNotifier.nextCall
        }
        _ <- {
          changeFile(server)(title, content2, 2)
          diagnosticClientNotifier.nextCall
        }
        _ <- requestDocumentSymbol(server)(title)
        _ <-
          diagnosticClientNotifier.nextCall // There are a couple of diagnostics notifications not used in the test that could potentially bug proceeding tests
        _ <-
          diagnosticClientNotifier.nextCall // There are a couple of diagnostics notifications not used in the test that could potentially bug proceeding tests
      } yield {
        server.shutdown()
        succeed // if it hasn't blown, it's OK
      }
    }
  }

  test("Workspace Manager multiworkspace support - basic test") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val ws1path  = s"${filePath("multiworkspace/ws1")}"
      val filesWS1 = List(s"$ws1path/api.raml", s"$ws1path/sub/type.raml", s"$ws1path/type.json")
      val ws2path  = s"${filePath("multiworkspace/ws2")}"
      val filesWS2 = List(s"$ws2path/api.raml", s"$ws2path/sub/type.raml")
      val ws1      = WorkspaceFolder(Some(ws1path), Some("ws1"))
      val ws2      = WorkspaceFolder(Some(ws2path), Some("ws2"))
      val allFiles = filesWS1 ++ filesWS2
      val wsList   = List(ws1, ws2)

      for {
        _ <- server.testInitialize(
          AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(ws1path), workspaceFolders = Some(wsList))
        )
        _ <- setMainFile(server)(ws1path, "api.raml")
        _ <- setMainFile(server)(ws2path, "api.raml")
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
        c <- diagnosticClientNotifier.nextCall
        d <- diagnosticClientNotifier.nextCall
        e <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val allDiagnosticsFolders = List(a, b, c, d, e).map(_.uri)
        assert(allDiagnosticsFolders.size == allDiagnosticsFolders.distinct.size)
        assert(allFiles.forall(u => allDiagnosticsFolders.contains(u)))
      }
    }
  }

  test("Workspace Manager multiworkspace support - add workspace") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val ws1path  = s"${filePath("multiworkspace/ws1")}"
      val filesWS1 = List(s"$ws1path/api.raml", s"$ws1path/sub/type.raml", s"$ws1path/type.json")
      val ws2path  = s"${filePath("multiworkspace/ws2")}"
      val filesWS2 = List(s"$ws2path/api.raml", s"$ws2path/sub/type.raml")
      val ws1      = WorkspaceFolder(Some(ws1path), Some("ws1"))
      val ws2      = WorkspaceFolder(Some(ws2path), Some("ws2"))

      for {
        _ <- server.testInitialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(ws1path)))
        _ <- setMainFile(server)(ws1path, "api.raml")
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
        c <- diagnosticClientNotifier.nextCall
        _ <- addWorkspaceFolder(server)(ws2)
        _ <- setMainFile(server)(ws2path, "api.raml")
        d <- diagnosticClientNotifier.nextCall
        e <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val firstDiagnostics  = List(a, b, c).map(_.uri)
        val secondDiagnostics = List(d, e).map(_.uri)
        assert(firstDiagnostics == filesWS1)
        assert(secondDiagnostics == filesWS2)
      }
    }
  }

  test("Workspace Manager multiworkspace support - remove workspace") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val root  = s"${filePath("multiworkspace/ws-error-stack-1")}"
      val file1 = s"${filePath("multiworkspace/ws-error-stack-1/api.raml")}"
      val file2 =
        s"${filePath("multiworkspace/ws-error-stack-1/external1.yaml")}"
      val file3 =
        s"${filePath("multiworkspace/ws-error-stack-1/external2.yaml")}"

      val rootWSF = WorkspaceFolder(Some(root), Some("ws-error-stack-1"))

      for {
        _ <- server.testInitialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(root)))
        _ <- setMainFile(server)(root, "api.raml")
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
        c <- diagnosticClientNotifier.nextCall
        _ <- removeWorkspaceFolder(server)(rootWSF)
        d <- diagnosticClientNotifier.nextCall
        e <- diagnosticClientNotifier.nextCall
        f <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val firstDiagnostics  = Seq(a, b, c)
        val secondDiagnostics = Seq(d, e, f)
        verifyWS1ErrorStack(root, firstDiagnostics)
        assert(secondDiagnostics.forall(_.diagnostics.isEmpty))
        secondDiagnostics.map(_.uri) should contain(file1)
        secondDiagnostics.map(_.uri) should contain(file2)
        secondDiagnostics.map(_.uri) should contain(file3)
        assert(
          secondDiagnostics
            .map(_.uri)
            .forall(List(file1, file2, file3).contains)
        )
      }
    }
  }

  test("Workspace Manager multiworkspace support - included workspace") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val root1 = s"${filePath("multiworkspace/containedws")}"
      val root2 = s"${filePath("multiworkspace/containedws/ws1")}"
      val file1 = s"${filePath("multiworkspace/containedws/api.raml")}"
      val file2 = s"${filePath("multiworkspace/containedws/ws1/api.raml")}"
      val file3 = s"${filePath("multiworkspace/containedws/ws1/sub/type.raml")}"

      val root2WSF = WorkspaceFolder(Some(root2), Some("ws-2"))
      val root1WSF = WorkspaceFolder(Some(root1), Some("ws-1"))

      for {
        _ <- server.testInitialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(root2)))
        _ <- setMainFile(server)(root2, "api.raml")
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
        _ <- didChangeWorkspaceFolders(server)(List(root1WSF), List())
        c <- diagnosticClientNotifier.nextCall
        d <- diagnosticClientNotifier.nextCall
        _ <- setMainFile(server)(root1, "api.raml")
        e <- diagnosticClientNotifier.nextCall
        f <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val firstDiagnostics  = Seq(a, b)
        val secondDiagnostics = Seq(c, d)
        val thirdDiagnostics  = Seq(e, f)

        assert(firstDiagnostics.map(_.uri).toSet == Set(file2, file3))
        assert(secondDiagnostics.map(_.uri).toSet == Set(file2, file3))
        assert(thirdDiagnostics.map(_.uri).toSet == Set(file1, file3))
        assert(secondDiagnostics.forall(_.diagnostics.isEmpty))
        assert(firstDiagnostics.find(_.uri == file3).exists(_.diagnostics.nonEmpty))
        assert(thirdDiagnostics.find(_.uri == file3).exists(_.diagnostics.nonEmpty))
      }
    }
  }

  test("Workspace Manager multiworkspace support - multiple included workspaces") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val root1      = s"${filePath("multiworkspace/ws1")}"
      val root2      = s"${filePath("multiworkspace/ws2")}"
      val globalRoot = s"${filePath("multiworkspace")}"

      val filesWS1     = Set(s"$root1/api.raml", s"$root1/sub/type.raml", s"$root1/type.json")
      val filesWS2     = Set(s"$root2/api.raml", s"$root2/sub/type.raml")
      val fileGlobalWS = s"$globalRoot/api.raml"

      val root2WSF  = WorkspaceFolder(Some(root2), Some("ws-2"))
      val root1WSF  = WorkspaceFolder(Some(root1), Some("ws-1"))
      val globalWSF = WorkspaceFolder(Some(globalRoot), Some("global"))
      for {
        _ <- server.testInitialize(
          AlsInitializeParams(
            None,
            Some(TraceKind.Off),
            rootUri = Some(root2),
            workspaceFolders = Some(Seq(root1WSF, root2WSF))
          )
        )
        _   <- setMainFile(server)(root1, "api.raml")
        _   <- setMainFile(server)(root2, "api.raml")
        d11 <- diagnosticClientNotifier.nextCall
        d12 <- diagnosticClientNotifier.nextCall
        d13 <- diagnosticClientNotifier.nextCall
        d14 <- diagnosticClientNotifier.nextCall
        d15 <- diagnosticClientNotifier.nextCall
        _   <- didChangeWorkspaceFolders(server)(List(globalWSF), List())
        c11 <- diagnosticClientNotifier.nextCall
        c12 <- diagnosticClientNotifier.nextCall
        c13 <- diagnosticClientNotifier.nextCall
        c14 <- diagnosticClientNotifier.nextCall
        c15 <- diagnosticClientNotifier.nextCall
        _   <- setMainFile(server)(globalRoot, "api.raml")
        d21 <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val firstDiagnostic = Seq(d11, d12, d13, d14, d15)
        val firstClean      = Seq(c11, c12, c13, c14, c15)
        assert(firstDiagnostic.map(_.uri).toSet == (filesWS1 ++ filesWS2))
        assert(firstClean.map(_.uri).toSet == (filesWS1 ++ filesWS2))
        assert(firstClean.forall(_.diagnostics.isEmpty))
        assert(d21.uri == fileGlobalWS)
      }
    }
  }

  test("Workspace Manager test isolated file that needs encodes") {
    val text =
      """#%RAML 1.0
        |title: test
        |""".stripMargin
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      for {
        _ <- server.testInitialize(
          AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(s"${filePath("for-encode")}"))
        )
        _ <- Future(openFile(server)(s"${filePath("for-encode/api with spaces.raml")}", text))
        b <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        b.diagnostics.isEmpty should be(true)
      }
    }
  }

  test("Workspace Manager test syntax error in external fragment", Flaky) {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      for {
        _ <- server.testInitialize(
          AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(s"${filePath("external-fragment-syntax")}"))
        )
        _  <- setMainFile(server)(filePath("external-fragment-syntax"), "api.raml")
        n1 <- diagnosticClientNotifier.nextCall
        n2 <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        n2.uri should endWith("file.json")
        n2.diagnostics.size should be(1)
        n1.uri should endWith("api.raml")
        n1.diagnostics.isEmpty should be(true)
      }
    }
  }

  test("Workspace Manager check validations when opening with different content to fs") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      for {
        _ <- server.testInitialize(
          AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(s"${filePath("ws1")}"))
        )
        _ <- setMainFile(server)(filePath("ws1"), "api.raml")
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
        c <- diagnosticClientNotifier.nextCall // this should correspond to filesystem notifications
        main = filePath("ws1/api.raml")
        _ <- openFile(server)(main, "#%RAML 1.0\ninvalid")
        d <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val allDiagnostics = Seq(a, b, c)
        assert(allDiagnostics.size == allDiagnostics.map(_.uri).distinct.size)
        assert(d.uri == main)
        assert(d.diagnostics.nonEmpty)
      }
    }
  }

  test("Trigger reparse when changed watched file") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      for {
        _ <- server.testInitialize(
          AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(s"${filePath("ws1")}"))
        )
        _ <- setMainFile(server)(filePath("ws1"), "api.raml")
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
        c <- diagnosticClientNotifier.nextCall // this should correspond to filesystem notifications
        _ <- changeWatchedFiles(server)(filePath("ws1/type.json"), FileChangeType.Changed)
        d <- diagnosticClientNotifier.nextCall
        e <- diagnosticClientNotifier.nextCall
        f <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val firstDiagnostics  = Set(a, b, c)
        val secondDiagnostics = Set(d, e, f)
        assert(firstDiagnostics == secondDiagnostics)
      }
    }
  }

  // This test throws timeout if not working
  test("Workspace Manager OAS 3 SOF relatedFor branches case") {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog(60000)
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val rootFolder = s"${filePath("sof-test")}"
      for {
        _ <- server.testInitialize(
          AlsInitializeParams(
            None,
            Some(TraceKind.Off),
            rootUri = Some(rootFolder)
          )
        )
        _ <- setMainFile(server)(rootFolder, "api.yaml")
        a <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val allDiagnostics = Seq(a)
        assert(allDiagnostics.size == allDiagnostics.map(_.uri).distinct.size)
      }
    }
  }

  test("Workspace Manager check validation Stack - Performance case - wait", Flaky) {
    val diagnosticClientNotifier: MockDiagnosticClientNotifierWithTelemetryLog =
      new MockDiagnosticClientNotifierWithTelemetryLog(60000)
    withServer[Assertion](buildServer(diagnosticClientNotifier)) { server =>
      val rootFolder = s"${filePath("performance-stack")}"
      for {
        _ <- server.testInitialize(
          AlsInitializeParams(
            None,
            Some(TraceKind.Off),
            rootUri = Some(rootFolder)
          )
        )
        _ <- setMainFile(server)(rootFolder, "references.raml")
        a <- diagnosticClientNotifier.nextCall
        b <- diagnosticClientNotifier.nextCall
        c <- diagnosticClientNotifier.nextCall
        d <- diagnosticClientNotifier.nextCall
        e <- diagnosticClientNotifier.nextCall
        f <- diagnosticClientNotifier.nextCall
        g <- diagnosticClientNotifier.nextCall
        h <- diagnosticClientNotifier.nextCall
        i <- diagnosticClientNotifier.nextCall
        j <- diagnosticClientNotifier.nextCall
        k <- diagnosticClientNotifier.nextCall
        m <- diagnosticClientNotifier.nextCall
        n <- diagnosticClientNotifier.nextCall
      } yield {
        server.shutdown()
        val allDiagnostics = Seq(a, b, c, d, e, f, g, h, i, j, k, m, n)
        assert(allDiagnostics.size == allDiagnostics.map(_.uri).distinct.size)
      }
    }
  }

  /** Used to log cases in which timeouts occur
    */
  class MockDiagnosticClientNotifierWithTelemetryLog(timeoutMillis: Int = 8000)
      extends MockDiagnosticClientNotifier(timeoutMillis) {
    override def notifyTelemetry(params: TelemetryMessage): Unit = {} // println(params)
  }

  override def rootPath: String = "workspace"
}
