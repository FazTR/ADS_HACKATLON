"use client";

import { useEffect, useRef, useState } from "react";
import { MapContainer, TileLayer, useMap } from "react-leaflet";
import "leaflet/dist/leaflet.css";

const TILE_URL = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}";
const TILE_ATTR = "Tiles &copy; Esri";

function DroneController() {
  const map = useMap();
  const keysPressed = useRef<{ [key: string]: boolean }>({});
  const animationRef = useRef<number | null>(null);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const key = e.key.toLowerCase();
      if (['w', 'a', 's', 'd', 'q', 'e'].includes(key)) {
        keysPressed.current[key] = true;
        if (['w', 'a', 's', 'd'].includes(key)) {
          e.preventDefault();
        }
      }
    };

    const handleKeyUp = (e: KeyboardEvent) => {
      const key = e.key.toLowerCase();
      if (keysPressed.current[key]) {
        keysPressed.current[key] = false;
      }
    };

    window.addEventListener("keydown", handleKeyDown, { passive: false });
    window.addEventListener("keyup", handleKeyUp);

    const animate = () => {
      let dx = 0;
      let dy = 0;
      const speed = 10; // pixels per frame

      if (keysPressed.current['w']) dy -= speed;
      if (keysPressed.current['s']) dy += speed;
      if (keysPressed.current['a']) dx -= speed;
      if (keysPressed.current['d']) dx += speed;

      if (dx !== 0 || dy !== 0) {
        map.panBy([dx, dy], { animate: false });
      }

      if (keysPressed.current['q']) {
        map.setZoom(map.getZoom() - 0.05, { animate: false });
      }
      if (keysPressed.current['e']) {
        map.setZoom(map.getZoom() + 0.05, { animate: false });
      }

      animationRef.current = requestAnimationFrame(animate);
    };

    animationRef.current = requestAnimationFrame(animate);

    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("keyup", handleKeyUp);
      if (animationRef.current) cancelAnimationFrame(animationRef.current);
    };
  }, [map]);

  return null;
}

export default function DroneSimulatorMap({ lat, lng }: { lat: number; lng: number }) {
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) return null;

  return (
    <div className="absolute inset-0 z-0">
      <MapContainer
        center={[lat, lng]}
        zoom={18}
        zoomSnap={0.05}
        zoomDelta={0.05}
        zoomControl={false}
        attributionControl={false}
        className="w-full h-full"
      >
        <TileLayer url={TILE_URL} attribution={TILE_ATTR} maxZoom={21} maxNativeZoom={19} />
        <DroneController />
      </MapContainer>
    </div>
  );
}
