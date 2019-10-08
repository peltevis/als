package org.mulesoft.als.server.custom

import com.google.gson.JsonElement
import org.mulesoft.lsp.textsync.DidFocusParams

object DidFocusCommandExecutor extends CommandExecutor[DidFocusParams] {
  override def matcher: AnyRef => Option[DidFocusParams] = {
    case dpf: DidFocusParams => Some(dpf)
    case json: JsonElement   => parseJson(json)
    case _                   => None
  }
}
