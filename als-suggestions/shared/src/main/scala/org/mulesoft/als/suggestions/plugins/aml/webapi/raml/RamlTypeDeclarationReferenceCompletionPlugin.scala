package org.mulesoft.als.suggestions.plugins.aml.webapi.raml

import amf.core.annotations.SynthesizedField
import amf.core.metamodel.domain.ShapeModel
import amf.core.model.domain.Shape
import amf.plugins.domain.shapes.models.UnresolvedShape
import org.mulesoft.als.common.ElementNameExtractor._
import org.mulesoft.als.suggestions.interfaces.CompletionPlugin
import org.mulesoft.als.suggestions.plugins.aml.AMLDeclarationsReferencesCompletionPlugin
import org.mulesoft.als.suggestions.{CompletionParams, RawSuggestion}
import org.yaml.model.YMapEntry

import scala.concurrent.Future

object RamlTypeDeclarationReferenceCompletionPlugin extends CompletionPlugin {
  override def id: String = "RamlTypeDeclarationReferenceCompletionPlugin"

  override def resolve(params: CompletionParams): Future[Seq[RawSuggestion]] = {
    Future.successful {
      params.amfObject match {
        case s: Shape if params.yPartBranch.isValue =>
          params.yPartBranch.parent
            .collectFirst({ case e: YMapEntry => e })
            .flatMap(_.key.asScalar.map(_.text)) match {
            case Some("type") =>
              // i need to force generic shape model search for default amf parsed types
              val iri =
                if (s.annotations.contains(classOf[SynthesizedField]) || params.yPartBranch.isEmptyNode)
                  ShapeModel.`type`.head.iri()
                else s.meta.`type`.head.iri()
              new AMLDeclarationsReferencesCompletionPlugin(Seq(iri),
                                                            params.prefix,
                                                            params.declarationProvider,
                                                            s.name.option()).resolve()
            case Some(text)
                if params.amfObject.elementIdentifier().contains(text) || params.amfObject
                  .isInstanceOf[UnresolvedShape] =>
              Raml10TypesDialect.shapeTypesProperty
                .enum()
                .map(v => v.value().toString)
                .map(RawSuggestion.apply(_, isAKey = false))
            case _ => Nil
          }
        case _ => Nil
      }
    }
  }

}