import type { Metadata } from "next";
import { LoginScreen } from "@/components/login-screen";
import "./login.css";

export const metadata: Metadata = {
  title: "Đăng nhập · VibeSocial",
  description: "Trở lại với những người cùng tần số và những câu chuyện của bạn.",
};

export default function Page() {
  return <LoginScreen />;
}
