package org.mulesoft.amfintegration.dialect.dialects.raml

import amf.core.annotations.Aliases
import amf.core.metamodel.domain.ModelVocabularies
import amf.core.vocabulary.Namespace
import amf.plugins.document.vocabularies.ReferenceStyles
import amf.plugins.document.vocabularies.model.document.{Dialect, Vocabulary}
import amf.plugins.document.vocabularies.model.domain._

trait RamlDialect {

  // Base location for all information the dialect
  val dialectLocation: String
  // Marking syntactic fields in the AST that are not directly mapped to properties in the model
  final val ImplicitField: String = (Namespace.Meta + "implicit").iri()

  // Dialect
  protected val version: String
  protected val dialectDeclares: Seq[NodeMapping]
  protected val rootId: String
  final lazy val dialect: Dialect = {
    val d = Dialect()
      .withId(dialectLocation)
      .withName("RAML")
      .withVersion(version)
      .withLocation(dialectLocation)
      .withId(dialectLocation)
      .withDeclares(dialectDeclares)
      .withDocuments(
        DocumentsModel()
          .withId(dialectLocation + "#/documents")
          .withReferenceStyle(ReferenceStyles.RAML)
          .withRoot(
            DocumentMapping()
              .withId(dialectLocation + "#/documents/root")
              .withEncoded(rootId)
          ))

    d.withExternals(
      Seq(
        External()
          .withId(dialectLocation + "#/externals/core")
          .withAlias("core")
          .withBase(Namespace.Core.base),
        External()
          .withId(dialectLocation + "#/externals/shacl")
          .withAlias("shacl")
          .withBase(Namespace.Shacl.base),
        External()
          .withId(dialectLocation + "#/externals/apiContract")
          .withAlias("apiContract")
          .withBase(Namespace.ApiContract.base),
        External()
          .withId(dialectLocation + "#/externals/meta")
          .withAlias("meta")
          .withBase(Namespace.Meta.base),
        External()
          .withId(dialectLocation + "#/externals/owl")
          .withAlias("owl")
          .withBase(Namespace.Owl.base)
      ))

    val vocabularies = Seq(
      ModelVocabularies.AmlDoc,
      ModelVocabularies.ApiContract,
      ModelVocabularies.Core,
      ModelVocabularies.Shapes,
      ModelVocabularies.Meta,
      ModelVocabularies.Security
    )
    d.annotations += Aliases(vocabularies.map { vocab =>
      (vocab.alias, (vocab.base, vocab.filename))
    }.toSet)

    d.withReferences(vocabularies.map { vocab =>
      Vocabulary()
        .withLocation(vocab.filename)
        .withId(vocab.filename)
        .withBase(vocab.base)
    })

    d
  }
  def apply(): Dialect = dialect
}