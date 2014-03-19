/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.session;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.SparseArray;

import com.android.camera2.R;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A notification manager for computation events.
 * <p>
 * It maintains a single in-progress notification that indicates processing
 * progress and status. Once processing is complete, a new completion
 * notification is added. The completion notification triggers an intent on
 * click.
 * <p>
 * Calling {@link #notifyStart(int)} while there is a current notification is a
 * no-op. Calling {@link #notifyCompletion}, {@link #setProgress} or
 * {@link #setStatus} when there is no current notification is a no-op.
 * <p>
 * The expected use case is as follows:
 *
 * <pre>
 * {@code
 * manager = new ProcessingNotificationManager(context);
 * manager.notifyStart(resourceId);
 * manager.setProgress(...);  // Can be called multiple times.
 * manager.setStatus(...);  // Can be called multiple times.
 * manager.notifyCompletion();
 * }
 * </pre>
 */
public class ProcessingNotificationManager {
    private static final int FIRST_NOTIFICATION_ID = 0;
    private static AtomicInteger sUniqueNotificationId;

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final SparseArray<CameraNotification> mPendingNotifications =
            new SparseArray<CameraNotification>();
    private CameraNotification mCurrentNotification;

    /**
     * Creates a new {@code ProcessingNotificationManager} with a
     * {@link Context}.
     */
    public ProcessingNotificationManager(Context context) {
        if (sUniqueNotificationId == null) {
            sUniqueNotificationId = new AtomicInteger(FIRST_NOTIFICATION_ID);
        }
        this.mContext = context;
        this.mNotificationManager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancelAll();
    }

    /**
     * @param progress sets the progress in the in-progress notification. The
     *            expected value is in [0, 100]
     * @return whether the update was successful
     */
    public boolean setProgress(int progress, int notificationId) {
        CameraNotification notification = getNotification(notificationId);
        if (notification == null) {
            return false;
        }
        Notification.Builder builder = notification.builder;
        builder.setProgress(100, progress, false);
        if (mCurrentNotification == notification) {
            mNotificationManager.notify(notificationId, buildNotification(builder));
        }
        return true;
    }

    /**
     * @param status sets the status message in the in-progress notification
     * @return whether the update was successful
     */
    public boolean setStatus(CharSequence status, int notificationId) {
        CameraNotification notification = getNotification(notificationId);
        if (notification == null) {
            return false;
        }
        Notification.Builder builder = notification.builder;
        builder.setContentText(status);
        if (mCurrentNotification == notification) {
            mNotificationManager.notify(notificationId, buildNotification(builder));
        }
        return true;
    }

    /**
     * Creates a new notification indicating that a new computation has started.
     * It will initialize the in-progress notification and add it to the
     * notification bar when all previous notifications have completed.
     *
     * It is possible that the notification may not be shown if it is completed
     * or canceled before its opportunity to be shown.
     *
     * Only one notification is shown at a time.
     *
     * @param statusMessage the status message to show on start
     * @return The ID of the notification.
     */
    public int notifyStart(CharSequence statusMessage) {
        Notification.Builder builder = createInProgressNotificationBuilder(statusMessage);
        // Increment the global notification id to make sure we have a unique
        // id.
        int notificationId = sUniqueNotificationId.incrementAndGet();
        CameraNotification newNotification = new CameraNotification();
        newNotification.builder = builder;
        newNotification.notificationId = notificationId;
        mPendingNotifications.put(notificationId, newNotification);

        displayNextNotification();
        return notificationId;
    }

    private void displayNextNotification() {
        if (mCurrentNotification == null) {
            if (mPendingNotifications.size() > 0) {
                mCurrentNotification = mPendingNotifications.valueAt(0);
                mPendingNotifications.removeAt(0);
                Notification.Builder builder = mCurrentNotification.builder;
                mNotificationManager.notify(
                        mCurrentNotification.notificationId, buildNotification(builder));
            }
        }
    }

    private CameraNotification getNotification(int notificationId) {
        if (mCurrentNotification.notificationId == notificationId) {
            return  mCurrentNotification;
        }
        return mPendingNotifications.get(notificationId);
    }

    /**
     * Notify a computation is completed. It will remove the in-progress
     * notification.
     *
     * This may cause a notification to never be seen if it has not been
     * shown yet.
     */
    public void notifyCompletion(int notificationId) {
        CameraNotification notification = getNotification(notificationId);
        if (notification != null) {
            if (mCurrentNotification.notificationId == notificationId) {
                mNotificationManager.cancel(notificationId);
                mCurrentNotification = null;
            } else {
                mPendingNotifications.remove(notificationId);
            }
        }
        displayNextNotification();
    }

    /**
     * Cancel the in-progress notification. Any completion notification will be
     * left intact.
     */
    public void cancel(int notificationId) {
        notifyCompletion(notificationId);
    }

    /**
     * Creates a notification to indicate that a computation is in progress.
     *
     * @param statusMessage a human readable message indicating the current
     *            progress status.
     */
    Notification.Builder createInProgressNotificationBuilder(CharSequence statusMessage) {
        return new Notification.Builder(mContext)
                .setSmallIcon(R.drawable.ic_notification)
                .setWhen(System.currentTimeMillis())
                .setOngoing(true)
                .setContentTitle(mContext.getText(R.string.app_name))
                .setProgress(100, 0, false)
                .setContentText(statusMessage);
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    static Notification buildNotification(Notification.Builder builder) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            return builder.build();
        }
        return builder.getNotification();
    }

    protected static final class CameraNotification {
        public Notification.Builder builder;
        public int notificationId;
    }
}