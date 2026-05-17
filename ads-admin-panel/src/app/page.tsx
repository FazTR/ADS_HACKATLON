"use client";

import { Activity, AlertTriangle, ShieldAlert, Zap, ChevronRight, Users, Heart, CheckCircle2, Plane } from "lucide-react";
import { Alert } from "@/lib/db";
import Link from "next/link";
import { useEffect, useState, useRef } from "react";
import { MapWrapper } from "@/components/MapWrapper";

export default function Home() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [dronesInAir, setDronesInAir] = useState(0);
  const [totalUsers, setTotalUsers] = useState(0);
  const isActiveRef = useRef(true);

  useEffect(() => {
    isActiveRef.current = true;
    let timeoutId: NodeJS.Timeout;

    const fetchData = async () => {
      try {
        const [alertRes, detRes, userRes] = await Promise.allSettled([
          fetch("/api/alerts"),
          fetch("/api/detections"),
          fetch("/api/users"),
        ]);

        if (alertRes.status === "fulfilled" && alertRes.value.ok) {
          const data = await alertRes.value.json();
          if (Array.isArray(data)) setAlerts(data);
        }

        if (detRes.status === "fulfilled" && detRes.value.ok) {
          const data = await detRes.value.json();
          if (Array.isArray(data)) {
            const uniqueDrones = new Set(data.map((d: any) => d.drone_id));
            setDronesInAir(uniqueDrones.size);
          }
        }

        if (userRes.status === "fulfilled" && userRes.value.ok) {
          const data = await userRes.value.json();
          if (Array.isArray(data)) setTotalUsers(data.length);
        }
      } catch (error) {
        console.error("Dashboard fetch error:", error);
      }
    };

    const poll = async () => {
      if (!isActiveRef.current) return;
      await fetchData();
      if (isActiveRef.current) timeoutId = setTimeout(poll, 10000);
    };

    poll();
    return () => { isActiveRef.current = false; clearTimeout(timeoutId); };
  }, []);

  const criticalCount = alerts.filter(a => a.status === "Kritik" || a.status === "Acil").length;
  const safeCount = alerts.filter(a => a.status === "Orta").length;
  const activeInterventions = alerts.filter(a => a.status === "Müdahale Ediliyor").length;

  const stats = [
    { title: "Toplam İhbar", value: alerts.length, icon: AlertTriangle, color: "text-red-500", bg: "bg-red-500/10 border-red-500/20", glow: "shadow-[0_0_20px_rgba(255,69,58,0.12)]" },
    { title: "Kritik / Acil", value: criticalCount, icon: ShieldAlert, color: "text-orange-500", bg: "bg-orange-500/10 border-orange-500/20", glow: "shadow-[0_0_20px_rgba(255,159,10,0.12)]" },
    { title: "Aktif Dronelar", value: dronesInAir, icon: Plane, color: "text-purple-500", bg: "bg-purple-500/10 border-purple-500/20", glow: "shadow-[0_0_20px_rgba(168,85,247,0.12)]" },
    { title: "Kayıtlı Kullanıcı", value: totalUsers, icon: Users, color: "text-blue-500", bg: "bg-blue-500/10 border-blue-500/20", glow: "shadow-[0_0_20px_rgba(10,132,255,0.12)]" },
  ];

  const statusConfig: Record<string, { color: string; bg: string; border: string }> = {
    Kritik:            { color: "text-red-500",    bg: "bg-red-500/10",    border: "border-red-500/20" },
    Acil:              { color: "text-orange-500", bg: "bg-orange-500/10", border: "border-orange-500/20" },
    "Müdahale Ediliyor": { color: "text-blue-500",   bg: "bg-blue-500/10",   border: "border-blue-500/20" },
    Orta:              { color: "text-green-500",  bg: "bg-green-500/10",  border: "border-green-500/20" },
  };

  return (
    <div className="space-y-6 w-full">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:justify-between items-start md:items-end gap-4 pl-12 md:pl-0">
        <div>
          <h1 className="text-2xl md:text-4xl font-bold text-foreground tracking-tight">Sismik Ağ Özeti</h1>
          <p className="text-zinc-400 mt-1 font-medium text-xs md:text-sm">Bölgesel deprem durumu ve özet sismik veriler (Canlı)</p>
        </div>
        <div className="bg-card px-4 py-2 rounded-xl border border-border flex items-center gap-3">
          <span className="w-2.5 h-2.5 rounded-full bg-accent-neon animate-pulse" />
          <span className="text-[10px] md:text-xs font-bold text-foreground tracking-widest uppercase">Sistem Devrede</span>
        </div>
      </div>

      {/* ── İstatistik Kartları ── */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-6">
        {stats.map((stat, idx) => (
          <div key={idx} className="bg-card border border-border p-8 rounded-xl flex flex-col justify-between">
            <div className="flex items-center justify-between mb-4">
              <p className="text-zinc-400 text-xs font-semibold tracking-wider uppercase">{stat.title}</p>
              <stat.icon className="text-zinc-500" size={20} strokeWidth={2} />
            </div>
            <p className="text-4xl font-bold text-foreground tracking-tighter">{stat.value}</p>
          </div>
        ))}
      </div>

      {/* ── Harita (tam genişlik) ── */}
      <div className="h-[400px] md:h-[700px] w-full relative border border-border rounded-xl overflow-hidden">
        {/* Portre modunda (dikey) mobilde gösterilecek uyarı overlay'i */}
        <div className="absolute inset-0 z-[1001] bg-card/95 backdrop-blur-sm flex flex-col items-center justify-center p-6 text-center md:hidden landscape:hidden">
          <div className="w-16 h-16 rounded-full bg-border flex items-center justify-center mb-4 shadow-lg animate-bounce">
            <svg className="w-8 h-8 text-foreground" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 10a2 2 0 114 0 2 2 0 01-4 0zM4 14a2 2 0 114 0 2 2 0 01-4 0zM16 10a2 2 0 114 0 2 2 0 01-4 0zM16 14a2 2 0 114 0 2 2 0 01-4 0zM12 4a2 2 0 110 4 2 2 0 010-4zM12 16a2 2 0 110 4 2 2 0 010-4z" />
            </svg>
          </div>
          <h3 className="text-foreground font-bold text-lg mb-2">Cihazınızı Yan Çevirin</h3>
          <p className="text-zinc-500 text-sm font-medium">Sismik canlı harita, yüksek çözünürlüklü veri analizi için mobilde yalnızca yatay (landscape) modda görüntülenir.</p>
        </div>
        
        <div className="w-full h-full hidden md:block landscape:block">
          <MapWrapper />
        </div>
      </div>

      {/* ── Alt Panel: 3 Kolon ── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* Sol: Canlı İhbar Listesi */}
        <div className="bg-card border border-border rounded-xl flex flex-col h-[450px]">
          <div className="p-6 border-b border-border flex justify-between items-center">
            <h2 className="text-base font-bold text-foreground tracking-tight">Canlı Enkaz İhbarları</h2>
            <Link href="/mobil-bildirimler" className="text-zinc-400 hover:text-white text-xs font-semibold flex items-center gap-1 transition-colors">
              Tümü <ChevronRight size={14} />
            </Link>
          </div>
          <div className="divide-y divide-border overflow-y-auto flex-1 custom-scrollbar">
            {alerts.length === 0 ? (
              <div className="p-8 text-center text-zinc-500 font-medium text-sm">İhbarlar yükleniyor...</div>
            ) : (
              alerts.slice(0, 8).map((alert, idx) => {
                const isCritical = alert.status === "Kritik" || alert.status === "Acil";
                return (
                  <div key={alert.id || idx} className="p-5 hover:bg-hover transition-colors flex items-center gap-4 cursor-pointer">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className={`w-2 h-2 rounded-full flex-shrink-0 ${isCritical ? "bg-red-500" : "bg-accent-neon"}`} />
                        <span className="text-foreground font-bold text-sm truncate">
                          {alert.full_name ?? alert.location.district}
                        </span>
                      </div>
                      <p className="text-zinc-400 text-xs truncate ml-4">{alert.location.city} — {alert.location.district}</p>
                    </div>
                    <div className="text-right flex-shrink-0">
                      <p className="text-zinc-500 text-[10px] font-mono">{new Date(alert.timestamp).toLocaleTimeString("tr-TR")}</p>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Orta: Durum Dağılımı */}
        <div className="bg-card border border-border rounded-xl p-8 flex flex-col h-[450px]">
          <h2 className="text-base font-bold text-foreground tracking-tight mb-6">Durum Dağılımı</h2>

          {/* Mini grafiksel dağılım */}
          <div className="space-y-4">
            {(["Kritik", "Acil", "Müdahale Ediliyor", "Orta"] as const).map((s) => {
              const count = alerts.filter(a => a.status === s).length;
              const isAccent = count > 0 && (s === "Kritik" || s === "Acil");
              return (
                <div key={s} className="flex flex-col gap-1.5">
                  <div className="flex justify-between text-xs">
                    <span className="text-zinc-400 font-semibold">{s}</span>
                    <span className="text-foreground font-bold">{count}</span>
                  </div>
                  <div className="h-1.5 w-full bg-hover rounded-full overflow-hidden">
                    <div 
                      className={`h-full rounded-full ${isAccent ? "bg-accent-neon" : "bg-zinc-600"}`} 
                      style={{ width: `${Math.max(2, Math.min(100, count * 5))}%` }} 
                    />
                  </div>
                </div>
              );
            })}
          </div>

          <Link href="/yapay-zeka-analizi" className="mt-auto text-center text-xs font-semibold text-zinc-400 hover:text-white transition-colors">
            Analizleri Gör →
          </Link>
        </div>

        {/* Sağ: Hızlı Erişim + Son Aktivite */}
        <div className="flex flex-col gap-6 h-[450px]">
          {/* Hızlı Erişim Linkleri */}
          <div className="bg-card border border-border rounded-xl p-8 flex-1">
            <h2 className="text-base font-bold text-foreground tracking-tight mb-5">Hızlı Erişim</h2>
            <div className="grid grid-cols-2 gap-3">
              {[
                { href: "/mobil-bildirimler", label: "İhbarlar", icon: AlertTriangle },
                { href: "/kullanici-bilgileri",   label: "Kullanıcılar",   icon: Users },
                { href: "/drone-filosu",          label: "Dronelar",          icon: Plane },
                { href: "/yapay-zeka-analizi",    label: "AI Triage",             icon: Zap },
              ].map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className="flex flex-col items-center justify-center gap-2 p-4 rounded-xl border border-border bg-hover hover:border-zinc-500 transition-colors group"
                >
                  <item.icon size={20} className="text-zinc-400 group-hover:text-white transition-colors" />
                  <span className="text-xs font-semibold text-zinc-400 group-hover:text-white transition-colors">{item.label}</span>
                </Link>
              ))}
            </div>
          </div>

          {/* Son Kritik İhbar */}
          <div className="bg-card border border-border rounded-xl p-6 h-[120px] flex flex-col justify-center relative overflow-hidden">
            <div className="absolute left-0 top-0 bottom-0 w-1 bg-accent-neon" />
            <h2 className="text-xs font-semibold text-zinc-400 tracking-widest uppercase mb-2 ml-2">Son Kritik Veri</h2>
            {alerts.find(a => a.status === "Kritik" || a.status === "Acil") ? (
              <div className="ml-2">
                <p className="font-bold text-foreground truncate">{alerts.find(a => a.status === "Kritik" || a.status === "Acil")?.full_name}</p>
                <p className="text-xs text-zinc-500 mt-1">{alerts.find(a => a.status === "Kritik" || a.status === "Acil")?.location.district}</p>
              </div>
            ) : (
              <p className="text-sm text-zinc-500 ml-2">Bekleyen kritik ihbar yok.</p>
            )}
          </div>
        </div>

      </div>
    </div>
  );
}
