package org.mulesoft.als.suggestions.implementation

import org.mulesoft.als.suggestions.interfaces.{ICompletionRequest, ICompletionResponse, ISuggestion, LocationKind}

class CompletionResponse(_suggestions:Seq[ISuggestion], _kind:LocationKind, _request:ICompletionRequest) extends ICompletionResponse {

    private var _noColon:Boolean = false

    override def kind: LocationKind = _kind

    override def request: ICompletionRequest = _request

    override def suggestions: Seq[ISuggestion] = _suggestions

    override def isEmpty: Boolean = _suggestions.isEmpty

    override def nonEmpty: Boolean = _suggestions.nonEmpty

    override def noColon: Boolean = _noColon

    def withNoColon(v:Boolean=true):CompletionResponse = {
        _noColon = v
        this
    }
}

object CompletionResponse {
    def apply(suggestions: Seq[ISuggestion], kind: LocationKind, request: ICompletionRequest): CompletionResponse = new CompletionResponse(suggestions, kind, request)
    def apply(kind: LocationKind, request: ICompletionRequest): CompletionResponse = new CompletionResponse(Seq(), kind, request)
}