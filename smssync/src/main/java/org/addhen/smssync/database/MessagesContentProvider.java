/*******************************************************************************
 *  Copyright (c) 2010 - 2013 Ushahidi Inc
 *  All rights reserved
 *  Contact: team@ushahidi.com
 *  Website: http://www.ushahidi.com
 *  GNU Lesser General Public License Usage
 *  This file may be used under the terms of the GNU Lesser
 *  General Public License version 3 as published by the Free Software
 *  Foundation and appearing in the file LICENSE.LGPL included in the
 *  packaging of this file. Please review the following information to
 *  ensure the GNU Lesser General Public License version 3 requirements
 *  will be met: http://www.gnu.org/licenses/lgpl.html.
 *
 * If you have questions regarding the use of this file, please contact
 * Ushahidi developers at team@ushahidi.com.
 ******************************************************************************/

package org.addhen.smssync.database;

import org.addhen.smssync.models.Message;
import org.addhen.smssync.models.MessageResult;
import org.addhen.smssync.util.Logger;
import org.addhen.smssync.util.Util;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author eyedol
 */
public class MessagesContentProvider extends DbContentProvider implements
        IMessagesContentProvider, IMessagesSchema {

    private Cursor cursor;

    private List<Message> listMessages;

    private ContentValues initialValues;

    private final static String TAG = MessagesContentProvider.class.getSimpleName();

    /**
     * Initialize the connected database
     *
     * @param db The connected database object
     */
    public MessagesContentProvider(SQLiteDatabase db) {
        super(db);
    }

    @Override
    public int messagesCount() {

        if (Util.isHoneycomb()) {
            return (int) DatabaseUtils.queryNumEntries(mDb, TABLE);
        }

        // For API < 11
        try {
            String sql = "SELECT COUNT(*) FROM " + TABLE;
            SQLiteStatement statement = mDb.compileStatement(sql);
            if (statement != null) {
                return (int) statement.simpleQueryForLong();
            }
        } catch (SQLiteDoneException ex) {
            Logger.log(TAG, ex.getMessage());
        }
        return 0;
    }

    /*
     * (non-Javadoc)
     * @see
     * org.addhen.smssync.database.IMessagesContentProvider#addMessage(java
     * .util.List)
     */
    @Override
    public boolean addMessages(List<Message> messages) {
        try {
            mDb.beginTransaction();

            for (Message message : messages) {
                addMessage(message);
            }
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * @see
     * org.addhen.smssync.database.IMessagesContentProvider#addMessage(org.
     * addhen.smssync.models.MessageModel)
     */
    @Override
    public boolean addMessage(Message messages) {

        setContentValue(messages);
        String selectionClause = MESSAGE_UUID + " =?";
        String[] selectionArgs = {messages.getUuid()};

        if (super.update(TABLE, getContentValue(), selectionClause, selectionArgs) > 0) {
            return true;
        } else {
            initialValues.put(MESSAGE_UUID, messages.getUuid());
            return super.insert(TABLE, getContentValue()) > 0;
        }
    }

    /**
     * Delete message by UUID
     *
     * @param messageUuid The message unique id
     */
    @Override
    public boolean deleteMessagesByUuid(String messageUuid) {
        final String whereClause = MESSAGE_UUID + "= ?";
        final String whereArgs[] = { messageUuid };
        return super.delete(TABLE, whereClause, whereArgs) > 0;
    }

    /**
     * Delete all saved messages
     */
    @Override
    public boolean deleteAllMessages() {
        return super.delete(TABLE, "1", null) > 0;
    }

    @Override
    public List<MessageResult> fetchMessageResultsByUuid(List<String> messageUuid) {
        List<MessageResult> messageResults = new ArrayList<>();
        String selection = Database.SENT_MESSAGES_UUID + "= ?";
        if (null != messageUuid) {
            for (String uuid : messageUuid) {
                log("uuids_message " + uuid);
                String selectionArgs[] = { uuid };
                cursor = super.query(Database.SENT_MESSAGES_TABLE, Database.SENT_MESSAGES_COLUMNS, selection, selectionArgs, Database.SENT_MESSAGES_DATE
                        + " DESC");

                if (cursor != null) {
                    try {
                        while (cursor.moveToNext()) {
                            int sentResultCodeIndex = cursor.getColumnIndexOrThrow(Database.SENT_RESULT_CODE);
                            int sentResultMessageIndex = cursor.getColumnIndexOrThrow(Database.SENT_RESULT_MESSAGE);
                            int deliveryResultCodeIndex = cursor.getColumnIndexOrThrow(Database.DELIVERY_RESULT_CODE);
                            int deliveryResultMessageIndex = cursor.getColumnIndexOrThrow(Database.DELIVERY_RESULT_MESSAGE);

                            int sentResultCode = cursor.getInt(sentResultCodeIndex);
                            String sentResultMessage = cursor.getString(sentResultMessageIndex);
                            int deliveryResultCode = cursor.getInt(deliveryResultCodeIndex);
                            String deliveryResultMessage = cursor.getString(deliveryResultMessageIndex);
                            log("uuids_message messages " + " sentResultCode " + sentResultCode
                                    + " sentResultMessage " + sentResultMessage
                                    + " deliveryResultCode: " + deliveryResultCode
                                    + "deliveryResultCode " + deliveryResultMessage);
                            messageResults.add(new MessageResult(uuid, sentResultCode, sentResultMessage, deliveryResultCode, deliveryResultMessage));
                        }
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                }
            }
        }

        return messageResults;
    }

    /*
     * (non-Javadoc)
     * @see
     * org.addhen.smssync.database.IMessagesContentProvider#fetchMessagesById
     * (int)
     */
    @Override
    public List<Message> fetchMessagesByUuid(String messageUuid) {
        listMessages = new ArrayList<>();
        String selection = MESSAGE_UUID + "= ?";
        String selectionArgs[] = {
                messageUuid
        };
        cursor = super.query(TABLE, COLUMNS, selection, selectionArgs, DATE
                + " DESC");

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    Message message = cursorToEntity(cursor);
                    listMessages.add(message);

                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

        }

        return listMessages;
    }

    /*
     * (non-Javadoc)
     * @see
     * org.addhen.smssync.database.IMessagesContentProvider#fetchAllMessages()
     */
    @Override
    public List<Message> fetchAllMessages() {
        listMessages = new ArrayList<>();
        cursor = super.query(TABLE, COLUMNS, null, null, DATE + " DESC");

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    final Message message = cursorToEntity(cursor);
                    listMessages.add(message);

                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

        }

        return listMessages;
    }

    /*
     * (non-Javadoc)
     * @see
     * org.addhen.smssync.database.IMessagesContentProvider#fetchMessagesByLimit
     * (int)
     */
    @Override
    public List<Message> fetchMessagesByLimit(int limit) {
        listMessages = new ArrayList<>();
        cursor = super.query(TABLE, COLUMNS, null, null, MESSAGE_UUID + " DESC",
                String.valueOf(limit));
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    Message message = cursorToEntity(cursor);
                    listMessages.add(message);

                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

        }

        return listMessages;
    }

    /**
     * Initializes content values for the messages table.
     *
     * @param messages The message to be saved
     */
    private void setContentValue(Message messages) {
        initialValues = new ContentValues();
        initialValues.put(FROM, messages.getPhoneNumber());
        initialValues.put(BODY, messages.getMessage());
        initialValues.put(DATE, messages.getTimestamp());
        initialValues.put(TYPE, messages.getMessageType());
        
    }

    private ContentValues getContentValue() {
        return initialValues;
    }

    /**
     * Convert the cursor to the messages Model
     *
     * @param cursor The database curs object
     */
    @SuppressWarnings("unchecked")
    @Override
    protected Message cursorToEntity(Cursor cursor) {
        Message message = new Message();

        int messageUuidIndex;
        int fromIndex;
        int messageIndex;
        int dateIndex;
        int messageTypeIndex;
        int sentResultCodeIndex;
        int sentResultMessageIndex;
        int deliveryResultCodeIndex;
        int deliveryResultMessageIndex;

        if (cursor != null) {
            if (cursor.getColumnIndex(MESSAGE_UUID) != -1) {
                messageUuidIndex = cursor.getColumnIndexOrThrow(MESSAGE_UUID);
                message.setUuid(cursor.getString(messageUuidIndex));
            }

            if (cursor.getColumnIndex(FROM) != -1) {
                fromIndex = cursor.getColumnIndexOrThrow(FROM);
                message.setPhoneNumber(cursor.getString(fromIndex));
            }

            if (cursor.getColumnIndex(BODY) != -1) {
                messageIndex = cursor.getColumnIndexOrThrow(BODY);
                message.setMessage(cursor.getString(messageIndex));
            }

            if (cursor.getColumnIndex(DATE) != -1) {
                dateIndex = cursor.getColumnIndexOrThrow(DATE);
                message.setTimestamp(cursor.getString(dateIndex));
            }

            if (cursor.getColumnIndex(TYPE) != -1) {
                messageTypeIndex = cursor.getColumnIndexOrThrow(TYPE);
                message.setMessageType(cursor.getInt(messageTypeIndex));
            }

            if (cursor.getColumnIndex(SENT_RESULT_CODE) != -1) {
                sentResultCodeIndex = cursor.getColumnIndexOrThrow(SENT_RESULT_CODE);
                message.setSentResultCode(cursor.getInt(sentResultCodeIndex));
            }

            if (cursor.getColumnIndex(SENT_RESULT_MESSAGE) != -1) {
                sentResultMessageIndex = cursor.getColumnIndexOrThrow(SENT_RESULT_MESSAGE);
                message.setSentResultMessage(cursor.getString(sentResultMessageIndex));
            }

            if (cursor.getColumnIndex(DELIVERY_RESULT_CODE) != -1) {
                deliveryResultCodeIndex = cursor.getColumnIndexOrThrow(DELIVERY_RESULT_CODE);
                message.setDeliveryResultCode(cursor.getInt(deliveryResultCodeIndex));
            }

            if (cursor.getColumnIndex(DELIVERY_RESULT_MESSAGE) != -1) {
                deliveryResultMessageIndex = cursor.getColumnIndexOrThrow(DELIVERY_RESULT_MESSAGE);
                message.setDeliveryResultMessage(cursor.getString(deliveryResultMessageIndex));
            }
        }
        return message;
    }
}
