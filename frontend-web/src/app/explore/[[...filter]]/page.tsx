export const metadata = {title: "Explore · VibeSocial"};
import { decodeRoute } from "@/lib/route";
import { ExploreScreen } from "@/components/screens";

export default async function Page({
  params,
}: {
  params: Promise<{ filter?: string[] }>;
}) {
  const { filter } = await params;
  const value = decodeRoute(filter?.join("/") || "all");
  return <ExploreScreen key={value} filter={value} />;
}
