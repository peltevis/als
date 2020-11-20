package org.mulesoft.als.actions.codeactions.plugins.declarations.common

import amf.core.model.domain.AmfObject
import amf.core.remote.{Mimes, Vendor}
import org.mulesoft.als.actions.codeactions.plugins.base.CodeActionRequestParams
import org.mulesoft.als.actions.codeactions.plugins.declarations.common.ExtractorCommon.singularize
import org.mulesoft.als.actions.codeactions.plugins.declarations.fragment.webapi.raml.{
  FragmentBundle,
  RamlFragmentMatcher
}
import org.mulesoft.als.common.YamlUtils.isJson
import org.mulesoft.als.common.dtoTypes.{Position, PositionRange}
import org.mulesoft.als.common.{ObjectInTree, YPartBranch}
import org.mulesoft.als.convert.LspRangeConverter
import org.mulesoft.amfintegration.AmfImplicits.{AmfAnnotationsImp, AmfObjectImp, BaseUnitImp, DialectImplicits}
import org.mulesoft.lsp.edit.TextEdit
import org.mulesoft.lsp.feature.common.Range
import org.yaml.model._
import org.yaml.render.{JsonRender, JsonRenderOptions, YamlRender, YamlRenderOptions}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait BaseElementDeclarableExtractors {

  protected val params: CodeActionRequestParams

  private lazy val baseName: String =
    amfObject
      .flatMap(_.declarableKey(params.dialect))
      .map(singularize)
      .map(t => s"new$t")
      .getOrElse("newDeclaration")

  /**
    * Placeholder for the new name (key and reference)
    */
  protected def newName: String = ExtractorCommon.nameNotInList(baseName, params.bu.declaredNames.toSet)

  protected val maybeTree: Option[ObjectInTree] =
    params.tree.treeWithUpperElement(params.range, params.uri)

  /**
    * Based on the chosen position from the range
    */
  private lazy val position: Option[Position] =
    maybeTree.map(_.amfPosition).map(Position(_))

  /**
    * Information about the AST for the chosen position
    */
  protected lazy val yPartBranch: Option[YPartBranch] =
    position.map(params.yPartBranch.getCachedOrNew(_, params.uri))

  protected lazy val amfObject: Option[AmfObject] = ExtractorCommon.amfObject(maybeTree, params.dialect)

  /**
    * The original node with lexical info for the declared node
    */
  protected lazy val entryAst: Option[YPart] =
    amfObject.flatMap(_.annotations.ast()) match {
      case Some(entry: YMapEntry) => Some(entry.value)
      case c                      => c
    }

  /** gets the range for the whole value of the parent */
  private def getRangeForAML(entryAst: YPart): Option[Range] = yPartBranch.flatMap { b =>
    val wholeStack = b.node +: b.stack
    val ix         = wholeStack.indexOf(entryAst)
    wholeStack(ix + 1)
    if (ix >= 0 && (ix + 1) < wholeStack.length)
      Some(LspRangeConverter.toLspRange(PositionRange(wholeStack(ix + 1).range)))
    else None
  }

  /**
    * The original range info for the declared node
    */
  protected lazy val entryRange: Option[Range] =
    if (vendor != Vendor.AML)
      entryAst
        .map(_.range)
        .map(PositionRange(_))
        .map(LspRangeConverter.toLspRange)
    else entryAst.flatMap(getRangeForAML)

  /**
    * The indentation for the existing node, as we already ensured it is a key, the first position gives de current indentation
    */
  protected lazy val entryIndentation: Int =
    yPartBranch.flatMap(_.parentEntry).map(_.range.columnFrom).getOrElse(0)

  protected def positionIsExtracted: Boolean =
    entryRange
      .map(n => PositionRange(n))
      .exists(r => position.exists(r.contains))

  protected lazy val sourceName: String =
    entryAst.map(_.sourceName).getOrElse(params.uri)

  /**
    * Fallback entry, should not be necessary as the link should be rendered
    */
  protected lazy val jsonRefEntry: YNode =
    YNode(
      YMap(
        IndexedSeq(YMapEntry(YNode("$ref"), YNode(s"$newName"))),
        sourceName
      ))

  /**
    * Render of the link generated by the new object
    */
  protected val renderLink: Future[Option[YNode]] = Future.successful(None)

  protected lazy val vendor: Vendor =
    params.bu.sourceVendor.getOrElse(Vendor.AML)

  /**
    * The entry which holds the reference for the new declaration (`{"$ref": "declaration/$1"}`)
    */
  protected lazy val linkEntry: Future[Option[TextEdit]] =
    renderLink.map { rl =>
      if (isJson(params.bu))
        entryRange.map(
          TextEdit(
            _,
            JsonRender.render(rl.getOrElse(jsonRefEntry), entryIndentation, jsonOptions)
          ))
//      else if (params.dialect.isRamlStyle)
//        entryRange.map(TextEdit(_, s" ${rl.map(YamlRender.render(_, 0, yamlOptions)).getOrElse(newName)}\n"))
      else if (params.dialect.isJsonStyle)
        entryRange.map(
          TextEdit(
            _,
            s"\n${YamlRender.render(rl.getOrElse(jsonRefEntry), entryIndentation, yamlOptions)}\n"
          ))
      else // default as raml style if none defined
        entryRange.map(TextEdit(_, s" ${rl.map(YamlRender.render(_, 0, yamlOptions).trim).getOrElse(newName)}\n"))
    }

  protected val jsonOptions: JsonRenderOptions = JsonRenderOptions().withIndentationSize(
    params.configuration
      .getFormatOptionForMime(Mimes.`APPLICATION/JSON`)
      .indentationSize
  )

  protected val yamlOptions: YamlRenderOptions = YamlRenderOptions().withIndentationSize(
    params.configuration
      .getFormatOptionForMime(Mimes.`APPLICATION/YAML`)
      .indentationSize
  )
}