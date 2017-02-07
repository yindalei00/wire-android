/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.views

import android.content.Context
import android.util.{AttributeSet, TypedValue}
import com.waz.api.EphemeralExpiration
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.ViewHelper
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.ui.R
import com.waz.zclient.ui.utils.{ColorUtils, ResourceUtils, TypefaceUtils}
import com.waz.zclient.ui.views.CursorIconButton

class EphemeralCursorButton(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends CursorIconButton(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val accentColorController = inject[AccentColorController]
  val ephemeralExpiration = Signal[EphemeralExpiration](EphemeralExpiration.NONE)

  accentColorController.accentColor.zip(ephemeralExpiration).on(Threading.Ui){
    case (accentColor, expiration) =>
      val value = expiration match {
        case EphemeralExpiration.ONE_DAY =>
          getResources.getString(R.string.cursor__ephemeral_message__timer_days, String.valueOf(expiration.milliseconds / 1000 / 60 / 60 / 24))
        case EphemeralExpiration.ONE_MINUTE | EphemeralExpiration.FIVE_MINUTES =>
          getResources.getString(R.string.cursor__ephemeral_message__timer_min, String.valueOf(expiration.milliseconds / 1000 / 60))
        case EphemeralExpiration.NONE =>
          getResources.getString(R.string.glyph__hourglass)
        case _ =>
          getResources.getString(R.string.cursor__ephemeral_message__timer_seconds, String.valueOf(expiration.milliseconds / 1000))
      }
      setText(value)
      if (expiration == EphemeralExpiration.NONE) {
        setBackground(null)
        setTypeface(TypefaceUtils.getTypeface(TypefaceUtils.getGlyphsTypefaceName))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getContext.getResources.getDimensionPixelSize(R.dimen.wire__text_size__regular))
      } else {
        //val bgColor: Int = ColorUtils.injectAlpha(ResourceUtils.getResourceFloat(getResources, R.dimen.ephemeral__accent__timer_alpha), accentColor.getColor())
        //setSolidBackgroundColor(bgColor)
        setBackground(ColorUtils.getTintedDrawable(getContext, R.drawable.background__cursor__ephemeral_timer, accentColor.getColor()))
        setTypeface(TypefaceUtils.getTypeface(getContext.getString(R.string.wire__typeface__regular)))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getContext.getResources.getDimensionPixelSize(R.dimen.wire__text_size__small))
      }
    case _ =>
  }

  def setExpiration(ephemeralExpiration: EphemeralExpiration): Unit = {
    this.ephemeralExpiration ! ephemeralExpiration
  }
}
