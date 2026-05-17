"use client";

import { useState, useRef, useEffect } from "react";
import { Send, Bot, User, Loader2, BarChart2, PieChart as PieChartIcon, List, Activity } from "lucide-react";
import { BarChart, Bar, XAxis, YAxis, Tooltip as RechartsTooltip, ResponsiveContainer, PieChart, Pie, Cell } from "recharts";

type Message = {
  role: "user" | "assistant";
  content: string; // User gets plain string. Assistant gets JSON string.
};

const COLORS = ["#00ffcc", "#ff003c", "#0066ff", "#ff9900", "#a100ff", "#10b981"];

export default function AiAssistantPage() {
  const [messages, setMessages] = useState<Message[]>([
    { role: "assistant", content: JSON.stringify({ type: "text", content: "Sistem devrede. ADS veritabanına bağlıyım. İhbarları, deprem loglarını veya kullanıcı istatistiklerini grafikler halinde isteyebilirsiniz." }) }
  ]);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || isLoading) return;

    const userMessage = input.trim();
    setInput("");
    setMessages(prev => [...prev, { role: "user", content: userMessage }]);
    setIsLoading(true);

    try {
      const apiMessages = messages.map(m => ({ role: m.role, content: m.content }));
      apiMessages.push({ role: "user", content: userMessage });

      const res = await fetch("/api/ai", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ messages: apiMessages })
      });

      if (!res.ok) throw new Error("API hatası");

      const data = await res.json();
      setMessages(prev => [...prev, data]);
    } catch (error) {
      console.error(error);
      setMessages(prev => [...prev, { role: "assistant", content: JSON.stringify({ type: "text", content: "Sistemle bağlantı kurulamadı. Lütfen tekrar deneyin." }) }]);
    } finally {
      setIsLoading(false);
    }
  };

  const renderWidget = (jsonStr: string) => {
    try {
      const widget = JSON.parse(jsonStr);

      if (widget.type === "text") {
        return <p className="text-slate-300 leading-relaxed text-sm">{widget.content}</p>;
      }

      if (widget.type === "list") {
        return (
          <div className="bg-[#0f172a]/50 border border-slate-700/50 rounded-xl overflow-hidden shadow-lg mt-2">
            <div className="bg-slate-800/50 px-4 py-3 border-b border-slate-700/50 flex items-center gap-2">
              <List size={16} className="text-emerald-400" />
              <h3 className="text-slate-200 font-bold text-sm tracking-wide">{widget.title}</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-300">
                <thead className="bg-slate-800/30 text-xs uppercase text-slate-400">
                  <tr>
                    {widget.columns?.map((col: string, i: number) => (
                      <th key={i} className="px-4 py-3">{col}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-700/50">
                  {widget.rows?.map((row: string[], i: number) => (
                    <tr key={i} className="hover:bg-slate-800/30 transition-colors">
                      {row.map((cell: string, j: number) => (
                        <td key={j} className="px-4 py-3 whitespace-nowrap">{cell}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        );
      }

      if (widget.type === "stats") {
        return (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-2">
            {widget.items?.map((item: any, i: number) => (
              <div key={i} className="bg-[#0f172a]/50 border border-slate-700/50 rounded-xl p-4 flex flex-col items-center justify-center text-center shadow-lg">
                <span className="text-xs font-semibold text-slate-400 uppercase tracking-widest mb-1">{item.label}</span>
                <span className="text-2xl font-bold text-white" style={{ color: item.color || "#fff" }}>{item.value}</span>
              </div>
            ))}
          </div>
        );
      }

      if (widget.type === "bar-chart") {
        return (
          <div className="bg-[#0f172a]/50 border border-slate-700/50 rounded-xl p-4 shadow-lg mt-2 h-[400px] xl:h-[500px]">
            <div className="flex items-center gap-2 mb-4 px-2">
              <BarChart2 size={16} className="text-blue-400" />
              <h3 className="text-slate-200 font-bold text-sm tracking-wide">{widget.title}</h3>
            </div>
            <ResponsiveContainer width="100%" height="85%">
              <BarChart data={widget.data}>
                <XAxis dataKey="name" stroke="#64748b" fontSize={12} tickLine={false} axisLine={false} />
                <YAxis stroke="#64748b" fontSize={12} tickLine={false} axisLine={false} />
                <RechartsTooltip 
                  contentStyle={{ backgroundColor: "#0f172a", borderColor: "#334155", borderRadius: "8px", color: "#f8fafc" }}
                  itemStyle={{ color: "#00ffcc" }}
                  cursor={{ fill: "rgba(255,255,255,0.05)" }}
                />
                <Bar dataKey="value" fill="#0066ff" radius={[4, 4, 0, 0]}>
                  {widget.data?.map((entry: any, index: number) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        );
      }

      if (widget.type === "pie-chart") {
        return (
          <div className="bg-[#0f172a]/50 border border-slate-700/50 rounded-xl p-4 shadow-lg mt-2 h-[400px] xl:h-[500px]">
            <div className="flex items-center gap-2 mb-4 px-2">
              <PieChartIcon size={16} className="text-purple-400" />
              <h3 className="text-slate-200 font-bold text-sm tracking-wide">{widget.title}</h3>
            </div>
            <ResponsiveContainer width="100%" height="85%">
              <PieChart>
                <Pie data={widget.data} cx="50%" cy="50%" innerRadius={60} outerRadius={80} paddingAngle={5} dataKey="value">
                  {widget.data?.map((entry: any, index: number) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <RechartsTooltip 
                  contentStyle={{ backgroundColor: "#0f172a", borderColor: "#334155", borderRadius: "8px", color: "#f8fafc" }}
                  itemStyle={{ color: "#00ffcc" }}
                />
              </PieChart>
            </ResponsiveContainer>
          </div>
        );
      }

      return <p className="text-red-400">Desteklenmeyen Widget Tipi: {widget.type}</p>;
    } catch (e) {
      return <p className="text-slate-300">{jsonStr}</p>;
    }
  };

  return (
    <div className="w-full h-screen flex flex-col bg-[#050505] overflow-hidden font-sans">
      
      {/* ── Header ── */}
      <div className="h-16 px-6 bg-slate-900/50 border-b border-slate-800/60 flex items-center justify-between flex-shrink-0 backdrop-blur-md">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-indigo-500/10 border border-indigo-500/30 flex items-center justify-center">
            <Bot size={22} className="text-indigo-400" />
          </div>
          <div>
            <h1 className="text-sm font-bold text-white tracking-wide">ADS Triage AI</h1>
            <div className="flex items-center gap-1.5 mt-0.5">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
              <span className="text-[10px] text-slate-400 font-mono tracking-widest uppercase">System: Online</span>
            </div>
          </div>
        </div>
        <div className="hidden md:flex items-center gap-2 bg-slate-800/50 px-3 py-1.5 rounded-lg border border-slate-700/50">
          <Activity size={14} className="text-emerald-400" />
          <span className="text-[10px] font-bold text-slate-300 tracking-wider">GEMINI-3.1-FLASH</span>
        </div>
      </div>

      {/* ── Chat Alanı ── */}
      <div ref={scrollRef} className="flex-1 overflow-y-auto p-4 md:p-8 space-y-6 custom-scrollbar bg-gradient-to-b from-transparent to-[#0a0a0a]">
        {messages.map((m, i) => (
          <div key={i} className={`flex gap-4 ${m.role === "user" ? "flex-row-reverse" : "flex-row"}`}>
            
            <div className={`w-8 h-8 flex-shrink-0 rounded-full flex items-center justify-center mt-1 shadow-md ${
              m.role === "user" ? "bg-slate-700 text-white" : "bg-indigo-600 text-white"
            }`}>
              {m.role === "user" ? <User size={16} /> : <Bot size={16} />}
            </div>

            <div className={`${m.role === "user" ? "max-w-[85%] md:max-w-[75%] text-right" : "flex-1 min-w-0 text-left"}`}>
              {m.role === "user" ? (
                <div className="inline-block bg-slate-800 text-white px-5 py-3 rounded-2xl rounded-tr-sm shadow-md text-sm leading-relaxed">
                  {m.content}
                </div>
              ) : (
                <div className="w-full">
                  {renderWidget(m.content)}
                </div>
              )}
            </div>

          </div>
        ))}
        {isLoading && (
          <div className="flex gap-4 flex-row">
            <div className="w-8 h-8 flex-shrink-0 rounded-full bg-indigo-600/50 text-white flex items-center justify-center mt-1">
              <Loader2 size={16} className="animate-spin" />
            </div>
            <div className="bg-slate-800/50 border border-slate-700/50 px-5 py-3 rounded-2xl rounded-tl-sm flex items-center gap-2 shadow-md">
              <span className="w-2 h-2 rounded-full bg-indigo-400 animate-bounce" style={{ animationDelay: "0ms" }} />
              <span className="w-2 h-2 rounded-full bg-indigo-400 animate-bounce" style={{ animationDelay: "150ms" }} />
              <span className="w-2 h-2 rounded-full bg-indigo-400 animate-bounce" style={{ animationDelay: "300ms" }} />
            </div>
          </div>
        )}
      </div>

      {/* ── Input Alanı ── */}
      <div className="p-4 md:p-6 bg-slate-900/50 border-t border-slate-800/60 backdrop-blur-md flex-shrink-0">
        <form onSubmit={handleSubmit} className="relative flex items-center w-full mx-auto">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            disabled={isLoading}
            placeholder="Kullanıcıları listele, durum grafiği çıkar, son ihbarları getir..."
            className="w-full bg-slate-950 border border-slate-700/60 rounded-full pl-6 pr-14 py-4 text-sm text-white placeholder:text-slate-500 focus:outline-none focus:border-indigo-500/50 focus:ring-1 focus:ring-indigo-500/50 transition-all shadow-inner disabled:opacity-50"
          />
          <button
            type="submit"
            disabled={!input.trim() || isLoading}
            className="absolute right-2 top-1/2 -translate-y-1/2 w-10 h-10 bg-indigo-600 hover:bg-indigo-500 disabled:bg-slate-700 text-white rounded-full flex items-center justify-center transition-all shadow-md active:scale-95"
          >
            <Send size={16} className={input.trim() && !isLoading ? "translate-x-[1px]" : ""} />
          </button>
        </form>
      </div>

      {/* ── Özel Scrollbar ── */}
      <style jsx global>{`
        .custom-scrollbar::-webkit-scrollbar { width: 6px; }
        .custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
        .custom-scrollbar::-webkit-scrollbar-thumb { background: #334155; border-radius: 10px; }
        .custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #475569; }
      `}</style>
    </div>
  );
}