"use client";

import dynamic from 'next/dynamic';
import { Loader2 } from 'lucide-react';

const MapComponent = dynamic(() => import('./MapComponent'), {
  ssr: false,
  loading: () => (
    <div className="h-full w-full rounded-3xl border border-border bg-card flex flex-col items-center justify-center text-slate-500">
      <Loader2 className="w-8 h-8 animate-spin mb-2" />
      <span className="text-sm font-bold tracking-widest uppercase">Sismik Harita Yükleniyor...</span>
    </div>
  ),
});

export function MapWrapper() {
  return <MapComponent />;
}
