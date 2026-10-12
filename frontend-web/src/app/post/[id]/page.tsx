export const metadata = {title: "Post · VibeSocial"};
import { decodeRoute } from "@/lib/route";
import { PostDetail } from "@/components/post-detail";
export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <PostDetail id={decodeRoute(id)} />;
}
