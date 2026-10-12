export const metadata = {title: "Profile · VibeSocial"};
import { decodeRoute } from "@/lib/route";
import { ProfileScreen } from "@/components/screens";

export default async function Page({
  params,
}: {
  params: Promise<{ name?: string[] }>;
}) {
  const { name } = await params;
  const value = decodeRoute(name?.join("/") || "");
  return <ProfileScreen key={value} name={value} />;
}
