"use client";

import { useState, useEffect, useRef } from "react";
import { AlertTriangle, MapPin, Activity, Navigation, Search, Route, Clock, ChevronLeft, ChevronRight } from "lucide-react";
import { GeocodeText } from "@/components/GeocodeText";
import { useAiRoutes } from "@/context/AiRouteContext";
import { useRouter } from "next/navigation";
import { toast } from "sonner";

function extractTime(eq: any): string | null {
  if (!eq) return null;
  
  // Try known keys
  const knownKeys = ['occurred_at', 'timestamp', 'created_at', 'createdAt', 'time', 'date', 'datetime', 'date_time', 'origin_time', 'eventDate', '_arrivedAt'];
  let rawTime: any = null;
  
  for (const k of knownKeys) {
    if (eq[k]) {
      rawTime = eq[k];
      break;
    }
  }

  // Fallback scan properties
  if (!rawTime) {
    for (const key of Object.keys(eq)) {
      const val = eq[key];
      if (typeof val === 'string' && val.includes('T') && val.includes('Z')) {
        rawTime = val;
        break;
      }
      if (typeof val === 'string' && /^\d{4}-\d{2}-\d{2}[T ]/.test(val)) {
        rawTime = val;
        break;
      }
      if (typeof val === 'number' && val > 1000000000000) {
        rawTime = val;
        break;
      }
    }
  }

  if (!rawTime) return null;
  
  const d = new Date(rawTime);
  if (isNaN(d.getTime())) return null;
  
  return d.toLocaleTimeString("tr-TR", {
    timeZone: "Europe/Istanbul",
    hour: "2-digit",
    minute: "2-digit",
  });
}

interface Earthquake {
  id: string;
  magnitude: number;
  depth_km: number;
  location_name: string;
  latitude: number;
  longitude: number;
  created_at?: string;
  timestamp?: string;
  occurred_at?: string;
  time?: string;
  date?: string;
  datetime?: string;
}

export default function DepremlerPage() {
  const [earthquakes, setEarthquakes] = useState<Earthquake[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const ITEMS_PER_PAGE = 6;
  const isActiveRef = useRef(true);
  const { routes, addEarthquakeRoute } = useAiRoutes();
  const router = useRouter();

  // Reset pagination
  useEffect(() => {
    setCurrentPage(1);
  }, [search]);

  useEffect(() => {
    isActiveRef.current = true;
    let timeoutId: NodeJS.Timeout;

    const fetchEarthquakes = async () => {
      if (!isActiveRef.current) return;
      try {
        const res = await fetch("/api/earthquakes");
        if (res.ok) {
          const data = await res.json();
          if (Array.isArray(data)) {
            setEarthquakes(data);
          }
        }
      } catch (e) {
        console.error("Deprem verisi alınamadı:", e);
      } finally {
        setIsLoading(false);
      }
      if (isActiveRef.current) timeoutId = setTimeout(fetchEarthquakes, 15000);
    };

    fetchEarthquakes();

    let es: EventSource | null = null;
    let sseRetry = 0;
    let sseTimer: NodeJS.Timeout;

    const connectSSE = () => {
      if (!isActiveRef.current || sseRetry >= 3) return;
      es = new EventSource("/api/stream");

      es.addEventListener("earthquake_alert", (event) => {
        try {
          const payload = JSON.parse(event.data);
          if (payload && payload.data) {
            setEarthquakes((prev) => {
              if (prev.some(eq => eq.id === payload.data.id)) return prev;
              return [payload.data, ...prev].slice(0, 100);
            });
          }
        } catch (err) {
          console.error("SSE parse error", err);
        }
      });

      es.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          if (payload.event_type === "earthquake_alert" && payload.data) {
            setEarthquakes((prev) => {
              if (prev.some(eq => eq.id === payload.data.id)) return prev;
              return [payload.data, ...prev].slice(0, 100);
            });
          }
        } catch (err) {}
      };

      es.onerror = () => {
        es?.close();
        es = null;
        sseRetry++;
        if (isActiveRef.current && sseRetry < 3) {
          sseTimer = setTimeout(connectSSE, sseRetry * 5000);
        }
      };
    };

    connectSSE();

    return () => {
      isActiveRef.current = false;
      clearTimeout(timeoutId);
      clearTimeout(sseTimer);
      es?.close();
    };
  }, []);

  const filtered = earthquakes.filter((eq) => {
    const q = search.toLowerCase();
    return !q || eq.location_name?.toLowerCase().includes(q);
  });

  const totalPages = Math.max(1, Math.ceil(filtered.length / ITEMS_PER_PAGE));
  const paginated = filtered.slice((currentPage - 1) * ITEMS_PER_PAGE, currentPage * ITEMS_PER_PAGE);

  const handleLocate = (eq: Earthquake) => {
    addEarthquakeRoute(eq);
    toast.success("Rota haritaya eklendi", {
      description: `${eq.location_name || "Konum"} için AFAD üssünden rota hesaplanıyor…`,
      duration: 4000,
    });
    router.push("/");
  };

  return (
    <div className="space-y-6 w-full">
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-3xl font-bold text-foreground tracking-tight flex items-center gap-3">
            <AlertTriangle className="text-red-500" size={32} />
            Deprem Olayları
          </h1>
          <p className="text-slate-500 mt-2 text-sm font-medium">
            Sisteme ulaşan resmi deprem bildirimleri (Webhook)
          </p>
        </div>
        <div className="flex items-center gap-2 bg-card border border-border px-4 py-2 rounded-full">
          <span className="w-2 h-2 rounded-full bg-red-500 animate-pulse" />
          <span className="text-xs font-bold text-foreground tracking-wider uppercase">
            {earthquakes.length} Olay
          </span>
        </div>
      </div>

      <div className="flex gap-3">
        <div className="relative flex-1">
          <Search size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            type="text"
            placeholder="Konum adı ile ara..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full bg-card border border-border rounded-2xl pl-11 pr-4 py-3.5 text-sm text-foreground placeholder:text-slate-500 focus:outline-none focus:border-red-500 transition-colors font-medium"
          />
        </div>
      </div>

      {isLoading ? (
        <div className="text-center py-24 text-slate-500 font-bold">Veriler yükleniyor...</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-24 text-slate-500 font-bold">Kayıtlı deprem olayı bulunamadı.</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {paginated.map((eq, idx) => {
            const targetId = `eq-${eq.id}`;
            const hasRoute = routes.some(r => r.targetId === targetId);
            const routeStatus = routes.find(r => r.targetId === targetId)?.status;

            const mag = eq.magnitude ?? 0;
            const magBg =
              mag >= 6.0 ? "from-red-600 to-red-800" :
              mag >= 5.0 ? "from-orange-500 to-red-600" :
              mag >= 4.0 ? "from-yellow-500 to-orange-500" :
              "from-red-500 to-orange-600";

            return (
              <div
                key={eq.id ?? `eq-${idx}`}
                className="bg-card backdrop-blur-xl border border-border rounded-3xl p-5 text-left transition-all hover:-translate-y-1 hover:shadow-lg hover:border-red-500/30 duration-200 flex flex-col"
              >
                {/* Header */}
                <div className="flex items-start justify-between mb-4">
                  <div className="flex items-center gap-3">
                    <div className={`w-12 h-12 rounded-2xl bg-gradient-to-br ${magBg} flex items-center justify-center text-white shadow-lg flex-shrink-0`}>
                      <Activity size={24} />
                    </div>
                    <div className="min-w-0">
                      <p className="text-foreground font-bold text-lg leading-tight">
                        {mag.toFixed(1)} Büyüklüğünde
                      </p>
                      <p className="text-slate-500 text-[10px] font-bold font-mono mt-0.5 truncate">
                        ID: {eq.id?.slice(0, 8) ?? "Bilinmiyor"}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-1 text-slate-500 bg-border/50 px-2 py-1 rounded-lg flex-shrink-0">
                    <Clock size={12} className="text-slate-400" />
                    <span className="text-xs font-semibold font-mono">
                      {extractTime(eq) ?? "--:--"}
                    </span>
                  </div>
                </div>

                {/* Location Info */}
                <div className="space-y-2 flex-1">
                  {/* Geocoded location */}
                  <div className="flex items-start gap-2 p-2.5 rounded-xl bg-red-500/8 border border-red-500/15">
                    <MapPin size={13} className="flex-shrink-0 text-red-400 mt-0.5" />
                    <span className="font-semibold text-sm text-foreground leading-snug">
                      <GeocodeText lat={eq.latitude} lng={eq.longitude} fallback={eq.location_name || "Konum çözümleniyor..."} />
                    </span>
                  </div>

                  {/* Coordinates */}
                  <div className="flex items-center gap-2 px-2.5 py-2 rounded-xl bg-card border border-border">
                    <Navigation size={12} className="flex-shrink-0 text-blue-400" />
                    <span className="font-mono text-xs font-bold text-foreground tracking-tight">
                      {eq.latitude?.toFixed(4)}°N, {eq.longitude?.toFixed(4)}°E
                    </span>
                  </div>

                  {/* Depth */}
                  <div className="flex items-center gap-2 text-xs text-slate-500">
                    <Activity size={11} className="flex-shrink-0" />
                    <span className="font-medium">Derinlik: <strong className="text-foreground">{eq.depth_km} km</strong></span>
                  </div>
                </div>

                {/* Location Button */}
                <div className="mt-4 pt-3 border-t border-border">
                  <button
                    onClick={() => handleLocate(eq)}
                    disabled={hasRoute}
                    className={`
                      w-full flex items-center justify-center gap-2 py-2.5 rounded-xl
                      text-sm font-bold transition-all duration-200
                      ${hasRoute
                        ? "bg-border text-slate-500 cursor-not-allowed"
                        : "bg-blue-600 hover:bg-blue-500 active:scale-95 text-white shadow-md hover:shadow-blue-500/25"
                      }
                    `}
                  >
                    <Route size={14} />
                    {hasRoute
                      ? routeStatus === "Hesaplanıyor"
                        ? "Rota Hesaplanıyor…"
                        : "Haritada Gösteriliyor"
                      : "Konum"}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Pagination */}
      {!isLoading && filtered.length > 0 && (
        <div className="flex items-center justify-between bg-card border border-border rounded-2xl px-5 py-4 mt-6">
          <span className="text-sm font-medium text-slate-500">
            Toplam <strong className="text-foreground">{filtered.length}</strong> kayıttan <strong className="text-foreground">{(currentPage - 1) * ITEMS_PER_PAGE + 1} - {Math.min(currentPage * ITEMS_PER_PAGE, filtered.length)}</strong> arası gösteriliyor
          </span>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
              disabled={currentPage === 1}
              className="p-2 rounded-xl border border-border bg-card hover:bg-hover hover:border-slate-500 disabled:opacity-50 disabled:cursor-not-allowed text-foreground transition-colors flex items-center justify-center shadow-sm"
              aria-label="Önceki Sayfa"
            >
              <ChevronLeft size={18} />
            </button>
            <div className="flex items-center justify-center w-16 h-10 rounded-xl bg-hover border border-border">
              <span className="text-sm font-bold text-foreground">
                {currentPage} / {totalPages}
              </span>
            </div>
            <button
              onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
              disabled={currentPage === totalPages}
              className="p-2 rounded-xl border border-border bg-card hover:bg-hover hover:border-slate-500 disabled:opacity-50 disabled:cursor-not-allowed text-foreground transition-colors flex items-center justify-center shadow-sm"
              aria-label="Sonraki Sayfa"
            >
              <ChevronRight size={18} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
