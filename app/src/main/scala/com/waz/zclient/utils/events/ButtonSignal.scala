/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.utils.events

import com.waz.threading.Threading
import com.waz.utils.events._
import com.waz.ZLog.ImplicitTag._

// TODO consider moving to the rest of the signals package
/**
  * ButtonSignal extends Signal[Boolean] so that it can be listened to directly for the toggle state
  *
  * @param buttonState the toggle state the button should reflect (should probably be provided by the service)
  * @param service Some service of type A that controls what happens when the button is pressed
  * @param onPressed a function that takes the service and the current state of the button and performs the toggling
  */
case class ButtonSignal[A](buttonState: Signal[Boolean], service: Signal[A])(onPressed: (A, Boolean) => Unit) (implicit eventContext: EventContext)
  extends ProxySignal[Boolean](buttonState) {

  private val buttonPress = EventStream[Unit]()

  //TODO should this always be on the background thread?
  new EventStreamWithAuxSignal(buttonPress, service.zip(buttonState)).on(Threading.Background){
    case (_, Some((s, state))) => onPressed(s, state)
    case _ =>
  }

  def press(): Unit = buttonPress ! (())

  override protected def computeValue(current: Option[Boolean]): Option[Boolean] = buttonState.currentValue
}
