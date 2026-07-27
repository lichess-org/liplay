/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.controllers {
  sealed trait TrampolineContextProvider:
    inline given trampoline: play.api.libs.streams.Execution.Trampoline =
      play.core.Execution.Implicits.trampoline
}

package controllers {
  import play.api.controllers.TrampolineContextProvider

  object Execution extends TrampolineContextProvider
}
