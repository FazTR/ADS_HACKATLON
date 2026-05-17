"use client";

import { useState, useEffect, useRef } from "react";
import {
  UserCircle,
  Search,
  Heart,
  MapPin,
  Calendar,
  Home,
  Phone,
  Filter,
  ChevronDown,
  X,
  User,
  Clock,
} from "lucide-react";
import { User as UserType } from "@/lib/db";

function age(birthDate?: string): string {
  if (!birthDate) return "—";
  const years = Math.floor(
    (Date.now() - new Date(birthDate).getTime()) /
      (1000 * 60 * 60 * 24 * 365.25)
  );
  return `${years} yaş`;
}

function formatDate(iso?: string): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("tr-TR", {
    day: "2-digit",
    month: "long",
    year: "numeric",
  });
}

function UserModal({
  user,
  onClose,
}: {
  user: UserType;
  onClose: () => void;
}) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      onClick={onClose}
    >
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />

      <div
        className="relative bg-card border border-border rounded-3xl shadow-2xl w-full max-w-lg overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Başlık */}
        <div className="p-6 border-b border-border bg-gradient-to-r from-blue-500/10 to-transparent">
          <div className="flex items-start justify-between">
            <div className="flex items-center gap-4">
              {/* Avatar */}
              <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white text-xl font-bold shadow-lg shadow-blue-500/20">
                {user.full_name
                  ? user.full_name
                      .split(" ")
                      .map((w) => w[0])
                      .slice(0, 2)
                      .join("")
                      .toUpperCase()
                  : "?"}
              </div>
              <div>
                <h2 className="text-xl font-bold text-foreground">
                  {user.full_name ?? "—"}
                </h2>
                <p className="text-sm text-slate-500 font-mono mt-0.5">
                  {user.device_id}
                </p>
              </div>
            </div>
            <button
              onClick={onClose}
              className="w-8 h-8 rounded-full bg-hover flex items-center justify-center text-slate-500 hover:text-foreground transition-colors"
            >
              <X size={16} />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="p-6 space-y-5 max-h-[65vh] overflow-y-auto">
          {/* Personal */}
          <Section title="Kişisel Bilgiler">
            <Row icon={User} label="Ad Soyad" value={user.full_name} />
            <Row icon={Heart} label="Kan Grubu" value={user.blood_type} accent />
            <Row
              icon={Calendar}
              label="Doğum Tarihi"
              value={user.birth_date ? `${formatDate(user.birth_date)} — ${age(user.birth_date)}` : undefined}
            />
            <Row icon={User} label="Cinsiyet" value={user.gender} />
          </Section>

          {/* Location */}
          <Section title="Konum Bilgileri">
            <Row
              icon={MapPin}
              label="Şehir / İlçe"
              value={
                user.city && user.district
                  ? `${user.city} — ${user.district}`
                  : user.city ?? user.district
              }
            />
            <Row icon={Home} label="Adres" value={user.address} />
          </Section>

          {/* System */}
          <Section title="Sistem Bilgileri">
            <Row icon={Phone} label="Cihaz ID" value={user.device_id} mono />
            <Row
              icon={Clock}
              label="Kayıt Tarihi"
              value={formatDate(user.registered_at)}
            />
          </Section>
        </div>

        <div className="px-6 pb-6">
          <button
            onClick={onClose}
            className="w-full bg-hover border border-border text-foreground hover:bg-border py-3 rounded-2xl font-bold transition-all text-sm"
          >
            Kapat
          </button>
        </div>
      </div>
    </div>
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-3">
        {title}
      </p>
      <div className="grid grid-cols-1 gap-2.5">{children}</div>
    </div>
  );
}

function Row({
  icon: Icon,
  label,
  value,
  accent,
  mono,
}: {
  icon: any;
  label: string;
  value?: string | null;
  accent?: boolean;
  mono?: boolean;
}) {
  return (
    <div className="bg-hover rounded-2xl p-3.5 border border-border flex items-start gap-3">
      <Icon
        size={15}
        className={`mt-0.5 flex-shrink-0 ${
          accent ? "text-red-500" : "text-slate-500"
        }`}
      />
      <div className="min-w-0">
        <p className="text-[9px] font-bold text-slate-500 uppercase tracking-wider mb-0.5">
          {label}
        </p>
        <p
          className={`text-sm font-bold text-foreground truncate ${
            mono ? "font-mono" : ""
          } ${accent ? "text-red-500" : ""}`}
        >
          {value ?? "—"}
        </p>
      </div>
    </div>
  );
}

export default function KullaniciBilgileri() {
  const [users, setUsers] = useState<UserType[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [genderFilter, setGenderFilter] = useState("Tümü");
  const [bloodFilter, setBloodFilter] = useState("Tümü");
  const [selected, setSelected] = useState<UserType | null>(null);
  const isActiveRef = useRef(true);

  useEffect(() => {
    isActiveRef.current = true;
    let timeoutId: NodeJS.Timeout;

    const poll = async () => {
      if (!isActiveRef.current) return;
      try {
        const res = await fetch("/api/users");
        if (res.ok) {
          const data = await res.json();
          if (Array.isArray(data)) setUsers(data);
        }
      } catch (e) {
        console.error("Kullanıcı verisi alınamadı:", e);
      } finally {
        setIsLoading(false);
      }
      if (isActiveRef.current) timeoutId = setTimeout(poll, 15000);
    };

    poll();
    return () => {
      isActiveRef.current = false;
      clearTimeout(timeoutId);
    };
  }, []);

  // Unique blood types and genders
  const bloodTypes = [
    "Tümü",
    ...Array.from(new Set(users.map((u) => u.blood_type).filter(Boolean))).sort(),
  ] as string[];
  const genders = [
    "Tümü",
    ...Array.from(new Set(users.map((u) => u.gender).filter(Boolean))),
  ] as string[];

  const filtered = users.filter((u) => {
    const q = search.toLowerCase();
    const matchSearch =
      !q ||
      u.full_name?.toLowerCase().includes(q) ||
      u.city?.toLowerCase().includes(q) ||
      u.district?.toLowerCase().includes(q) ||
      u.device_id?.toLowerCase().includes(q) ||
      u.blood_type?.toLowerCase().includes(q) ||
      u.address?.toLowerCase().includes(q);
    const matchGender = genderFilter === "Tümü" || u.gender === genderFilter;
    const matchBlood = bloodFilter === "Tümü" || u.blood_type === bloodFilter;
    return matchSearch && matchGender && matchBlood;
  });

  const initials = (name?: string) =>
    name
      ? name
          .split(" ")
          .map((w) => w[0])
          .slice(0, 2)
          .join("")
          .toUpperCase()
      : "?";

  const avatarGradients = [
    "from-blue-500 to-indigo-600",
    "from-purple-500 to-pink-600",
    "from-green-500 to-teal-600",
    "from-orange-500 to-red-600",
    "from-cyan-500 to-blue-600",
    "from-rose-500 to-pink-600",
  ];

  return (
    <>
      {selected && (
        <UserModal user={selected} onClose={() => setSelected(null)} />
      )}

      <div className="space-y-6 w-full">
        {/* Başlık */}
        <div className="flex justify-between items-end">
          <div>
            <h1 className="text-3xl font-bold text-foreground tracking-tight flex items-center gap-3">
              <UserCircle className="text-purple-500" size={32} />
              Kullanıcı Bilgileri
            </h1>
            <p className="text-slate-500 mt-2 text-sm font-medium">
              Sisteme kayıtlı tüm vatandaşların kayıt bilgileri
            </p>
          </div>
          <div className="flex items-center gap-2 bg-card border border-border px-4 py-2 rounded-full">
            <span className="w-2 h-2 rounded-full bg-purple-500 animate-pulse" />
            <span className="text-xs font-bold text-foreground tracking-wider uppercase">
              {users.length} Kayıtlı Kullanıcı
            </span>
          </div>
        </div>

        {/* Özet Kartlar */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {[
            { label: "Toplam Kayıt", value: users.length, color: "text-purple-500", bg: "bg-purple-500/10", border: "border-purple-500/20" },
            { label: "Kadın", value: users.filter((u) => u.gender === "Kadın").length, color: "text-pink-500", bg: "bg-pink-500/10", border: "border-pink-500/20" },
            { label: "Erkek", value: users.filter((u) => u.gender === "Erkek").length, color: "text-blue-500", bg: "bg-blue-500/10", border: "border-blue-500/20" },
            {
              label: "Kan Grubu Çeşidi",
              value: new Set(users.map((u) => u.blood_type).filter(Boolean)).size,
              color: "text-red-500",
              bg: "bg-red-500/10",
              border: "border-red-500/20",
            },
          ].map((stat) => (
            <div
              key={stat.label}
              className={`bg-card backdrop-blur-xl border rounded-3xl p-5 ${stat.border}`}
            >
              <p className={`text-4xl font-bold tracking-tight mb-2 ${stat.color}`}>
                {stat.value}
              </p>
              <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">
                {stat.label}
              </p>
            </div>
          ))}
        </div>

        {/* Arama + Filtreler */}
        <div className="flex gap-3 flex-col sm:flex-row">
          <div className="relative flex-1">
            <Search
              size={16}
              className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-500"
            />
            <input
              type="text"
              placeholder="Ad, şehir, cihaz, kan grubu ile ara..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full bg-card border border-border rounded-2xl pl-11 pr-4 py-3.5 text-sm text-foreground placeholder:text-slate-500 focus:outline-none focus:border-purple-500 transition-colors font-medium"
            />
          </div>
          <FilterSelect
            value={genderFilter}
            onChange={setGenderFilter}
            options={genders}
            icon={<User size={14} className="text-slate-500" />}
          />
          <FilterSelect
            value={bloodFilter}
            onChange={setBloodFilter}
            options={bloodTypes}
            icon={<Heart size={14} className="text-red-400" />}
          />
        </div>

        {/* Sonuç sayısı */}
        {(search || genderFilter !== "Tümü" || bloodFilter !== "Tümü") && (
          <p className="text-sm text-slate-500 font-medium">
            <span className="font-bold text-foreground">{filtered.length}</span>{" "}
            sonuç bulundu
          </p>
        )}

        {/* Kullanıcı Kartları */}
        {isLoading ? (
          <div className="text-center py-24 text-slate-500 font-bold">
            Kullanıcı listesi yükleniyor...
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-24 text-slate-500 font-bold">
            {search || genderFilter !== "Tümü" || bloodFilter !== "Tümü"
              ? "Arama kriterine uyan kullanıcı bulunamadı."
              : "Henüz kayıtlı kullanıcı yok."}
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {filtered.map((user, idx) => (
              <button
                key={user.id}
                onClick={() => setSelected(user)}
                className="bg-card backdrop-blur-xl border border-border rounded-3xl p-5 text-left transition-all hover:-translate-y-1 hover:shadow-lg hover:border-purple-500/30 duration-200 group"
              >
                {/* Üst: Avatar + İsim + Kan Grubu */}
                <div className="flex items-start justify-between mb-4">
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-12 h-12 rounded-2xl bg-gradient-to-br ${
                        avatarGradients[idx % avatarGradients.length]
                      } flex items-center justify-center text-white font-bold text-base shadow-lg flex-shrink-0`}
                    >
                      {initials(user.full_name)}
                    </div>
                    <div className="min-w-0">
                      <p className="text-foreground font-bold text-sm leading-tight truncate">
                        {user.full_name ?? "İsim Yok"}
                      </p>
                      <p className="text-slate-500 text-[10px] font-bold font-mono mt-0.5 truncate">
                        {user.device_id}
                      </p>
                    </div>
                  </div>
                  {user.blood_type && (
                    <div className="flex items-center gap-1 bg-red-500/10 border border-red-500/20 px-2.5 py-1 rounded-xl flex-shrink-0 ml-2">
                      <Heart size={10} className="text-red-500" />
                      <span className="text-[10px] font-bold text-red-500 tracking-wider">
                        {user.blood_type}
                      </span>
                    </div>
                  )}
                </div>

                {/* Bilgi satırları */}
                <div className="space-y-2">
                  {(user.city || user.district) && (
                    <InfoLine icon={MapPin}>
                      {[user.city, user.district].filter(Boolean).join(" — ")}
                    </InfoLine>
                  )}
                  {user.address && (
                    <InfoLine icon={Home}>{user.address}</InfoLine>
                  )}
                  <div className="flex items-center justify-between">
                    {user.birth_date && (
                      <InfoLine icon={Calendar}>{age(user.birth_date)}</InfoLine>
                    )}
                    {user.gender && (
                      <span className="text-[10px] font-bold text-slate-500 bg-hover border border-border px-2 py-0.5 rounded-lg">
                        {user.gender}
                      </span>
                    )}
                  </div>
                  {user.registered_at && (
                    <InfoLine icon={Clock}>
                      Kayıt: {formatDate(user.registered_at)}
                    </InfoLine>
                  )}
                </div>

                {/* Hover efekti */}
                <div className="mt-4 pt-3 border-t border-border flex items-center justify-between opacity-0 group-hover:opacity-100 transition-opacity">
                  <span className="text-xs font-bold text-purple-500">
                    Tüm Bilgileri Gör
                  </span>
                  <div className="w-6 h-6 rounded-full bg-purple-500/10 flex items-center justify-center">
                    <ChevronDown size={12} className="text-purple-500 -rotate-90" />
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </>
  );
}

function InfoLine({
  icon: Icon,
  children,
}: {
  icon: any;
  children: React.ReactNode;
}) {
  return (
    <div className="flex items-center gap-2 text-xs text-slate-500">
      <Icon size={11} className="flex-shrink-0" />
      <span className="font-medium truncate">{children}</span>
    </div>
  );
}

function FilterSelect({
  value,
  onChange,
  options,
  icon,
}: {
  value: string;
  onChange: (v: string) => void;
  options: string[];
  icon: React.ReactNode;
}) {
  return (
    <div className="relative">
      <div className="absolute left-4 top-1/2 -translate-y-1/2 pointer-events-none">
        {icon}
      </div>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="bg-card border border-border rounded-2xl pl-10 pr-10 py-3.5 text-sm text-foreground focus:outline-none focus:border-purple-500 transition-colors font-medium appearance-none cursor-pointer min-w-[150px]"
      >
        {options.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </select>
      <ChevronDown
        size={14}
        className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none"
      />
    </div>
  );
}
