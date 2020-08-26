package org.mulesoft.als.server.workspace.rename

import amf.client.remote.Content
import amf.internal.environment.Environment
import amf.internal.resource.ResourceLoader
import org.mulesoft.als.actions.rename.FindRenameLocations
import org.mulesoft.als.common.WorkspaceEditSerializer
import org.mulesoft.als.common.diff.{Diff, FileAssertionTest, Tests}
import org.mulesoft.als.server.modules.WorkspaceManagerFactoryBuilder
import org.mulesoft.als.server.protocol.LanguageServer
import org.mulesoft.als.server.{LanguageServerBaseTest, LanguageServerBuilder, MockDiagnosticClientNotifier}
import org.mulesoft.als.server.protocol.configuration.AlsInitializeParams
import org.mulesoft.als.server.workspace.WorkspaceManager
import org.mulesoft.lsp.configuration.TraceKind
import org.mulesoft.lsp.edit.{TextDocumentEdit, TextEdit, WorkspaceEdit}
import org.mulesoft.lsp.feature.common.{Position, Range, VersionedTextDocumentIdentifier}
import org.mulesoft.als.common.dtoTypes.{Position => DtoPosition}

import scala.concurrent.{ExecutionContext, Future}

class RenameTest extends LanguageServerBaseTest with FileAssertionTest {

  override implicit val executionContext: ExecutionContext =
    ExecutionContext.Implicits.global

  private val ws1 = Map(
    "file:///root/exchange.json" -> """{"main": "api.raml"}""",
    "file:///root/api.raml" ->
      """#%RAML 1.0
        |uses:
        |  lib: lib.raml
        |
        |/links:
        |  is:
        |    - lib.tr""".stripMargin,
    "file:///root/lib.raml" ->
      """#%RAML 1.0 Library
        |traits:
        |  tr:
        |    description: example trait
        |types:
        |  A: string
        |  C: A
        |  D: A""".stripMargin
  )
  private val ws2 = Map(
    "file:///root/exchange.json" -> """{"main": "api.raml"}""",
    "file:///root/api.raml" ->
      """#%RAML 1.0
        |types:
        |  simpleType:
        |    properties:
        |      name: string
        |  ramlExpression:
        |    properties:
        |      reports: anotherType | anotherType[] | supertype
        |      prop: string
        |  anotherType:
        |    properties:
        |        name: anotherType[]""".stripMargin
  )
  private val ws3 = Map(
    "file:///root/exchange.json" -> """{"main": "api.raml"}""",
    "file:///root/api.raml" ->
      """#%RAML 1.0
        |types:
        |  person:
        |    properties:
        |      name: manager[]
        |      age?: person
        |  manager:
        |    properties:
        |      reports: person
        |""".stripMargin
  )

  private val ws4 = Map(
    "file:///root/exchange.json" -> """{"main": "api.raml"}""",
    "file:///root/api.raml" ->
      """#%RAML 1.0
        |title: rename
        |uses:
        |  lib: library.raml
        |/person:
        |  get:
        |    responses:
        |      200:
        |        body:
        |          application/xml:
        |            type: array
        |            items:
        |              type: lib.Person []""".stripMargin,
    "file:///root/library.raml" ->
      """#%RAML 1.0 Library
        |
        |types:
        |  Person:
        |    properties:
        |      name: string""".stripMargin
  )
  val testSets: Map[String, TestEntry] = Map(
    "Test1" ->
      TestEntry(
        "file:///root/lib.raml",
        Position(2, 3),
        "trait1",
        ws1,
        createWSE(
          Seq(
            ("file:///root/api.raml",
             Seq(
               TextEdit(Range(Position(6, 6), Position(6, 12)), "lib.trait1")
             )),
            ("file:///root/lib.raml",
             Seq(
               TextEdit(Range(Position(2, 2), Position(2, 4)), "trait1")
             ))
          ))
      ),
    "Test2" -> TestEntry(
      "file:///root/lib.raml",
      Position(5, 3),
      "type1",
      ws1,
      createWSE(
        Seq(
          ("file:///root/lib.raml",
           Seq(
             TextEdit(Range(Position(6, 5), Position(6, 6)), "type1"),
             TextEdit(Range(Position(7, 5), Position(7, 6)), "type1"),
             TextEdit(Range(Position(5, 2), Position(5, 3)), "type1")
           ))
        ))
    ),
    "Test4" -> TestEntry(
      "file:///root/api.raml",
      Position(9, 6),
      "type1",
      ws2,
      createWSE(
        Seq(
          ("file:///root/api.raml",
           Seq(
             TextEdit(Range(Position(7, 15), Position(7, 26)), "type1"),
             TextEdit(Range(Position(11, 14), Position(11, 25)), "type1"),
             TextEdit(Range(Position(7, 29), Position(7, 40)), "type1"),
             TextEdit(Range(Position(9, 2), Position(9, 13)), "type1")
           ))
        ))
    ),
    "Test4" -> TestEntry(
      "file:///root/api.raml",
      Position(2, 3),
      "RENAMED",
      ws3,
      createWSE(
        Seq(
          ("file:///root/api.raml",
           Seq(
             TextEdit(Range(Position(8, 15), Position(8, 21)), "RENAMED"),
             TextEdit(Range(Position(5, 12), Position(5, 18)), "RENAMED"),
             TextEdit(Range(Position(2, 2), Position(2, 8)), "RENAMED")
           ))
        ))
    )
//    "Test5" -> TestEntry(
//      "file:///root/library.raml",
//      Position(3, 6),
//      "RENAMED",
//      ws4,
//      createWSE(Seq(
//        ("file:///root/library.raml",
//        Seq(
//          TextEdit(Range(Position(7, 10), Position(7, 16)), "RENAMED"),
//          TextEdit(Range(Position(3, 2), Position(3, 8)), "RENAMED"),
//
//        )),
//        ("file:///root/root.raml",
//          Seq(
//            TextEdit(Range(Position(12, 24), Position(12, 30)), "RENAMED"),
//
//          ))
//      ))
//    )
  )

  private def createWSE(edits: Seq[(String, Seq[TextEdit])]): WorkspaceEdit =
    WorkspaceEdit(
      edits.groupBy(_._1).mapValues(_.flatMap(_._2)),
      edits.map(e => Left(TextDocumentEdit(VersionedTextDocumentIdentifier(e._1, None), e._2)))
    )

  testSets.foreach {
    case (name, testCase) =>
      test("No handler " + name) {
        for {
          (_, wsManager) <- buildServer(testCase.root, testCase.ws)
          cu             <- wsManager.getLastUnit(testCase.targetUri, "")
          renames <- FindRenameLocations
            .changeDeclaredName(testCase.targetUri,
                                DtoPosition(testCase.targetPosition),
                                testCase.newName,
                                wsManager.getRelationships(testCase.targetUri, ""), cu.unit)
          actual <- (writeTemporaryFile(s"file://rename-test-$name-actual.yaml")(
            WorkspaceEditSerializer(renames).serialize()))
          expected <- writeTemporaryFile(s"file://rename-test-$name-expected.yaml")(
            WorkspaceEditSerializer(testCase.result).serialize())
          r <- Tests.checkDiff(actual, expected)
        } yield r
      }
  }

  private def equalWSE(a: WorkspaceEdit, b: WorkspaceEdit): Boolean =
    a.changes.mapValues(_.toSet) == b.changes.mapValues(_.toSet) &&
      a.documentChanges.map(_.left) == b.documentChanges.map(_.left)

  case class TestEntry(targetUri: String,
                       targetPosition: Position,
                       newName: String,
                       ws: Map[String, String],
                       result: WorkspaceEdit,
                       root: String = "file:///root")

  def buildServer(root: String, ws: Map[String, String]): Future[(LanguageServer, WorkspaceManager)] = {
    val rs = new ResourceLoader {
      override def fetch(resource: String): Future[Content] =
        ws.get(resource)
          .map(c => new Content(c, resource))
          .map(Future.successful)
          .getOrElse(Future.failed(new Exception("File not found on custom ResourceLoader")))
      override def accepts(resource: String): Boolean =
        ws.keySet.contains(resource)
    }

    val env = Environment().withLoaders(Seq(rs))

    val factory =
      new WorkspaceManagerFactoryBuilder(new MockDiagnosticClientNotifier, logger, env)
        .buildWorkspaceManagerFactory()
    val workspaceManager: WorkspaceManager = factory.workspaceManager
    val server =
      new LanguageServerBuilder(factory.documentManager,
                                workspaceManager,
                                factory.configurationManager,
                                factory.resolutionTaskManager)
        .addRequestModule(factory.renameManager)
        .build()

    server
      .initialize(AlsInitializeParams(None, Some(TraceKind.Off), rootUri = Some(root)))
      .andThen { case _ => server.initialized() }
      .map(_ => (server, workspaceManager))
  }

  override def rootPath: String = ???
}
