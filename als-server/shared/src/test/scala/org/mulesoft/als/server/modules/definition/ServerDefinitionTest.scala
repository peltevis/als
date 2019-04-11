package org.mulesoft.als.server.modules.definition

import common.dtoTypes.Position
import org.mulesoft.als.server.modules.ast.AstManager
import org.mulesoft.als.server.modules.common.LspConverter
import org.mulesoft.als.server.modules.hlast.HlAstManager
import org.mulesoft.als.server.platform.ServerPlatform
import org.mulesoft.als.server.textsync.TextDocumentManager
import org.mulesoft.als.server.{LanguageServerBaseTest, LanguageServerBuilder}
import org.mulesoft.lsp.common
import org.mulesoft.lsp.common.{TextDocumentIdentifier, TextDocumentPositionParams}
import org.mulesoft.lsp.feature.definition.DefinitionRequestType

abstract class ServerDefinitionTest extends LanguageServerBaseTest {

  override def addModules(documentManager: TextDocumentManager,
                          serverPlatform: ServerPlatform,
                          builder: LanguageServerBuilder): LanguageServerBuilder = {

    val astManager = new AstManager(documentManager, serverPlatform, logger)
    val hlAstManager = new HlAstManager(documentManager, astManager, serverPlatform, logger)
    val referencesModule = new DefinitionModule(hlAstManager, serverPlatform, logger)

    builder
      .addInitializable(astManager)
      .addInitializable(hlAstManager)
      .addRequestModule(referencesModule)
  }

  test("Open declaration test 001") {
    withServer { server =>
      var content1 =
        """#%RAML 1.0
          |title: test
          |types:
          |  MyType:
          |  MyType2:
          |    properties:
          |      p1: MyType
          |""".stripMargin
      val ind = content1.indexOf("p1: MyType") + "p1: My".length
      val usagePosition = LspConverter.toLspPosition(Position(ind, content1))

      val url = "file:///findDeclarationTest001.raml"

      openFile(server)(url, content1)

      val handler = server.resolveHandler(DefinitionRequestType).value

      handler(new TextDocumentPositionParams() {
        override val textDocument: TextDocumentIdentifier = TextDocumentIdentifier(url)
        override val position: common.Position = usagePosition
      })
        .map(declarations => {
          closeFile(server)(url)

          if (declarations.nonEmpty) {
            succeed
          } else {
            fail("No references have been found")
          }
        })

    }
  }
}
