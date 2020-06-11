package org.mulesoft.als.server.modules.workspace

import amf.core.model.document.BaseUnit
import org.mulesoft.als.common.dtoTypes.ReferenceStack
import org.mulesoft.amfintegration.UnitWithNextReference

import scala.concurrent.Future

case class CompilableUnit(uri: String,
                          unit: BaseUnit,
                          mainFile: Option[String],
                          stack: Seq[ReferenceStack],
                          isDirty: Boolean = false,
                          private val n: Option[Future[CompilableUnit]])
    extends UnitWithNextReference {
  override protected type T = CompilableUnit
  override def next: Option[Future[T]] = n
}