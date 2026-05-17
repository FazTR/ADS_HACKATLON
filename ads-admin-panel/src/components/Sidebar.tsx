"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LayoutDashboard, RadioTower, Plane, BrainCircuit, Settings, ActivitySquare, Users, UserCircle, AlertTriangle, ScrollText } from "lucide-react";
import { ThemeToggle } from "./ThemeToggle";
import { useSettings } from "@/context/SettingsContext";

const navItems = [
  { name: "Sismik Ağ Özeti", href: "/", icon: LayoutDashboard },
  { name: "Enkaz İhbarları", href: "/mobil-bildirimler", icon: RadioTower },
  { name: "Kullanıcı Bilgileri", href: "/kullanici-bilgileri", icon: UserCircle },
  { name: "Arama Kurtarma Droneları", href: "/drone-filosu", icon: Plane },
  { name: "Yapay Zeka Triage", href: "/yapay-zeka-analizi", icon: BrainCircuit },
  { name: "Deprem Olayları", href: "/depremler", icon: AlertTriangle },
  { name: "Deprem Logları", href: "/loglar", icon: ScrollText },
  { name: "Sistem Ayarları", href: "/ayarlar", icon: Settings },
];

export function Sidebar({ isOpen, onClose }: { isOpen?: boolean; onClose?: () => void }) {
  const pathname = usePathname();
  const { systemName } = useSettings();

  return (
    <>
      {isOpen && (
        <div 
          className="fixed inset-0 bg-black/60 z-30 md:hidden backdrop-blur-sm transition-opacity" 
          onClick={onClose} 
        />
      )}
      <aside 
        className={`w-64 bg-sidebar border-r border-border h-screen flex flex-col fixed left-0 top-0 z-40 transition-transform duration-300 ease-in-out md:translate-x-0 ${
          isOpen ? "translate-x-0 shadow-2xl" : "-translate-x-full"
        }`}
      >
      <div className="p-6 flex items-center space-x-3 mt-4 flex-shrink-0">
        <div className="w-10 h-10 border border-border bg-card rounded flex items-center justify-center text-foreground flex-shrink-0">
          <ActivitySquare size={20} strokeWidth={2} />
        </div>
        <div className="min-w-0">
          <h1 className="text-sm font-bold text-foreground tracking-tight truncate" title={systemName || "ADS"}>{systemName || "ADS"}</h1>
          <p className="text-[9px] font-bold text-slate-500 tracking-wider uppercase truncate">Sismik Ağ Yönetimi</p>
        </div>
      </div>
      
      <nav className="flex-1 px-4 py-2 space-y-1.5 mt-2 overflow-y-auto custom-scrollbar">
        {navItems.map((item) => {
          const isActive = pathname === item.href;
          return (
            <Link
              key={item.name}
              href={item.href}
              className={`flex items-center space-x-3 px-4 py-3 border-l-2 transition-all duration-200 group ${
                isActive
                  ? "bg-hover border-accent-neon text-foreground"
                  : "border-transparent text-zinc-500 hover:bg-hover hover:text-foreground hover:border-zinc-500"
              }`}
            >
              <item.icon size={18} strokeWidth={2} className={`${isActive ? "text-accent-neon" : "text-zinc-500 group-hover:text-foreground transition-colors"}`} />
              <span className={`text-sm ${isActive ? "font-bold text-foreground" : "font-semibold text-zinc-400"}`}>{item.name}</span>
            </Link>
          );
        })}
      </nav>

      <div className="p-6 pb-8 md:mb-4 flex flex-col gap-4 border-t border-border mt-auto flex-shrink-0">
        <div className="bg-card rounded-xl p-4 border border-border flex flex-col gap-2">
          <span className="text-[10px] font-bold text-zinc-500 tracking-widest uppercase">Sistem Durumu</span>
          <div className="flex items-center space-x-2">
            <span className="w-2 h-2 bg-accent-neon rounded-full animate-pulse" />
            <span className="text-sm font-bold text-foreground">Tüm Servisler Aktif</span>
          </div>
        </div>
        
        <div className="flex items-center justify-between">
          <ThemeToggle />
          <button 
            onClick={() => {
              import("next-auth/react").then((mod) => mod.signOut({ callbackUrl: "/login" }));
            }}
            className="text-xs font-semibold text-zinc-500 hover:text-white px-3 py-2 rounded-xl transition-all border border-transparent hover:border-zinc-700 bg-hover"
          >
            Çıkış
          </button>
        </div>
      </div>
    </aside>
    </>
  );
}
