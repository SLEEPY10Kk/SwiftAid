const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { logger } = require("firebase-functions");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const db = getFirestore();
const TARGET_RADIUS_METERS = 20_000;
// Hard cap enforced by the Admin SDK on a single sendEachForMulticast call.
const FCM_MULTICAST_LIMIT = 500;
const VALID_SERVICE_TYPES = new Set(["POLICE", "HOSPITAL"]);
const EARTH_RADIUS_METERS = 6_371_000;

exports.enrichSosTargetResponders = onDocumentCreated(
  "sos_events/{eventId}",
  async (event) => {
    const snapshot = event.data;
    const eventId = event.params.eventId;

    if (!snapshot) {
      logger.warn("SOS create trigger had no snapshot", { eventId });
      return;
    }

    const sos = snapshot.data();
    if (sos.status && sos.status !== "ACTIVE") {
      logger.info("Skipping non-active SOS targeting", {
        eventId,
        status: sos.status,
      });
      return;
    }

    const latitude = numberValue(sos.latitude ?? sos.lat);
    const longitude = numberValue(sos.longitude ?? sos.lng);

    if (latitude === null || longitude === null || (latitude === 0 && longitude === 0)) {
      await snapshot.ref.set(
        {
          responderTargeting: {
            computed: false,
            radiusMeters: TARGET_RADIUS_METERS,
            targetCount: 0,
            error: "MISSING_VALID_COORDINATES",
            computedAt: FieldValue.serverTimestamp(),
          },
        },
        { merge: true },
      );
      logger.warn("Cannot target responders without valid SOS coordinates", {
        eventId,
        latitude,
        longitude,
      });
      return;
    }

    const respondersSnapshot = await db
      .collection("responders")
      .where("active", "==", true)
      .get();

    const targetResponders = respondersSnapshot.docs
      .map((doc) => responderFromDocument(doc, latitude, longitude))
      .filter((responder) => responder !== null)
      .filter((responder) => responder.distanceMeters <= TARGET_RADIUS_METERS)
      .sort((left, right) => {
        if (left.serviceType !== right.serviceType) {
          return left.serviceType.localeCompare(right.serviceType);
        }
        return left.distanceMeters - right.distanceMeters;
      });

    const nearestHospital = nearestByType(targetResponders, "HOSPITAL");
    const nearestPolice = nearestByType(targetResponders, "POLICE");
    const targetResponderIds = targetResponders.map((responder) => responder.id);
    const targetServiceTypes = Array.from(
      new Set(targetResponders.map((responder) => responder.serviceType)),
    );

    const fcmTargets = targetResponders
      .map((responder) => responderWithToken(responder))
      .filter((responder) => responder.fcmToken !== "");

    let notificationResult = {
      attempted: false,
      tokenCount: 0,
      successCount: 0,
      failureCount: 0,
      notifiedResponderIds: [],
      error: targetResponders.length === 0 ? "NO_TARGET_RESPONDERS" : "NO_FCM_TOKENS",
      sentAt: FieldValue.serverTimestamp(),
    };

    if (fcmTargets.length > 0) {
      // sendEachForMulticast rejects more than FCM_MULTICAST_LIMIT tokens in one call. An
      // unchunked send would throw once a dense area had that many responders in range, and the
      // throw would abort the whole trigger - so the SOS document would never receive its
      // nearestResponders/routing fields either. Chunk instead, and let one failed batch cost
      // only that batch.
      const payload = buildSosAlertPayload(sos, eventId, latitude, longitude);
      let successCount = 0;
      let failureCount = 0;
      let failedIds = [];

      for (let i = 0; i < fcmTargets.length; i += FCM_MULTICAST_LIMIT) {
        const batch = fcmTargets.slice(i, i + FCM_MULTICAST_LIMIT);
        try {
          const response = await getMessaging().sendEachForMulticast({
            tokens: batch.map((responder) => responder.fcmToken),
            data: payload,
            android: {
              priority: "high",
            },
          });
          successCount += response.successCount;
          failureCount += response.failureCount;
          failedIds = failedIds.concat(failedResponderIds(batch, response.responses));
        } catch (error) {
          // Keep going: notifying some responders beats notifying none.
          failureCount += batch.length;
          failedIds = failedIds.concat(batch.map((responder) => responder.id));
          logger.error("FCM multicast batch failed", {
            eventId,
            batchStart: i,
            batchSize: batch.length,
            error: error.message,
          });
        }
      }

      notificationResult = {
        attempted: true,
        tokenCount: fcmTargets.length,
        successCount,
        failureCount,
        notifiedResponderIds: fcmTargets.map((responder) => responder.id),
        failedResponderIds: failedIds,
        sentAt: FieldValue.serverTimestamp(),
      };
    }

    await snapshot.ref.set(
      {
        nearestResponders: targetResponders.map(toFirestoreResponder),
        targetResponderIds,
        targetServiceTypes,
        nearestHospitalId: nearestHospital?.id ?? "",
        nearestHospitalName: nearestHospital?.name ?? "",
        nearestHospitalPhone: nearestHospital?.phoneNumber ?? "",
        nearestHospitalDistanceMeters: nearestHospital?.distanceMeters ?? 0,
        nearestHospitalRouteUrl: nearestHospital?.routeUrl ?? "",
        nearestPoliceId: nearestPolice?.id ?? "",
        nearestPoliceName: nearestPolice?.name ?? "",
        nearestPolicePhone: nearestPolice?.phoneNumber ?? "",
        nearestPoliceDistanceMeters: nearestPolice?.distanceMeters ?? 0,
        nearestPoliceRouteUrl: nearestPolice?.routeUrl ?? "",
        responderTargeting: {
          computed: true,
          radiusMeters: TARGET_RADIUS_METERS,
          targetCount: targetResponderIds.length,
          targetResponderIds,
          targetServiceTypes,
          computedAt: FieldValue.serverTimestamp(),
        },
        responderNotification: notificationResult,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );

    logger.info("SOS responder targeting enriched", {
      eventId,
      targetCount: targetResponderIds.length,
      targetResponderIds,
      notificationAttempted: notificationResult.attempted,
      notificationSuccessCount: notificationResult.successCount,
      notificationFailureCount: notificationResult.failureCount,
    });
  },
);

function responderFromDocument(doc, sosLatitude, sosLongitude) {
  const data = doc.data();
  const serviceType = String(data.serviceType || "").toUpperCase();
  const name = String(data.name || "");
  const phoneNumber = String(data.phoneNumber || "");
  const latitude = numberValue(data.latitude);
  const longitude = numberValue(data.longitude);

  if (
    !VALID_SERVICE_TYPES.has(serviceType) ||
    name.trim() === "" ||
    phoneNumber.trim() === "" ||
    latitude === null ||
    longitude === null
  ) {
    return null;
  }

  const distanceMeters = haversineMeters(
    sosLatitude,
    sosLongitude,
    latitude,
    longitude,
  );

  return {
    id: doc.id,
    serviceType,
    name,
    phoneNumber,
    latitude,
    longitude,
    fcmToken: String(data.fcmToken || "").trim(),
    distanceMeters,
    routeUrl: routeUrl(sosLatitude, sosLongitude, latitude, longitude),
  };
}

function responderWithToken(responder) {
  return {
    id: responder.id,
    fcmToken: responder.fcmToken,
  };
}

function nearestByType(responders, serviceType) {
  return responders
    .filter((responder) => responder.serviceType === serviceType)
    .sort((left, right) => left.distanceMeters - right.distanceMeters)[0];
}

function toFirestoreResponder(responder) {
  return {
    id: responder.id,
    serviceType: responder.serviceType,
    name: responder.name,
    phoneNumber: responder.phoneNumber,
    latitude: responder.latitude,
    longitude: responder.longitude,
    distanceMeters: responder.distanceMeters,
    routeUrl: responder.routeUrl,
  };
}

function numberValue(value) {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function buildSosAlertPayload(sos, eventId, latitude, longitude) {
  return {
    type: "sos_alert",
    sos_id: eventId,
    victim_name: stringValue(sos.victimName, "SwiftAid User"),
    severity: stringValue(sos.severity, "HIGH"),
    address: stringValue(sos.address ?? sos.locationSource, "Unknown location"),
    lat: String(latitude),
    lng: String(longitude),
    distance_meters: "0",
  };
}

function stringValue(value, fallback) {
  if (value === null || value === undefined) {
    return fallback;
  }
  const text = String(value).trim();
  return text === "" ? fallback : text;
}

function failedResponderIds(responders, responses) {
  return responses
    .map((response, index) => response.success ? null : responders[index]?.id)
    .filter((id) => id);
}

function haversineMeters(startLat, startLng, endLat, endLng) {
  const dLat = toRadians(endLat - startLat);
  const dLng = toRadians(endLng - startLng);
  const lat1 = toRadians(startLat);
  const lat2 = toRadians(endLat);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function toRadians(value) {
  return (value * Math.PI) / 180;
}

function routeUrl(emergencyLat, emergencyLng, responderLat, responderLng) {
  return "https://www.google.com/maps/dir/?api=1" +
    `&origin=${responderLat},${responderLng}` +
    `&destination=${emergencyLat},${emergencyLng}` +
    "&travelmode=driving";
}
