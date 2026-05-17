"use client";

import { useState, useEffect } from "react";
import { Settings as SettingsIcon, Database, Check, Download, AlertTriangle, Users, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useSettings } from "@/context/SettingsContext";

function BackupCard({
  title,
  description,
  icon: Icon,
  endpoint,
  filename,
  color,
}: {
  title: string;
  description: string;
  icon: React.ElementType;
  endpoint: string;
  filename: string;
  color: string;
}) {
  const [loading, setLoading] = useState(false);
  const [recordCount, setRecordCount] = useState<number | null>(null);

  useEffect(() => {
    fetch(endpoint)
      .then((r) => r.json())
      .then((data) => setRecordCount(Array.isArray(data) ? data.length : null))
      .catch(() => setRecordCount(null));
  }, [endpoint]);

  const handleBackup = async () => {
    setLoading(true);
    try {
      const res = await fetch(endpoint);
      if (!res.ok) throw new Error("Veri alınamadı");
      const data = await res.json();

      const json = JSON.stringify(data, null, 2);
      const blob = new Blob([json], { type: "application/json" });
      const url = URL.createObjectURL(blob);

      const a = document.createElement("a");
      const ts = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
      a.href = url;
      a.download = `${filename}_${ts}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);

      toast.success(`${title} yedeği başarıyla indirildi.`);
    } catch (err) {
      toast.error(`Yedekleme başarısız oldu.`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-hover border border-border rounded-2xl p-6 flex flex-col gap-5">
      {/* Başlık */}
      <div className="flex items-start gap-4">
        <div className={`w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0 ${color}`}>
          <Icon size={22} />
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="font-bold text-foreground text-base">{title}</h3>
          <p className="text-slate-500 text-xs mt-1 font-medium leading-relaxed">{description}</p>
        </div>
      </div>

      {/* Kayıt Sayısı */}
      <div className="bg-card border border-border rounded-xl px-4 py-3 flex items-center justify-between">
        <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Toplam Kayıt</span>
        <span className="font-bold text-foreground font-mono text-sm">
          {recordCount === null ? (
            <span className="text-slate-400">Yükleniyor…</span>
          ) : (
            <>{recordCount.toLocaleString("tr-TR")} kayıt</>
          )}
        </span>
      </div>

      {/* Yedekle Butonu */}
      <button
        onClick={handleBackup}
        disabled={loading}
        className="w-full py-3 rounded-xl font-bold text-sm flex items-center justify-center gap-2 transition-all disabled:opacity-50 disabled:cursor-not-allowed bg-foreground text-background hover:opacity-90 shadow-md"
      >
        {loading ? (
          <><Loader2 size={16} className="animate-spin" /> İndiriliyor...</>
        ) : (
          <><Download size={16} /> YEDEKLE</>
        )}
      </button>
    </div>
  );
}

export default function Ayarlar() {
  const [activeTab, setActiveTab] = useState("Sismik Ayarlar");
  const { highContrast, setHighContrast, autoDrone, setAutoDrone, systemName, setSystemName } = useSettings();

  const [draftHighContrast, setDraftHighContrast] = useState(highContrast);
  const [draftAutoDrone, setDraftAutoDrone] = useState(autoDrone);
  const [draftSystemName, setDraftSystemName] = useState(systemName);

  useEffect(() => {
    setDraftHighContrast(highContrast);
    setDraftAutoDrone(autoDrone);
    setDraftSystemName(systemName);
  }, [highContrast, autoDrone, systemName]);

  const tabs = [
    { name: "Sismik Ayarlar", icon: SettingsIcon },
    { name: "Veri Yedekleme", icon: Database },
  ];

  const handleSave = () => {
    setHighContrast(draftHighContrast);
    setAutoDrone(draftAutoDrone);
    setSystemName(draftSystemName);
    toast.success("Sistem ayarları başarıyla kaydedildi.");
  };

  return (
    <div className="space-y-8 w-full">
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-3xl font-bold text-foreground tracking-tight">Sistem Ayarları</h1>
          <p className="text-slate-500 mt-2 text-sm font-medium">Sismik ağ yapılandırması ve uyarı tercihleri</p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-8">
        {/* Sol Menü */}
        <div className="bg-card backdrop-blur-xl border border-border rounded-3xl shadow-lg p-4 h-fit transition-colors">
          <ul className="space-y-1">
            {tabs.map((tab) => (
              <li
                key={tab.name}
                onClick={() => setActiveTab(tab.name)}
                className={`px-4 py-3 rounded-2xl font-bold cursor-pointer transition-all flex items-center gap-3 text-sm ${
                  activeTab === tab.name
                    ? "bg-foreground text-background shadow-md"
                    : "text-slate-500 hover:bg-hover hover:text-foreground"
                }`}
              >
                <tab.icon size={18} /> {tab.name}
              </li>
            ))}
          </ul>
        </div>

        {/* Sağ İçerik */}
        <div className="md:col-span-3 bg-card backdrop-blur-xl border border-border rounded-3xl shadow-lg p-8 transition-colors">
          <h2 className="text-2xl font-bold text-foreground mb-8 pb-6 border-b border-border tracking-tight">{activeTab}</h2>

          {activeTab === "Sismik Ayarlar" ? (
            <div className="space-y-8">
              <div className="space-y-2">
                <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-wider pl-1">Sistem Adı</label>
                <input
                  type="text"
                  value={draftSystemName}
                  onChange={(e) => setDraftSystemName(e.target.value)}
                  className="w-full bg-card border border-border rounded-2xl px-5 py-4 text-foreground focus:border-blue-500 outline-none transition-colors font-bold"
                />
              </div>

              <div className="flex items-center justify-between py-5 border-t border-border">
                <div>
                  <h4 className="text-foreground font-bold">Yüksek Kontrast Modu</h4>
                  <p className="text-slate-500 text-sm mt-1 font-medium">Görme engelli kullanıcılar için daha belirgin renkler</p>
                </div>
                <button
                  onClick={() => setDraftHighContrast(!draftHighContrast)}
                  className={`w-14 h-8 rounded-full relative transition-colors border ${draftHighContrast ? "bg-green-500 border-green-500" : "bg-hover border-border"}`}
                >
                  <div className={`w-6 h-6 bg-white rounded-full absolute top-[3px] shadow-sm transition-transform ${draftHighContrast ? "translate-x-[26px]" : "translate-x-1"}`}></div>
                </button>
              </div>

              <div className="flex items-center justify-between py-5 border-t border-border">
                <div>
                  <h4 className="text-foreground font-bold">Sismik Otonom Dronelar (AI)</h4>
                  <p className="text-slate-500 text-sm mt-1 font-medium">Deprem anında yapay zeka analizine göre drone'ları otomatik kaldır</p>
                </div>
                <button
                  onClick={() => setDraftAutoDrone(!draftAutoDrone)}
                  className={`w-14 h-8 rounded-full relative transition-colors border ${draftAutoDrone ? "bg-green-500 border-green-500" : "bg-hover border-border"}`}
                >
                  <div className={`w-6 h-6 bg-white rounded-full absolute top-[3px] shadow-sm transition-transform ${draftAutoDrone ? "translate-x-[26px]" : "translate-x-1"}`}></div>
                </button>
              </div>

              <div className="pt-6 flex justify-end">
                <button
                  onClick={handleSave}
                  className="bg-foreground text-background hover:opacity-90 px-8 py-3.5 rounded-2xl font-bold transition-all shadow-md flex items-center gap-2"
                >
                  <Check size={18} strokeWidth={3} /> Değişiklikleri Kaydet
                </button>
              </div>
            </div>
          ) : activeTab === "Veri Yedekleme" ? (
            <div className="space-y-6">
              <div className="flex items-start gap-3 bg-amber-500/10 border border-amber-500/20 rounded-2xl px-5 py-4">
                <AlertTriangle size={18} className="text-amber-500 mt-0.5 flex-shrink-0" />
                <p className="text-amber-600 dark:text-amber-400 text-sm font-medium leading-relaxed">
                  Yedeklenen veriler JSON formatında indirilir. Kişisel veriler içerdiğinden güvenli bir ortamda saklayınız.
                </p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
                <BackupCard
                  title="Enkaz İhbarları"
                  description="Mobil cihazlardan gelen tüm enkaz ve acil durum ihbar kayıtları"
                  icon={AlertTriangle}
                  endpoint="/api/alerts"
                  filename="enkaz_ihbarlari"
                  color="bg-red-500/10 text-red-500 border border-red-500/20"
                />
                <BackupCard
                  title="Kullanıcı Bilgileri"
                  description="Sisteme kayıtlı vatandaş ve kullanıcı hesap bilgileri"
                  icon={Users}
                  endpoint="/api/users"
                  filename="kullanici_bilgileri"
                  color="bg-blue-500/10 text-blue-500 border border-blue-500/20"
                />
              </div>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

