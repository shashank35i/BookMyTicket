const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

// Utility function to validate FCM token
async function isValidFcmToken(token) {
  try {
    await admin.messaging().send({ token, data: { test: 'validation' } }, { dryRun: true });
    return true;
  } catch (error) {
    console.error('Invalid FCM token:', error.code, error.message);
    return false;
  }
}

// Send push notification for tourist notifications (create and update)
exports.sendTouristNotification = functions.database
  .ref('/tourist_notifications/{userId}/{notificationId}')
  .onWrite(async (change, context) => {
    // Skip if it's a delete event
    if (!change.after.exists()) {
      console.log(`Notification ${context.params.notificationId} deleted, skipping`);
      return null;
    }

    const notificationData = change.after.val();
    const userId = context.params.userId;
    const notificationId = context.params.notificationId;

    // Skip if notification is already seen
    if (notificationData.seen) {
      console.log(`Skipping seen notification ${notificationId} for user ${userId}`);
      return null;
    }

    // Get user's FCM token
    const userRef = admin.database().ref(`users/${userId}/fcmToken`);
    const userSnapshot = await userRef.once('value');
    const fcmToken = userSnapshot.val();

    if (!fcmToken) {
      console.log(`No FCM token found for user ${userId}`);
      return null;
    }

    // Validate FCM token
    if (!(await isValidFcmToken(fcmToken))) {
      console.log(`Removing invalid FCM token for user ${userId}`);
      await userRef.remove();
      return null;
    }

    // Construct notification message
    let messageBody;
    if (notificationData.vehicleNumber) {
      messageBody = `${change.before.exists() ? 'Updated' : 'New'} ${notificationData.type.replace('_', ' ')} for vehicle ${notificationData.vehicleNumber || 'Unknown'}`;
    } else {
      messageBody = `${change.before.exists() ? 'Updated' : 'New'} ${notificationData.type.replace('_', ' ')} for ticket ${notificationData.ticketId || 'Unknown'}`;
    }

    const payload = {
      notification: {
        title: change.before.exists() ? 'Notification Updated' : 'New Notification',
        body: messageBody,
        click_action: 'FLUTTER_NOTIFICATION_CLICK',
      },
      data: {
        notificationId: notificationId,
        type: notificationData.type,
        userId: userId,
        paymentId: notificationData.paymentId || '',
        vehicleNumber: notificationData.vehicleNumber || '',
        ticketId: notificationData.ticketId || '',
        place: notificationData.place || '',
        amount: notificationData.amount || '',
      },
    };

    // Send notification
    try {
      await admin.messaging().send({ token: fcmToken, ...payload });
      console.log(`Notification sent to user ${userId} for ${notificationId}`);
    } catch (error) {
      console.error(`Error sending notification to ${userId}:`, error);
      if (error.code === 'messaging/registration-token-not-registered') {
        console.log(`Removing stale FCM token for user ${userId}`);
        await userRef.remove();
      }
    }

    return null;
  });

// Send push notification for placeadmin notifications (create and update)
exports.sendPlaceAdminNotification = functions.database
  .ref('/placeadmin_notifications/{userId}/{notificationId}')
  .onWrite(async (change, context) => {
    // Skip if it's a delete event
    if (!change.after.exists()) {
      console.log(`Notification ${context.params.notificationId} deleted, skipping`);
      return null;
    }

    const notificationData = change.after.val();
    const userId = context.params.userId;
    const notificationId = context.params.notificationId;

    // Only send notification for SUCCESS status
    if (notificationData.status !== 'SUCCESS') {
      console.log(`Skipping non-SUCCESS notification ${notificationId} for user ${userId}`);
      return null;
    }

    // Get user's FCM token
    const userRef = admin.database().ref(`users/${userId}/fcmToken`);
    const userSnapshot = await userRef.once('value');
    const fcmToken = userSnapshot.val();

    if (!fcmToken) {
      console.log(`No FCM token found for user ${userId}`);
      return null;
    }

    // Validate FCM token
    if (!(await isValidFcmToken(fcmToken))) {
      console.log(`Removing invalid FCM token for user ${userId}`);
      await userRef.remove();
      return null;
    }

    // Construct notification message
    const messageBody = `${change.before.exists() ? 'Updated' : 'New'} ${notificationData.type.replace('_', ' ')} notification`;

    const payload = {
      notification: {
        title: change.before.exists() ? 'PlaceAdmin Notification Updated' : 'PlaceAdmin Update',
        body: messageBody,
        click_action: 'FLUTTER_NOTIFICATION_CLICK',
      },
      data: {
        notificationId: notificationId,
        type: notificationData.type,
        userId: userId,
        details: notificationData.details || '',
        timestamp: notificationData.timestamp ? notificationData.timestamp.toString() : '',
      },
    };

    // Send notification
    try {
      await admin.messaging().send({ token: fcmToken, ...payload });
      console.log(`Notification sent to user ${userId} for ${notificationId}`);
    } catch (error) {
      console.error(`Error sending notification to ${userId}:`, error);
      if (error.code === 'messaging/registration-token-not-registered') {
        console.log(`Removing stale FCM token for user ${userId}`);
        await userRef.remove();
      }
    }

    return null;
  });