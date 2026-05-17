export type Report = {
  id: string;
  device_id: string;
  status: string;           // ok | injured | under_rubble
  latitude: number;
  longitude: number;
  battery_level: number;
  reported_at: string;
  // Personal info
  full_name?: string;
  birth_date?: string;
  address?: string;
  city?: string;
  district?: string;
  gender?: string;
  blood_type?: string;
};

export type Alert = {
  id: string;
  device_id: string;
  timestamp: string;
  location: {
    lat: number;
    lng: number;
    city: string;
    district: string;
  };
  status: string;
  callerStatus: string;
  message: string;
  battery_level: number;
  // Personal info
  full_name?: string;
  birth_date?: string;
  address?: string;
  gender?: string;
  blood_type?: string;
};

export type User = {
  id: string;
  device_id: string;
  full_name: string;
  birth_date?: string;
  address?: string;
  city?: string;
  district?: string;
  gender?: string;
  blood_type?: string;
  registered_at: string;
};

export type DroneTelemety = {
  drone_id: string;
  status: string;       // active | returning | offline
  battery_level: number;
  altitude: number;
  speed: number;
  latitude: number;
  longitude: number;
  last_seen: string;
};

export const db = {
  getAlerts: () => [] as Alert[],
  updateAlertStatus: (id: string, status: string) => { return null; },
};
