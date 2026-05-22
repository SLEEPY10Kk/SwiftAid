const {onDocumentCreated, onDocumentUpdated} = require("firebase-functions/v2/firestore");
const {logger} = require("firebase-functions");
const admin = require("firebase-admin");
const geofire = require("geofire-common");
const twilio = require("twilio");

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

const SOS_COLLECTION = "sos_events";
const VOLUNTEER_COLLECTION = "volunteers";
const DEFAULT_RADIUS_METERS = 2000;
const MAX_FCM_BATCH = 500;

exports.notifyNearbyVolunteers = onDocumentCreated(`${SOS_COLLECTION}/{sosId}`, async (event) => {
  const sosId = event.params.sosId;
  const sos = event.data.data();
  const lat = Number(sos.lat);
  const lng = Number(sos.lng);
  const radiusMeters = Number(sos.radiusMeters || DEFAULT_RADIUS_METERS);

  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    logger.warn("SOS missing coordinates", {sosId});
    return;
  }

  const volunteers = await findNearbyVolunteers({
    lat,
    lng,
    radiusMeters,
    excludeInstallationIds: [sos.victimInstallationId].filter(Boolean),
  });

  const mapsUrl = googleMapsLink(lat, lng);
  const messages = [];
  for (const volunteer of volunteers) {
    for (const token of volunteer.tokens) {
      messages.push({
        token,
        data: {
          type: "sos_alert",
          sos_id: sosId,
          lat: String(lat),
          lng: String(lng),
          distance_meters: String(Math.round(volunteer.distanceMeters)),
          maps_url: mapsUrl,
        },
        android: {
          priority: "high",
          ttl: 60 * 1000,
          notification: {
            channelId: "swift_aid_volunteer_alert",
            priority: "max",
            sound: "default",
          },
        },
      });
    }
  }

  const sendResults = await sendMessagesInChunks(messages);
  await event.data.ref.set({
    notifiedVolunteerIds: volunteers.map((volunteer) => volunteer.id),
    notifiedCount: messages.length,
    notificationSentAt: admin.firestore.FieldValue.serverTimestamp(),
    fcmSuccessCount: sendResults.successCount,
    fcmFailureCount: sendResults.failureCount,
  }, {merge: true});

  logger.info("SOS volunteer notification completed", {
    sosId,
    volunteerCount: volunteers.length,
    tokenCount: messages.length,
    ...sendResults,
  });
});

exports.handleVolunteerAccepted = onDocumentUpdated(`${SOS_COLLECTION}/{sosId}`, async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();

  if (before.status === "accepted" || after.status !== "accepted") {
    return;
  }

  const sosId = event.params.sosId;
  const lat = Number(after.lat);
  const lng = Number(after.lng);
  const radiusMeters = Number(after.radiusMeters || DEFAULT_RADIUS_METERS);
  const acceptedInstallationId = after.acceptedByInstallationId;

  if (Number.isFinite(lat) && Number.isFinite(lng)) {
    const volunteers = await findNearbyVolunteers({
      lat,
      lng,
      radiusMeters,
      excludeInstallationIds: [
        acceptedInstallationId,
        after.victimInstallationId,
      ].filter(Boolean),
    });

    const dismissMessages = volunteers.flatMap((volunteer) =>
      volunteer.tokens.map((token) => ({
        token,
        data: {
          type: "sos_dismiss",
          sos_id: sosId,
        },
        android: {
          priority: "high",
          ttl: 30 * 1000,
        },
      }))
    );

    await sendMessagesInChunks(dismissMessages);
  }

  const twilioResult = await sendTwilioHandoffSms(after);
  await event.data.after.ref.set({
    handoffCompletedAt: admin.firestore.FieldValue.serverTimestamp(),
    twilioStatus: twilioResult,
  }, {merge: true});
});

async function findNearbyVolunteers({lat, lng, radiusMeters, excludeInstallationIds}) {
  const center = [lat, lng];
  const bounds = geofire.geohashQueryBounds(center, radiusMeters);
  const seen = new Map();

  await Promise.all(bounds.map(async ([start, end]) => {
    const snapshot = await db.collection(VOLUNTEER_COLLECTION)
      .where("volunteer_mode", "==", true)
      .orderBy("geohash")
      .startAt(start)
      .endAt(end)
      .get();

    snapshot.forEach((doc) => {
      if (excludeInstallationIds.includes(doc.id)) return;
      const volunteer = doc.data();
      const vLat = Number(volunteer.lat);
      const vLng = Number(volunteer.lng);
      if (!Number.isFinite(vLat) || !Number.isFinite(vLng)) return;

      const distanceMeters = geofire.distanceBetween(center, [vLat, vLng]) * 1000;
      if (distanceMeters > radiusMeters) return;

      const tokens = Array.isArray(volunteer.fcmTokens) ?
        volunteer.fcmTokens.filter(Boolean) :
        [volunteer.fcmToken].filter(Boolean);

      if (tokens.length === 0) return;
      seen.set(doc.id, {
        id: volunteer.id || doc.id,
        installationId: doc.id,
        distanceMeters,
        tokens,
      });
    });
  }));

  return [...seen.values()];
}

async function sendMessagesInChunks(messages) {
  let successCount = 0;
  let failureCount = 0;

  for (let i = 0; i < messages.length; i += MAX_FCM_BATCH) {
    const chunk = messages.slice(i, i + MAX_FCM_BATCH);
    if (chunk.length === 0) continue;
    const response = await messaging.sendEach(chunk);
    successCount += response.successCount;
    failureCount += response.failureCount;
  }

  return {successCount, failureCount};
}

async function sendTwilioHandoffSms(sos) {
  const accountSid = process.env.TWILIO_ACCOUNT_SID;
  const authToken = process.env.TWILIO_AUTH_TOKEN;
  const fromNumber = process.env.TWILIO_FROM_NUMBER;
  const emergencyNumbers = (process.env.EMERGENCY_NUMBERS || "")
    .split(",")
    .map((number) => number.trim())
    .filter(Boolean);

  if (!accountSid || !authToken || !fromNumber || emergencyNumbers.length === 0) {
    logger.warn("Twilio handoff skipped because environment variables are incomplete");
    return "skipped_missing_config";
  }

  const volunteer = sos.volunteer || {};
  const mapsUrl = googleMapsLink(Number(sos.lat), Number(sos.lng));
  const body = `CRITICAL: Accident detected at ${mapsUrl}. A registered volunteer is en route. ` +
    `Volunteer Name: ${volunteer.name || "Unknown"}, ID: ${volunteer.id || sos.acceptedBy || "Unknown"}, ` +
    `Contact: ${volunteer.phone || "Unknown"}.`;

  const client = twilio(accountSid, authToken);
  await Promise.all(
    emergencyNumbers.map((to) => client.messages.create({
      body,
      from: fromNumber,
      to,
    }))
  );

  return "sent";
}

function googleMapsLink(lat, lng) {
  return `https://maps.google.com/?q=${lat.toFixed(6)},${lng.toFixed(6)}`;
}
