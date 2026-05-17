export const API_BASE_URL = process.env.ADS_API_URL || "http://192.168.43.4:4030/api/v1";
export const API_KEY = process.env.ADS_API_KEY || "";

export async function fetchWithAuth(endpoint: string, options: RequestInit = {}) {
  const url = `${API_BASE_URL}${endpoint}`;
  
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 3000); // 3s timeout

  const headers = {
    ...options.headers,
    "x-api-key": API_KEY,
    "Content-Type": "application/json",
  };

  try {
    const response = await fetch(url, { ...options, headers, signal: controller.signal });
    clearTimeout(timeoutId);
    
    if (!response.ok) {
      console.error(`API Error on ${endpoint}:`, response.statusText);
      throw new Error(`API call failed: ${response.statusText}`);
    }
    
    return await response.json();
  } catch (error) {
    clearTimeout(timeoutId);
    throw error;
  }
}
