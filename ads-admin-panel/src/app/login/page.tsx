"use client";

import { useState } from "react";
import { signIn } from "next-auth/react";
import { Loader2, Shield, AlertCircle } from "lucide-react";
import { useRouter } from "next/navigation";

export default function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const router = useRouter();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError("");

    const result = await signIn("credentials", {
      username,
      password,
      redirect: false,
    });

    if (result?.error) {
      setError("Giriş başarısız.");
      setIsLoading(false);
    } else {
      router.push("/");
      router.refresh();
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-black text-white">
      <div className="w-full max-w-sm bg-neutral-900 border border-neutral-800/50 rounded-3xl p-8">
        
        <div className="flex justify-center mb-8">
          <Shield size={40} className="text-neutral-500" strokeWidth={1.5} />
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {error && (
            <div className="flex items-center gap-2 text-red-400 bg-red-950/30 border border-red-900/50 px-4 py-3 rounded-xl text-sm font-medium">
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
          )}

          <div>
            <input 
              type="text" 
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              disabled={isLoading}
              className="w-full bg-neutral-800 text-white rounded-xl px-4 py-3 outline-none focus:ring-1 focus:ring-neutral-400 transition-all placeholder:text-neutral-500 text-sm"
              placeholder="Kullanıcı Adı"
            />
          </div>

          <div>
            <input 
              type="password" 
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              disabled={isLoading}
              className="w-full bg-neutral-800 text-white rounded-xl px-4 py-3 outline-none focus:ring-1 focus:ring-neutral-400 transition-all placeholder:text-neutral-500 text-sm"
              placeholder="Şifre"
            />
          </div>

          <button 
            type="submit" 
            disabled={isLoading}
            className="w-full bg-white text-black hover:bg-neutral-200 active:scale-95 font-medium rounded-xl px-4 py-3 transition-all flex items-center justify-center gap-2 text-sm mt-2 disabled:opacity-50 disabled:active:scale-100"
          >
            {isLoading ? <Loader2 size={18} className="animate-spin text-neutral-500" /> : "Giriş Yap"}
          </button>
        </form>
        
      </div>
    </div>
  );
}
