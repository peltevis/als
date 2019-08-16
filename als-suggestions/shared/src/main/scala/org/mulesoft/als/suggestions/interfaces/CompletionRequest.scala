package org.mulesoft.als.suggestions.interfaces

import amf.core.model.document.BaseUnit
import amf.core.model.domain.AmfObject
import amf.core.parser.FieldEntry
import amf.plugins.document.vocabularies.model.document.Dialect
import amf.plugins.document.vocabularies.model.domain.PropertyMapping
import org.mulesoft.als.common.YPartBranch
import org.mulesoft.als.common.dtoTypes.Position

trait CompletionRequest {

  val propertyMapping: Seq[PropertyMapping]

  val baseUnit: BaseUnit

  val position: Position

  val fieldEntry: Option[FieldEntry]

  val actualDialect: Dialect

  val amfObject: AmfObject

  val styler: Boolean => Seq[Suggestion] => Seq[Suggestion]

  val yPartBranch: YPartBranch
}