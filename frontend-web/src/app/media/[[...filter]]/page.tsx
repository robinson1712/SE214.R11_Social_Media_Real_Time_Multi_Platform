import { redirect } from "next/navigation";
import { decodeRoute } from "@/lib/route";

export default async function Page({
  params,
}: {
  params: Promise<{ filter?: string[] }>;
}) {
  const { filter } = await params;
  redirect(decodeRoute(filter?.join("/") || "") === "stories" ? "/stories" : "/notifications");
}
