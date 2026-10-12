export const metadata = {title: "Friends · VibeSocial"};
import { decodeRoute } from "@/lib/route";
import { FriendsScreen } from "@/components/screens";

export default async function Page({
  params,
}: {
  params: Promise<{ filter?: string[] }>;
}) {
  const { filter } = await params;
  const value = decodeRoute(filter?.join("/") || "all");
  return <FriendsScreen key={value} filter={value} />;
}
