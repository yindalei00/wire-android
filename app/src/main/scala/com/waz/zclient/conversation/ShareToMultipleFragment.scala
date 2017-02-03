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
package com.waz.zclient.conversation

import android.content.Context
import android.os.Bundle
import android.support.v7.widget.RecyclerView.ViewHolder
import android.support.v7.widget.{LinearLayoutManager, RecyclerView, Toolbar}
import android.view.View.OnClickListener
import android.view._
import android.widget.LinearLayout.LayoutParams
import android.widget._
import com.waz.api._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{MessageContent => _, _}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal, SourceSignal}
import com.waz.zclient._
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.controllers.{AssetsController, SharingController}
import com.waz.zclient.messages.MessagesController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.pickuser.views.SearchBoxView
import com.waz.zclient.pages.main.pickuser.views.SearchBoxView.Callback
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.ui.views.CursorIconButton
import com.waz.zclient.utils.ViewUtils
import com.waz.ZLog.ImplicitTag._


class ShareToMultipleFragment extends BaseFragment[ShareToMultipleFragment.Container] with FragmentHelper {

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val assetsController = inject[AssetsController]
  lazy val messagesController = inject[MessagesController]
  lazy val accentColorController = inject[AccentColorController]
  lazy val sharingController = inject[SharingController]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = inflater.inflate(R.layout.fragment_collection_share, container, false)
    val listView = ViewUtils.getView(view, R.id.lv__conversation_list).asInstanceOf[RecyclerView]
    val sendButton = ViewUtils.getView(view, R.id.cib__send_button).asInstanceOf[CursorIconButton]
    val toolbar = ViewUtils.getView(view, R.id.t_toolbar).asInstanceOf[Toolbar]
    val searchBox = ViewUtils.getView(view, R.id.multi_share_search_box).asInstanceOf[SearchBoxView]
    val onClickEvent = EventStream[Unit]()
    val filterText = Signal[String]("")
    val adapter = new ShareToMultipleAdapter(getContext, filterText)

    searchBox.setHintText(getString(R.string.multi_share_search_hint))
    searchBox.setCallback(new Callback {
      override def onClearButton(): Unit = {
        searchBox.reset()
        KeyboardUtils.hideKeyboard(getContext, searchBox.getWindowToken)
      }

      override def onFocusChange(hasFocus: Boolean): Unit = {}

      override def onKeyboardDoneAction(): Unit = {
        KeyboardUtils.hideKeyboard(getContext, searchBox.getWindowToken)
      }

      override def afterTextChanged(s: String): Unit = {
        filterText ! s
      }

      override def onRemovedTokenSpan(user: User): Unit = {}
    })

    accentColorController.accentColor.on(Threading.Ui) {
      color =>
        sendButton.setSolidBackgroundColor(color.getColor())
        searchBox.setAccentColor(color.getColor())
    }

    toolbar.setNavigationIcon(if (getControllerFactory.getThemeController.isDarkTheme) R.drawable.ic_action_close_light else R.drawable.ic_action_close_dark)
    toolbar.setNavigationOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = getActivity.onBackPressed()
    })

    listView.setLayoutManager(new LinearLayoutManager(getContext))
    listView.setAdapter(adapter)

    onClickEvent { _ =>
      sharingController.sharableContent.on(Threading.Ui) { result =>
        result.foreach(res => sharingController.onContentShared(getActivity, res, adapter.selectedConversations.currentValue.getOrElse(Set())))
        Toast.makeText(getContext, R.string.multi_share_toast_sending, Toast.LENGTH_SHORT).show()
        getActivity.finish()
      }
    }

    sendButton.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        if (adapter.selectedConversations.currentValue.forall(_.isEmpty)) {
          return
        }
        onClickEvent ! (())
      }
    })
    view
  }
}

class ShareToMultipleAdapter(context: Context, filter: Signal[String])(implicit injector: Injector, eventContext: EventContext) extends RecyclerView.Adapter[RecyclerView.ViewHolder] with Injectable {
  setHasStableIds(true)

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val conversations = for{
    z <- zms
    conversationsSignal <- z.convsContent.conversationsSignal
    f <- filter
  } yield
    conversationsSignal.conversations.toSeq
      .filter(c => (c.convType == ConversationType.Group || c.convType == ConversationType.OneToOne) && !c.hidden && c.displayName.toLowerCase.contains(f.toLowerCase))
      .sortWith((a, b) => a.lastEventTime.isAfter(b.lastEventTime))

  conversations.on(Threading.Ui) {
    _ => notifyDataSetChanged()
  }

  val selectedConversations: SourceSignal[Set[ConvId]] = Signal(Set())

  private val checkBoxListener = new CompoundButton.OnCheckedChangeListener {
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
      Option(buttonView.getTag.asInstanceOf[ConvId]).foreach{ convId =>
        if (isChecked) {
          selectedConversations.mutate( _ + convId)
        } else {
          selectedConversations.mutate( _ - convId)
        }
      }
    }
  }

  def getItem(position: Int): Option[ConversationData] = conversations.currentValue.map(_(position))

  override def getItemCount: Int = conversations.currentValue.fold(0)(_.size)

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = {
    getItem(position) match {
      case Some(conv) =>
        holder.asInstanceOf[SelectableConversationRowViewHolder].setConversation(conv.id, selectedConversations.currentValue.exists(_.contains(conv.id)))
      case _ =>
    }
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = {
    val view = new SelectableConversationRow(context, checkBoxListener)
    parent.addView(view)
    SelectableConversationRowViewHolder(view)
  }

  override def getItemId(position: Int): Long = getItem(position).fold(0)(_.id.hashCode()).toLong

  override def getItemViewType(position: Int): Int = 1
}

case class SelectableConversationRowViewHolder(view: SelectableConversationRow)(implicit eventContext: EventContext, injector: Injector) extends RecyclerView.ViewHolder(view) with Injectable{
  lazy val zms = inject[Signal[ZMessaging]]

  val conversationId = Signal[ConvId]()

  zms.flatMap(z => conversationId.flatMap(convId => z.convsContent.conversationsSignal.map(_.conversations.find(_.id == convId)))).on(Threading.Ui){
    case Some(conversationData) => view.nameView.setText(conversationData.displayName)
    case _ => view.nameView.setText("")
  }

  def setConversation(convId: ConvId, checked: Boolean): Unit = {
    view.checkBox.setTag(null)
    view.checkBox.setChecked(checked)
    view.checkBox.setTag(convId)
    conversationId ! convId
  }
}

class SelectableConversationRow(context: Context, checkBoxListener: CompoundButton.OnCheckedChangeListener) extends LinearLayout(context, null, 0) {

  setPadding(
    getResources.getDimensionPixelSize(R.dimen.wire__padding__12),
    getResources.getDimensionPixelSize(R.dimen.list_tile_top_padding),
    getResources.getDimensionPixelSize(R.dimen.wire__padding__12),
    getResources.getDimensionPixelSize(R.dimen.list_tile_bottom_padding))
  setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getResources.getDimensionPixelSize(R.dimen.list_row_height)))
  setOrientation(LinearLayout.HORIZONTAL)

  LayoutInflater.from(context).inflate(R.layout.row_selectable_conversation, this, true)
  val nameView = ViewUtils.getView(this, R.id.ttv__conversation_name).asInstanceOf[TypefaceTextView]
  val checkBox = ViewUtils.getView(this, R.id.rb__conversation_selected).asInstanceOf[CheckBox]

  checkBox.setOnCheckedChangeListener(checkBoxListener)
}

object ShareToMultipleFragment {
  val TAG = ShareToMultipleFragment.getClass.getSimpleName

  val MSG_ID_ARG = "MSG_ID_ARG"

  def newInstance(messageId: MessageId): ShareToMultipleFragment = {
    val fragment = new ShareToMultipleFragment
    val bundle = new Bundle()
    bundle.putString(MSG_ID_ARG, messageId.str)
    fragment.setArguments(bundle)
    fragment
  }

  def newInstance(): ShareToMultipleFragment = {
    new ShareToMultipleFragment
  }

  trait Container
}
