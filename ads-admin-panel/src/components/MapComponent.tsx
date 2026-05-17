"use client";

import { useEffect, useState, useRef, useMemo } from "react";
import { MapContainer, TileLayer, Marker, Popup, Polyline, Tooltip, useMap } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import L from "leaflet";
import { Alert } from "@/lib/db";
import { Crosshair, ShieldAlert, Navigation, Layers, Moon, Sun, Mountain, Route, Maximize2, Minimize2 } from "lucide-react";
import { useAiRoutes } from "@/context/AiRouteContext";
import MarkerClusterGroup from "react-leaflet-cluster";
import { createPortal } from "react-dom";
import "leaflet.markercluster/dist/MarkerCluster.css";
import "leaflet.markercluster/dist/MarkerCluster.Default.css";

const TURKEY_BOUNDS = L.latLngBounds(L.latLng(35.5, 25.5), L.latLng(42.5, 44.5));

type MapMode = "satellite" | "dark" | "fault";

const TILES: Record<MapMode, { url: string; attr: string }> = {
  satellite: {
    url: "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
    attr: "Tiles &copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community",
  },
  dark: {
    url: "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png",
    attr: "&copy; OpenStreetMap contributors &copy; CARTO",
  },
  fault: {
    url: "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}",
    attr: "Tiles &copy; Esri",
  },
};

const FAULT_LINES: { name: string; color: string; weight: number; positions: [number, number][] }[] = [
  {
    name: "Kuzey Anadolu Fayı (KAF)", color: "#dc2626", weight: 3,
    positions: [[40.8, 43.5], [40.6, 42.0], [40.3, 40.5], [40.0, 39.5], [39.9, 38.0], [40.2, 37.0], [40.5, 35.5], [40.7, 34.5], [40.8, 33.5], [40.7, 32.5], [40.5, 31.5], [40.5, 30.5], [40.8, 29.5], [40.7, 28.5], [40.8, 28.0]],
  },
  {
    name: "Doğu Anadolu Fayı (DAF)", color: "#ea580c", weight: 3,
    positions: [[39.5, 40.7], [39.2, 40.0], [38.8, 39.2], [38.4, 38.5], [38.0, 37.8], [37.6, 37.2], [37.2, 36.8], [36.8, 36.4], [36.5, 36.1]],
  },
];

const createCorporateIcon = (status: string) => {
  const bg = status === "Kritik" ? "bg-red-600" : status === "Acil" ? "bg-orange-500" : status === "Müdahale Ediliyor" ? "bg-blue-600" : "bg-emerald-500";
  
  return L.divIcon({
    className: "bg-transparent border-0",
    html: `<div class="flex items-center justify-center w-6 h-6">
      <span class="relative flex h-4 w-4">
        <span class="animate-ping absolute inline-flex h-full w-full rounded-full ${bg} opacity-50"></span>
        <span class="relative inline-flex rounded-full h-4 w-4 ${bg} border-2 border-white shadow-sm"></span>
      </span>
    </div>`,
    iconSize: [24, 24], iconAnchor: [12, 12], popupAnchor: [0, -12],
  });
};

const createLogIcon = () =>
  L.divIcon({
    className: "bg-transparent border-0",
    html: `<div class="flex items-center justify-center w-4 h-4">
      <div class="w-2.5 h-2.5 bg-slate-500 border border-white rounded-full shadow-sm"></div>
    </div>`,
    iconSize: [16, 16], iconAnchor: [8, 8], popupAnchor: [0, -8],
  });

const createBaseIcon = () =>
  L.divIcon({
    className: "bg-transparent border-0",
    html: `<div class="flex items-center justify-center w-8 h-8">
      <div class="w-6 h-6 bg-blue-700 border-2 border-white rounded-md shadow-md flex items-center justify-center">
        <div class="w-2 h-2 bg-white rounded-sm"></div>
      </div>
    </div>`,
    iconSize: [32, 32], iconAnchor: [16, 16], popupAnchor: [0, -16],
  });

const STATUS_CLR: Record<string, string> = {
  Kritik: "bg-red-100 text-red-700 border-red-200", 
  Acil: "bg-orange-100 text-orange-700 border-orange-200",
  "Müdahale Ediliyor": "bg-blue-100 text-blue-700 border-blue-200", 
  Orta: "bg-emerald-100 text-emerald-700 border-emerald-200",
};

function EdgeMarkers({ groupedAlerts }: { groupedAlerts: any[] }) {
  const map = useMap();
  const [edges, setEdges] = useState<{x: number, y: number, color: string}[]>([]);

  useEffect(() => {
    const update = () => {
      const bounds = map.getBounds();
      const mapSize = map.getSize();
      const center = map.latLngToContainerPoint(map.getCenter());
      const padding = 20;
      const newEdges: typeof edges = [];

      groupedAlerts.forEach(g => {
        if (!bounds.contains([g.lat, g.lng])) {
          const pt = map.latLngToContainerPoint([g.lat, g.lng]);
          let x = Math.max(padding, Math.min(mapSize.x - padding, pt.x));
          let y = Math.max(padding, Math.min(mapSize.y - padding, pt.y));
          const dx = pt.x - center.x;
          const dy = pt.y - center.y;
          let m = 1;
          
          if (x !== pt.x || y !== pt.y) {
             if (Math.abs(dx) > 0.001) {
                 const xBound = dx > 0 ? mapSize.x - padding : padding;
                 const mX = (xBound - center.x) / dx;
                 if (mX > 0 && mX < m) m = mX;
             }
             if (Math.abs(dy) > 0.001) {
                 const yBound = dy > 0 ? mapSize.y - padding : padding;
                 const mY = (yBound - center.y) / dy;
                 if (mY > 0 && mY < m) m = mY;
             }
             x = center.x + dx * m;
             y = center.y + dy * m;
          }

          let color = "#10b981"; // emerald-500
          if (g.maxStatus === "Kritik") color = "#dc2626"; // red-600
          else if (g.maxStatus === "Acil") color = "#f97316"; // orange-500
          else if (g.maxStatus === "Müdahale Ediliyor") color = "#2563eb"; // blue-600

          newEdges.push({ x, y, color });
        }
      });
      setEdges(newEdges);
    };

    map.on("move", update);
    map.on("zoom", update);
    update();
    return () => {
      map.off("move", update);
      map.off("zoom", update);
    };
  }, [map, groupedAlerts]);

  const container = map.getContainer();
  return createPortal(
    <div className="absolute inset-0 pointer-events-none z-[1000] overflow-hidden">
      {edges.map((e, i) => (
        <div
          key={i}
          className="absolute w-3 h-3 rounded-full border-2 border-white shadow-sm"
          style={{
            left: e.x, top: e.y,
            backgroundColor: e.color,
            transform: `translate(-50%, -50%)`,
          }}
        />
      ))}
    </div>,
    container
  );
}

function MapResizer({ trigger }: { trigger: boolean }) {
  const map = useMap();
  useEffect(() => { setTimeout(() => map.invalidateSize(), 120); }, [trigger, map]);
  return null;
}

function MapController() {
  const map = useMap();
  const didSet = useRef(false);
  useEffect(() => {
    if (didSet.current) return;
    map.fitBounds(TURKEY_BOUNDS);
    didSet.current = true;
  }, [map]);
  return null;
}

function TB({
  active, onClick, title, children,
}: { active?: boolean; onClick: () => void; title: string; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      title={title}
      className={`flex items-center gap-1.5 px-3 py-2 rounded text-xs font-semibold transition-colors whitespace-nowrap border ${
        active
          ? "bg-slate-800 border-slate-700 text-white"
          : "bg-white text-slate-600 border-slate-200 hover:bg-slate-50 hover:text-slate-900 shadow-sm"
      }`}
    >
      {children}
    </button>
  );
}

const MAX_LOGS = 100;

export default function MapComponent() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [eqLogs, setEqLogs] = useState<any[]>([]);
  const [mode, setMode] = useState<MapMode>("satellite");
  const [showFaults, setShowFaults] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const { routes, removeRoute, addRouteToRegion } = useAiRoutes();
  const isActiveRef = useRef(true);

  useEffect(() => {
    isActiveRef.current = true;
    let timeoutId: NodeJS.Timeout;

    const fetchAll = async () => {
      try {
        const [aRes, lRes] = await Promise.allSettled([
          fetch("/api/alerts"), fetch("/api/earthquake_logs"),
        ]);
        if (aRes.status === "fulfilled" && aRes.value.ok) {
          const d = await aRes.value.json();
          if (Array.isArray(d)) setAlerts(d);
        }
        if (lRes.status === "fulfilled" && lRes.value.ok) {
          const d = await lRes.value.json();
          if (Array.isArray(d)) setEqLogs(d.slice(0, MAX_LOGS));
        }
      } catch (e) { console.error("Map fetch error:", e); }
    };

    const poll = async () => {
      if (!isActiveRef.current) return;
      await fetchAll();
      if (isActiveRef.current) timeoutId = setTimeout(poll, 8000);
    };
    poll();

    return () => {
      isActiveRef.current = false;
      clearTimeout(timeoutId);
    };
  }, []);

  const groupedAlerts = useMemo(() => {
    const map = new Map<string, {
      key: string;
      city: string;
      district: string;
      lat: number;
      lng: number;
      count: number;
      criticalCount: number;
      maxStatus: string;
    }>();

    alerts.forEach(a => {
      const key = `${a.location.city}-${a.location.district}-${Math.round(a.location.lat * 1000)}-${Math.round(a.location.lng * 1000)}`;
      if (!map.has(key)) {
        map.set(key, {
          key, city: a.location.city, district: a.location.district,
          lat: a.location.lat, lng: a.location.lng,
          count: 0, criticalCount: 0, maxStatus: "Orta"
        });
      }
      
      const g = map.get(key)!;
      g.count++;
      if (a.status === "Kritik") g.criticalCount++;
      
      if (a.status === "Kritik") g.maxStatus = "Kritik";
      else if (a.status === "Acil" && g.maxStatus !== "Kritik") g.maxStatus = "Acil";
      else if (a.status === "Müdahale Ediliyor" && g.maxStatus !== "Kritik" && g.maxStatus !== "Acil") g.maxStatus = "Müdahale Ediliyor";
    });

    return Array.from(map.values());
  }, [alerts]);

  const containerCls = isFullscreen
    ? "fixed inset-0 z-[9999] bg-slate-50"
    : "h-full w-full rounded-2xl overflow-hidden border border-slate-200 relative bg-slate-50 shadow-sm";

  return (
    <div className={containerCls}>
      
      {/* ── Üst Araç Çubuğu ── */}
      <div className="absolute top-4 left-4 right-4 z-[1000] flex items-center justify-between pointer-events-none">
        <div className="flex gap-2 pointer-events-auto">
          <div className="flex items-center gap-1 bg-white rounded-lg p-1 shadow-md border border-slate-200">
            <Layers size={14} className="text-slate-400 ml-2" />
            <TB active={mode === "satellite"} onClick={() => setMode("satellite")} title="Uydu Haritası">Uydu</TB>
            <TB active={mode === "dark"} onClick={() => setMode("dark")} title="Karanlık Harita">Koyu</TB>
          </div>
          <div className="flex items-center gap-1 bg-white rounded-lg p-1 shadow-md border border-slate-200">
            <TB active={showFaults || mode === "fault"} onClick={() => setShowFaults((p) => !p)} title="Fay Hatlarını Göster">
              <Mountain size={14} /> Fay Hatları
            </TB>
          </div>
        </div>

        <div className="pointer-events-auto">
          <button
            onClick={() => setIsFullscreen((p) => !p)}
            className="w-10 h-10 bg-white border border-slate-200 rounded-lg flex items-center justify-center text-slate-600 hover:bg-slate-50 hover:text-slate-900 shadow-md transition-colors"
          >
            {isFullscreen ? <Minimize2 size={18} /> : <Maximize2 size={18} />}
          </button>
        </div>
      </div>

      {/* ── Sağ Alt Rotalar Paneli (AFAD Koordinasyon) ── */}
      <div className="absolute bottom-4 right-4 z-[1000] w-72 flex flex-col bg-white border border-slate-200 rounded-xl p-4 shadow-lg pointer-events-auto">
        <h3 className="text-sm font-bold flex items-center gap-2 mb-3 pb-3 border-b border-slate-100 text-slate-800">
          <Navigation size={16} className="text-blue-600" /> Operasyonel Rotalar
        </h3>
        <div className="overflow-y-auto max-h-48 pr-1 space-y-2">
          {routes.length === 0 ? (
            <p className="text-xs text-slate-500 text-center py-2">Aktif yönlendirme bulunmuyor.</p>
          ) : (
            routes.map(r => (
              <div key={r.id} className="bg-slate-50 border border-slate-100 rounded-lg p-2.5 flex justify-between items-center">
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-bold text-slate-700 truncate">{r.destination}</p>
                  <p className="text-[10px] text-slate-500 mt-0.5 truncate">{r.startName}</p>
                  {r.status === "Hesaplanıyor" && (
                    <p className="text-[10px] text-amber-600 font-semibold mt-0.5 animate-pulse">Rota hesaplanıyor…</p>
                  )}
                </div>
                <button onClick={() => removeRoute(r.id)} className="ml-2 text-xs font-semibold text-red-600 hover:text-red-700 px-2 py-1 bg-red-50 rounded flex-shrink-0">İptal</button>
              </div>
            ))
          )}
        </div>
      </div>

      {/* ── Sol Alt Lejant ── */}
      <div className="absolute bottom-4 left-4 z-[1000] bg-white rounded-xl p-4 border border-slate-200 shadow-lg text-xs font-medium text-slate-700 space-y-3 pointer-events-none min-w-[160px]">
        <div className="font-bold text-slate-900 border-b border-slate-100 pb-2 mb-1">Durum Göstergeleri</div>
        <div className="flex items-center gap-3"><span className="w-2.5 h-2.5 rounded-full bg-red-600 shadow-sm" />Kritik İhbar</div>
        <div className="flex items-center gap-3"><span className="w-2.5 h-2.5 rounded-full bg-orange-500 shadow-sm" />Acil</div>
        <div className="flex items-center gap-3"><span className="w-2.5 h-2.5 rounded-full bg-emerald-500 shadow-sm" />Güvende</div>
        <div className="flex items-center gap-3"><span className="w-2.5 h-2.5 rounded-full bg-slate-500 shadow-sm" />Sarsıntı Logu</div>
        <div className="flex items-center gap-3"><span className="w-6 border-t-2 border-dashed border-blue-600" />Yönlendirme</div>
      </div>

      {/* ── Leaflet Harita ── */}
      <MapContainer
        center={[39.0, 35.0]}
        zoom={6}
        minZoom={5}
        maxBounds={TURKEY_BOUNDS}
        maxBoundsViscosity={1.0}
        zoomControl={false}
        className="h-full w-full z-0"
      >
        <TileLayer key={mode} url={TILES[mode].url} attribution={TILES[mode].attr} />
        
        <MapResizer trigger={isFullscreen} />
        <MapController />
        <EdgeMarkers groupedAlerts={groupedAlerts} />

        {(showFaults || mode === "fault") && FAULT_LINES.map((fl) => (
          <Polyline key={fl.name} positions={fl.positions} pathOptions={{ color: fl.color, weight: fl.weight, opacity: 0.7 }} />
        ))}

        {routes.map((route) => {
          const positions: [number, number][] =
            route.routeGeometry && route.routeGeometry.length > 1
              ? route.routeGeometry
              : [[route.startLat, route.startLng], [route.lat, route.lng]];

          const isCalculating = route.status === "Hesaplanıyor";

          return (
            <Polyline
              key={`route-${route.id}`}
              positions={positions}
              pathOptions={{
                color: isCalculating ? "#94a3b8" : "#2563eb",
                weight: isCalculating ? 1.5 : 2.5,
                opacity: isCalculating ? 0.5 : 0.85,
                dashArray: "6 5",
              }}
            />
          );
        })}

        {Array.from(new Set(routes.map(r => r.startName))).map(name => {
          const route = routes.find(r => r.startName === name)!;
          return (
            <Marker key={name} position={[route.startLat, route.startLng]} icon={createBaseIcon()}>
              <Tooltip direction="top" permanent className="corporate-tooltip font-bold text-slate-800">Koordinasyon Merkezi</Tooltip>
            </Marker>
          );
        })}

        <MarkerClusterGroup chunkedLoading maxClusterRadius={35} iconCreateFunction={(cluster) => {
          return L.divIcon({
            html: `<div class="w-8 h-8 rounded-full bg-white border-2 border-slate-800 text-slate-800 flex items-center justify-center font-bold text-xs shadow-md">${cluster.getChildCount()}</div>`,
            className: 'bg-transparent',
            iconSize: L.point(32, 32, true),
          });
        }}>
          {groupedAlerts.map((g) => (
            <Marker key={g.key} position={[g.lat, g.lng]} icon={createCorporateIcon(g.maxStatus)}>
              <Popup minWidth={260} className="corporate-popup">
                <div className="p-1">
                  <div className="border-b border-slate-100 pb-3 mb-3">
                    <p className="font-bold text-base text-slate-800 leading-tight">{g.city}</p>
                    <p className="text-sm text-slate-500 font-medium">{g.district}</p>
                  </div>
                  
                  <div className="grid grid-cols-2 gap-2 mb-4">
                    <div className="bg-slate-50 p-2 rounded-lg border border-slate-100">
                      <p className="text-[10px] font-semibold text-slate-500 uppercase tracking-wide">Toplam İhbar</p>
                      <p className="text-xl font-bold text-slate-800">{g.count}</p>
                    </div>
                    <div className={`p-2 rounded-lg border ${g.criticalCount > 0 ? 'bg-red-50 border-red-100' : 'bg-slate-50 border-slate-100'}`}>
                      <p className={`text-[10px] font-semibold uppercase tracking-wide ${g.criticalCount > 0 ? 'text-red-600' : 'text-slate-500'}`}>Kritik</p>
                      <p className={`text-xl font-bold ${g.criticalCount > 0 ? 'text-red-700' : 'text-slate-800'}`}>{g.criticalCount}</p>
                    </div>
                  </div>

                  <button
                    onClick={() => addRouteToRegion(g.key, `${g.city} (${g.district})`, g.lat, g.lng)}
                    disabled={routes.some(r => r.targetId === g.key)}
                    className="w-full bg-blue-600 hover:bg-blue-700 disabled:bg-slate-200 disabled:text-slate-400 disabled:cursor-not-allowed text-white py-2.5 rounded-lg font-semibold transition-colors text-sm shadow-sm"
                  >
                    {routes.some(r => r.targetId === g.key) ? "Ekip Yönlendirildi" : "Arama Kurtarma Ekibi Sevk Et"}
                  </button>
                </div>
              </Popup>
            </Marker>
          ))}

          {eqLogs.map((log, idx) => (
            <Marker key={log.id || `log-${idx}`} position={[log.latitude, log.longitude]} icon={createLogIcon()}>
              <Tooltip direction="top" className="corporate-tooltip text-slate-600">Sarsıntı Logu: {log.device_id.substring(0,8)}</Tooltip>
            </Marker>
          ))}
        </MarkerClusterGroup>
      </MapContainer>

      {/* ── KURUMSAL STİLLER ── */}
      <style jsx global>{`
        /* Temiz Harita Container */
        .leaflet-container {
          background-color: #f8fafc !important;
          font-family: inherit !important;
        }

        /* Kurumsal Popup Stilleri */
        .corporate-popup .leaflet-popup-content-wrapper {
          background: #ffffff;
          border-radius: 12px;
          border: 1px solid #e2e8f0;
          box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
          padding: 4px;
        }
        .corporate-popup .leaflet-popup-content { margin: 12px; line-height: 1.4; }
        .corporate-popup .leaflet-popup-tip { background: #ffffff; border-top: 1px solid #e2e8f0; border-left: 1px solid #e2e8f0; }
        .corporate-popup .leaflet-popup-close-button { color: #64748b !important; padding: 12px 12px 0 0 !important; }
        .corporate-popup .leaflet-popup-close-button:hover { color: #0f172a !important; background: transparent; }
        
        /* Tooltipler */
        .corporate-tooltip {
          background: #ffffff !important;
          border: 1px solid #e2e8f0 !important;
          border-radius: 6px !important;
          font-size: 11px !important;
          font-weight: 600 !important;
          box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1) !important;
          padding: 6px 10px !important;
        }
        .corporate-tooltip::before { border-top-color: #ffffff !important; }

        /* Marker Animasyonları (Ping effect is handled by tailwind classes inside the icon HTML) */
      `}</style>
    </div>
  );
}