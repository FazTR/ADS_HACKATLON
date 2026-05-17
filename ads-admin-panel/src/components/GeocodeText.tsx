"use client";

import { useState, useEffect } from "react";

// Memory cache
const cache = new Map<string, string>();

// Request queue for rate limiting
let pendingPromise: Promise<void> = Promise.resolve();

export function GeocodeText({ lat, lng, fallback }: { lat: number; lng: number; fallback?: string }) {
  const [location, setLocation] = useState<string>(fallback || "Konum çözümleniyor...");

  useEffect(() => {
    let isMounted = true;
    // Cache by coordinate (approx 100m)

    if (cache.has(key)) {
      setLocation(cache.get(key)!);
      return;
    }

    const fetchLocation = async () => {
      // Sequential requests for Nominatim rate limits
      pendingPromise = pendingPromise.then(async () => {
        if (!isMounted) return;
        
        // Skip if cached
        if (cache.has(key)) {
          setLocation(cache.get(key)!);
          return;
        }

        try {
          const res = await fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=12&addressdetails=1`, {
            headers: { 'Accept-Language': 'tr' }
          });
          
          if (!res.ok) throw new Error("Geocode failed");
          
          const data = await res.json();
          const address = data.address || {};
          
          const city = address.city || address.province || address.state || "Bilinmeyen İl";
          const district = address.town || address.county || address.village || address.suburb || "Bilinmeyen İlçe";
          
          let result = `${city}`;
          if (district !== "Bilinmeyen İlçe" && district !== city) {
            result += ` — ${district}`;
          }

          cache.set(key, result);
          if (isMounted) setLocation(result);
          
          // 1s rate limit delay
          await new Promise(resolve => setTimeout(resolve, 1000));
          
        } catch (error) {
          console.error("Geocoding hatası:", error);
          if (isMounted) setLocation(fallback || "Konum Bulunamadı");
        }
      });
    };

    fetchLocation();

    return () => {
      isMounted = false;
    };
  }, [lat, lng, fallback]);

  return <span>{location}</span>;
}
