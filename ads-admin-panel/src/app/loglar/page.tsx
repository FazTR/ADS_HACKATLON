"use client";

import { useState, useEffect, useRef } from "react";
import { ScrollText, MapPin, Smartphone, Clock, Search, Navigation } from "lucide-react";
import { GeocodeText } from "@/components/GeocodeText";

interface EarthquakeLog {
  id: string;
  device_id: string;
  latitude: number;
  longitude: number;
  reported_at: string;
}

function formatDate(iso?: string): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("tr-TR", {
    day: "2-digit",
    month: "long",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export default function LoglarPage() {
  const [logs, setLogs] = useState<EarthquakeLog[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const ITEMS_PER_PAGE = 12;
  const isActiveRef = useRef(true);

  useEffect(() => {
    isActiveRef.current = true;
    let timeoutId: NodeJS.Timeout;

    const fetchLogs = async () => {
      if (!isActiveRef.current) return;
      try {
        const res = await fetch("/api/earthquake_logs");
        if (res.ok) {
          const data = await res.json();
          if (Array.isArray(data)) setLogs(data);
        }
      } catch (e) {
        console.error("Log verisi alınamadı:", e);
      } finally {
        setIsLoading(false);
      }
      if (isActiveRef.current) timeoutId = setTimeout(fetchLogs, 15000);
    };

    fetchLogs();

    // SSE for real-time updates
    let es: EventSource | null = null;
    let sseRetry = 0;
    let sseTimer: NodeJS.Timeout;

    const connectSSE = () => {
      if (!isActiveRef.current || sseRetry >= 3) return;
      es = new EventSource("/api/stream");
      
      es.addEventListener("earthquake_log", (event) => {
        try {
          const payload = JSON.parse(event.data);
          // Prepend new log
          if (payload && payload.data) {
            setLogs((prev) => {
              // Avoid duplicates
              if (prev.some(l => l.id === payload.data.id)) return prev;
              return [payload.data, ...prev].slice(0, 500);
            });
          }
        } catch (err) {
          console.error("SSE parse error", err);
        }
      });

      // Also listen to general messages just in case
      es.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          if (payload.event_type === "earthquake_log" && payload.data) {
            setLogs((prev) => {
              if (prev.some(l => l.id === payload.data.id)) return prev;
              return [payload.data, ...prev].slice(0, 500);
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

  const filtered = logs.filter((log) => {
    const q = search.toLowerCase();
    return !q || log.device_id?.toLowerCase().includes(q) || log.id?.toLowerCase().includes(q);
  });

  const totalPages = Math.ceil(filtered.length / ITEMS_PER_PAGE);
  const paginatedLogs = filtered.slice(
    (currentPage - 1) * ITEMS_PER_PAGE,
    currentPage * ITEMS_PER_PAGE
  );

  return (
    <div className="space-y-6 w-full">
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-3xl font-bold text-foreground tracking-tight flex items-center gap-3">
            <ScrollText className="text-orange-500" size={32} />
            Kitle Kaynaklı Deprem Logları
          </h1>
          <p className="text-slate-500 mt-2 text-sm font-medium">
            Mobil cihazlardan gelen ham sarsıntı verileri
          </p>
        </div>
        <div className="flex items-center gap-2 bg-card border border-border px-4 py-2 rounded-full">
          <span className="w-2 h-2 rounded-full bg-orange-500 animate-pulse" />
          <span className="text-xs font-bold text-foreground tracking-wider uppercase">
            {logs.length} Log
          </span>
        </div>
      </div>

      <div className="flex gap-3">
        <div className="relative flex-1">
          <Search size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            type="text"
            placeholder="Cihaz ID veya Log ID ile ara..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setCurrentPage(1);
            }}
            className="w-full bg-card border border-border rounded-2xl pl-11 pr-4 py-3.5 text-sm text-foreground placeholder:text-slate-500 focus:outline-none focus:border-orange-500 transition-colors font-medium"
          />
        </div>
      </div>

      {isLoading ? (
        <div className="text-center py-24 text-slate-500 font-bold">Loglar yükleniyor...</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-24 text-slate-500 font-bold">Kayıtlı sarsıntı logu bulunamadı.</div>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {paginatedLogs.map((log) => (
              <div
                key={log.id}
                className="bg-card backdrop-blur-xl border border-border rounded-3xl p-5 text-left transition-all hover:-translate-y-1 hover:shadow-lg hover:border-orange-500/30 duration-200"
              >
                <div className="flex items-start justify-between mb-4">
                  <div className="flex items-center gap-3">
                    <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-orange-500 to-amber-600 flex items-center justify-center text-white shadow-lg flex-shrink-0">
                      <Smartphone size={24} />
                    </div>
                    <div className="min-w-0">
                      <p className="text-foreground font-bold text-sm leading-tight truncate">
                        Cihaz: {log.device_id}
                      </p>
                      <p className="text-slate-500 text-[10px] font-bold font-mono mt-0.5 truncate">
                        Log ID: {log.id?.slice(0, 8) ?? "Bilinmiyor"}
                      </p>
                    </div>
                  </div>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center gap-2 text-xs text-slate-500">
                    <MapPin size={11} className="flex-shrink-0" />
                    <span className="font-medium truncate">
                      <GeocodeText lat={log.latitude} lng={log.longitude} fallback="Konum çözümleniyor..." />
                    </span>
                  </div>
                  <div className="flex items-center gap-2 text-xs text-slate-500">
                    <Navigation size={11} className="flex-shrink-0" />
                    <span className="font-medium truncate">
                      Enlem: {log.latitude}, Boylam: {log.longitude}
                    </span>
                  </div>
                  {log.reported_at && (
                    <div className="flex items-center gap-2 text-xs text-slate-500">
                      <Clock size={11} className="flex-shrink-0" />
                      <span className="font-medium">{formatDate(log.reported_at)}</span>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>

          {totalPages > 1 && (
            <div className="flex items-center justify-between pt-6 mt-6 border-t border-border">
              <button
                onClick={() => setCurrentPage((prev) => Math.max(prev - 1, 1))}
                disabled={currentPage === 1}
                className="px-4 py-2 bg-card border border-border rounded-xl text-sm font-bold text-foreground disabled:opacity-50 transition-all hover:bg-hover disabled:hover:bg-card"
              >
                Önceki
              </button>
              <div className="text-sm font-medium text-slate-500">
                Sayfa <span className="font-bold text-foreground">{currentPage}</span> / {totalPages}
              </div>
              <button
                onClick={() => setCurrentPage((prev) => Math.min(prev + 1, totalPages))}
                disabled={currentPage === totalPages}
                className="px-4 py-2 bg-card border border-border rounded-xl text-sm font-bold text-foreground disabled:opacity-50 transition-all hover:bg-hover disabled:hover:bg-card"
              >
                Sonraki
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
