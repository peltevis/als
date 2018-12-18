package org.mulesoft.language.test.dtoTypes

import org.mulesoft.language.outline.structure.structureInterfaces.{StructureNodeJSON => SharedStructureNode}
import org.mulesoft.language.common.dtoTypes.{IChangedPosition => SharedChangedPosition, ILocation => SharedLocation, IFindRequest => SharedFindRequest, IChangedDocument => SharedChangedDocument, IOpenedDocument => SharedOpenDocument, IRange => SharedRange, IStructureReport => SharedStructureReport, ITextEdit => SharedTextEdit, IValidationIssue => SharedValidationIssue, IValidationReport => SharedValidationReport}
import org.mulesoft.language.common.logger.{ILoggerSettings, MessageSeverity => SharedMessageSeverity}
import org.mulesoft.als.suggestions.interfaces.ISuggestion
import upickle.default.{macroRW, write, ReadWriter => RW, read}
/**
  * Tag for potential payloads, in order to serialize/deserialize to JSON
  */
sealed trait ProtocolMessagePayload {

}

object ProtocolMessagePayload {
  //implicit def rw: RW[ProtocolMessagePayload] = macroRW

  //  implicit val readWriter: RW[ProtocolMessagePayload] = RW.merge(
  //    macroRW[OpenedDocument], macroRW[ChangedDocument]
  //  )
}

/**
  * Document being opened.
  */
case class OpenedDocument (

  /**
    * Document URI
    */
  var uri: String,

  /**
    * Optional document version.
    */
  var version: Int,

  /**
    * Optional document content
    */
  var text: String

) extends ProtocolMessagePayload
{
}



object OpenedDocument {
  //implicit def rw: RW[OpenedDocument] = macroRW

  implicit def transportToShared(
    from: OpenedDocument): SharedOpenDocument = {

    SharedOpenDocument(
      from.uri,
      from.version,
      from.text
    )
  }

  implicit def sharedToTransport(
    from: SharedOpenDocument): OpenedDocument = {

      OpenedDocument(from.uri,from.version,from.text)
  }
}

case class FindDeclarationRequest(var uri: String, var position: Int) extends ProtocolMessagePayload;

object FindDeclarationRequest {
  //implicit def rw: RW[FindDeclarationRequest] = macroRW;
  
  implicit def transportToShared(from: FindDeclarationRequest): SharedFindRequest = SharedFindRequest(from.uri, from.position);

    def apply(uri:String, position: Int):FindDeclarationRequest = new FindDeclarationRequest(uri,position)
}

case class FindReferencesRequest(var uri: String, var position: Int) extends ProtocolMessagePayload;

object FindReferencesRequest {
  //implicit def rw: RW[FindReferencesRequest] = macroRW;
  
  implicit def transportToShared(from: FindReferencesRequest): SharedFindRequest = SharedFindRequest(from.uri, from.position);

  def apply(uri: String, position: Int):FindReferencesRequest = new FindReferencesRequest(uri,position)
}

case class Location(uri: String, range: Range, version: Int)

object Location {
    implicit def transportToShared(from: Location): SharedLocation = new SharedLocation{
        override var uri: String = from.uri
        override var version: Int = from.version
        override var range: SharedRange = Range.transportToShared(from.range)
    }

    //implicit def rw: RW[Location] = macroRW;
  
  implicit def sharedToTransport(from: SharedLocation): Location = Location(from.uri, Range(from.range.start, from.range.end), from.version);
}

case class ClosedDocument(var wrapped: String) extends ProtocolMessagePayload;

object ClosedDocument {
  //implicit def rw: RW[ClosedDocument] = macroRW;
}

/**
  * Document being opened.
  */
case class ChangedDocument (

    /**
      * Document URI
      */
    var uri: String,

    /**
      * Optional document version.
      */
    var version: Int,

    /**
      * Optional document content
      */
    var text: Option[String],

    /**
      * Optional set of text edits instead of complete text replacement.
      * Is only taken into account if text is null.
      */
    var textEdits: Option[Seq[TextEdit]]

  ) extends ProtocolMessagePayload
{
}

object ChangedDocument {
  //implicit def rw: RW[ChangedDocument] = macroRW

  implicit def transportToShared(
    from: ChangedDocument): SharedChangedDocument = {

    SharedChangedDocument(
      from.uri,
      from.version,
      from.text,
      from.textEdits.map(_.map(TextEdit.transportToShared))
//      if(from.textEdits.isDefined)
//        Some(from.textEdits.get.map(edit=>TextEdit.transportToShared(edit)))
//      else None
    )
  }

  implicit def sharedToTransport(
    from: SharedChangedDocument): ChangedDocument = {

    ChangedDocument(from.uri,from.version,from.text,from.textEdits.map(_.map(TextEdit.sharedToTransport)))
  }
}

case class ChangedPosition (var uri: String, var position: Int) extends ProtocolMessagePayload;

object ChangedPosition {
  //implicit def rw: RW[ChangedPosition] = macroRW
  
  implicit def transportToShared(from: ChangedPosition): SharedChangedPosition = SharedChangedPosition(from.uri, from.position);

    def apply(uri: String,position: Int): ChangedPosition = new ChangedPosition(uri,position)
}

/**
  * Validation report.
  */
case class ValidationReport (

  /**
    * This is the "point of view" uri, actual reported unit paths are located
    * in the particular issues.
    */
  var pointOfViewUri: String,

  /**
    * Optional document version of the point of view.
    */
  var version: Int,

  /**
    * Validation issues.
    */
  var issues: Seq[ValidationIssue]
) extends ProtocolMessagePayload
{

}

object ValidationReport {
  //implicit def rw: RW[ValidationReport] = macroRW

//  implicit def transportToShared(
//    from: ValidationReport): SharedValidationReport = {
//
//    val genFrom = Generic[ValidationReport]
//    val genTo = Generic[SharedValidationReport]
//
//    genTo.from(genFrom.to(from))
//  }

  implicit def sharedToTransport(
    from: SharedValidationReport): ValidationReport = {

    ValidationReport(
      from.pointOfViewUri,
      from.version,
      from.issues.map(issue=>ValidationIssue.sharedToTransport(issue))
    )
  }
}

/**
  * Validation issue: error or warning
  */
case class ValidationIssue (

  /**
    * Error code
    */
  var code: String,

  /**
    * Error type.
    */
  var `type`: String,

  /**
    * Document uri. Legacy: to be renamed to uri.
    */
  var filePath: String,

  /**
    * Issue human-readable text.
    */
  var text: String,

  /**
    * Range producing the issue.
    */
  var range: Range,

  /**
    * Subsequent validation issues
    */
  var trace: Seq[ValidationIssue]
)
{
}

object ValidationIssue {
  //implicit def rw: RW[ValidationIssue] = macroRW

  implicit def sharedToTransport(
    from: SharedValidationIssue): ValidationIssue = {

    ValidationIssue(
      from.code,
      from.`type`,
      from.filePath,
      from.text,
      from.range,
      from.trace.map(issue=>ValidationIssue.sharedToTransport(issue))
    )
  }
}

/**
  * Single text edit in a document.
  */
case class TextEdit (

  /**
    * Range to replace. Range start==end==0 => insert into the beginning of the document,
    * start==end==document end => insert into the end of the document
    */
  var range: Range,

  /**
    * Text to replace given range with.
    */
  var text: String
)
{
}

object TextEdit {
  //implicit def rw: RW[TextEdit] = macroRW

  implicit def transportToShared(
                                  from: TextEdit): SharedTextEdit = {

    SharedTextEdit(
      from.range,
      from.text
    )
  }

  implicit def sharedToTransport(
                                  from: SharedTextEdit): TextEdit = {

    TextEdit(
      from.range,
      from.text
    )
  }
}

/**
  * Range in the document.
  */
case class Range (

  /**
    * Range start position, counting from 0
    */
  var start: Int,

  /**
    * Range end position, counting from 0
    */
  var end: Int
)
{
}

object Range {
  //implicit def rw: RW[Range] = macroRW

  implicit def transportToShared(
    from: Range): SharedRange = {

    SharedRange(
      from.start,
      from.end
    )
  }

  implicit def sharedToTransport(
    from: SharedRange): Range = {

    Range(
      from.start,
      from.end
    )
  }
}

/**
  * Report for document structure.
  */
case class StructureReport (

  /**
    * Document uri.
    */
  var uri: String,

  /**
    * Optional document version.
    */
  var version: Int,

  /**
    * Document structure.
    */
  var structure: Map[String, StructureNode]
) extends ProtocolMessagePayload
{

}

object StructureReport {
  //implicit def rw: RW[StructureReport] = macroRW


  implicit def sharedToTransport(
    from: SharedStructureReport): StructureReport = {

    StructureReport(
      from.uri,
      from.version,
      from.structure.map{case (key, value) => (key, StructureNode.sharedToTransport(value))}
    )
  }
}

case class StructureNode (
  /**
    * Node label text to be displayed.
    */
  text: String = null,
  /**
    * Node type label, if any.
    */
  typeText: String = null,
  /**
    * Node icon. Structure module is not setting up, how icons are represented in the client
    * system, or what icons exist,
    * instead the client is responsible to configure the mapping from nodes to icon identifiers.
    */
  icon: String = null,
  /**
    * Text style of the node. Structure module is not setting up, how text styles are represented in the client
    * system, or what text styles exist,
    * instead the client is responsible to configure the mapping from nodes to text styles identifiers.
    */
  textStyle: String = null,
  /**
    * Unique node identifier.
    */
  key: String = null,
  /**
    * Node start position from the beginning of the document.
    */
  start: Int = -1,
  /**
    * Node end position from the beginning of the document.
    */
  end: Int = -1,
  /**
    * Whether the node is selected.
    */
  selected: Boolean =false,
  /**
    * Node children.
    */
  children: Seq[StructureNode] = Seq(),
  /**
    * Node category, if determined by a category filter.
    */
  category: String = null
)
{

}

object StructureNode {
  implicit def rw: RW[StructureNode] = macroRW

  implicit def transportToShared(from: StructureNode): SharedStructureNode = {
      new SharedStructureNode {override def icon: String = from.icon

          override def start: Int = from.start

          override def children: Seq[SharedStructureNode] = from.children.map(StructureNode.transportToShared)

          override def typeText: Option[String] = Option(from.typeText)

          override def end: Int = from.end

          override def textStyle: String = from.textStyle

          override def text: String = from.text

          override def category: String = from.category

          override def selected: Boolean = from.selected

          override def key: String = from.key
      }
  }

  implicit def sharedToTransport(
    from: SharedStructureNode): StructureNode = {

    val result = StructureNode(
      from.text,
      from.typeText.orNull,
      from.icon,
      from.textStyle,
      from.key,
      from.start,
      from.end,
      from.selected,
      from.children.map(StructureNode.sharedToTransport),
      from.category
    )
    result
  }
}

/**
  * Request from client to server to obtain structure
  * @param wrapped
  */
case class GetStructureRequest (
   /**
     * Url
     */
   wrapped: String

) extends ProtocolMessagePayload
{
}

case class RenameRequest (
    uri: String,
    newName: String,
    position: Int) extends ProtocolMessagePayload
{
}


object GetStructureRequest {
  //implicit def rw: RW[GetStructureRequest] = macroRW
  def apply(wrapped: String): GetStructureRequest = new GetStructureRequest(wrapped)
}

/**
  * Request from client to server to obtain completion
  */
case class GetCompletionRequest (
   /**
     * Url
     */
   uri: String,

   /**
     * Completion position
     */
   position: Int

) extends ProtocolMessagePayload
{
}

/**
  * Request from client to server to obtain completion
  */
case class GetCompletionResponse (
                                    suggestions:Seq[Suggestion]

                                ) extends ProtocolMessagePayload
{
}

object GetCompletionRequest {
  //implicit def rw: RW[GetCompletionRequest] = macroRW
}

/**
  * Request from client to server to obtain structure
  * @param wrapped
  */
case class GetStructureResponse (

 /**
   * Document structure.
   */
 wrapped: Map[String, StructureNode]

) extends ProtocolMessagePayload
{
}

object GetStructureResponse {
  //implicit def rw: RW[GetStructureResponse] = macroRW
}

case class Suggestion (
   /**
     * Full text to insert, including the index.
     */
   text: String,

   /**
     * Description of the suggestion.
     */
   description: Option[String],

   /**
     * Text to display.
     */
   displayText: Option[String],
   /**
     * Detected suggestion prefix.
     */
   prefix: Option[String],

   /**
     * Suggestion category.
     */
   category: Option[String]
 ) extends ProtocolMessagePayload
{

}

object Suggestion {
  //implicit def rw: RW[Suggestion] = macroRW

  implicit def sharedToTransport(from: ISuggestion): Suggestion = {

    Suggestion(
      from.text,
      if (from.description != null) Some(from.description) else None,
      if (from.displayText != null) Some(from.displayText) else None,
      if (from.prefix != null) Some(from.prefix) else None,
      if (from.category != null) Some(from.category) else None
    )
  }

    implicit def transportToShared(from: Suggestion): ISuggestion = {

        new ISuggestion {
            override def displayText: String = from.displayText.orNull

            override def prefix: String = from.prefix.orNull

            override def description: String = from.description.orNull

            override def text: String = from.text

            override def category: String = from.category.orNull

            override def trailingWhitespace: String = ""
        }
    }
}


case class LocationsResponse(val wrapped: Seq[Location]) extends ProtocolMessagePayload;

case class RenameResponse(val wrapped: Seq[ChangedDocument]) extends ProtocolMessagePayload;

object LocationsResponse {
  //implicit def rw: RW[LocationsResponse] = macroRW;
}

case class ClientPathRequest(wrapped: String) extends ProtocolMessagePayload;

object ClientPathRequest {
  //implicit def rw: RW[ClientPathRequest] = macroRW;
}

case class ClientBoolResponse(wrapped: Boolean) extends ProtocolMessagePayload;

object ClientBoolResponse {
  //implicit def rw: RW[ClientBoolResponse] = macroRW;
}

case class ClientStringResponse(wrapped: String) extends ProtocolMessagePayload;

object ClientStringResponse {
  //implicit def rw: RW[ClientStringResponse] = macroRW;
}

case class ClientStringSeqResponse(wrapped: Seq[String]) extends ProtocolMessagePayload;

object ClientStringSeqResponse {
	//implicit def rw: RW[ClientStringSeqResponse] = macroRW;
}

/**
  * Logger configuration / settings
  */
case class LoggerSettings (

   /**
     * If true, disables all logging.
     */
   //var disabled: Option[Boolean],

   /**
     * List of components, which are allowed to appear in log.
     * If empty or absent, all components are allowed (except those excplicitly denied).
     */
   var allowedComponents: Option[Seq[String]],

   /**
     * Components, which never appear in the log
     */
   //var deniedComponents: Option[Seq[String]],

   /**
     * Messages with lower severity will not appear in log.
     */
   var maxSeverity: Option[Int],

   /**
     * Messages having more length will be cut off to this number.
     */
   var maxMessageLength: Option[Int]
 ) extends ProtocolMessagePayload
{

}

object LoggerSettings {
  //implicit def rw: RW[LoggerSettings] = macroRW

  def transportToShared(
    from: LoggerSettings): ILoggerSettings = {


    new ILoggerSettings() {
      var disabled = None.asInstanceOf[Option[Boolean]]//from.disabled
      var allowedComponents = from.allowedComponents
      var deniedComponents = None.asInstanceOf[Option[Seq[String]]]//from.deniedComponents
      var maxSeverity = if(from.maxSeverity.isDefined) Some(MessageSeverity.sharedToTransport(from.maxSeverity.get)) else None
      var maxMessageLength = from.maxMessageLength
    }
  }
}

object MessageSeverity {


  implicit def sharedToTransport(
    from: Int): SharedMessageSeverity.Value = {

    from match {
      case 0 => SharedMessageSeverity.DEBUG_DETAIL
      case 1 => SharedMessageSeverity.DEBUG
      case 2 => SharedMessageSeverity.DEBUG_OVERVIEW
      case 3 => SharedMessageSeverity.WARNING
      case 4 => SharedMessageSeverity.ERROR
      case _ => SharedMessageSeverity.DEBUG
    }
  }

}