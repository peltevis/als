package org.mulesoft.als.suggestions.plugins.aml.webapi.raml

import amf.core.metamodel.domain.extensions.{CustomDomainPropertyModel, PropertyShapeModel}
import amf.core.metamodel.domain.{DataNodeModel, ShapeModel}
import amf.core.vocabulary.Namespace
import amf.core.vocabulary.Namespace.XsdTypes.{xsdBoolean, xsdFloat, xsdInteger, xsdString}
import amf.plugins.document.vocabularies.model.document.Dialect
import amf.plugins.document.vocabularies.model.domain.{NodeMapping, PropertyMapping}
import amf.plugins.domain.shapes.metamodel._

object Raml10TypesDialect {

  val DialectLocation = "file://parallel-als/vocabularies/dialects/raml10.yaml"

  val ShapeNodeId: String = DialectLocation + "#/declarations/ShapeNode"

  val shapeProperties: Seq[PropertyMapping] = Seq(
    PropertyMapping()
      .withId(DialectLocation + "#/declarations/ShapeNode/in")
      .withNodePropertyMapping(ShapeModel.Values.value.iri())
      .withName("enum")
      .withObjectRange(Seq(ShapeNodeId))
      .withAllowMultiple(true),
    PropertyMapping()
      .withId(DialectLocation + "#/declarations/ShapeNode/default")
      .withNodePropertyMapping(ShapeModel.Default.value.iri())
      .withName("default")
      .withObjectRange(Seq(DataNodeModel.`type`.head.iri())),
    PropertyMapping()
      .withId(DialectLocation + "#/declarations/ShapeNode/displayName")
      .withNodePropertyMapping(ShapeModel.DisplayName.value.iri())
      .withName("displayName")
      .withLiteralRange(xsdString.iri()),
    PropertyMapping()
      .withId(DialectLocation + "#/declarations/ShapeNode/inherits")
      .withNodePropertyMapping(ShapeModel.Inherits.value.iri())
      .withName("type")
      .withEnum(
        Seq(
          "string",
          "number",
          "integer",
          "float",
          "boolean",
          "array",
          "file",
          "object",
          "date",
          "date-only",
          "time-only",
          "datetime-only",
          "datetime",
          "nil"
        ))
      .withLiteralRange(xsdString.iri())
      .withObjectRange(Seq(ShapeNodeId)),
    PropertyMapping()
      .withId(DialectLocation + "#/declarations/ShapeNode/description")
      .withNodePropertyMapping(ShapeModel.Description.value.iri())
      .withName("description")
      .withLiteralRange(xsdString.iri()),
    PropertyMapping()
      .withId(DialectLocation + "#/declarations/ShapeNode/facets")
      .withNodePropertyMapping(ShapeModel.CustomShapeProperties.value.iri())
      .withName("facets")
      .withObjectRange(Seq(CustomDomainPropertyModel.`type`.head.iri()))
      .withMapTermKeyProperty(CustomDomainPropertyModel.Name.value.iri())
  )

  val ShapeNode: NodeMapping = NodeMapping()
    .withId(ShapeNodeId)
    .withName("ShapeNode")
    .withNodeTypeMapping(ShapeModel.`type`.head.iri())
    .withPropertiesMapping(shapeProperties)

  val anyShapeProperties: Seq[PropertyMapping] = Seq(
    PropertyMapping()
      .withId(DialectLocation + "#/declarations/AnyShapeNode/xmlSerialization")
      .withNodePropertyMapping(AnyShapeModel.XMLSerialization.value.iri())
      .withName("xml")
      .withObjectRange(Seq(XMLSerializerModel.`type`.head.iri())),
    PropertyMapping()
      .withId(DialectLocation + "#/declarations/AnyShapeNode/example")
      .withNodePropertyMapping(AnyShapeModel.Examples.value.iri())
      .withName("example")
      .withObjectRange(Seq(DataNodeModel.`type`.head.iri())),
    PropertyMapping()
      .withId(DialectLocation + "#/declarations/AnyShapeNode/examples")
      .withNodePropertyMapping(AnyShapeModel.Examples.value.iri())
      .withName("examples")
      .withObjectRange(Seq(DataNodeModel.`type`.head.iri()))
      .withMapTermKeyProperty(ExampleModel.Name.value.iri())
  ) ++ shapeProperties

  val AnyShapeNode: NodeMapping = NodeMapping()
    .withId(DialectLocation + "#/declarations/AnyShapeNode")
    .withName("AnyShapeNode")
    .withNodeTypeMapping(AnyShapeModel.`type`.head.iri())
    .withPropertiesMapping(anyShapeProperties)

  val PropertyShapeNode: NodeMapping = NodeMapping()
    .withId(DialectLocation + "#/declarations/PropertyShapeNode")
    .withName("PropertyShapeNode")
    .withNodeTypeMapping(PropertyShapeModel.`type`.head.iri())
    .withPropertiesMapping(
      anyShapeProperties ++ Seq(
        PropertyMapping()
          .withId(DialectLocation + "#/declarations/PropertyShapeNode/required")
          .withNodePropertyMapping(PropertyShapeModel.MinCount.value.iri())
          .withName("required")
          .withLiteralRange(xsdBoolean.iri())
      ))

  val NodeShapeNode: NodeMapping = NodeMapping()
    .withId(DialectLocation + "#/declarations/NodeShapeNode")
    .withName("NodeShapeNode")
    .withNodeTypeMapping(NodeShapeModel.`type`.head.iri())
    .withPropertiesMapping(anyShapeProperties ++ Seq(
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/NodeShapeNode/minProperties")
        .withNodePropertyMapping(NodeShapeModel.MinProperties.value.iri())
        .withName("minProperties")
        withLiteralRange xsdInteger.iri(),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/NodeShapeNode/maxProperties")
        .withNodePropertyMapping(NodeShapeModel.MaxProperties.value.iri())
        .withName("maxProperties")
        withLiteralRange xsdInteger.iri(),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/NodeShapeNode/additionalProperties")
        .withNodePropertyMapping(NodeShapeModel.Closed.value.iri())
        .withName("additionalProperties")
        withLiteralRange xsdBoolean.iri(),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/NodeShapeNode/discriminator")
        .withNodePropertyMapping(NodeShapeModel.Discriminator.value.iri())
        .withName("discriminator")
        withLiteralRange xsdString.iri(),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/NodeShapeNode/discriminatorValue")
        .withNodePropertyMapping(NodeShapeModel.DiscriminatorValue.value.iri())
        .withName("discriminatorValue")
        withLiteralRange xsdString.iri(),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/NodeShapeNode/properties")
        .withNodePropertyMapping(NodeShapeModel.Properties.value.iri())
        .withName("properties")
        .withObjectRange(Seq(PropertyShapeNode.id))
        .withMapTermKeyProperty(PropertyShapeModel.Name.value.iri())
    ))

  val ArrayShapeNode: NodeMapping = NodeMapping()
    .withId(DialectLocation + "#/declarations/ArrayShapeNode")
    .withName("ArrayShapeNode")
    .withNodeTypeMapping(ArrayShapeModel.`type`.head.iri())
    .withPropertiesMapping(anyShapeProperties ++ Seq(
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ArrayShapeNode/items")
        .withNodePropertyMapping(ArrayShapeModel.Items.value.iri())
        .withName("items")
        .withObjectRange(Seq(ShapeNodeId)),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ArrayShapeNode/minItems")
        .withNodePropertyMapping(ArrayShapeModel.MinItems.value.iri())
        .withName("minItems")
        .withLiteralRange(xsdInteger.iri()),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ArrayShapeNode/maxItems")
        .withNodePropertyMapping(ArrayShapeModel.MaxItems.value.iri())
        .withName("maxItems")
        .withLiteralRange(xsdInteger.iri()),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ArrayShapeNode/uniqueItems")
        .withNodePropertyMapping(ArrayShapeModel.UniqueItems.value.iri())
        .withName("uniqueItems")
        .withLiteralRange(xsdBoolean.iri())
    ))

  val UnionShapeNode: NodeMapping = NodeMapping()
    .withId(DialectLocation + "#/declarations/UnionShapeNode")
    .withName("UnionShapeNode")
    .withNodeTypeMapping(UnionShapeModel.`type`.head.iri())
    .withPropertiesMapping(anyShapeProperties)

  val StringShapeNode: NodeMapping = NodeMapping()
    .withId(DialectLocation + "#/declarations/StringShapeNode")
    .withName("StringShapeNode")
    .withNodeTypeMapping((Namespace.Shapes + "StringShape").iri())
    .withPropertiesMapping(anyShapeProperties ++ Seq(
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ScalarShapeNode/pattern")
        .withNodePropertyMapping(ScalarShapeModel.Pattern.value.iri())
        .withName("pattern")
        .withLiteralRange(xsdString.iri()),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ScalarShapeNode/minLength")
        .withNodePropertyMapping(ScalarShapeModel.MinLength.value.iri())
        .withName("minLength")
        .withLiteralRange(xsdInteger.iri()),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ScalarShapeNode/maxLength")
        .withNodePropertyMapping(ScalarShapeModel.MaxLength.value.iri())
        .withName("maxLength")
        .withLiteralRange(xsdInteger.iri())
    ))

  val NumberShapeNode: NodeMapping = NodeMapping()
    .withId(DialectLocation + "#/declarations/NumberShapeNode")
    .withName("NumberShapeNode")
    .withNodeTypeMapping((Namespace.Shapes + "NumberShape").iri())
    .withPropertiesMapping(anyShapeProperties ++ Seq(
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ScalarShapeNode/minimum")
        .withNodePropertyMapping(ScalarShapeModel.Minimum.value.iri())
        .withName("minimum")
        .withLiteralRange(xsdFloat.iri()),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ScalarShapeNode/maximun")
        .withNodePropertyMapping(ScalarShapeModel.Maximum.value.iri())
        .withName("maximum")
        .withLiteralRange(xsdFloat.iri()),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ScalarShapeNode/format")
        .withNodePropertyMapping(ScalarShapeModel.Format.value.iri())
        .withName("format")
        .withEnum(
          Seq(
            "int8",
            "int16",
            "int32",
            "int64",
            "int",
            "long",
            "float",
            "double"
          ))
        .withLiteralRange(xsdString.iri()),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ScalarShapeNode/maximun")
        .withNodePropertyMapping(ScalarShapeModel.MultipleOf.value.iri())
        .withName("multipleOf")
        .withLiteralRange(xsdFloat.iri())
    ))

  val FileShapeNode: NodeMapping = NodeMapping()
    .withId(DialectLocation + "#/declarations/FileShapeNode")
    .withName("FileShapeNode")
    .withNodeTypeMapping(FileShapeModel.`type`.head.iri())
    .withPropertiesMapping(anyShapeProperties ++ Seq(
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ScalarShapeNode/fileTypes")
        .withNodePropertyMapping(FileShapeModel.FileTypes.value.iri())
        .withName("fileTypes")
        .withLiteralRange(xsdString.iri()),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ScalarShapeNode/minLength")
        .withNodePropertyMapping(FileShapeModel.MinLength.value.iri())
        .withName("minLength")
        .withLiteralRange(xsdInteger.iri()),
      PropertyMapping()
        .withId(DialectLocation + "#/declarations/ScalarShapeNode/maxLength")
        .withNodePropertyMapping(FileShapeModel.MaxLength.value.iri())
        .withName("maxLength")
        .withLiteralRange(xsdInteger.iri())
    ))

  val NilShapeNode: NodeMapping = NodeMapping()
    .withId(DialectLocation + "#/declarations/NilShapeNode")
    .withName("NilShapeNode")
    .withNodeTypeMapping(NilShapeModel.`type`.head.iri())

  val dialect: Dialect = {
    Dialect()
      .withId(DialectLocation)
      .withName("RAML")
      .withVersion("1.0")
      .withLocation(DialectLocation)
      .withId(DialectLocation)
      .withDeclares(
        Seq(
          AnyShapeNode,
          PropertyShapeNode,
          NodeShapeNode,
          ArrayShapeNode,
          UnionShapeNode,
          StringShapeNode,
          NumberShapeNode,
          FileShapeNode,
          NilShapeNode
        ))
  }
}