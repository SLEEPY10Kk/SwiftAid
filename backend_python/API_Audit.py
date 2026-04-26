"""
API Audit — FastAPI version
Mappls (MapmyIndia) + Google Places + OSM Overpass

Start:  uvicorn main:app --reload
Docs:   http://localhost:8000/docs

Keys required in .env / Keys.env:
    MAPPLS_CLIENT_ID=...
    MAPPLS_CLIENT_SECRET=...
    GOOGLE_API_KEY=...        (optional)
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

# ── Ground truth + test points ────────────────────────────────────────────────
GROUND_TRUTH = [
    {"name": "Anand Agricultural University", "lat": 22.5421, "lon": 72.9563, "region": "semi-urban"},
    {"name": "Dantewada District Hospital",   "lat": 18.8930, "lon": 81.3476, "region": "rural"},
    {"name": "Lahaul Primary School",         "lat": 32.6900, "lon": 77.0500, "region": "remote"},
    {"name": "Lakshadweep Jetty",             "lat": 10.5593, "lon": 72.6358, "region": "island"},
    {"name": "Sabarmati Ashram",              "lat": 23.0603, "lon": 72.5802, "region": "urban"},
]

TEST_POINTS = [
    {"name": "Anand",       "lat": 22.56, "lon": 72.92},
    {"name": "Dantewada",   "lat": 18.89, "lon": 81.34},
    {"name": "Lahaul",      "lat": 32.69, "lon": 77.05},
    {"name": "Lakshadweep", "lat": 10.56, "lon": 72.63},
    {"name": "Ahmedabad",   "lat": 23.02, "lon": 72.57},
]

# ── In-memory caches ──────────────────────────────────────────────────────────
_MAPPLS_TOKEN_CACHE:  dict = {"token": None, "expires_at": 0}
_MAPPLS_NEARBY_CACHE: dict = {}
_GOOGLE_CACHE:        dict = {}

# ── POI cache (replaces per-API caches for crash use) ─────────────────────────
# Keyed by (lat4, lon4) → {"places": [...], "cached_at": float}
_POI_CACHE:           dict = {}
POI_CACHE_TTL         = 1800   # 30 minutes
POI_CACHE_MOVE_THRESHOLD_M = 500  # refresh if user moves more than this

MAPPLS_KEYWORDS   = "hospital;school;police;bank;post office;government"
MAPPLS_TOKEN_URL  = "https://outpost.mapmyindia.com/api/security/oauth/token"
MAPPLS_NEARBY_URL = "https://atlas.mappls.com/api/places/nearby/json"


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
    Merge results from multiple API sources into a single deduplicated list.

    Two places are considered the same if they are within distance_threshold_m
    of each other. When merging:
      - Keep the longer/more descriptive name
      - Record all source APIs that reported this place
      - Recalculate distance_m from the canonical coords
    """
    merged = []
    for place in places:
        duplicate = False
        for existing in merged:
            if haversine(
                place["lat"], place["lon"],
                existing["lat"], existing["lon"]
            ) < distance_threshold_m:
                # Same physical location — keep better name
                if len(str(place.get("name", ""))) > len(str(existing.get("name", ""))):
                    existing["name"] = place["name"]
                # Merge source tags
                existing_sources = existing.get("sources", [existing.get("source", "unknown")])
                new_source = place.get("source", "unknown")
                if new_source not in existing_sources:
                    existing_sources.append(new_source)
                existing["sources"] = existing_sources
                existing.pop("source", None)
                duplicate = True
                break
        if not duplicate:
            p = dict(place)
            p["sources"] = [p.pop("source", "unknown")]
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
        results.append({
            "name":       p.get("placeName", "Unknown"),
            "address":    p.get("placeAddress", ""),
            "lat":        plat,
            "lon":        plon,
            "eloc":       p.get("eLoc", ""),
            "category":   ";".join(p.get("keywords", [])),
            "distance_m": p.get("distance"),
            "source":     "mappls",
        })
    _MAPPLS_NEARBY_CACHE[cache_key] = results
    return results


# ══════════════════════════════════════════════════════════════════════════════
# Google Places (New)
# ══════════════════════════════════════════════════════════════════════════════

async def fetch_google_nearby(lat: float, lon: float) -> list:
    cache_key = (round(lat, 4), round(lon, 4))
    if cache_key in _GOOGLE_CACHE:
        return _GOOGLE_CACHE[cache_key]
    if not GOOGLE_API_KEY:
        return []
    payload = {
        "includedTypes": ["hospital", "police", "school", "local_government_office", "bank", "post_office"],
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
                "X-Goog-FieldMask": "places.displayName,places.location,places.types,places.formattedAddress",
            },
        )
        r.raise_for_status()
        places = r.json().get("places", [])
    results = []
    for p in places:
        plat  = p["location"]["latitude"]
        plon  = p["location"]["longitude"]
        ptype = next(
            (t for t in p.get("types", [])
             if t not in ("point_of_interest", "establishment") and not t.endswith("_1")),
            "unknown"
        )
        results.append({
            "name":       p.get("displayName", {}).get("text", "Unknown"),
            "lat":        plat,
            "lon":        plon,
            "type":       ptype,
            "address":    p.get("formattedAddress", ""),
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
    [out:json][timeout:600];
    (
      node["amenity"](around:{r},{lat},{lon});
      way["amenity"](around:{r},{lat},{lon});
      relation["amenity"](around:{r},{lat},{lon});
    );
    out center;
    """
    headers = {"User-Agent": "RoadSOSAudit/1.0 (contact@yourdomain.com)"}
    async with httpx.AsyncClient(timeout=65, headers=headers) as client:
        for endpoint in OVERPASS_ENDPOINTS:
            try:
                resp = await client.post(endpoint, data={"data": query})
                if resp.status_code != 200:
                    print(f"OSM {resp.status_code} from {endpoint}: {resp.text[:200]}")
                    continue
                results = []
                for el in resp.json().get("elements", []):
                    tags   = el.get("tags", {})
                    center = el.get("center", {})
                    elat   = el.get("lat") or center.get("lat")
                    elon   = el.get("lon") or center.get("lon")
                    if elat is None or elon is None:
                        continue
                    results.append({
                        "name":       tags.get("name", "Unnamed"),
                        "lat":        elat,
                        "lon":        elon,
                        "amenity":    tags.get("amenity", ""),
                        "distance_m": round(haversine(lat, lon, elat, elon)),
                        "source":     "osm",
                    })
                return results
            except Exception as e:
                print(f"OSM exception on {endpoint}: {e}")
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
    headers = {"User-Agent": "RoadSOSAudit/1.0 (contact@yourdomain.com)"}
    async with httpx.AsyncClient(timeout=65, headers=headers) as client:
        for endpoint in OVERPASS_ENDPOINTS:
            try:
                resp = await client.post(endpoint, data={"data": query})
                resp.raise_for_status()
                results = []
                for el in resp.json().get("elements", []):
                    center = el.get("center", {})
                    results.append({
                        "name": el.get("tags", {}).get("name", "Unnamed"),
                        "lat":  el.get("lat") or center.get("lat"),
                        "lon":  el.get("lon") or center.get("lon"),
                    })
                return results
            except Exception:
                continue
    return []


# ══════════════════════════════════════════════════════════════════════════════
# POI Cache — setup phase (called on app launch / background refresh)
# ══════════════════════════════════════════════════════════════════════════════

def _poi_cache_key(lat: float, lon: float) -> tuple:
    return (round(lat, 2), round(lon, 2))   # coarser grid than per-API cache


def _poi_cache_valid(lat: float, lon: float) -> bool:
    key = _poi_cache_key(lat, lon)
    entry = _POI_CACHE.get(key)
    if not entry:
        return False
    if time.time() - entry["cached_at"] > POI_CACHE_TTL:
        return False
    return True


async def warm_poi_cache(lat: float, lon: float, radius: int = DEFAULT_RADIUS) -> list:
    """
    Fetch from all 3 APIs in parallel, deduplicate, sort by distance,
    and store in the POI cache. Returns the merged list.

    Call this on app launch and every 30 min in the background.
    The crash handler reads from this cache — zero API calls on crash.
    """
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

    _POI_CACHE[key] = {
        "places":    merged,
        "cached_at": time.time(),
        "lat":       lat,
        "lon":       lon,
    }
    return merged


# ══════════════════════════════════════════════════════════════════════════════
# Crash handler — reads from cache, falls back to waterfall if cache is cold
# ══════════════════════════════════════════════════════════════════════════════

async def get_emergency_pois(lat: float, lon: float, radius: int = DEFAULT_RADIUS) -> dict:
    """
    Main function called when a crash is detected.

    Flow:
      1. POI cache warm and fresh → return instantly, zero API calls
      2. POI cache stale/missing  → waterfall: Mappls → OSM → Google
         (first API that returns results wins; others are skipped)

    Returns:
      {
        "source":    "cache" | "mappls" | "osm" | "google" | "none",
        "cached_at": <unix timestamp> | null,
        "places":    [...sorted by distance_m...]
      }
    """
    key = _poi_cache_key(lat, lon)

    # ── 1. Cache hit ──────────────────────────────────────────────────────────
    if _poi_cache_valid(lat, lon):
        entry = _POI_CACHE[key]
        # Re-sort by distance from current crash coords (user may have moved)
        places = sorted(
            entry["places"],
            key=lambda x: haversine(lat, lon, x["lat"], x["lon"])
        )
        # Update distance_m relative to crash location
        for p in places:
            p["distance_m"] = round(haversine(lat, lon, p["lat"], p["lon"]))
        return {
            "source":    "cache",
            "cached_at": entry["cached_at"],
            "places":    places,
        }

    # ── 2. Cache cold — waterfall ─────────────────────────────────────────────
    # Try Mappls first (best India coverage + distance field built-in)
    if MAPPLS_CLIENT_ID:
        try:
            results = await fetch_mappls_nearby(lat, lon, radius)
            if results:
                results.sort(key=lambda x: x.get("distance_m") or 0)
                return {"source": "mappls", "cached_at": None, "places": results}
        except Exception:
            pass

    # Try OSM next (free, no quota risk)
    try:
        results = await fetch_osm_nearby(lat, lon, radius)
        if results:
            results.sort(key=lambda x: x.get("distance_m") or 0)
            return {"source": "osm", "cached_at": None, "places": results}
    except Exception:
        pass

    # Try Google last (paid, use as last resort)
    if GOOGLE_API_KEY:
        try:
            results = await fetch_google_nearby(lat, lon)
            if results:
                results.sort(key=lambda x: x.get("distance_m") or 0)
                return {"source": "google", "cached_at": None, "places": results}
        except Exception:
            pass

    # All APIs failed
    return {"source": "none", "cached_at": None, "places": []}


# ══════════════════════════════════════════════════════════════════════════════
# Audit logic functions — plain Python, no Query objects
# ══════════════════════════════════════════════════════════════════════════════

async def _inventory_logic() -> dict:
    return {
        "apis": [
            {
                "name":       "Mappls (MapmyIndia)",
                "base_url":   "https://atlas.mappls.com/api/places/",
                "auth":       "OAuth2 — client_id + client_secret → 24 h bearer token",
                "cost":       "Paid tier (free evaluation available)",
                "rate_limit": "Fair use",
                "format":     "JSON",
            },
            {
                "name":       "Google Places (New)",
                "base_url":   "https://places.googleapis.com/v1/",
                "auth":       "API Key (X-Goog-Api-Key header)",
                "cost":       "Paid tier",
                "rate_limit": "~100 req/s",
                "format":     "JSON",
            },
            {
                "name":       "OSM Overpass",
                "base_url":   "https://overpass-api.de/api/interpreter",
                "auth":       "None",
                "cost":       "Free",
                "rate_limit": "Fair use",
                "format":     "JSON / XML",
            },
        ]
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
            {"concept": "Name",     "mappls": "placeName",          "google": "displayName.text",      "osm": "tags.name"},
            {"concept": "Latitude", "mappls": "latitude",           "google": "location.latitude",     "osm": "lat / center.lat"},
            {"concept": "Longitude","mappls": "longitude",          "google": "location.longitude",    "osm": "lon / center.lon"},
            {"concept": "Unique ID","mappls": "eLoc",               "google": "id",                    "osm": "id (osm_id)"},
            {"concept": "Category", "mappls": "keywords[] (codes)", "google": "types[]",               "osm": "tags.amenity"},
            {"concept": "Address",  "mappls": "placeAddress",       "google": "formattedAddress",      "osm": "tags.addr:*"},
            {"concept": "Distance", "mappls": "distance (m)",       "google": "client-computed",       "osm": "client-computed"},
            {"concept": "Auth",     "mappls": "bearer {token}",     "google": "X-Goog-Api-Key header", "osm": "none"},
        ],
        "canonical_mapping": [
            {"canonical": "name",         "mappls": "placeName",    "google": "displayName.text",   "osm": "tags.name"},
            {"canonical": "lat",          "mappls": "latitude",     "google": "location.latitude",  "osm": "lat"},
            {"canonical": "lon",          "mappls": "longitude",    "google": "location.longitude", "osm": "lon"},
            {"canonical": "source_id",    "mappls": "eLoc",         "google": "id",                 "osm": "str(id)"},
            {"canonical": "feature_type", "mappls": "keywords[0]",  "google": "types[0]",           "osm": "tags.amenity"},
            {"canonical": "address",      "mappls": "placeAddress", "google": "formattedAddress",   "osm": "tags.addr:full"},
        ],
    }


async def _coverage_logic() -> list:
    output = []
    for pt in TEST_POINTS:
        row: dict = {"location": pt["name"], "lat": pt["lat"], "lon": pt["lon"]}
        try:
            row["osm"] = len(await fetch_osm_nearby(pt["lat"], pt["lon"]))
        except Exception as e:
            row["osm"] = f"ERR: {e}"
        if MAPPLS_CLIENT_ID:
            try:
                row["mappls"] = len(await fetch_mappls_nearby(pt["lat"], pt["lon"]))
            except Exception as e:
                row["mappls"] = f"ERR: {e}"
        else:
            row["mappls"] = "no key"
        if GOOGLE_API_KEY:
            try:
                row["google"] = len(await fetch_google_nearby(pt["lat"], pt["lon"]))
            except Exception as e:
                row["google"] = f"ERR: {e}"
        else:
            row["google"] = "no key"
        output.append(row)
    return output


async def _reliability_logic(n: int = 10) -> dict:
    test_lats = [22.56 + i * 0.001 for i in range(n)]
    output = {}

    if MAPPLS_CLIENT_ID:
        times = []
        for pt in (TEST_POINTS * 4)[:n]:
            t0 = time.time()
            await fetch_mappls_nearby(pt["lat"], pt["lon"])
            times.append(time.time() - t0)
        output["mappls"] = {
            "avg_s":  round(statistics.mean(times), 4),
            "p95_s":  round(sorted(times)[int(len(times) * 0.95) - 1], 4),
            "errors": 0,
            "note":   "Cache hits — real network latency ~0.7 s",
        }
    else:
        output["mappls"] = {"note": "no key configured"}

    osm_times, osm_errors = [], 0
    for lat in test_lats:
        try:
            t0 = time.time()
            await fetch_osm_nearby(lat, 72.92)
            osm_times.append(time.time() - t0)
        except Exception:
            osm_errors += 1
    output["osm"] = {
        "avg_s":  round(statistics.mean(osm_times), 4) if osm_times else None,
        "p95_s":  round(sorted(osm_times)[int(len(osm_times) * 0.95) - 1], 4) if len(osm_times) >= 2 else None,
        "errors": osm_errors,
    }

    g_times, g_errors = [], 0
    for lat in test_lats:
        try:
            t0 = time.time()
            await fetch_google_nearby(lat, 72.92)
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


async def _data_quality_logic() -> list:
    results = []
    for place in GROUND_TRUTH:
        entry: dict = {"place": place["name"], "region": place["region"], "apis": {}}

        if MAPPLS_CLIENT_ID:
            try:
                candidates = await fetch_mappls_nearby(place["lat"], place["lon"])
                if candidates:
                    best = max(candidates, key=lambda r: name_similarity(r["name"], place["name"]))
                    sim  = name_similarity(best["name"], place["name"])
                    dist = haversine(place["lat"], place["lon"], best["lat"], best["lon"])
                    entry["apis"]["mappls"] = {
                        "matched_name":  best["name"],
                        "similarity":    round(sim, 2),
                        "coord_error_m": round(dist),
                    }
                else:
                    entry["apis"]["mappls"] = {"matched_name": None, "similarity": 0, "note": "0 results"}
            except Exception as e:
                entry["apis"]["mappls"] = {"error": str(e)}
        else:
            entry["apis"]["mappls"] = {"note": "no key"}

        if GOOGLE_API_KEY:
            try:
                candidates = await fetch_google_nearby(place["lat"], place["lon"])
                if candidates:
                    best = max(candidates, key=lambda r: name_similarity(r["name"], place["name"]))
                    sim  = name_similarity(best["name"], place["name"])
                    dist = haversine(place["lat"], place["lon"], best["lat"], best["lon"])
                    entry["apis"]["google"] = {
                        "matched_name":  best["name"],
                        "similarity":    round(sim, 2),
                        "coord_error_m": round(dist),
                    }
                else:
                    entry["apis"]["google"] = {"matched_name": None, "similarity": 0, "note": "0 results"}
            except Exception as e:
                entry["apis"]["google"] = {"error": str(e)}
        else:
            entry["apis"]["google"] = {"note": "no key"}

        try:
            candidates = await fetch_osm_by_name(place["name"], place["lat"], place["lon"])
            if candidates:
                best = max(candidates, key=lambda r: name_similarity(r.get("name") or "", place["name"]))
                sim  = name_similarity(best.get("name", ""), place["name"])
                dist = haversine(
                    place["lat"], place["lon"],
                    best["lat"] or place["lat"],
                    best["lon"] or place["lon"],
                )
                entry["apis"]["osm"] = {
                    "matched_name":  best.get("name"),
                    "similarity":    round(sim, 2),
                    "coord_error_m": round(dist),
                }
            else:
                entry["apis"]["osm"] = {"matched_name": None, "similarity": 0, "note": "0 results"}
        except Exception as e:
            entry["apis"]["osm"] = {"error": str(e)}

        results.append(entry)
    return results


# ══════════════════════════════════════════════════════════════════════════════
# FastAPI app
# ══════════════════════════════════════════════════════════════════════════════

app = FastAPI(
    title="RoadSOS API Audit — Mappls + Google Places + OSM",
    description=(
        "Audit checks + crash-optimised POI endpoints.\n\n"
        "**Crash flow:** Call `/crash/pois` with GPS coords. "
        "Returns cached results instantly if warm, else waterfalls Mappls → OSM → Google."
    ),
    version="2.0.0",
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


# ══════════════════════════════════════════════════════════════════════════════
# Crash endpoints
# ══════════════════════════════════════════════════════════════════════════════

@app.get("/crash/pois", tags=["Crash"])
async def crash_pois(
    lat:    float = Query(..., description="Crash latitude"),
    lon:    float = Query(..., description="Crash longitude"),
    radius: int   = Query(DEFAULT_RADIUS, description="Search radius in metres"),
):
    """
    **Primary endpoint for crash response.**

    Returns nearest emergency POIs (hospitals, police, etc.) as fast as possible.
    - If cache is warm: instant response, zero API calls
    - If cache is cold: waterfall Mappls → OSM → Google (first that responds wins)
    """
    return await get_emergency_pois(float(lat), float(lon), int(radius))


@app.post("/crash/warm-cache", tags=["Crash"])
async def warm_cache(
    lat:    float = Query(..., description="User's current latitude"),
    lon:    float = Query(..., description="User's current longitude"),
    radius: int   = Query(DEFAULT_RADIUS, description="Search radius in metres"),
):
    """
    **Call this on app launch and every 30 min in the background.**

    Fetches all 3 APIs in parallel, deduplicates, and stores in memory.
    Subsequent `/crash/pois` calls will be served from cache — zero API calls.
    """
    places = await warm_poi_cache(float(lat), float(lon), int(radius))
    return {
        "cached":      len(places),
        "cached_at":   _POI_CACHE[_poi_cache_key(lat, lon)]["cached_at"],
        "expires_in_s": POI_CACHE_TTL,
    }


@app.get("/crash/cache-status", tags=["Crash"])
async def cache_status(
    lat: float = Query(..., description="Latitude to check"),
    lon: float = Query(..., description="Longitude to check"),
):
    """Check whether the POI cache is warm for a given location."""
    key   = _poi_cache_key(lat, lon)
    entry = _POI_CACHE.get(key)
    if not entry:
        return {"warm": False, "reason": "no cache entry"}
    age = time.time() - entry["cached_at"]
    if age > POI_CACHE_TTL:
        return {"warm": False, "reason": f"expired ({int(age)}s old, TTL={POI_CACHE_TTL}s)"}
    return {
        "warm":         True,
        "places":       len(entry["places"]),
        "age_s":        round(age),
        "expires_in_s": round(POI_CACHE_TTL - age),
    }


# ══════════════════════════════════════════════════════════════════════════════
# Fetch endpoints (ad-hoc / audit use)
# ══════════════════════════════════════════════════════════════════════════════

@app.get("/nearby/mappls", tags=["Fetch"])
async def nearby_mappls(
    lat:    float = Query(..., description="Latitude"),
    lon:    float = Query(..., description="Longitude"),
    radius: int   = Query(DEFAULT_RADIUS, description="Search radius in metres"),
):
    return await fetch_mappls_nearby(float(lat), float(lon), int(radius))


@app.get("/nearby/google", tags=["Fetch"])
async def nearby_google(
    lat: float = Query(..., description="Latitude"),
    lon: float = Query(..., description="Longitude"),
):
    return await fetch_google_nearby(float(lat), float(lon))


@app.get("/nearby/osm", tags=["Fetch"])
async def nearby_osm(
    lat:    float = Query(..., description="Latitude"),
    lon:    float = Query(..., description="Longitude"),
    radius: int   = Query(DEFAULT_RADIUS, description="Search radius in metres"),
):
    return await fetch_osm_nearby(float(lat), float(lon), int(radius))


@app.get("/nearby/all", tags=["Fetch"])
async def nearby_all(
    lat:    float = Query(..., description="Latitude"),
    lon:    float = Query(..., description="Longitude"),
    radius: int   = Query(DEFAULT_RADIUS, description="Search radius in metres"),
):
    """Raw results from all three APIs in parallel (not deduplicated)."""
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
    lat:    float = Query(..., description="Latitude"),
    lon:    float = Query(..., description="Longitude"),
    radius: int   = Query(DEFAULT_RADIUS, description="Search radius in metres"),
):
    """Deduplicated + sorted POI list from all three APIs."""
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


# ══════════════════════════════════════════════════════════════════════════════
# Audit check endpoints
# ══════════════════════════════════════════════════════════════════════════════

@app.get("/checks/inventory", tags=["Checks"])
async def check_inventory():
    return await _inventory_logic()


@app.get("/checks/endpoint-mapping", tags=["Checks"])
async def check_endpoint_mapping(
    lat: float = Query(22.56, description="Probe latitude"),
    lon: float = Query(72.92, description="Probe longitude"),
):
    return await _endpoint_mapping_logic(float(lat), float(lon))


@app.get("/checks/schema", tags=["Checks"])
async def check_schema():
    return await _schema_logic()


@app.get("/checks/coverage", tags=["Checks"])
async def check_coverage():
    return await _coverage_logic()


@app.get("/checks/reliability", tags=["Checks"])
async def check_reliability(
    runs: int = Query(10, ge=1, le=20, description="Number of timed calls per API"),
):
    return await _reliability_logic(int(runs))


@app.get("/checks/data-quality", tags=["Checks"])
async def check_data_quality():
    return await _data_quality_logic()


@app.get("/checks/run-all", tags=["Checks"])
async def run_all():
    """Runs all six audit checks and returns a combined report."""
    return {
        "inventory":        await _inventory_logic(),
        "endpoint_mapping": await _endpoint_mapping_logic(22.56, 72.92),
        "schema":           await _schema_logic(),
        "coverage":         await _coverage_logic(),
        "reliability":      await _reliability_logic(10),
        "data_quality":     await _data_quality_logic(),
    }