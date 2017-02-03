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
package com.waz.zclient.messages.parts.assets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.waz.threading.Threading
import com.waz.zclient.R
import com.waz.zclient.messages.MsgPart
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.ZLog.ImplicitTag._
import com.waz.zclient.views.ImageAssetDrawable.State.Loaded

class VideoAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with PlayableAsset with ImageLayoutAssetPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.VideoAsset

  private val controls = findById[View](R.id.controls)

  hideContent.map(!_).on(Threading.Ui)(controls.setVisible)

  imageDrawable.state.map {
    case Loaded(_, _, _) => getColor(R.color.white)
    case _ => getColor(R.color.black)
  }.on(Threading.Ui)(durationView.setTextColor)

  padding.on(Threading.Ui)(offset => controls.setMargin(offset))

  asset.disableAutowiring()

  assetActionButton.onClicked.filter(_ == DeliveryState.Complete) { _ =>
    asset.currentValue foreach { case (a, _) =>
      controller.openFile(a)
    }
  }
}
