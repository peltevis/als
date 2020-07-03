package org.mulesoft.amfintegration.vocabularies

import amf.plugins.document.vocabularies.model.domain.{ClassTerm, ObjectPropertyTerm, PropertyTerm}

trait TermObjectNode {
  val name: String
  val description: String
}

trait PropertyTermObjectNode extends TermObjectNode {

  lazy val obj: PropertyTerm = ObjectPropertyTerm()
    .withName(name)
    .withDisplayName(name)
    .withDescription(description)
}

trait ClassTermObjectNode extends TermObjectNode {
  lazy val obj: ClassTerm = ClassTerm()
    .withName(name)
    .withDisplayName(name)
    .withDescription(description)
}
