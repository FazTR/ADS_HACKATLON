# 1. Builder Stage
FROM rust:1.80-slim-bookworm AS builder
WORKDIR /app

# Gerekli derleme araçlarını kur (Eğer OpenSSL veya C kütüphaneleri gerekiyorsa)
RUN apt-get update && apt-get install -y pkg-config libssl-dev

# Önce sadece bağımlılıkları derleyerek Docker cache mekanizmasından faydalan
COPY Cargo.toml Cargo.lock ./
RUN mkdir src && echo "fn main() {}" > src/main.rs
RUN cargo build --release
RUN rm -rf src

# Şimdi asıl kaynak kodlarını kopyala ve projeyi derle
COPY src ./src
# Son değiştirilme tarihini güncelle ki rust yeniden derlesin
RUN touch src/main.rs
RUN cargo build --release

# 2. Runtime Stage (Minimal)
FROM debian:bookworm-slim
WORKDIR /app

# OpenSSL çalışma zamanı bağımlılıklarını kur ve apt önbelleğini temizle (imajı ufaltır)
RUN apt-get update && apt-get install -y libssl3 ca-certificates && rm -rf /var/lib/apt/lists/*

# Builder aşamasındaki derlenmiş dosyayı çalışma zamanına kopyala
COPY --from=builder /app/target/release/ads-backend /usr/local/bin/ads-backend

# API portu
EXPOSE 4030

# Çalıştır
CMD ["ads-backend"]
