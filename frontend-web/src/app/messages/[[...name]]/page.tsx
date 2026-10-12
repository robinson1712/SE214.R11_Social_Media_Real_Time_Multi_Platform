export const metadata = {title: "Messages · VibeSocial"};
import { decodeRoute } from "@/lib/route";
import { MessagesScreen } from "@/components/screens";

export default async function Page({
  params,
}: {
  params: Promise<{ name?: string[] }>;
}) {
  const { name } = await params;
  const value = decodeRoute(name?.join("/") || "");
  return <MessagesScreen key={value} name={value} />;
}
