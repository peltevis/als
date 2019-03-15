package org.mulesoft.language.server.modules.astManager

import amf.core.model.document.BaseUnit
import org.mulesoft.language.server.core.ServerIOCModule

import scala.concurrent.Future

/**
  * Manager of AST states.
  */
trait ASTManagerModule extends ServerIOCModule {

  /**
    * Module ID
    */
  val moduleId: String = "AST_MANAGER"

  /**
    * Returns currently available AST for the document, if any
    *
    * @param uri
    */
  def getCurrentAST(uri: String): Option[BaseUnit]

  /**
    * Gets current AST if there is any.
    * If not, performs immediate asynchronous parsing and returns the results.
    *
    * @param uri
    */
  def forceGetCurrentAST(uri: String): Future[BaseUnit]

  /**
    * Adds listener for new ASTs being parsed.
    *
    * @param listener
    * @param unsubscribe - if true, existing listener will be removed. False by default.
    */
  def onNewASTAvailable(listener: ASTListener, unsubscribe: Boolean = false): Unit

  /**
    * Gets current AST if there is any.
    * If not, performs immediate asynchronous parsing and returns the results.
    *
    * @param uri
    */
  def forceBuildNewAST(uri: String, text: String): Future[BaseUnit]
}

object ASTManagerModule {

  /**
    * Module ID
    */
  val moduleId: String = "AST_MANAGER"
}