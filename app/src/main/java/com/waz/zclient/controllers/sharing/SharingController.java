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
package com.waz.zclient.controllers.sharing;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.Nullable;

import com.waz.api.EphemeralExpiration;
import com.waz.api.IConversation;
import com.waz.zclient.utils.IntentUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SharingController implements ISharingController {

    private SharedContentType sharedContentType;

    private String sharedText;
    private List<Uri> sharedFileUris;
    private String conversationId;
    private Set<SharingObserver> observerSet = new HashSet<>();
    private IConversation destination;

    @Override
    public void addObserver(SharingObserver observer) {
        observerSet.add(observer);
    }

    @Override
    public void removeObserver(SharingObserver observer) {
        observerSet.remove(observer);
    }

    @Override
    public void tearDown() {
        observerSet.clear();
    }

    @Override
    public void setSharedContentType(SharedContentType type) {
        if (type == null) {
            return;

        }
        sharedContentType = type;
    }

    @Override
    public SharedContentType getSharedContentType() {
        return sharedContentType;
    }

    @Override
    public void setSharedText(String text) {
        sharedText = text;
    }

    @Override
    public String getSharedText() {
        return sharedText;
    }

    @Override
    public void setSharedUris(List<Uri> imageUris) {
        sharedFileUris = imageUris;
    }

    @Override
    public List<Uri> getSharedFileUris() {
        return sharedFileUris;
    }

    @Override
    public void onContentShared(Activity activity, IConversation toConversation) {
        onContentShared(activity, toConversation, (String) null);
    }

    @Override
    public void onContentShared(Activity activity, IConversation toConversation, @Nullable String sharedText) {
        Intent i = IntentUtils.getAppLaunchIntent(activity,
                                                  toConversation == null ? null : toConversation.getId(),
                                                  sharedText);
        activity.startActivity(i);
    }

    @Override
    public void onContentShared(Activity activity, IConversation toConversation, List<Uri> sharedUris) {
        Intent i = IntentUtils.getAppLaunchIntent(activity,
                                                  toConversation == null ? null : toConversation.getId(),
                                                  sharedUris,
                                                  EphemeralExpiration.NONE);
        activity.startActivity(i);
    }

    @Override
    public void setSharingConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    @Override
    public String getSharingConversation() {
        return conversationId;
    }

    @Override
    public void maybeResetSharedText(IConversation currentConversation) {
        if (currentConversation == null) {
            return;
        }

        if (!currentConversation.getId().equals(conversationId)) {
            return;
        }

        conversationId = null;
        sharedText = null;
    }

    @Override
    public void maybeResetSharedUris(IConversation currentConversation) {
        if (currentConversation == null) {
            return;
        }

        if (!currentConversation.getId().equals(conversationId)) {
            return;
        }

        if (sharedContentType != SharedContentType.FILE) {
            return;
        }

        conversationId = null;
        sharedFileUris = null;
    }

    @Override
    public boolean isSharedConversation(IConversation conversation) {
        if (conversationId == null ||
            conversation == null) {
            return false;
        }
        return conversationId.equals(conversation.getId());
    }

    @Override
    public void setDestination(IConversation conversation) {
        this.destination = conversation;
        for (SharingObserver observer : observerSet) {
            observer.onDestinationSelected(conversation);
        }
    }

    @Override
    public IConversation getDestination() {
        return destination;
    }
}
