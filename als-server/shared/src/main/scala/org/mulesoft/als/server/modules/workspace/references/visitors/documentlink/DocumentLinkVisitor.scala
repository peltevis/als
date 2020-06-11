package org.mulesoft.als.server.modules.workspace.references.visitors.documentlink

import amf.core.model.document.BaseUnit
import amf.core.model.domain.AmfElement
import org.mulesoft.als.actions.links.FindLinks
import org.mulesoft.als.server.modules.workspace.references.visitors.AmfElementVisitorFactory
import org.mulesoft.lsp.feature.link.DocumentLink

class DocumentLinkVisitor extends DocumentLinkVisitorType {
  override protected def innerVisit(element: AmfElement): Seq[(String, Seq[DocumentLink])] = {
    element match {
      case bu: BaseUnit =>
        Seq((bu.location().getOrElse(bu.id), FindLinks.getLinks(bu)))
      case _ => Nil
    }
  }
}

object DocumentLinkVisitor extends AmfElementVisitorFactory {
  override def apply(): DocumentLinkVisitor = new DocumentLinkVisitor()
}