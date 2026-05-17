import { NextResponse } from "next/server";
import { fetchWithAuth } from "@/lib/api";

const OPENAI_URL = "https://api.interneteco.systems/v1/chat/completions";
const OPENAI_KEY = process.env.LLM_API_KEY || "";
const MODEL = "gemini-3.1-flash-lite-preview";

export async function POST(req: Request) {
  try {
    const body = await req.json();
    const { messages } = body;

    // Fetch system context
    const [alerts, users, drones, logs] = await Promise.allSettled([
      fetchWithAuth("/admin/reports").catch(() => []),
      // Fallback to users array
      fetchWithAuth("/admin/drones").catch(() => []),
      fetchWithAuth("/admin/earthquake_logs").catch(() => []),
    ]);

    const contextData = {
      reports: alerts.status === "fulfilled" && Array.isArray(alerts.value) ? alerts.value.slice(0, 50) : [],
      users: users.status === "fulfilled" && Array.isArray(users.value) ? users.value.slice(0, 20) : [],
      drones: drones.status === "fulfilled" && Array.isArray(drones.value) ? drones.value.slice(0, 20) : [],
      earthquake_logs: logs.status === "fulfilled" && Array.isArray(logs.value) ? logs.value.slice(0, 100) : [],
    };

    const systemPrompt = `Sen, AFAD ve ADS (Acil Durum Sistemi) için geliştirilmiş ileri düzey bir veri analisti ve kriz yönetimi Yapay Zeka asistanısın. 
Adın: ADS Triage AI.
Kullanıcının sorduğu sorulara aşağıda verdiğim SİSTEM VERİLERİ (JSON) ile cevap vereceksin. 

ZORUNLU KURAL: Vereceğin TÜM cevaplar SADECE geçerli bir JSON objesi formatında olmalıdır. Markdown veya başka bir metin ekleme. JSON dışında hiçbir karakter kullanma.

Desteklediğin Widget formatları (Sadece bunlardan birini kullan):
1. Metin (Text): Sadece açıklama veya kısa cevap gerekiyorsa.
{"type": "text", "content": "Detaylı açıklaman..."}

2. Liste (List): Tablo veya liste halinde veri göstermen gerekiyorsa (örnek: son depremler, kritik ihbarlar, kullanıcı listesi).
{"type": "list", "title": "Listenin Başlığı", "columns": ["İsim", "Durum", "Konum"], "rows": [["Ahmet", "Kritik", "Balıkesir"], ["Ayşe", "Güvende", "İzmir"]]}

3. İstatistik (Stats): Sayısal özetler göstermen gerekiyorsa.
{"type": "stats", "items": [{"label": "Toplam İhbar", "value": 150}, {"label": "Kritik", "value": 12, "color": "red"}]}

4. Bar Grafiği (Bar Chart): Dağılım veya karşılaştırma göstermen gerekiyorsa. (Örn: deprem şiddetleri veya bölgelere göre ihbarlar)
{"type": "bar-chart", "title": "Bölgelere Göre İhbarlar", "data": [{"name": "Balıkesir", "value": 45}, {"name": "İzmir", "value": 12}]}

5. Pasta Grafiği (Pie Chart): Oransal dağılım göstermen gerekiyorsa.
{"type": "pie-chart", "title": "Durum Dağılımı", "data": [{"name": "Kritik", "value": 15}, {"name": "Güvende", "value": 85}]}

SİSTEM VERİLERİ (ÖZET):
${JSON.stringify(contextData)}

Eğer kullanıcı sistemde olmayan bir veri sorarsa, "Bu veriye şu an ulaşılamıyor" şeklinde text widget'ı dön. Kesinlikle JSON formatının dışına çıkma. Sadece JSON dondur, kod blogu falan kullanma.
`;

    const payload = {
      model: MODEL,
      messages: [
        { role: "system", content: systemPrompt },
        ...messages
      ],
      temperature: 0.1,
    };

    const response = await fetch(OPENAI_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${OPENAI_KEY}`
      },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      const errorText = await response.text();
      console.error("LLM API Error:", errorText);
      throw new Error(`LLM Error: ${response.status}`);
    }

    const data = await response.json();
    let assistantMessage = data.choices[0].message.content;

    // Remove markdown JSON formatting
    if (assistantMessage.startsWith("\`\`\`json")) {
      assistantMessage = assistantMessage.replace(/\`\`\`json\n?/, "").replace(/\`\`\`$/, "").trim();
    }

    // Validate JSON
    try {
      JSON.parse(assistantMessage);
    } catch (e) {
      console.error("Invalid JSON from LLM:", assistantMessage);
      return NextResponse.json({
        role: "assistant",
        content: JSON.stringify({ type: "text", content: "AI geçerli bir formatta cevap veremedi. Lütfen tekrar deneyin." })
      });
    }

    return NextResponse.json({
      role: "assistant",
      content: assistantMessage
    });

  } catch (error) {
    console.error("AI Route Error:", error);
    return NextResponse.json({ error: "İşlem sırasında bir hata oluştu." }, { status: 500 });
  }
}
