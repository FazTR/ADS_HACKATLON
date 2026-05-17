"use client";

import React, { createContext, useContext, useEffect, useState } from "react";

interface SettingsContextType {
  highContrast: boolean;
  setHighContrast: (val: boolean) => void;
  autoDrone: boolean;
  setAutoDrone: (val: boolean) => void;
  systemName: string;
  setSystemName: (val: string) => void;
}

const SettingsContext = createContext<SettingsContextType | undefined>(undefined);

export function SettingsProvider({ children }: { children: React.ReactNode }) {
  const [highContrast, setHighContrast] = useState(false);
  const [autoDrone, setAutoDrone] = useState(true);
  const [systemName, setSystemName] = useState("ADS Sismik Ağ ve Enkaz Yönetimi");
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    // Load from local storage
    const storedContrast = localStorage.getItem("ads_highContrast");
    const storedDrone = localStorage.getItem("ads_autoDrone");
    const storedSystemName = localStorage.getItem("ads_systemName");

    if (storedContrast) setHighContrast(storedContrast === "true");
    if (storedDrone) setAutoDrone(storedDrone === "true");
    if (storedSystemName) setSystemName(storedSystemName);

    setMounted(true);
  }, []);

  useEffect(() => {
    if (!mounted) return;
    localStorage.setItem("ads_highContrast", String(highContrast));
    
    // Add class globally
    if (highContrast) {
      document.documentElement.classList.add("high-contrast");
    } else {
      document.documentElement.classList.remove("high-contrast");
    }
  }, [highContrast, mounted]);

  useEffect(() => {
    if (!mounted) return;
    localStorage.setItem("ads_autoDrone", String(autoDrone));
  }, [autoDrone, mounted]);



  useEffect(() => {
    if (!mounted) return;
    localStorage.setItem("ads_systemName", systemName);
  }, [systemName, mounted]);

  return (
    <SettingsContext.Provider value={{
      highContrast, setHighContrast,
      autoDrone, setAutoDrone,
      systemName, setSystemName
    }}>
      <div style={{ visibility: mounted ? "visible" : "hidden" }}>
        {children}
      </div>
    </SettingsContext.Provider>
  );
}

export function useSettings() {
  const context = useContext(SettingsContext);
  if (context === undefined) {
    throw new Error("useSettings must be used within a SettingsProvider");
  }
  return context;
}
