"use client";

import { useEffect, useState, useRef, useCallback } from "react";
import { useRouter } from "next/navigation";
import { AlertTriangle, X, Activity, MapPin, Navigation } from "lucide-react";

interface EarthquakeAlert {
  id: string;
  magnitude: number;
  depth_km: number;
  location_name: string;
  latitude: number;
  longitude: number;
  created_at?: string;
  timestamp?: string;
  occurred_at?: string;
}

interface BannerItem extends EarthquakeAlert {
  _bannerId: string;
  _arrivedAt: number;
}

const AUTO_CLOSE_MS = 10000;

export function EarthquakeBanner() {
  const [items, setItems] = useState<BannerItem[]>([]);
  const seenIds = useRef<Set<string>>(new Set());

  useEffect(() => {
    let es: EventSource;
    let retryCount = 0;
    let retryTimer: NodeJS.Timeout;

    const connect = () => {
      es = new EventSource("/api/stream");

      const handleEq = (raw: string) => {
        try {
          const payload = JSON.parse(raw);
          const eq: EarthquakeAlert = payload?.data ?? payload;
          if (!eq?.id || seenIds.current.has(eq.id)) return;
          seenIds.current.add(eq.id);

          const item: BannerItem = {
            ...eq,
            _bannerId: `${eq.id}-${Date.now()}`,
            _arrivedAt: Date.now(),
          };

          // Keep max 5 recent events
          setItems((prev) => [item, ...prev].slice(0, 5));
        } catch {}
      };

      es.addEventListener("earthquake_alert", (e) => handleEq(e.data));

      es.onmessage = (e) => {
        try {
          const p = JSON.parse(e.data);
          if (p.event_type === "earthquake_alert") handleEq(JSON.stringify(p));
        } catch {}
      };

      es.onerror = () => {
        es.close();
        retryCount++;
        if (retryCount < 5) {
          retryTimer = setTimeout(connect, retryCount * 4000);
        }
      };
    };

    connect();
    return () => {
      es?.close();
      clearTimeout(retryTimer);
    };
  }, []);

  const isFirstLoad = useRef(true);
  
  useEffect(() => {
    let timeoutId: NodeJS.Timeout;

    const pollEarthquakes = async () => {
      try {
        const res = await fetch("/api/earthquakes");
        if (res.ok) {
          const data = await res.json();
          if (Array.isArray(data)) {
            let added = false;
            const newItems: BannerItem[] = [];
            
            data.forEach((eq: EarthquakeAlert) => {
              if (!eq?.id) return;
              
              if (isFirstLoad.current) {
                // Only add initial events to seenIds
                seenIds.current.add(eq.id);
              } else if (!seenIds.current.has(eq.id)) {
                // New earthquake event
                seenIds.current.add(eq.id);
                newItems.push({
                  ...eq,
                  _bannerId: `${eq.id}-${Date.now()}`,
                  _arrivedAt: Date.now(),
                });
                added = true;
              }
            });
            isFirstLoad.current = false;

            if (added) {
              setItems((prev) => [...newItems, ...prev].slice(0, 5));
            }
          }
        }
      } catch (e) {
        // Silent catch
      } finally {
        // Poll every 8s
      }
    };

    pollEarthquakes();

    return () => {
      clearTimeout(timeoutId);
    };
  }, []);

  const handleRemove = useCallback((idToRemove: string) => {
    setItems((prev) => prev.filter((item) => item._bannerId !== idToRemove));
  }, []);

  if (items.length === 0) return null;

  return (
    <div
      className="fixed top-4 left-0 right-0 z-[9999] pointer-events-none flex flex-col items-center gap-3"
      aria-live="assertive"
    >
      <style>{`
        @keyframes drain {
          from { transform: scaleX(1); transform-origin: left; }
          to   { transform: scaleX(0); transform-origin: left; }
        }
      `}</style>
      
      {items.map((item) => (
        <SingleBanner key={item._bannerId} item={item} onRemove={handleRemove} />
      ))}
    </div>
  );
}

function SingleBanner({ item, onRemove }: { item: BannerItem; onRemove: (id: string) => void }) {
  const [closing, setClosing] = useState(false);
  const router = useRouter();

  const close = useCallback(() => {
    setClosing(true);
    setTimeout(() => {
      onRemove(item._bannerId);
    }, 450);
  }, [item._bannerId, onRemove]);

  useEffect(() => {
    const timer = setTimeout(close, AUTO_CLOSE_MS);
    return () => clearTimeout(timer);
  }, [close]);

  const mag = item.magnitude ?? 0;
  const magColor =
    mag >= 6.0 ? "text-red-300" :
    mag >= 5.0 ? "text-orange-300" :
    mag >= 4.0 ? "text-yellow-300" : "text-emerald-300";

  return (
    <div
      className={`
        relative w-[90%] max-w-4xl rounded-2xl overflow-hidden
        pointer-events-auto cursor-pointer
        transition-all duration-450 ease-in-out
        ${closing
          ? "opacity-0 -translate-y-5 scale-95"
          : "opacity-100 translate-y-0 scale-100"}
      `}
      style={{
        background: "rgba(15, 5, 5, 0.82)",
        backdropFilter: "blur(20px) saturate(180%)",
        WebkitBackdropFilter: "blur(20px) saturate(180%)",
        border: "1px solid rgba(239, 68, 68, 0.35)",
        boxShadow: "0 8px 32px rgba(220, 38, 38, 0.25), 0 2px 8px rgba(0,0,0,0.4)",
      }}
      onClick={() => {
        close();
        router.push("/depremler");
      }}
    >
      {/* Progress bar */}
      <div
        className="absolute bottom-0 left-0 h-[2px] rounded-full"
        style={{
          width: "100%",
          background: "linear-gradient(90deg, #ef4444, #f97316)",
          animation: `drain ${AUTO_CLOSE_MS}ms linear forwards`,
        }}
      />

      <div className="flex items-center gap-4 px-5 py-4">
        {/* Icon */}
        <div className="relative flex-shrink-0">
          <span className="absolute inset-0 rounded-full bg-red-500 opacity-30 animate-ping" />
          <div className="relative w-10 h-10 rounded-full bg-gradient-to-br from-red-500 to-orange-600 flex items-center justify-center shadow-lg">
            <AlertTriangle size={20} className="text-white" strokeWidth={2.5} />
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-0.5">
            <span className="text-[10px] font-black tracking-[0.2em] text-red-400 uppercase">
              ⚠ Deprem Bildirisi
            </span>
            <span className="text-[10px] text-zinc-500 font-mono">
              {(() => {
                const d = new Date(item.occurred_at || item.timestamp || item.created_at || item._arrivedAt);
                return isNaN(d.getTime()) ? "--:--" : d.toLocaleTimeString("tr-TR", { hour: '2-digit', minute: '2-digit', second: '2-digit' });
              })()}
            </span>
          </div>

          <div className="flex items-baseline gap-3 flex-wrap">
            <span className={`text-2xl font-black tracking-tight ${magColor}`}>
              {mag.toFixed(1)}
            </span>
            <span className="text-white font-bold text-sm">Büyüklüğünde</span>

            <div className="flex items-center gap-1 text-zinc-400 text-xs font-medium truncate max-w-xs">
              <MapPin size={11} className="flex-shrink-0 text-zinc-500" />
              <span className="truncate">{item.location_name || `${item.latitude?.toFixed(3)}, ${item.longitude?.toFixed(3)}`}</span>
            </div>

            <div className="flex items-center gap-1 text-zinc-500 text-xs">
              <Activity size={10} className="flex-shrink-0" />
              <span>{item.depth_km} km derinlik</span>
            </div>
          </div>
        </div>

        {/* Right: CTA + close */}
        <div className="flex items-center gap-2 flex-shrink-0">
          <span className="hidden sm:flex items-center gap-1 text-xs font-semibold text-red-400 bg-red-500/10 border border-red-500/25 px-3 py-1.5 rounded-full">
            <Navigation size={11} />
            Deprem Olayları
          </span>
          <button
            onClick={(e) => { e.stopPropagation(); close(); }}
            className="w-7 h-7 rounded-full flex items-center justify-center text-zinc-500 hover:text-white hover:bg-white/10 transition-colors"
            aria-label="Kapat"
          >
            <X size={15} />
          </button>
        </div>
      </div>
    </div>
  );
}
