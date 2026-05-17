import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";

export async function PUT(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  try {
    const body = await request.json();
    const { id } = await params;

    if (!body.status) {
      return NextResponse.json({ error: "Durum (status) gerekli" }, { status: 400 });
    }

    const updatedAlert = db.updateAlertStatus(id, body.status);

    if (!updatedAlert) {
      return NextResponse.json({ error: "İhbar bulunamadı" }, { status: 404 });
    }

    return NextResponse.json(updatedAlert);
  } catch (error) {
    return NextResponse.json({ error: "Sunucu hatası" }, { status: 500 });
  }
}
