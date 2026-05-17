"use client";

import { useState, useEffect, useRef } from "react";
import {
  AlertTriangle, MapPin, Clock, ChevronRight, CheckCircle2,
  Heart, User, Battery, Home, Search, Filter, ChevronDown,
  Ambulance, X, Phone,
} from "lucide-react";
import { Alert } from "@/lib/db";
import { toast } from "sonner";
import { useAiRoutes } from "@/context/AiRouteContext";

const STATUS_CFG: Record<string, { label: string; color: string; bg: string; border: string; icon: any }> = {
  Kritik:               { label: "Kritik — Enkaz Altında", color: "text-red-500",    bg: "bg-red-500/10",    border: "border-red-500/30",    icon: AlertTriangle },
  Acil:                 { label: "Acil — Yaralı",          color: "text-orange-500", bg: "bg-orange-500/10", border: "border-orange-500/30", icon: Ambulance },
  "Müdahale Ediliyor":  { label: "Müdahale Ediliyor",      color: "text-blue-500",   bg: "bg-blue-500/10",   border: "border-blue-500/30",   icon: CheckCircle2 },
  Orta:                 { label: "Güvende",                color: "text-green-500",  bg: "bg-green-500/10",  border: "border-green-500/30",  icon: CheckCircle2 },
};

function age(bd?: string) {
  if (!bd) return null;
  return `${Math.floor((Date.now() - new Date(bd).getTime()) / (1000 * 60 * 60 * 24 * 365.25))} yaş`;
}

function DetailPanel({ alert, onClose, onIntervene }: { alert: Alert; onClose: () => void; onIntervene: (id: string) => void }) {
  const cfg = STATUS_CFG[alert.status] ?? STATUS_CFG["Orta"];
  const Icon = cfg.icon;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />
      <div
        className="relative bg-card border border-border rounded-3xl shadow-2xl w-full max-w-lg overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className={`p-6 border-b border-border ${cfg.bg}`}>
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-4">
              <div className={`w-14 h-14 rounded-2xl flex items-center justify-center border ${cfg.bg} ${cfg.border}`}>
                <Icon size={24} className={cfg.color} />
              </div>
              <div>
                <h2 className="text-xl font-bold text-foreground">{alert.full_name ?? "Kimlik Bilgisi Yok"}</h2>
                <p className="text-sm text-slate-500 font-mono mt-0.5">{alert.device_id}</p>
              </div>
            </div>
            <button onClick={onClose} className="w-8 h-8 rounded-full bg-hover flex items-center justify-center text-slate-500 hover:text-foreground transition-colors">
              <X size={16} />
            </button>
          </div>
          <div className={`mt-4 inline-flex items-center gap-2 px-3 py-1.5 rounded-xl border text-sm font-bold ${cfg.color} ${cfg.bg} ${cfg.border}`}>
            <Icon size={14} />{cfg.label}
          </div>
        </div>

        {/* Body */}
        <div className="p-6 space-y-4 max-h-[60vh] overflow-y-auto">
          {/* Personal */}
          <Section title="Kişisel Bilgiler">
            <Row icon={User}  label="Ad Soyad"   value={alert.full_name} />
            <Row icon={Heart} label="Kan Grubu"  value={alert.blood_type} accent />
            <Row icon={User}  label="Yaş"        value={age(alert.birth_date) ?? undefined} />
            <Row icon={User}  label="Cinsiyet"   value={alert.gender} />
          </Section>
          {/* Location */}
          <Section title="Konum">
            <Row icon={MapPin} label="Şehir / İlçe" value={`${alert.location.city} — ${alert.location.district}`} />
            <Row icon={Home}   label="Adres"         value={alert.address} />
            <Row icon={MapPin} label="Koordinat"     value={`${alert.location.lat.toFixed(5)}, ${alert.location.lng.toFixed(5)}`} mono />
          </Section>
          {/* Device */}
          <Section title="Cihaz &amp; İhbar">
            <Row icon={Battery} label="Pil"         value={alert.battery_level != null ? `%${alert.battery_level}` : undefined} />
            <Row icon={Clock}   label="İhbar Saati" value={new Date(alert.timestamp).toLocaleTimeString("tr-TR")} />
            <Row icon={Phone}   label="Cihaz ID"    value={alert.device_id} mono />
            <Row icon={AlertTriangle} label="Durum" value={alert.callerStatus} />
          </Section>
        </div>

        {/* Footer */}
        <div className="p-6 border-t border-border flex gap-3">
          <button
            onClick={() => { onIntervene(alert.id); onClose(); }}
            disabled={alert.status === "Müdahale Ediliyor"}
            className="flex-1 bg-blue-600 hover:bg-blue-500 disabled:bg-hover disabled:text-slate-400 text-white py-3.5 rounded-2xl font-bold transition-all shadow-md shadow-blue-600/20 disabled:shadow-none text-sm"
          >
            {alert.status === "Müdahale Ediliyor" ? "Ekipler Yolda" : "Kurtarma Ekibini Yönlendir"}
          </button>
          <button onClick={onClose} className="px-6 bg-hover border border-border text-foreground hover:bg-border py-3.5 rounded-2xl font-bold transition-all text-sm">
            Kapat
          </button>
        </div>
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-2.5">{title}</p>
      <div className="grid grid-cols-1 gap-2">{children}</div>
    </div>
  );
}

function Row({ icon: Icon, label, value, accent, mono }: { icon: any; label: string; value?: string; accent?: boolean; mono?: boolean }) {
  if (!value) return null;
  return (
    <div className="bg-hover rounded-xl p-3 border border-border flex items-start gap-3">
      <Icon size={14} className={`mt-0.5 flex-shrink-0 ${accent ? "text-red-500" : "text-slate-500"}`} />
      <div className="min-w-0">
        <p className="text-[9px] font-bold text-slate-500 uppercase tracking-wider mb-0.5">{label}</p>
        <p className={`text-sm font-bold text-foreground truncate ${mono ? "font-mono" : ""} ${accent ? "text-red-500" : ""}`}>{value}</p>
      </div>
    </div>
  );
}

export default function MobilBildirimler() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [selected, setSelected] = useState<Alert | null>(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("Tümü");
  const { addRoute } = useAiRoutes();
  const isActiveRef = useRef(true);

  useEffect(() => {
    isActiveRef.current = true;
    let timeoutId: NodeJS.Timeout;

    const poll = async () => {
      if (!isActiveRef.current) return;
      try {
        // Fetch both sources and merge into alerts format
        const [alertRes, reportRes] = await Promise.allSettled([
          fetch("/api/alerts"),
          fetch("/api/reports"),
        ]);

        let combined: Alert[] = [];

        if (alertRes.status === "fulfilled" && alertRes.value.ok) {
          const data = await alertRes.value.json();
          if (Array.isArray(data)) combined = [...data];
        }

        if (reportRes.status === "fulfilled" && reportRes.value.ok) {
          const raw = await reportRes.value.json();
          if (Array.isArray(raw)) {
            // Convert reports to Alert format
            const mapped: Alert[] = raw.map((r: any) => ({
              id: r.id,
              device_id: r.device_id,
              timestamp: r.reported_at,
              location: {
                lat: r.latitude,
                lng: r.longitude,
                city: r.city ?? "—",
                district: r.district ?? "—",
              },
              status: r.status === "under_rubble" ? "Kritik" : r.status === "injured" ? "Acil" : "Orta",
              callerStatus: r.status === "under_rubble" ? "Enkaz Altında" : r.status === "injured" ? "Yaralı" : "Güvende",
              message: `Mobil ihbar. Pil: %${r.battery_level}`,
              battery_level: r.battery_level,
              full_name: r.full_name,
              birth_date: r.birth_date,
              address: r.address,
              gender: r.gender,
              blood_type: r.blood_type,
            }));

            // Avoid duplicates
            const alertIds = new Set(combined.map((a) => a.id));
            const unique = mapped.filter((r) => !alertIds.has(r.id));
            combined = [...combined, ...unique];
          }
        }

        // Sort by time (newest first)
        combined.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
        setAlerts(combined);
      } catch (e) {
        console.error("İhbar verisi alınamadı:", e);
      } finally {
        setIsLoading(false);
      }
      if (isActiveRef.current) timeoutId = setTimeout(poll, 10000);
    };

    poll();
    return () => { isActiveRef.current = false; clearTimeout(timeoutId); };
  }, []);

  const handleIntervene = (id: string) => {
    const targetAlert = alerts.find((a) => a.id === id);
    setAlerts((prev) => prev.map((a) => a.id === id ? { ...a, status: "Müdahale Ediliyor", callerStatus: "Ekip Yolda" } : a));
    toast.success(`${id} için arama kurtarma ekibi yönlendirildi.`);
    if (targetAlert) {
      addRoute(targetAlert);
      toast.info("Yapay zeka otonom rotası oluşturuldu.", { icon: "🤖" });
    }
  };

  const statuses = ["Tümü", "Kritik", "Acil", "Müdahale Ediliyor", "Orta"];
  const counts: Record<string, number> = {
    Tümü: alerts.length,
    Kritik: alerts.filter((a) => a.status === "Kritik").length,
    Acil: alerts.filter((a) => a.status === "Acil").length,
    "Müdahale Ediliyor": alerts.filter((a) => a.status === "Müdahale Ediliyor").length,
    Orta: alerts.filter((a) => a.status === "Orta").length,
  };

  const filtered = alerts.filter((a) => {
    const q = search.toLowerCase();
    const matchQ = !q || a.full_name?.toLowerCase().includes(q) || a.location.city?.toLowerCase().includes(q) || a.location.district?.toLowerCase().includes(q) || a.device_id?.toLowerCase().includes(q) || a.blood_type?.toLowerCase().includes(q);
    return matchQ && (statusFilter === "Tümü" || a.status === statusFilter);
  });

  return (
    <>
      {selected && <DetailPanel alert={selected} onClose={() => setSelected(null)} onIntervene={handleIntervene} />}

      <div className="space-y-6 w-full">
        {/* Başlık */}
        <div className="flex justify-between items-end">
          <div>
            <h1 className="text-3xl font-bold text-foreground tracking-tight flex items-center gap-3">
              <AlertTriangle className="text-red-500" size={30} />
              Enkaz İhbarları
            </h1>
            <p className="text-slate-500 mt-2 text-sm font-medium">
              Mobil uygulamadan gelen tüm deprem ihbarları — gerçek zamanlı
            </p>
          </div>
          <div className="flex items-center gap-2 bg-card border border-border px-4 py-2 rounded-lg">
            <span className="w-2 h-2 rounded-full bg-accent-neon animate-pulse" />
            <span className="text-xs font-bold text-foreground tracking-wider uppercase">{alerts.length} İhbar</span>
          </div>
        </div>

        {/* Özet Kartlar */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {(["Kritik", "Acil", "Müdahale Ediliyor", "Orta"] as const).map((s) => {
            const cfg = STATUS_CFG[s];
            const Icon = cfg.icon;
            return (
              <button
                key={s}
                onClick={() => setStatusFilter(statusFilter === s ? "Tümü" : s)}
                className={`bg-card border rounded-xl p-6 text-left transition-colors ${statusFilter === s ? `border-accent-neon bg-hover` : "border-border hover:border-zinc-500"}`}
              >
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <span className={`w-2 h-2 rounded-full ${cfg.color.replace("text-", "bg-")}`} />
                    <span className="text-xs font-bold text-zinc-400 uppercase tracking-widest">{cfg.label}</span>
                  </div>
                  <span className="text-2xl font-bold text-foreground">{counts[s]}</span>
                </div>
              </button>
            );
          })}
        </div>

        {/* Arama + Filtre */}
        <div className="flex gap-3 flex-col sm:flex-row">
          <div className="relative flex-1">
            <Search size={15} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              placeholder="Ad, cihaz, şehir veya kan grubu ile ara..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full bg-card border border-border rounded-xl pl-10 pr-4 py-3 text-sm text-foreground placeholder:text-zinc-500 focus:outline-none focus:border-zinc-500 transition-colors font-semibold"
            />
          </div>
          <div className="relative">
            <Filter size={13} className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500" />
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="bg-card border border-border rounded-xl pl-10 pr-10 py-3 text-sm text-foreground focus:outline-none focus:border-zinc-500 transition-colors font-semibold appearance-none cursor-pointer min-w-[190px]"
            >
              {statuses.map((s) => (
                <option key={s} value={s}>{s} ({counts[s] ?? 0})</option>
              ))}
            </select>
            <ChevronDown size={13} className="absolute right-4 top-1/2 -translate-y-1/2 text-zinc-500 pointer-events-none" />
          </div>
        </div>

        {/* Liste */}
        <div className="bg-card border border-border rounded-xl overflow-hidden">
          {/* Tablo Başlıkları */}
          <div className="hidden md:grid grid-cols-[2fr_1.5fr_1fr_1fr_1.5fr_48px] gap-4 px-6 py-4 border-b border-border bg-hover">
            {["Kişi", "Konum", "Kan Grubu", "Pil", "Durum", ""].map((h) => (
              <span key={h} className="text-[10px] font-bold text-zinc-400 uppercase tracking-widest">{h}</span>
            ))}
          </div>

          {isLoading ? (
            <div className="py-20 text-center text-zinc-500 font-bold">İhbarlar yükleniyor...</div>
          ) : filtered.length === 0 ? (
            <div className="py-20 text-center text-zinc-500 font-bold">
              {search || statusFilter !== "Tümü" ? "Arama kriterine uyan kayıt yok." : "Henüz ihbar gelmedi."}
            </div>
          ) : (
            <div className="divide-y divide-border">
              {filtered.map((alert) => {
                const cfg = STATUS_CFG[alert.status] ?? STATUS_CFG["Orta"];
                const Icon = cfg.icon;
                const isCritical = alert.status === "Kritik" || alert.status === "Acil";
                return (
                  <div
                    key={alert.id}
                    className="grid grid-cols-1 md:grid-cols-[2fr_1.5fr_1fr_1fr_1.5fr_48px] gap-4 px-6 py-5 hover:bg-hover transition-colors items-center cursor-pointer group"
                    onClick={() => setSelected(alert)}
                  >
                    {/* Kişi */}
                    <div className="flex items-center gap-3">
                      <span className={`w-2 h-2 rounded-full flex-shrink-0 ${isCritical ? "bg-red-500" : "bg-accent-neon"}`} />
                      <div className="min-w-0">
                        <p className="font-bold text-foreground text-sm truncate">{alert.full_name ?? "Kimlik Yok"}</p>
                        <p className="text-[10px] text-zinc-500 font-mono truncate">{alert.device_id}</p>
                      </div>
                    </div>

                    {/* Location */}
                    <div className="flex flex-col min-w-0">
                      <span className="text-sm font-bold text-foreground truncate">{alert.location.city}</span>
                      <span className="text-[10px] text-zinc-500 font-semibold flex items-center gap-1 truncate">
                        {alert.location.district}
                      </span>
                    </div>

                    {/* Kan Grubu */}
                    <div>
                      {alert.blood_type ? (
                        <span className="inline-flex items-center text-xs font-bold text-foreground bg-hover border border-border px-2 py-1 rounded-md">
                          {alert.blood_type}
                        </span>
                      ) : (
                        <span className="text-zinc-500 text-xs">—</span>
                      )}
                    </div>

                    {/* Pil */}
                    <div className="flex items-center gap-1.5">
                      <Battery size={13} className="text-zinc-500 flex-shrink-0" />
                      <span className={`text-sm font-bold ${(alert.battery_level ?? 100) < 20 ? "text-red-500" : (alert.battery_level ?? 100) < 50 ? "text-orange-500" : "text-foreground"}`}>
                        {alert.battery_level != null ? `%${alert.battery_level}` : "—"}
                      </span>
                    </div>

                    {/* Durum */}
                    <div>
                      <span className={`inline-flex items-center gap-1.5 text-xs font-semibold ${isCritical ? "text-red-500" : "text-accent-neon"}`}>
                        <Icon size={12} />
                        {cfg.label.split(" — ")[0]}
                      </span>
                      <p className="text-[9px] text-slate-400 mt-1 font-mono">
                        {new Date(alert.timestamp).toLocaleTimeString("tr-TR")}
                      </p>
                    </div>

                    {/* Ok */}
                    <div className="flex items-center justify-center">
                      <div className="w-8 h-8 rounded-full bg-hover border border-border flex items-center justify-center group-hover:bg-blue-500/10 group-hover:border-blue-500/30 transition-all">
                        <ChevronRight size={15} className="text-slate-400 group-hover:text-blue-500 transition-colors" />
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </>
  );
}
