package org.mulesoft.als.suggestions.plugins.aml.webapi.raml.raml10

import amf.core.model.domain.Shape
import amf.plugins.domain.webapi.models.security.SecurityScheme
import org.mulesoft.als.suggestions.RawSuggestion
import org.mulesoft.als.suggestions.aml.AmlCompletionRequest
import org.mulesoft.als.suggestions.interfaces.AMLCompletionPlugin
import org.mulesoft.als.suggestions.plugins.aml.AMLStructureCompletionPlugin

import scala.concurrent.Future

object Raml10StructureCompletionPlugin extends AMLCompletionPlugin {
  override def id: String = "AMLStructureCompletionPlugin"

  override def resolve(
      request: AmlCompletionRequest): Future[Seq[RawSuggestion]] = {
    request.amfObject match {
      case _: Shape | _: SecurityScheme => emptySuggestion
      case _                            => AMLStructureCompletionPlugin.resolve(request)
    }
  }
}