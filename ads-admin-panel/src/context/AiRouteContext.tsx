"use client";

import React, { createContext, useContext, useState } from "react";
import { Alert } from "@/lib/db";

export interface AfadBase {
  name: string;
  lat: number;
  lng: number;
}

export const AFAD_BASES: AfadBase[] = [
  { name: "İstanbul Ana Müdahale Üssü", lat: 41.0082, lng: 28.9784 },
  { name: "Bursa Koordinasyon", lat: 40.1824, lng: 29.0669 },
  { name: "Sakarya Lojistik Merkez", lat: 40.7569, lng: 30.3783 },
  { name: "Tekirdağ Depo", lat: 40.9780, lng: 27.5110 },
  { name: "Yalova Destek", lat: 40.6500, lng: 29.2769 },
  { name: "Balıkesir Depo", lat: 39.6484, lng: 27.8826 },
  { name: "İzmir Komuta Merkezi", lat: 38.4192, lng: 27.1287 },
  { name: "Afyonkarahisar Lojistik Üssü", lat: 38.7507, lng: 30.5367 },
  { name: "Adana Doğu Akdeniz Müd.", lat: 37.0000, lng: 35.3213 },
  { name: "Antalya Batı Akdeniz Müd.", lat: 36.8969, lng: 30.7133 },
  { name: "Ankara Yönetim Karargahı", lat: 39.9334, lng: 32.8597 },
  { name: "Konya Geçiş Noktası", lat: 37.8667, lng: 32.4833 },
  { name: "Sivas Bölgesel Depo", lat: 39.7477, lng: 37.0179 },
  { name: "Samsun Orta Karadeniz", lat: 41.2867, lng: 36.3300 },
  { name: "Trabzon Kurtarma", lat: 41.0015, lng: 39.7178 },
  { name: "Rize Kurtarma", lat: 41.0201, lng: 40.5234 },
  { name: "Kastamonu Lojistik", lat: 41.3887, lng: 33.7827 },
  { name: "Düzce Batı Karadeniz", lat: 40.8438, lng: 31.1565 },
  { name: "Erzurum Müdahale Üssü", lat: 39.9000, lng: 41.2700 },
  { name: "Van Sınır Ana Merkez", lat: 38.4891, lng: 43.3889 },
  { name: "Elazığ (DAF) Noktası", lat: 38.6810, lng: 39.2264 },
  { name: "Malatya (DAF) Noktası", lat: 38.3552, lng: 38.3095 },
  { name: "Diyarbakır Ana Birlik Müd.", lat: 37.9144, lng: 40.2306 },
  { name: "Gaziantep Bölgesel Destek", lat: 37.0662, lng: 37.3833 },
];

function getDistance(lat1: number, lon1: number, lat2: number, lon2: number) {
  const R = 6371;
  const dLat = (lat2 - lat1) * (Math.PI / 180);
  const dLon = (lon2 - lon1) * (Math.PI / 180);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * (Math.PI / 180)) *
      Math.cos(lat2 * (Math.PI / 180)) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

function findNearestBase(lat: number, lng: number): AfadBase {
  let nearest = AFAD_BASES[0];
  let minDistance = getDistance(lat, lng, nearest.lat, nearest.lng);

  for (let i = 1; i < AFAD_BASES.length; i++) {
    const d = getDistance(lat, lng, AFAD_BASES[i].lat, AFAD_BASES[i].lng);
    if (d < minDistance) {
      minDistance = d;
      nearest = AFAD_BASES[i];
    }
  }

  return nearest;
}

async function fetchOsrmRoute(
  startLat: number,
  startLng: number,
  destLat: number,
  destLng: number
): Promise<[number, number][]> {
  try {
    const url = `https://router.project-osrm.org/route/v1/driving/${startLng},${startLat};${destLng},${destLat}?overview=full&geometries=geojson`;
    const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
    if (!res.ok) throw new Error("OSRM response not ok");
    const data = await res.json();
    const coords: [number, number][] = data.routes?.[0]?.geometry?.coordinates ?? [];
    // OSRM returns [lng, lat], Leaflet expects [lat, lng]
    return coords.map(([lng, lat]) => [lat, lng]);
  } catch {
    // Fallback: straight line
    return [
      [startLat, startLng],
      [destLat, destLng],
    ];
  }
}

export interface AiRoute {
  id: string;
  targetId: string;
  destination: string;
  lat: number;
  lng: number;
  startName: string;
  startLat: number;
  startLng: number;
  status: "Hesaplanıyor" | "Aktif" | "Tamamlandı";
  timestamp: number;
  /** Gerçek yol geometrisi — OSRM'den gelen [lat, lng][] dizisi */
  routeGeometry?: [number, number][];
}

interface AiRouteContextType {
  routes: AiRoute[];
  addRoute: (alert: Alert) => void;
  addRouteToRegion: (regionKey: string, destName: string, lat: number, lng: number) => void;
  addEarthquakeRoute: (eq: { id: string; latitude: number; longitude: number; location_name?: string }) => void;
  removeRoute: (id: string) => void;
}

const AiRouteContext = createContext<AiRouteContextType | undefined>(undefined);

export function AiRouteProvider({ children }: { children: React.ReactNode }) {
  const [routes, setRoutes] = useState<AiRoute[]>([]);

  const createAndAddRoute = async (
    targetId: string,
    destination: string,
    destLat: number,
    destLng: number
  ) => {
    const base = findNearestBase(destLat, destLng);

    const tempRoute: AiRoute = {
      id: Math.random().toString(36).substr(2, 9),
      targetId,
      destination,
      lat: destLat,
      lng: destLng,
      startName: base.name,
      startLat: base.lat,
      startLng: base.lng,
      status: "Hesaplanıyor",
      timestamp: Date.now(),
      routeGeometry: undefined,
    };

    setRoutes((prev) => [tempRoute, ...prev]);

    // Fetch real route from OSRM in background
    const geometry = await fetchOsrmRoute(base.lat, base.lng, destLat, destLng);

    setRoutes((prev) =>
      prev.map((r) =>
        r.id === tempRoute.id
          ? { ...r, status: "Aktif", routeGeometry: geometry }
          : r
      )
    );
  };

  const addRoute = (alert: Alert) => {
    if (routes.some((r) => r.targetId === alert.id)) return;
    const destination = alert.full_name
      ? `${alert.full_name} (${alert.location.district})`
      : alert.location.district;
    createAndAddRoute(alert.id, destination, alert.location.lat, alert.location.lng);
  };

  const addRouteToRegion = (regionKey: string, destName: string, lat: number, lng: number) => {
    if (routes.some((r) => r.targetId === regionKey)) return;
    createAndAddRoute(regionKey, destName, lat, lng);
  };

  const addEarthquakeRoute = (eq: {
    id: string;
    latitude: number;
    longitude: number;
    location_name?: string;
  }) => {
    const targetId = `eq-${eq.id}`;
    if (routes.some((r) => r.targetId === targetId)) return;
    const destName = eq.location_name
      ? `Deprem: ${eq.location_name}`
      : `Deprem: ${eq.latitude.toFixed(3)}, ${eq.longitude.toFixed(3)}`;
    createAndAddRoute(targetId, destName, eq.latitude, eq.longitude);
  };

  const removeRoute = (id: string) => {
    setRoutes((prev) => prev.filter((r) => r.id !== id));
  };

  return (
    <AiRouteContext.Provider value={{ routes, addRoute, addRouteToRegion, addEarthquakeRoute, removeRoute }}>
      {children}
    </AiRouteContext.Provider>
  );
}

export function useAiRoutes() {
  const context = useContext(AiRouteContext);
  if (context === undefined) {
    throw new Error("useAiRoutes must be used within an AiRouteProvider");
  }
  return context;
}
