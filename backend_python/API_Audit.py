"""
RoadSOS API — FastAPI
Mappls (MapmyIndia) + Google Places + OSM Overpass

Start:  uvicorn main:app --host 0.0.0.0 --port 8000
Docs:   http://localhost:8000/docs

Keys required in .env:
    MAPPLS_CLIENT_ID=...
    MAPPLS_CLIENT_SECRET=...
    GOOGLE_API_KEY=...
"""

from __future__ import annotations

import asyncio
import os
import time
import statistics
from math import radians, sin, cos, sqrt, atan2

import httpx
from fastapi import FastAPI, Query
from rapidfuzz import fuzz
from dotenv import load_dotenv

# ── env ───────────────────────────────────────────────────────────────────────
for _f in (".env", "Keys.env"):
    if os.path.exists(_f):
        load_dotenv(_f)
        break

MAPPLS_CLIENT_ID     = os.getenv("MAPPLS_CLIENT_ID", "")
MAPPLS_CLIENT_SECRET = os.getenv("MAPPLS_CLIENT_SECRET", "")
GOOGLE_API_KEY       = os.getenv("GOOGLE_API_KEY", "")

DEFAULT_RADIUS = 10000

# ── Overpass mirrors ──────────────────────────────────────────────────────────
OVERPASS_ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://lz4.overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]

# ── In-memory caches ──────────────────────────────────────────────────────────
_MAPPLS_TOKEN_CACHE:  dict = {"token": None, "expires_at": 0}
_MAPPLS_NEARBY_CACHE: dict = {}
_GOOGLE_CACHE:        dict = {}
_POI_CACHE:           dict = {}

POI_CACHE_TTL = 1800  # 30 minutes

MAPPLS_KEYWORDS   = "hospital;school;police;bank;post office;government;fire station;pharmacy;fuel"
MAPPLS_TOKEN_URL  = "https://outpost.mapmyindia.com/api/security/oauth/token"
MAPPLS_NEARBY_URL = "https://atlas.mappls.com/api/places/nearby/json"

# ── Canonical type normalizer ─────────────────────────────────────────────────
TYPE_ALIASES: dict[str, str] = {
    # Medical
    "hospital":                "hospital",
    "clinic":                  "hospital",
    "doctors":                 "hospital",
    "healthcare":              "hospital",
    "nursing_home":            "hospital",
    "medical_center":          "hospital",
    "HLTHSP":                  "hospital",
    # Police
    "police":                  "police",
    "PLCSTN":                  "police",
    # Fire
    "fire_station":            "fire_station",
    "FIRSTN":                  "fire_station",
    # Pharmacy
    "pharmacy":                "pharmacy",
    "chemist":                 "pharmacy",
    "drugstore":               "pharmacy",
    "MEDST":                   "pharmacy",
    # Fuel
    "fuel":                    "fuel",
    "gas_station":             "fuel",
    "PETROL":                  "fuel",
    # Schools
    "school":                  "school",
    "primary_school":          "school",
    "secondary_school":        "school",
    "SCHOOL":                  "school",
    # Banks / ATM
    "bank":                    "bank",
    "atm":                     "bank",
    "BANK":                    "bank",
    # Government
    "local_government_office": "government",
    "government":              "government",
    "townhall":                "government",
    "GOVOFF":                  "government",
    # Post
    "post_office":             "post_office",
    "POSOFF":                  "post_office",
}

EMERGENCY_TYPES = {"hospital", "police", "fire_station", "pharmacy", "fuel"}


def normalize_type(raw: str) -> str:
    return TYPE_ALIASES.get(raw.strip(), raw.lower().strip())


# ══════════════════════════════════════════════════════════════════════════════
# Helpers
# ══════════════════════════════════════════════════════════════════════════════

def haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    R = 6_371_000
    phi1, phi2 = radians(lat1), radians(lat2)
    dphi    = radians(lat2 - lat1)
    dlambda = radians(lon2 - lon1)
    a = sin(dphi / 2) ** 2 + cos(phi1) * cos(phi2) * sin(dlambda / 2) ** 2
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))


def name_similarity(a: str, b: str) -> float:
    return fuzz.token_sort_ratio(str(a).lower(), str(b).lower()) / 100.0


def _extract_coords(p: dict, fallback_lat: float, fallback_lon: float):
    plat, plon = None, None
    for k in ("latitude", "lat", "y"):
        v = p.get(k)
        if v not in (None, "", "0", 0):
            try: plat = float(v); break
            except (TypeError, ValueError): pass
    for k in ("longitude", "lon", "lng", "x"):
        v = p.get(k)
        if v not in (None, "", "0", 0):
            try: plon = float(v); break
            except (TypeError, ValueError): pass
    return plat or fallback_lat, plon or fallback_lon


# ══════════════════════════════════════════════════════════════════════════════
# Deduplication
# ══════════════════════════════════════════════════════════════════════════════

def deduplicate(places: list, distance_threshold_m: int = 100) -> list:
    """
    Merge results from multiple sources.
    Two places within distance_threshold_m are considered the same.
    Phone numbers from any source are preserved on merge.
    """
    merged = []
    for place in places:
        duplicate = False
        for existing in merged:
            if haversine(
                place["lat"], place["lon"],
                existing["lat"], existing["lon"]
            ) < distance_threshold_m:
                # Keep longer name
                if len(str(place.get("name", ""))) > len(str(existing.get("name", ""))):
                    existing["name"] = place["name"]
                # Merge sources
                existing_sources = existing.get("sources", [existing.get("source", "unknown")])
                new_source = place.get("source", "unknown")
                if new_source not in existing_sources:
                    existing_sources.append(new_source)
                existing["sources"] = existing_sources
                existing.pop("source", None)
                # Preserve phone if new entry has one and existing doesn't
                if not existing.get("phone") and place.get("phone"):
                    existing["phone"] = place["phone"]
                duplicate = True
                break
        if not duplicate:
            p = dict(place)
            p["sources"] = [p.pop("source", "unknown")]
            raw = p.get("type") or p.get("amenity") or p.get("category", "unknown")
            p["type"] = normalize_type(str(raw))
            merged.append(p)
    return merged


# ══════════════════════════════════════════════════════════════════════════════
# Mappls
# ══════════════════════════════════════════════════════════════════════════════

async def _mappls_token() -> str:
    now = time.time()
    if _MAPPLS_TOKEN_CACHE["token"] and now < _MAPPLS_TOKEN_CACHE["expires_at"]:
        return _MAPPLS_TOKEN_CACHE["token"]
    if not MAPPLS_CLIENT_ID or not MAPPLS_CLIENT_SECRET:
        return ""
    async with httpx.AsyncClient(timeout=15) as client:
        r = await client.post(MAPPLS_TOKEN_URL, params={
            "grant_type":    "client_credentials",
            "client_id":     MAPPLS_CLIENT_ID,
            "client_secret": MAPPLS_CLIENT_SECRET,
        })
        r.raise_for_status()
        data       = r.json()
        token      = data.get("access_token", "")
        expires_in = int(data.get("expires_in", 86400))
        _MAPPLS_TOKEN_CACHE["token"]      = token
        _MAPPLS_TOKEN_CACHE["expires_at"] = now + expires_in - 60
        return token


async def fetch_mappls_nearby(lat: float, lon: float, radius: int = DEFAULT_RADIUS) -> list:
    cache_key = (round(lat, 4), round(lon, 4))
    if cache_key in _MAPPLS_NEARBY_CACHE:
        return _MAPPLS_NEARBY_CACHE[cache_key]
    token = await _mappls_token()
    if not token:
        _MAPPLS_NEARBY_CACHE[cache_key] = []
        return []
    async with httpx.AsyncClient(timeout=20) as client:
        r = await client.get(MAPPLS_NEARBY_URL, params={
            "keywords":    MAPPLS_KEYWORDS,
            "refLocation": f"{lat},{lon}",
            "radius":      int(radius),
            "region":      "IND",
            "sortBy":      "dist:asc",
        }, headers={"Authorization": f"bearer {token}"})
        r.raise_for_status()
        if not r.content.strip():
            _MAPPLS_NEARBY_CACHE[cache_key] = []
            return []
        suggestions = r.json().get("suggestedLocations", [])
    results = []
    for p in suggestions:
        plat, plon = _extract_coords(p, lat, lon)
        raw_type   = ";".join(p.get("keywords", []))
        results.append({
            "name":       p.get("placeName", "Unknown"),
            "address":    p.get("placeAddress", ""),
            "lat":        plat,
            "lon":        plon,
            "eloc":       p.get("eLoc", ""),
            "type":       normalize_type(raw_type.split(";")[0] if raw_type else "unknown"),
            "phone":      None,   # Mappls Nearby does not return phone numbers
            "distance_m": p.get("distance"),
            "source":     "mappls",
        })
    _MAPPLS_NEARBY_CACHE[cache_key] = results
    return results


# ══════════════════════════════════════════════════════════════════════════════
# Google Places (New) — includes phone numbers
# ══════════════════════════════════════════════════════════════════════════════

async def fetch_google_nearby(lat: float, lon: float) -> list:
    cache_key = (round(lat, 4), round(lon, 4))
    if cache_key in _GOOGLE_CACHE:
        return _GOOGLE_CACHE[cache_key]
    if not GOOGLE_API_KEY:
        return []
    # Only verified valid type names for Places API (New)
    # https://developers.google.com/maps/documentation/places/web-service/place-types
    payload = {
        "includedTypes": [
            "hospital",
            "pharmacy",
            "police",
            "fire_station",
            "school",
            "bank",
            "gas_station",
            "post_office",
            "local_government_office",
        ],
        "maxResultCount": 20,
        "locationRestriction": {
            "circle": {
                "center": {"latitude": lat, "longitude": lon},
                "radius": float(DEFAULT_RADIUS),
            }
        },
    }
    async with httpx.AsyncClient(timeout=20) as client:
        r = await client.post(
            "https://places.googleapis.com/v1/places:searchNearby",
            json=payload,
            headers={
                "X-Goog-Api-Key":   GOOGLE_API_KEY,
                "X-Goog-FieldMask": (
                    "places.displayName,"
                    "places.location,"
                    "places.types,"
                    "places.formattedAddress,"
                    "places.nationalPhoneNumber,"
                    "places.internationalPhoneNumber"
                ),
            },
        )
        if r.status_code != 200:
            print(f"Google Places error {r.status_code}: {r.text[:300]}")
            _GOOGLE_CACHE[cache_key] = []
            return []
        places = r.json().get("places", [])
    results = []
    for p in places:
        plat  = p["location"]["latitude"]
        plon  = p["location"]["longitude"]
        raw   = next(
            (t for t in p.get("types", [])
             if t not in ("point_of_interest", "establishment") and not t.endswith("_1")),
            "unknown"
        )
        # Prefer national number, fall back to international, then None
        phone = (
            p.get("nationalPhoneNumber")
            or p.get("internationalPhoneNumber")
            or None
        )
        results.append({
            "name":       p.get("displayName", {}).get("text", "Unknown"),
            "lat":        plat,
            "lon":        plon,
            "type":       normalize_type(raw),
            "address":    p.get("formattedAddress", ""),
            "phone":      phone,
            "distance_m": round(haversine(lat, lon, plat, plon)),
            "source":     "google",
        })
    _GOOGLE_CACHE[cache_key] = results
    return results


# ══════════════════════════════════════════════════════════════════════════════
# OSM Overpass
# ══════════════════════════════════════════════════════════════════════════════

async def fetch_osm_nearby(lat: float, lon: float, radius: int = DEFAULT_RADIUS) -> list:
    r = int(radius)
    query = f"""
    [out:json][timeout:60];
    (
      node["amenity"](around:{r},{lat},{lon});
      way["amenity"](around:{r},{lat},{lon});
      relation["amenity"](around:{r},{lat},{lon});
    );
    out center;
    """
    headers = {"User-Agent": "RoadSOS/1.0"}
    async with httpx.AsyncClient(timeout=65, headers=headers) as client:
        for endpoint in OVERPASS_ENDPOINTS:
            try:
                resp = await client.post(endpoint, data={"data": query})
                if resp.status_code != 200:
                    continue
                results = []
                for el in resp.json().get("elements", []):
                    tags   = el.get("tags", {})
                    center = el.get("center", {})
                    elat   = el.get("lat") or center.get("lat")
                    elon   = el.get("lon") or center.get("lon")
                    if elat is None or elon is None:
                        continue
                    # OSM sometimes has phone in tags
                    phone = tags.get("phone") or tags.get("contact:phone") or None
                    results.append({
                        "name":       tags.get("name", "Unnamed"),
                        "lat":        elat,
                        "lon":        elon,
                        "type":       normalize_type(tags.get("amenity", "unknown")),
                        "address":    tags.get("addr:full", ""),
                        "phone":      phone,
                        "distance_m": round(haversine(lat, lon, elat, elon)),
                        "source":     "osm",
                    })
                return results
            except Exception:
                continue
    return []


async def fetch_osm_by_name(name: str, lat: float, lon: float) -> list:
    STOP = {"primary", "school", "college", "hospital", "district",
            "government", "the", "and", "of", "india", "national"}
    words = [w for w in name.split() if w.lower() not in STOP and len(w) > 3]
    token = max(words, key=len) if words else name.split()[0]
    query = f"""
    [out:json][timeout:60];
    nwr["name"~"{token}",i](around:10000,{lat},{lon});
    out center;
    """
    headers = {"User-Agent": "RoadSOS/1.0"}
    async with httpx.AsyncClient(timeout=65, headers=headers) as client:
        for endpoint in OVERPASS_ENDPOINTS:
            try:
                resp = await client.post(endpoint, data={"data": query})
                resp.raise_for_status()
                results = []
                for el in resp.json().get("elements", []):
                    center = el.get("center", {})
                    tags   = el.get("tags", {})
                    results.append({
                        "name":  tags.get("name", "Unnamed"),
                        "lat":   el.get("lat") or center.get("lat"),
                        "lon":   el.get("lon") or center.get("lon"),
                        "phone": tags.get("phone") or tags.get("contact:phone") or None,
                    })
                return results
            except Exception:
                continue
    return []


# ══════════════════════════════════════════════════════════════════════════════
# POI Cache
# ══════════════════════════════════════════════════════════════════════════════

def _poi_cache_key(lat: float, lon: float) -> tuple:
    return (round(lat, 2), round(lon, 2))


def _poi_cache_valid(lat: float, lon: float) -> bool:
    entry = _POI_CACHE.get(_poi_cache_key(lat, lon))
    if not entry:
        return False
    return (time.time() - entry["cached_at"]) < POI_CACHE_TTL


async def warm_poi_cache(lat: float, lon: float, radius: int = DEFAULT_RADIUS) -> list:
    key = _poi_cache_key(lat, lon)
    mappls_res, google_res, osm_res = await asyncio.gather(
        fetch_mappls_nearby(lat, lon, radius),
        fetch_google_nearby(lat, lon),
        fetch_osm_nearby(lat, lon, radius),
        return_exceptions=True,
    )
    combined = []
    for res in (mappls_res, google_res, osm_res):
        if isinstance(res, list):
            combined.extend(res)
    merged = deduplicate(combined)
    merged.sort(key=lambda x: x.get("distance_m") or 0)
    _POI_CACHE[key] = {"places": merged, "cached_at": time.time(), "lat": lat, "lon": lon}
    return merged


# ══════════════════════════════════════════════════════════════════════════════
# Crash handler
# ══════════════════════════════════════════════════════════════════════════════

def _build_nearest_by_type(places: list) -> dict:
    """
    Returns one nearest POI per emergency type.
    Each entry includes phone if available — used directly by Routes API.
    """
    nearest: dict = {}
    for p in places:
        t = p.get("type", "unknown")
        if t in EMERGENCY_TYPES and t not in nearest:
            nearest[t] = {
                "name":       p.get("name"),
                "lat":        p.get("lat"),
                "lon":        p.get("lon"),
                "address":    p.get("address"),
                "phone":      p.get("phone"),
                "distance_m": p.get("distance_m"),
                "sources":    p.get("sources"),
            }
    return nearest


async def get_emergency_pois(lat: float, lon: float, radius: int = DEFAULT_RADIUS) -> dict:
    key = _poi_cache_key(lat, lon)

    # ── Cache hit ─────────────────────────────────────────────────────────────
    if _poi_cache_valid(lat, lon):
        entry  = _POI_CACHE[key]
        places = sorted(entry["places"], key=lambda x: haversine(lat, lon, x["lat"], x["lon"]))
        for p in places:
            p["distance_m"] = round(haversine(lat, lon, p["lat"], p["lon"]))
        return {
            "source":          "cache",
            "cached_at":       entry["cached_at"],
            "places":          places,
            "nearest_by_type": _build_nearest_by_type(places),
        }

    # ── Waterfall: Mappls → OSM → Google ─────────────────────────────────────
    waterfall = [
        ("mappls", fetch_mappls_nearby(lat, lon, radius) if MAPPLS_CLIENT_ID else None),
        ("osm",    fetch_osm_nearby(lat, lon, radius)),
        ("google", fetch_google_nearby(lat, lon) if GOOGLE_API_KEY else None),
    ]
    for label, coro in waterfall:
        if coro is None:
            continue
        try:
            results = await coro
            if results:
                results.sort(key=lambda x: x.get("distance_m") or 0)
                return {
                    "source":          label,
                    "cached_at":       None,
                    "places":          results,
                    "nearest_by_type": _build_nearest_by_type(results),
                }
        except Exception:
            continue

    return {"source": "none", "cached_at": None, "places": [], "nearest_by_type": {}}


# ══════════════════════════════════════════════════════════════════════════════
# Audit logic
# ══════════════════════════════════════════════════════════════════════════════

async def _inventory_logic() -> dict:
    return {
        "apis": [
            {
                "name":         "Mappls (MapmyIndia)",
                "base_url":     "https://atlas.mappls.com/api/places/",
                "auth":         "OAuth2 — client_id + client_secret → 24 h bearer token",
                "cost":         "Paid tier (free evaluation available)",
                "rate_limit":   "Fair use",
                "format":       "JSON",
                "phone_support": False,
            },
            {
                "name":         "Google Places (New)",
                "base_url":     "https://places.googleapis.com/v1/",
                "auth":         "API Key (X-Goog-Api-Key header)",
                "cost":         "Paid tier",
                "rate_limit":   "~100 req/s",
                "format":       "JSON",
                "phone_support": True,
            },
            {
                "name":         "OSM Overpass",
                "base_url":     "https://overpass-api.de/api/interpreter",
                "auth":         "None",
                "cost":         "Free",
                "rate_limit":   "Fair use",
                "format":       "JSON / XML",
                "phone_support": "partial (tags.phone when available)",
            },
        ],
        "type_normalization": TYPE_ALIASES,
        "emergency_types":    list(EMERGENCY_TYPES),
    }


async def _endpoint_mapping_logic(lat: float, lon: float) -> dict:
    results = {}
    for name, coro in [
        ("mappls", fetch_mappls_nearby(lat, lon)),
        ("osm",    fetch_osm_nearby(lat, lon)),
        ("google", fetch_google_nearby(lat, lon)),
    ]:
        t0 = time.time()
        try:
            data = await coro
            results[name] = {
                "ok":      True,
                "count":   len(data),
                "elapsed": round(time.time() - t0, 3),
                "sample":  data[0] if data else None,
            }
        except Exception as e:
            results[name] = {"ok": False, "error": str(e)}
    return results


async def _schema_logic() -> dict:
    return {
        "raw_fields": [
            {"concept": "Name",     "mappls": "placeName",          "google": "displayName.text",           "osm": "tags.name"},
            {"concept": "Latitude", "mappls": "latitude",           "google": "location.latitude",          "osm": "lat / center.lat"},
            {"concept": "Longitude","mappls": "longitude",          "google": "location.longitude",         "osm": "lon / center.lon"},
            {"concept": "Unique ID","mappls": "eLoc",               "google": "id",                         "osm": "id (osm_id)"},
            {"concept": "Category", "mappls": "keywords[] (codes)", "google": "types[]",                    "osm": "tags.amenity"},
            {"concept": "Address",  "mappls": "placeAddress",       "google": "formattedAddress",           "osm": "tags.addr:full"},
            {"concept": "Phone",    "mappls": "— (not available)",  "google": "nationalPhoneNumber",        "osm": "tags.phone / tags.contact:phone"},
            {"concept": "Distance", "mappls": "distance (m)",       "google": "client-computed (haversine)","osm": "client-computed (haversine)"},
            {"concept": "Auth",     "mappls": "bearer {token}",     "google": "X-Goog-Api-Key header",      "osm": "none"},
        ],
        "canonical_mapping": [
            {"canonical": "name",         "mappls": "placeName",    "google": "displayName.text",    "osm": "tags.name"},
            {"canonical": "lat",          "mappls": "latitude",     "google": "location.latitude",   "osm": "lat"},
            {"canonical": "lon",          "mappls": "longitude",    "google": "location.longitude",  "osm": "lon"},
            {"canonical": "source_id",    "mappls": "eLoc",         "google": "id",                  "osm": "str(id)"},
            {"canonical": "feature_type", "mappls": "keywords[0]",  "google": "types[0]",            "osm": "tags.amenity"},
            {"canonical": "address",      "mappls": "placeAddress", "google": "formattedAddress",    "osm": "tags.addr:full"},
            {"canonical": "phone",        "mappls": "—",            "google": "nationalPhoneNumber", "osm": "tags.phone"},
        ],
    }


async def _reliability_logic(lat: float, lon: float, n: int = 5) -> dict:
    output = {}
    test_lats = [lat + i * 0.001 for i in range(n)]

    if MAPPLS_CLIENT_ID:
        times = []
        for tlat in test_lats:
            t0 = time.time()
            await fetch_mappls_nearby(tlat, lon)
            times.append(time.time() - t0)
        output["mappls"] = {
            "avg_s":  round(statistics.mean(times), 4),
            "p95_s":  round(sorted(times)[int(len(times) * 0.95) - 1], 4),
            "errors": 0,
            "note":   "May include cache hits after first call",
        }
    else:
        output["mappls"] = {"note": "no key configured"}

    osm_times, osm_errors = [], 0
    for tlat in test_lats:
        try:
            t0 = time.time()
            await fetch_osm_nearby(tlat, lon)
            osm_times.append(time.time() - t0)
        except Exception:
            osm_errors += 1
    output["osm"] = {
        "avg_s":  round(statistics.mean(osm_times), 4) if osm_times else None,
        "p95_s":  round(sorted(osm_times)[int(len(osm_times) * 0.95) - 1], 4) if len(osm_times) >= 2 else None,
        "errors": osm_errors,
    }

    g_times, g_errors = [], 0
    for tlat in test_lats:
        try:
            t0 = time.time()
            await fetch_google_nearby(tlat, lon)
            g_times.append(time.time() - t0)
        except Exception:
            g_errors += 1
    if g_times:
        output["google"] = {
            "avg_s":  round(statistics.mean(g_times), 4),
            "p95_s":  round(sorted(g_times)[int(len(g_times) * 0.95) - 1], 4) if len(g_times) >= 2 else None,
            "errors": g_errors,
        }
    else:
        output["google"] = {
            "note":   "no key configured" if not GOOGLE_API_KEY else "all calls failed",
            "errors": g_errors,
        }
    return output


async def _data_quality_logic(lat: float, lon: float, name: str) -> dict:
    entry: dict = {"place": name, "lat": lat, "lon": lon, "apis": {}}

    if MAPPLS_CLIENT_ID:
        try:
            candidates = await fetch_mappls_nearby(lat, lon)
            if candidates:
                best = max(candidates, key=lambda r: name_similarity(r["name"], name))
                entry["apis"]["mappls"] = {
                    "matched_name":  best["name"],
                    "similarity":    round(name_similarity(best["name"], name), 2),
                    "coord_error_m": round(haversine(lat, lon, best["lat"], best["lon"])),
                    "phone":         best.get("phone"),
                }
            else:
                entry["apis"]["mappls"] = {"note": "0 results"}
        except Exception as e:
            entry["apis"]["mappls"] = {"error": str(e)}
    else:
        entry["apis"]["mappls"] = {"note": "no key"}

    if GOOGLE_API_KEY:
        try:
            candidates = await fetch_google_nearby(lat, lon)
            if candidates:
                best = max(candidates, key=lambda r: name_similarity(r["name"], name))
                entry["apis"]["google"] = {
                    "matched_name":  best["name"],
                    "similarity":    round(name_similarity(best["name"], name), 2),
                    "coord_error_m": round(haversine(lat, lon, best["lat"], best["lon"])),
                    "phone":         best.get("phone"),
                }
            else:
                entry["apis"]["google"] = {"note": "0 results"}
        except Exception as e:
            entry["apis"]["google"] = {"error": str(e)}
    else:
        entry["apis"]["google"] = {"note": "no key"}

    try:
        candidates = await fetch_osm_by_name(name, lat, lon)
        if candidates:
            best = max(candidates, key=lambda r: name_similarity(r.get("name") or "", name))
            entry["apis"]["osm"] = {
                "matched_name":  best.get("name"),
                "similarity":    round(name_similarity(best.get("name", ""), name), 2),
                "coord_error_m": round(haversine(lat, lon, best["lat"] or lat, best["lon"] or lon)),
                "phone":         best.get("phone"),
            }
        else:
            entry["apis"]["osm"] = {"note": "0 results"}
    except Exception as e:
        entry["apis"]["osm"] = {"error": str(e)}

    return entry


# ══════════════════════════════════════════════════════════════════════════════
# FastAPI app
# ══════════════════════════════════════════════════════════════════════════════

app = FastAPI(
    title="RoadSOS API",
    description=(
        "POI aggregation for emergency response — Mappls + Google Places + OSM.\n\n"
        "**Crash flow:** `POST /crash/warm-cache` on app launch. "
        "On crash call `GET /crash/pois` — returns cached results instantly "
        "or waterfalls Mappls → OSM → Google.\n\n"
        "Response includes `nearest_by_type` with phone numbers for direct Routes API use."
    ),
    version="2.1.0",
)


# ── Health ────────────────────────────────────────────────────────────────────

@app.get("/health", tags=["Meta"])
async def health():
    return {
        "status":            "ok",
        "mappls_configured": bool(MAPPLS_CLIENT_ID and MAPPLS_CLIENT_SECRET),
        "google_configured": bool(GOOGLE_API_KEY),
        "poi_cache_entries": len(_POI_CACHE),
    }


# ── Crash ─────────────────────────────────────────────────────────────────────

@app.get("/crash/pois", tags=["Crash"])
async def crash_pois(
    lat:    float = Query(..., description="Crash latitude"),
    lon:    float = Query(..., description="Crash longitude"),
    radius: int   = Query(DEFAULT_RADIUS, description="Search radius in metres"),
):
    """
    Primary crash endpoint.
    Returns all nearby POIs plus a `nearest_by_type` map (one per emergency
    category with phone number) ready to pass directly to the Routes API.
    """
    return await get_emergency_pois(float(lat), float(lon), int(radius))


@app.post("/crash/warm-cache", tags=["Crash"])
async def warm_cache(
    lat:    float = Query(..., description="User latitude"),
    lon:    float = Query(..., description="User longitude"),
    radius: int   = Query(DEFAULT_RADIUS, description="Search radius in metres"),
):
    """Call on app launch and every 30 min to keep cache fresh."""
    places = await warm_poi_cache(float(lat), float(lon), int(radius))
    key    = _poi_cache_key(lat, lon)
    return {
        "cached":       len(places),
        "cached_at":    _POI_CACHE[key]["cached_at"],
        "expires_in_s": POI_CACHE_TTL,
    }


@app.get("/crash/cache-status", tags=["Crash"])
async def cache_status(
    lat: float = Query(...),
    lon: float = Query(...),
):
    key   = _poi_cache_key(lat, lon)
    entry = _POI_CACHE.get(key)
    if not entry:
        return {"warm": False, "reason": "no cache entry"}
    age = time.time() - entry["cached_at"]
    if age > POI_CACHE_TTL:
        return {"warm": False, "reason": f"expired ({int(age)}s old)"}
    return {
        "warm":         True,
        "places":       len(entry["places"]),
        "age_s":        round(age),
        "expires_in_s": round(POI_CACHE_TTL - age),
    }


# ── Fetch ─────────────────────────────────────────────────────────────────────

@app.get("/nearby/mappls", tags=["Fetch"])
async def nearby_mappls(
    lat:    float = Query(...),
    lon:    float = Query(...),
    radius: int   = Query(DEFAULT_RADIUS),
):
    return await fetch_mappls_nearby(float(lat), float(lon), int(radius))


@app.get("/nearby/google", tags=["Fetch"])
async def nearby_google(
    lat: float = Query(...),
    lon: float = Query(...),
):
    return await fetch_google_nearby(float(lat), float(lon))


@app.get("/nearby/osm", tags=["Fetch"])
async def nearby_osm(
    lat:    float = Query(...),
    lon:    float = Query(...),
    radius: int   = Query(DEFAULT_RADIUS),
):
    return await fetch_osm_nearby(float(lat), float(lon), int(radius))


@app.get("/nearby/all", tags=["Fetch"])
async def nearby_all(
    lat:    float = Query(...),
    lon:    float = Query(...),
    radius: int   = Query(DEFAULT_RADIUS),
):
    """Raw results from all three APIs — not deduplicated."""
    mappls_res, google_res, osm_res = await asyncio.gather(
        fetch_mappls_nearby(float(lat), float(lon), int(radius)),
        fetch_google_nearby(float(lat), float(lon)),
        fetch_osm_nearby(float(lat), float(lon), int(radius)),
        return_exceptions=True,
    )
    return {
        "mappls": mappls_res if not isinstance(mappls_res, Exception) else {"error": str(mappls_res)},
        "google": google_res if not isinstance(google_res, Exception) else {"error": str(google_res)},
        "osm":    osm_res    if not isinstance(osm_res,    Exception) else {"error": str(osm_res)},
    }


@app.get("/nearby/merged", tags=["Fetch"])
async def nearby_merged(
    lat:    float = Query(...),
    lon:    float = Query(...),
    radius: int   = Query(DEFAULT_RADIUS),
):
    """Deduplicated + type-normalized + phone-enriched POI list, sorted by distance."""
    mappls_res, google_res, osm_res = await asyncio.gather(
        fetch_mappls_nearby(float(lat), float(lon), int(radius)),
        fetch_google_nearby(float(lat), float(lon)),
        fetch_osm_nearby(float(lat), float(lon), int(radius)),
        return_exceptions=True,
    )
    combined = []
    for res in (mappls_res, google_res, osm_res):
        if isinstance(res, list):
            combined.extend(res)
    merged = deduplicate(combined)
    merged.sort(key=lambda x: x.get("distance_m") or 0)
    return merged


# ── Audit checks ──────────────────────────────────────────────────────────────

@app.get("/checks/inventory", tags=["Checks"])
async def check_inventory():
    return await _inventory_logic()


@app.get("/checks/endpoint-mapping", tags=["Checks"])
async def check_endpoint_mapping(
    lat: float = Query(..., description="Test latitude"),
    lon: float = Query(..., description="Test longitude"),
):
    return await _endpoint_mapping_logic(float(lat), float(lon))


@app.get("/checks/schema", tags=["Checks"])
async def check_schema():
    return await _schema_logic()


@app.get("/checks/reliability", tags=["Checks"])
async def check_reliability(
    lat:  float = Query(..., description="Centre latitude for test"),
    lon:  float = Query(..., description="Centre longitude for test"),
    runs: int   = Query(5, ge=1, le=10, description="Calls per API"),
):
    return await _reliability_logic(float(lat), float(lon), int(runs))


@app.get("/checks/data-quality", tags=["Checks"])
async def check_data_quality(
    lat:  float = Query(..., description="Known place latitude"),
    lon:  float = Query(..., description="Known place longitude"),
    name: str   = Query(..., description="Known place name to match against"),
):
    return await _data_quality_logic(float(lat), float(lon), name)