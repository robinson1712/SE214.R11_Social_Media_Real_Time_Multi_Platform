export const metadata = {title: "Discussion · VibeSocial"};
import { decodeRoute } from "@/lib/route";
import { TopicScreen } from "@/components/screens";

export default async function Page({
  params,
}: {
  params: Promise<{ id?: string[] }>;
}) {
  const { id } = await params;
  const value = decodeRoute(id?.join("/") || "");
  return <TopicScreen key={value} id={value} />;
}
