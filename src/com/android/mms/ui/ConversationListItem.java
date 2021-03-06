/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.kylin.location.PhoneLocation;
import android.kylin.util.KyLinUtils;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Checkable;
import android.widget.QuickContactBadge;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class manages the view for given conversation.
 */
public class ConversationListItem extends RelativeLayout implements Contact.UpdateListener,
            Checkable {
    private static final String TAG = LogTag.TAG;
    private static final boolean DEBUG = false;

    private TextView mSubjectView;
    private TextView mFromView;
    private TextView mDateView;
    private TextView mLocationView;
    private View mAttachmentView;
    private View mErrorIndicator;
    private QuickContactBadge mAvatarView;

    static private Drawable sDefaultContactImage;

    // For posting UI update Runnables from other threads:
    private Handler mHandler = new Handler();

    private Conversation mConversation;

    public static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);

    public ConversationListItem(Context context) {
        super(context);
    }

    public ConversationListItem(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (sDefaultContactImage == null) {
            sDefaultContactImage = context.getResources().getDrawable(R.drawable.ic_contact_picture);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mFromView = (TextView) findViewById(R.id.from);
        mSubjectView = (TextView) findViewById(R.id.subject);

        mDateView = (TextView) findViewById(R.id.date);
        mLocationView = (TextView) findViewById(R.id.location);
        mAttachmentView = findViewById(R.id.attachment);
        mErrorIndicator = findViewById(R.id.error);
        mAvatarView = (QuickContactBadge) findViewById(R.id.avatar);
    }

    public Conversation getConversation() {
        return mConversation;
    }

    /**
     * Only used for header binding.
     */
    public void bind(String title, String explain) {
        mFromView.setText(title);
        mSubjectView.setText(explain);
    }

    private CharSequence formatMessage() {
        final int color = android.R.styleable.Theme_textColorSecondary;
        String from = mConversation.getRecipients().formatNames(", ");
        if (MessageUtils.isWapPushNumber(from)) {
            String[] mAddresses = from.split(":");
            from = mAddresses[mContext.getResources().getInteger(
                    R.integer.wap_push_address_index)];
        }

        /**
         * Add boolean to know that the "from" haven't the Arabic and '+'.
         * Make sure the "from" display normally for RTL.
         */
        Boolean isEnName = false;
        Boolean isLayoutRtl = (TextUtils.getLayoutDirectionFromLocale(Locale.getDefault())
                == View.LAYOUT_DIRECTION_RTL);
        if (isLayoutRtl && from != null) {
            if (from.length() >= 1) {
                Pattern pattern = Pattern.compile("[^أ-ي]+");
                Matcher matcher = pattern.matcher(from);
                isEnName = matcher.matches();
                if (from.charAt(0) != '\u202D') {
                    if (isEnName) {
                        from = '\u202D' + from + '\u202C';
                    }
                }
            }
        }

        SpannableStringBuilder buf = new SpannableStringBuilder(from);

        if (mConversation.getMessageCount() > 1) {
            int before = buf.length();
            if (isLayoutRtl) {
                if (isEnName) {
                    buf.insert(1, mConversation.getMessageCount() + " ");
                    buf.setSpan(new ForegroundColorSpan(
                            mContext.getResources().getColor(R.color.message_count_color)),
                            1, buf.length() - before, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                } else {
                    buf.append(" " + mConversation.getMessageCount());
                    buf.setSpan(new ForegroundColorSpan(
                            mContext.getResources().getColor(R.color.message_count_color)),
                            before, buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                }
            } else {
                buf.append(mContext.getResources().getString(R.string.message_count_format,
                        mConversation.getMessageCount()));
                buf.setSpan(new ForegroundColorSpan(
                        mContext.getResources().getColor(R.color.message_count_color)),
                        before, buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
           }
        }
        if (mConversation.hasDraft()) {
            if (isLayoutRtl && isEnName) {
                int before = buf.length();
                buf.insert(1,'\u202E'
                        + mContext.getResources().getString(R.string.draft_separator)
                        + '\u202C');
                buf.setSpan(new ForegroundColorSpan(
                        mContext.getResources().getColor(R.drawable.text_color_black)),
                        1, buf.length() - before + 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                before = buf.length();
                int size;
                buf.insert(1,mContext.getResources().getString(R.string.has_draft));
                size = android.R.style.TextAppearance_Small;
                buf.setSpan(new TextAppearanceSpan(mContext, size), 1,
                        buf.length() - before + 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                buf.setSpan(new ForegroundColorSpan(
                        mContext.getResources().getColor(R.drawable.text_color_red)),
                        1, buf.length() - before + 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            } else {
                buf.append(mContext.getResources().getString(R.string.draft_separator));
                int before = buf.length();
                int size;
                buf.append(mContext.getResources().getString(R.string.has_draft));
                size = android.R.style.TextAppearance_Small;
                buf.setSpan(new TextAppearanceSpan(mContext, size, color), before,
                        buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                buf.setSpan(new ForegroundColorSpan(
                        mContext.getResources().getColor(R.drawable.text_color_red)),
                        before, buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
              }
        }

        // Unread messages are shown in bold
        if (mConversation.hasUnreadMessages()) {
            buf.setSpan(STYLE_BOLD, 0, buf.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        return buf;
    }

    private void updateAvatarView() {
        Drawable avatarDrawable;
        if (mConversation.getRecipients().size() == 1) {
            Contact contact = mConversation.getRecipients().get(0);
            avatarDrawable = contact.getAvatar(mContext, sDefaultContactImage);

            if (contact.existsInDatabase()) {
                mAvatarView.assignContactUri(contact.getUri());
            } else if (MessageUtils.isWapPushNumber(contact.getNumber())) {
                mAvatarView.assignContactFromPhone(
                        MessageUtils.getWapPushNumber(contact.getNumber()), true);
            } else {
                mAvatarView.assignContactFromPhone(contact.getNumber(), true);
            }
        } else {
            // TODO get a multiple recipients asset (or do something else)
            avatarDrawable = sDefaultContactImage;
            mAvatarView.assignContactUri(null);
        }
        mAvatarView.setImageDrawable(avatarDrawable);
        mAvatarView.setVisibility(View.VISIBLE);
    }

    private void updateFromView() {
        mFromView.setText(formatMessage());
        updateAvatarView();
    }

    public void onUpdate(Contact updated) {
        if (Log.isLoggable(LogTag.CONTACT, Log.DEBUG)) {
            Log.v(TAG, "onUpdate: " + this + " contact: " + updated);
        }
        mHandler.post(new Runnable() {
            public void run() {
                updateFromView();
            }
        });
    }

    public final void bind(Context context, final Conversation conversation) {
        //if (DEBUG) Log.v(TAG, "bind()");

        mConversation = conversation;

        updateBackground();

        LayoutParams attachmentLayout = (LayoutParams)mAttachmentView.getLayoutParams();
        boolean hasError = conversation.hasError();
        // When there's an error icon, the attachment icon is left of the error icon.
        // When there is not an error icon, the attachment icon is left of the date text.
        // As far as I know, there's no way to specify that relationship in xml.
        if (hasError) {
            attachmentLayout.addRule(RelativeLayout.LEFT_OF, R.id.error);
        } else {
            attachmentLayout.addRule(RelativeLayout.LEFT_OF, R.id.date);
        }

        boolean hasAttachment = conversation.hasAttachment();
        mAttachmentView.setVisibility(hasAttachment ? VISIBLE : GONE);

        // Date
        mDateView.setText(MessageUtils.formatTimeStampString(context, conversation.getDate()));

        // From.
        mFromView.setText(formatMessage());

        // Register for updates in changes of any of the contacts in this conversation.
        ContactList contacts = conversation.getRecipients();

        //Location
        if (KyLinUtils.isSupportLanguage(true)) {
            mLocationView.setText(PhoneLocation.getCityFromPhone((CharSequence)contacts.get(0).getNumber()));
        }

        if (Log.isLoggable(LogTag.CONTACT, Log.DEBUG)) {
            Log.v(TAG, "bind: contacts.addListeners " + this);
        }
        Contact.addListener(this);

        // Subject
        mSubjectView.setText(conversation.getSnippet());
        LayoutParams subjectLayout = (LayoutParams)mSubjectView.getLayoutParams();
        // We have to make the subject left of whatever optional items are shown on the right.
        subjectLayout.addRule(RelativeLayout.START_OF, hasAttachment ? R.id.attachment :
            (hasError ? R.id.error : R.id.date));

        // Transmission error indicator.
        mErrorIndicator.setVisibility(hasError ? VISIBLE : GONE);

        updateAvatarView();
    }

    private void updateBackground() {
        int backgroundId;
        if (mConversation.isChecked()) {
            backgroundId = R.drawable.list_selected_holo_light;
        } else if (mConversation.hasUnreadMessages()) {
            backgroundId = R.drawable.conversation_item_background_unread;
        } else {
            backgroundId = R.drawable.conversation_item_background_read;
        }
        Drawable background = mContext.getResources().getDrawable(backgroundId);
        setBackground(background);
    }

    public final void unbind() {
        if (Log.isLoggable(LogTag.CONTACT, Log.DEBUG)) {
            Log.v(TAG, "unbind: contacts.removeListeners " + this);
        }
        // Unregister contact update callbacks.
        Contact.removeListener(this);
    }

    public void setChecked(boolean checked) {
        mConversation.setIsChecked(checked);
        updateBackground();
    }

    public boolean isChecked() {
        return mConversation.isChecked();
    }

    public void toggle() {
        mConversation.setIsChecked(!mConversation.isChecked());
    }
}
