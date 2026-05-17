"use client";

import { usePathname } from "next/navigation";
import { Sidebar } from "@/components/Sidebar";
import { EarthquakeBanner } from "@/components/EarthquakeBanner";
import { useState, useEffect } from "react";
import { Menu } from "lucide-react";

export function ClientLayoutWrapper({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isLoginPage = pathname === "/login";
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);

  useEffect(() => {
    setIsSidebarOpen(false);
  }, [pathname]);

  if (isLoginPage) {
    return (
      <main className="min-h-screen bg-background">
        {children}
      </main>
    );
  }

  return (
    <>
      {/* Global deprem bildirimi — tüm sayfalarda görünür */}
      <EarthquakeBanner />

      <button 
        onClick={() => setIsSidebarOpen(true)}
        className="md:hidden fixed top-4 left-4 z-20 p-2 bg-card border border-border rounded-md shadow-md text-foreground"
      >
        <Menu size={20} />
      </button>

      <Sidebar isOpen={isSidebarOpen} onClose={() => setIsSidebarOpen(false)} />
      
      <main className={`md:ml-64 min-h-screen pt-16 md:pt-0 w-full md:w-[calc(100%-16rem)] overflow-x-hidden ${pathname === '/yapay-zeka-analizi' ? 'p-0 h-screen' : 'p-4 md:p-8'}`}>
        {children}
      </main>
    </>
  );
}