"use client";

import { useState, useEffect, useRef } from "react";
import {
  Battery, Crosshair, Navigation, Wifi, Video, AlertTriangle,
  Plus, Settings2, Clock, Bot, Camera, CameraOff, Loader2, X,
} from "lucide-react";
import { Modal } from "@/components/Modal";
import { toast } from "sonner";
import { DroneTelemety } from "@/lib/db";
import dynamic from 'next/dynamic';

const DroneSimulatorMap = dynamic(() => import('@/components/DroneSimulatorMap'), {
  ssr: false,
});

interface DroneView extends Partial<DroneTelemety> {
  id: string;
  status: string;
  task: string;
  detectionCount: number;
  labels: string[];
}

interface CameraState {
  droneId: string;
  active: boolean;
  loading: boolean;
}

const statusTr: Record<string, string> = {
  active: "Aktif", returning: "Geri Dönüyor", offline: "Çevrimdışı",
};

const batteryColor = (pct?: number) => {
  if (pct == null) return "text-foreground";
  if (pct < 20) return "text-red-500";
  if (pct < 50) return "text-orange-500";
  return "text-foreground";
};

const labelTr: Record<string, string> = {
  person: "İnsan", rubble: "Enkaz", vehicle: "Araç",
};

function VideoModal({ drone, onClose }: { drone: DroneView; onClose: () => void }) {
  const [cameraState, setCameraState] = useState<CameraState>({
    droneId: drone.id, active: false, loading: false,
  });

  const toggleCamera = async () => {
    const action = cameraState.active ? "stop" : "start";
    setCameraState((p) => ({ ...p, loading: true }));
    
    // Simulate connection delay
    setTimeout(() => {
      setCameraState((p) => ({ ...p, active: action === "start", loading: false }));
      toast.success(action === "start" ? `${drone.id} uydu simülatörüne bağlanıldı.` : `${drone.id} simülatörü kapatıldı.`);
    }, 800);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" />
      <div
        className="relative bg-card border border-border rounded-3xl shadow-2xl w-full max-w-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="p-5 border-b border-border flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-purple-500/10 border border-purple-500/20 flex items-center justify-center">
              <Video size={18} className="text-purple-500" />
            </div>
            <div>
              <h2 className="font-bold text-foreground">{drone.id} — Kamera Sistemi</h2>
              <p className="text-xs text-slate-500">MQTT: drone/{drone.id}/command</p>
            </div>
          </div>
          <button onClick={onClose} className="w-8 h-8 rounded-full bg-hover flex items-center justify-center text-slate-500 hover:text-foreground transition-colors">
            <X size={16} />
          </button>
        </div>

        {/* Video Alanı */}
        <div className="relative bg-black" style={{ aspectRatio: "16/9" }}>
          {cameraState.active ? (
            <>
              <DroneSimulatorMap lat={drone.latitude || 39.0} lng={drone.longitude || 35.0} />
              
              {/* Drone HUD Katmanı */}
              <div className="absolute inset-0 z-10 pointer-events-none">
                <div className="absolute top-4 left-4 bg-black/60 backdrop-blur-md px-3 py-1.5 rounded-xl text-[10px] font-bold text-white flex items-center gap-2 border border-white/10 shadow-lg">
                  <span className="w-2 h-2 rounded-full bg-red-500 animate-pulse shadow-[0_0_8px_rgba(255,69,58,0.8)]" />
                  SIMÜLASYON KAYDI (ESRI)
                </div>

                <div className="absolute top-4 right-4 bg-black/50 backdrop-blur-md px-3 py-2 rounded-lg font-mono text-[10px] text-green-400 border border-green-500/30 text-right space-y-1 shadow-lg">
                  <div>ALT: {drone.altitude ? Math.round(drone.altitude) : 120} M</div>
                  <div>SPD: {drone.speed ? drone.speed.toFixed(1) : "12.4"} M/S</div>
                  <div>BAT: {drone.battery_level ? drone.battery_level : 85}%</div>
                </div>

                <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2">
                   <div className="relative">
                     <Crosshair size={64} strokeWidth={1} className="text-white/60 animate-[spin_10s_linear_infinite]" />
                     <div className="absolute inset-0 flex items-center justify-center">
                       <div className="w-2 h-2 bg-red-500/50 rounded-full" />
                     </div>
                   </div>
                </div>

                <div className="absolute bottom-4 right-4 bg-black/50 backdrop-blur-md px-3 py-2 rounded-lg font-mono text-[10px] text-white/80 border border-white/10 shadow-lg">
                  LAT: {drone.latitude?.toFixed(6) || "39.000000"} <br/> 
                  LNG: {drone.longitude?.toFixed(6) || "35.000000"}
                </div>

                <div className="absolute bottom-4 left-4 bg-black/50 backdrop-blur-md px-3 py-2 rounded-lg font-mono text-[10px] text-white/60 border border-white/10 shadow-lg leading-tight">
                  <span className="text-white/90">[W A S D]</span> Kamera Hareketi <br/>
                  <span className="text-white/90">[Q E]</span> Zoom Kontrolü
                </div>
              </div>
            </>
          ) : (
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-4 text-white/40">
              <CameraOff size={48} />
              <p className="text-sm font-bold tracking-wider uppercase">Kamera Kapalı</p>
              <p className="text-xs text-white/30">Kamerayı açmak için aşağıdaki butona basın</p>
            </div>
          )}
        </div>

        {/* Kontroller + Telemetri */}
        <div className="p-5 space-y-4">
          {/* Telemetri özeti */}
          <div className="grid grid-cols-3 gap-3">
            <div className="bg-hover rounded-2xl p-3 border border-border text-center">
              <p className="text-[9px] font-bold text-slate-500 uppercase tracking-wider mb-1">Batarya</p>
              <p className={`text-lg font-bold ${batteryColor(drone.battery_level)}`}>
                {drone.battery_level != null ? `%${drone.battery_level}` : "—"}
              </p>
            </div>
            <div className="bg-hover rounded-2xl p-3 border border-border text-center">
              <p className="text-[9px] font-bold text-slate-500 uppercase tracking-wider mb-1">İrtifa</p>
              <p className="text-lg font-bold text-foreground">
                {drone.altitude != null ? `${Math.round(drone.altitude)}m` : "—"}
              </p>
            </div>
            <div className="bg-hover rounded-2xl p-3 border border-border text-center">
              <p className="text-[9px] font-bold text-slate-500 uppercase tracking-wider mb-1">Hız</p>
              <p className="text-lg font-bold text-foreground">
                {drone.speed != null ? `${drone.speed.toFixed(1)}m/s` : "—"}
              </p>
            </div>
          </div>

          {/* Kamera toggle */}
          <button
            onClick={toggleCamera}
            disabled={cameraState.loading}
            className={`w-full py-3.5 rounded-2xl font-bold text-sm transition-all flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed shadow-md ${
              cameraState.active
                ? "bg-red-500/10 text-red-500 border border-red-500/30 hover:bg-red-500/20"
                : "bg-green-600 text-white hover:bg-green-500 shadow-green-600/20"
            }`}
          >
            {cameraState.loading ? (
              <><Loader2 size={18} className="animate-spin" /> Bağlanıyor...</>
            ) : cameraState.active ? (
              <><CameraOff size={18} /> Simülatörü Kapat</>
            ) : (
              <><Camera size={18} /> Drone Simülatörünü Başlat</>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function DroneFilosu() {
  const [fleet, setFleet] = useState<DroneView[]>([]);
  const [selectedDrone, setSelectedDrone] = useState<DroneView | null>(null);
  const [videoModalDrone, setVideoModalDrone] = useState<DroneView | null>(null);
  const [isAssignModalOpen, setIsAssignModalOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const isActiveRef = useRef(true);

  useEffect(() => {
    isActiveRef.current = true;
    let timeoutId: NodeJS.Timeout;

    const poll = async () => {
      if (!isActiveRef.current) return;
      try {
        const [droneRes, detRes] = await Promise.allSettled([
          fetch("/api/drones"),
          fetch("/api/detections"),
        ]);

        const telemetry: DroneTelemety[] =
          droneRes.status === "fulfilled" && droneRes.value.ok
            ? await droneRes.value.json() : [];

        const detections: any[] =
          detRes.status === "fulfilled" && detRes.value.ok
            ? await detRes.value.json() : [];

        const detMap = new Map<string, { count: number; labels: string[] }>();
        detections.forEach((d) => {
          if (!detMap.has(d.drone_id)) detMap.set(d.drone_id, { count: 0, labels: [] });
          const e = detMap.get(d.drone_id)!;
          e.count++;
          if (!e.labels.includes(d.label)) e.labels.push(d.label);
        });

        if (Array.isArray(telemetry) && telemetry.length > 0) {
          setFleet(telemetry.map((t) => ({
            ...t,
            id: t.drone_id,
            status: statusTr[t.status] ?? t.status,
            task: "Otonom Tarama",
            detectionCount: detMap.get(t.drone_id)?.count ?? 0,
            labels: detMap.get(t.drone_id)?.labels ?? [],
          })));
        } else {
          const droneMap = new Map<string, DroneView>();
          detections.forEach((det) => {
            if (!droneMap.has(det.drone_id)) {
              droneMap.set(det.drone_id, {
                id: det.drone_id, drone_id: det.drone_id,
                status: "Aktif", latitude: det.latitude,
                longitude: det.longitude, last_seen: det.detected_at,
                task: "Otonom Tarama", detectionCount: 0, labels: [],
              });
            }
            const d = droneMap.get(det.drone_id)!;
            d.detectionCount++;
            if (!d.labels.includes(det.label)) d.labels.push(det.label);
          });
          setFleet(Array.from(droneMap.values()));
        }
      } catch (e) { console.error("Drone verisi alınamadı:", e); }
      finally { setIsLoading(false); }
      if (isActiveRef.current) timeoutId = setTimeout(poll, 10000);
    };

    poll();
    return () => { isActiveRef.current = false; clearTimeout(timeoutId); };
  }, []);

  const handleAssignTask = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const task = fd.get("task") as string;
    const droneId = fd.get("droneId") as string;
    setFleet((prev) => prev.map((d) => d.id === droneId ? { ...d, task, status: "Aktif" } : d));
    toast.success(`${droneId} için yeni sismik görev atandı: ${task}`);
    setIsAssignModalOpen(false);
  };

  const handleReturnToBase = (id: string) => {
    setFleet((prev) => prev.map((d) => d.id === id ? { ...d, task: "Merkeze Dönüyor", status: "Geri Dönüyor" } : d));
    toast.info(`${id} şarj ve bakım için merkeze geri çağrıldı.`);
    setSelectedDrone(null);
  };

  const isActive = (d: DroneView) => d.status === "Aktif" || d.status === "active";

  return (
    <div className="space-y-6 w-full">
      {/* Video Modal */}
      {videoModalDrone && (
        <VideoModal drone={videoModalDrone} onClose={() => setVideoModalDrone(null)} />
      )}

      <div className="flex justify-between items-end mb-8">
        <div>
          <h1 className="text-3xl font-bold text-foreground tracking-tight">Arama Kurtarma Droneları</h1>
          <p className="text-slate-500 mt-2 font-medium text-sm">
            Deprem bölgesi sismik tarama İHA filosu — Canlı Telemetri &amp; Kamera Kontrolü
          </p>
        </div>
        <button
          onClick={() => setIsAssignModalOpen(true)}
          className="bg-foreground text-background hover:opacity-90 px-5 py-2.5 rounded-full text-sm font-bold transition-all shadow-md flex items-center gap-2"
        >
          <Plus size={18} strokeWidth={2.5} /> Yeni Görev Ata
        </button>
      </div>

      {isLoading ? (
        <div className="text-center py-20 text-slate-500 font-bold">Drone filosu haritalandırılıyor...</div>
      ) : fleet.length === 0 ? (
        <div className="text-center py-20 text-slate-500 font-bold">Aktif drone tespiti bulunamadı.</div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6">
          {fleet.map((drone) => (
            <div key={drone.id} className="bg-card backdrop-blur-xl border border-border rounded-3xl overflow-hidden flex flex-col shadow-lg transition-transform hover:-translate-y-1 duration-300">
              {/* Görüntü Alanı */}
              <div className="h-44 bg-black relative border-b border-border flex items-center justify-center overflow-hidden group">
                <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-black/30 z-10 pointer-events-none" />
                {isActive(drone) ? (
                  <>
                    <div className="absolute inset-0 opacity-50 bg-[url('https://images.unsplash.com/photo-1541888086225-ee22894589d8?q=80&w=600&auto=format&fit=crop')] bg-cover bg-center mix-blend-luminosity scale-110 group-hover:scale-100 transition-transform duration-700" />
                    <Video className="text-white/40 w-16 h-16 absolute z-0" />
                    <div className="absolute top-4 left-4 bg-black/60 backdrop-blur-md px-3 py-1.5 rounded-xl text-[10px] font-bold text-white flex items-center gap-2 z-20 border border-white/10">
                      <span className="w-2 h-2 rounded-full bg-red-500 animate-pulse shadow-[0_0_8px_rgba(255,69,58,0.8)]" /> CANLI KAYIT
                    </div>
                    <div className="absolute top-4 right-4 bg-black/60 backdrop-blur-md px-3 py-1.5 rounded-xl text-[10px] text-white font-mono z-20 border border-white/10">
                      {drone.id}
                    </div>
                    {/* Kamera Butonu - hover */}
                    <div className="absolute inset-0 flex items-center justify-center z-30 opacity-0 group-hover:opacity-100 transition-opacity gap-3">
                      <button
                        onClick={() => setVideoModalDrone(drone)}
                        className="bg-white/20 backdrop-blur-lg hover:bg-white/30 text-white px-4 py-2.5 rounded-full text-sm font-bold border border-white/30 transition-all shadow-lg flex items-center gap-2"
                      >
                        <Camera size={16} /> Kamera Kontrolü
                      </button>
                    </div>
                  </>
                ) : (
                  <div className="flex flex-col items-center text-white/40 z-20">
                    <AlertTriangle className="w-10 h-10 mb-3" />
                    <span className="text-[10px] font-bold tracking-wider uppercase">{drone.status}</span>
                  </div>
                )}
              </div>

              {/* Telemetri */}
              <div className="p-6 flex-1 flex flex-col justify-between bg-card">
                <div>
                  <div className="flex justify-between items-start mb-5">
                    <div>
                      <h3 className="text-lg font-bold text-foreground tracking-tight flex items-center gap-2">
                        {drone.id}
                        <span className={`w-2 h-2 rounded-full ${isActive(drone) ? "bg-green-500 shadow-[0_0_8px_rgba(50,215,75,0.8)]" : "bg-yellow-500 shadow-[0_0_8px_rgba(255,214,10,0.8)]"}`} />
                      </h3>
                      <p className="text-[10px] text-slate-500 mt-1 uppercase tracking-wider font-bold">
                        Görev: <span className="text-foreground">{drone.task}</span>
                      </p>
                    </div>
                    <div className="flex gap-1 flex-wrap justify-end">
                      {drone.labels.map((lbl) => (
                        <span key={lbl} className="text-[9px] px-2 py-0.5 rounded-md font-bold uppercase tracking-wider border bg-purple-500/10 text-purple-400 border-purple-500/20">
                          {labelTr[lbl] ?? lbl}
                        </span>
                      ))}
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-3 mb-4">
                    {/* Batarya */}
                    <div className="bg-hover p-4 rounded-2xl border border-border">
                      <div className="flex items-center justify-between text-slate-500 mb-2">
                        <span className="text-[10px] font-bold uppercase tracking-wider">Batarya</span>
                        <Battery size={14} />
                      </div>
                      <span className={`text-2xl font-bold tracking-tight ${batteryColor(drone.battery_level)}`}>
                        {drone.battery_level != null ? `%${drone.battery_level}` : "—"}
                      </span>
                    </div>
                    {/* İrtifa */}
                    <div className="bg-hover p-4 rounded-2xl border border-border">
                      <div className="flex items-center justify-between text-slate-500 mb-2">
                        <span className="text-[10px] font-bold uppercase tracking-wider">İrtifa</span>
                        <Crosshair size={14} />
                      </div>
                      <div className="flex items-baseline gap-1">
                        <span className="text-2xl font-bold tracking-tight text-foreground">
                          {drone.altitude != null ? Math.round(drone.altitude) : "—"}
                        </span>
                        {drone.altitude != null && <span className="text-xs text-slate-500 font-bold">m</span>}
                      </div>
                    </div>
                    {/* Hız */}
                    <div className="bg-hover p-4 rounded-2xl border border-border">
                      <div className="flex items-center justify-between text-slate-500 mb-2">
                        <span className="text-[10px] font-bold uppercase tracking-wider">Hız</span>
                        <Navigation size={14} />
                      </div>
                      <div className="flex items-baseline gap-1">
                        <span className="text-2xl font-bold tracking-tight text-foreground">
                          {drone.speed != null ? drone.speed.toFixed(1) : "—"}
                        </span>
                        {drone.speed != null && <span className="text-xs text-slate-500 font-bold">m/s</span>}
                      </div>
                    </div>
                    {/* Tespit */}
                    <div className="bg-hover p-4 rounded-2xl border border-border">
                      <div className="flex items-center justify-between text-slate-500 mb-2">
                        <span className="text-[10px] font-bold uppercase tracking-wider">Tespit</span>
                        <Bot size={14} />
                      </div>
                      <span className="text-2xl font-bold tracking-tight text-foreground">{drone.detectionCount}</span>
                    </div>
                  </div>
                </div>

                <div className="mt-2 pt-4 border-t border-border flex justify-between items-center">
                  <div className="flex items-center gap-2 text-slate-500">
                    <Wifi size={13} className={isActive(drone) ? "text-green-500" : "text-slate-400"} />
                    <span className="text-xs font-bold">{drone.status}</span>
                    {drone.last_seen && (
                      <span className="text-xs text-slate-400 font-mono ml-1">
                        {new Date(drone.last_seen).toLocaleTimeString("tr-TR")}
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    {/* Kamera */}
                    <button
                      onClick={() => setVideoModalDrone(drone)}
                      title="Kamera Kontrolü"
                      className="w-8 h-8 rounded-full bg-hover flex items-center justify-center text-slate-500 hover:text-purple-500 transition-colors"
                    >
                      <Camera size={15} />
                    </button>
                    {/* Ayarlar */}
                    <button
                      onClick={() => setSelectedDrone(drone)}
                      className="w-8 h-8 rounded-full bg-hover flex items-center justify-center text-slate-500 hover:text-foreground transition-colors"
                    >
                      <Settings2 size={16} />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Drone Detay Modal */}
      <Modal isOpen={!!selectedDrone} onClose={() => setSelectedDrone(null)} title={selectedDrone ? `${selectedDrone.id} Kontrol Paneli` : ""}>
        {selectedDrone && (
          <div className="space-y-6">
            <div className="grid grid-cols-2 gap-3">
              <div className="bg-hover rounded-2xl p-4 border border-border">
                <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-1">Batarya</p>
                <p className={`text-2xl font-bold ${batteryColor(selectedDrone.battery_level)}`}>
                  {selectedDrone.battery_level != null ? `%${selectedDrone.battery_level}` : "—"}
                </p>
              </div>
              <div className="bg-hover rounded-2xl p-4 border border-border">
                <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-1">İrtifa</p>
                <p className="text-2xl font-bold text-foreground">
                  {selectedDrone.altitude != null ? `${Math.round(selectedDrone.altitude)} m` : "—"}
                </p>
              </div>
              <div className="bg-hover rounded-2xl p-4 border border-border">
                <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-1">Hız</p>
                <p className="text-2xl font-bold text-foreground">
                  {selectedDrone.speed != null ? `${selectedDrone.speed.toFixed(1)} m/s` : "—"}
                </p>
              </div>
              <div className="bg-hover rounded-2xl p-4 border border-border">
                <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-1">Toplam Tespit</p>
                <p className="text-2xl font-bold text-foreground">{selectedDrone.detectionCount}</p>
              </div>
              {selectedDrone.latitude && (
                <div className="bg-hover rounded-2xl p-4 border border-border col-span-2">
                  <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-1">Konum (Lat / Lng)</p>
                  <p className="text-sm font-bold text-foreground font-mono">
                    {selectedDrone.latitude?.toFixed(5)}, {selectedDrone.longitude?.toFixed(5)}
                  </p>
                </div>
              )}
            </div>
            <div className="grid grid-cols-2 gap-4">
              <button
                onClick={() => { setSelectedDrone(null); setVideoModalDrone(selectedDrone); }}
                className="bg-purple-600 hover:bg-purple-500 text-white py-3.5 rounded-2xl font-bold transition-all shadow-md shadow-purple-600/20 text-sm flex items-center justify-center gap-2"
              >
                <Camera size={18} /> Kamera Kontrolü
              </button>
              <button
                onClick={() => handleReturnToBase(selectedDrone.id)}
                className="bg-hover text-foreground hover:bg-red-500/10 hover:text-red-500 py-3.5 rounded-2xl font-bold transition-all text-sm"
              >
                Merkeze Geri Çağır
              </button>
            </div>
          </div>
        )}
      </Modal>

      {/* Görev Atama Modal */}
      <Modal isOpen={isAssignModalOpen} onClose={() => setIsAssignModalOpen(false)} title="Yeni Drone Görevi (Sismik)">
        <form onSubmit={handleAssignTask} className="space-y-5">
          <div>
            <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-2">Hedef Drone</label>
            <select name="droneId" required className="w-full bg-hover border border-border rounded-xl px-4 py-3.5 text-foreground focus:outline-none focus:border-blue-500 transition-colors appearance-none font-semibold text-sm">
              {fleet.map((d) => <option key={d.id} value={d.id}>{d.id} ({d.status})</option>)}
            </select>
          </div>
          <div>
            <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-2">Görev Tipi</label>
            <select name="task" required className="w-full bg-hover border border-border rounded-xl px-4 py-3.5 text-foreground focus:outline-none focus:border-blue-500 transition-colors appearance-none font-semibold text-sm">
              <option value="Yıkılan Bina Tespiti">Yıkılan Bina Tespiti (Fotogrametri)</option>
              <option value="Göçük Altı Termal Arama">Göçük Altı Termal Arama</option>
              <option value="Fay Hattı Görüntüleme">Fay Hattı Görüntüleme</option>
              <option value="Erzak / İlaç Teslimatı">Erzak / İlaç Teslimatı</option>
            </select>
          </div>
          <button type="submit" className="w-full bg-foreground text-background hover:opacity-90 py-3.5 rounded-2xl font-bold transition-all mt-4">
            Sismik Görevi Başlat
          </button>
        </form>
      </Modal>
    </div>
  );
}
